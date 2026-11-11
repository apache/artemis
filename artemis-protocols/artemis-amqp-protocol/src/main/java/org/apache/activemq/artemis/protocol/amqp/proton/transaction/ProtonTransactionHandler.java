/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.protocol.amqp.proton.transaction;

import java.nio.ByteBuffer;

import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.activemq.artemis.protocol.amqp.proton.AMQPConnectionContext;
import org.apache.activemq.artemis.protocol.amqp.proton.ProtonDeliveryHandler;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transaction.Declare;
import org.apache.qpid.proton.amqp.transaction.Declared;
import org.apache.qpid.proton.amqp.transaction.Discharge;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.message.Message;
import org.apache.qpid.proton.message.impl.MessageImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * handles an amqp Coordinator to deal with transaction boundaries etc
 */
public class ProtonTransactionHandler implements ProtonDeliveryHandler {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final int amqpCredit;
   private final int amqpLowMark;
   private Transaction currentTx;

   final AMQPSessionCallback sessionSPI;
   final AMQPConnectionContext connection;

   private final ByteBuffer DECODE_BUFFER = ByteBuffer.allocate(64);

   public ProtonTransactionHandler(AMQPSessionCallback sessionSPI, AMQPConnectionContext connection) {
      this.sessionSPI = sessionSPI;
      this.connection = connection;
      this.amqpCredit = connection.getAmqpCredits();
      this.amqpLowMark = connection.getAmqpLowCredits();
      this.sessionSPI.setTransactionHandler(this);
   }

   @Override
   public void onMessage(Delivery delivery) throws ActiveMQAMQPException {
      final Receiver receiver;
      try {
         receiver = ((Receiver) delivery.getLink());

         if (!delivery.isReadable()) {
            return;
         }

         ByteBuffer buffer;
         MessageImpl msg;

         // Replenish coordinator receiver credit on exhaustion so sender can continue
         // transaction declare and discahrge operations.
         if (receiver.getCredit() < amqpLowMark) {
            receiver.flow(amqpCredit);
         }

         // Declare is generally 7 bytes and discharge is around 48 depending on the
         // encoded size of the TXN ID.  Decode buffer has a bit of extra space but if
         // the incoming request is to big just use a scratch buffer.
         if (delivery.available() > DECODE_BUFFER.capacity()) {
            buffer = ByteBuffer.allocate(delivery.available());
         } else {
            buffer = (ByteBuffer) DECODE_BUFFER.clear();
         }

         // Update Buffer for the next incoming command.
         buffer.limit(receiver.recv(buffer.array(), buffer.arrayOffset(), buffer.capacity()));

         receiver.advance();

         msg = decodeMessage(buffer);

         Object action = ((AmqpValue) msg.getBody()).getValue();
         if (action instanceof Declare) {
            Binary txID = sessionSPI.newTransaction();
            Declared declared = new Declared();
            declared.setTxnId(txID);
            currentTx = sessionSPI.getTransaction(txID, false);
            IOCallback ioAction = new IOCallback() {
               @Override
               public void done() {
                  connection.runLater(() -> {
                     delivery.settle();
                     delivery.disposition(declared);
                     connection.flush();
                  });
               }

               @Override
               public void onError(int errorCode, String errorMessage) {
                  currentTx = null;
               }
            };
            sessionSPI.afterIO(ioAction);
         } else if (action instanceof Discharge discharge) {

            Binary txID = discharge.getTxnId();
            ProtonTransactionImpl tx = (ProtonTransactionImpl) sessionSPI.getTransaction(txID, true);
            tx.discharge();

            IOCallback ioAction = new IOCallback() {
               @Override
               public void done() {
                  connection.runLater(() -> {
                     delivery.settle();
                     delivery.disposition(new Accepted());
                     currentTx = null;
                     connection.flush();
                  });
               }

               @Override
               public void onError(int errorCode, String errorMessage) {

               }
            };

            if (discharge.getFail()) {
               sessionSPI.withinSessionExecutor(() -> {
                  try {
                     tx.rollback();
                     sessionSPI.afterIO(ioAction);
                  } catch (Throwable e) {
                     txError(delivery, e);
                  }
               });
            } else {
               sessionSPI.withinSessionExecutor(() -> {
                  try {
                     tx.commit();
                     sessionSPI.afterIO(ioAction);
                  } catch (Throwable e) {
                     txError(delivery, e);
                  }
               });
            }
         }
      } catch (ActiveMQAMQPException amqpE) {
         txError(delivery, amqpE);
      } catch (Throwable e) {
         txError(delivery, e);
      }
   }

   private void txError(Delivery delivery, Throwable e) {
      logger.warn(e.getMessage(), e);
      connection.runNow(() -> {
         delivery.settle();
         if (e instanceof ActiveMQAMQPException activeMQAMQPException) {
            delivery.disposition(createRejected(activeMQAMQPException.getAmqpError(), e.getMessage()));
         } else {
            delivery.disposition(createRejected(Symbol.getSymbol("failed"), e.getMessage()));
         }
         connection.flush();
      });
   }

   @Override
   public void onFlow(int credits, boolean drain) {
   }

   @Override
   public void close(boolean linkRemoteClose) throws ActiveMQAMQPException {
   }

   @Override
   public void close(ErrorCondition condition) throws ActiveMQAMQPException {
   }

   private Rejected createRejected(Symbol amqpError, String message) {
      Rejected rejected = new Rejected();
      ErrorCondition condition = new ErrorCondition();
      condition.setCondition(amqpError);
      condition.setDescription(message);
      rejected.setError(condition);
      return rejected;
   }

   private MessageImpl decodeMessage(ByteBuffer encoded) {
      MessageImpl message = (MessageImpl) Message.Factory.create();
      message.decode(encoded);
      return message;
   }

   public Transaction getCurrentTransaction() {
      return currentTx;
   }
}
