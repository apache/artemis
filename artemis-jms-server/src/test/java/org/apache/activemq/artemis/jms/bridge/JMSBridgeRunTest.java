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
package org.apache.activemq.artemis.jms.bridge;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.jms.bridge.impl.JMSBridgeImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQMapMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;

@SuppressWarnings({"resource", "unchecked"})
public class JMSBridgeRunTest {
   Message sourceMessage;

   private static @NonNull JMSBridge createJMSBridge(ConnectionFactory mockSourceFactory, Destination mockDestination, ConnectionFactory mockDestinationFactory) {
      JMSBridge jmsBridge = new JMSBridgeImpl();
      jmsBridge.setSourceConnectionFactoryFactory(() -> mockSourceFactory);
      jmsBridge.setSourceDestinationFactory(() -> mockDestination);
      jmsBridge.setTargetConnectionFactoryFactory(() -> mockDestinationFactory);
      jmsBridge.setTargetDestinationFactory(() -> mockDestination);
      jmsBridge.setFailureRetryInterval(60000);
      jmsBridge.setMaxRetries(-1);
      jmsBridge.setMaxBatchSize(1);
      jmsBridge.setMaxBatchTime(1000);
      jmsBridge.setQualityOfServiceMode(QualityOfServiceMode.DUPLICATES_OK);
      jmsBridge.setClientID(jmsBridge.getBridgeName());

      return jmsBridge;
   }

   @BeforeEach
   public void createNewMessage() throws JMSException {
      ClientMessage clientMessage = new ClientMessageImpl(ActiveMQMapMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 4, 1000);
      var mockSession = Mockito.mock(ClientSession.class);
      Mockito.when(mockSession.createMessage(
                  Mockito.anyByte(),
                  Mockito.anyBoolean(),
                  Mockito.anyLong(),
                  Mockito.anyLong(),
                  Mockito.anyByte()))
            .thenReturn(clientMessage);

      sourceMessage = ActiveMQMessage.createMessage(clientMessage, mockSession);
      ((ActiveMQMessage) sourceMessage).getCoreMessage().putShortProperty("JMS_AMQP_ORIGINAL_ENCODING", (short) 7);
      ((ActiveMQMessage) sourceMessage).getCoreMessage().putBooleanProperty("JMS_AMQP_HEADER", true);
      ((ActiveMQMessage) sourceMessage).getCoreMessage().putBooleanProperty("JMS_AMQP_HEADERDURABLE", true);
      ((ActiveMQMessage) sourceMessage).getCoreMessage().putByteProperty("JMS_AMQP_MA_x-opt-jms-msg-type", (byte) 2);
      ((ActiveMQMessage) sourceMessage).getCoreMessage().putByteProperty("JMS_AMQP_MA_x-opt-jms-dest", (byte) 0);
      ((ActiveMQMessage) sourceMessage).getCoreMessage().putStringProperty("NATIVE_MESSAGE_ID", "ID:ae7d84f6-b32b-4135-9abf-96865914989b:1:1:1-1");
      sourceMessage.setJMSMessageID("ID:ae7d84f6-b32b-4135-9abf-96865914989b:1:1:1-1");
   }

   @Test
   public void goodRun() throws Exception {
      var mockDestination = Mockito.mock(Destination.class);
      var mockSourceFactory = Mockito.mock(ConnectionFactory.class, Mockito.RETURNS_DEEP_STUBS);
      var mockDestinationFactory = Mockito.mock(ConnectionFactory.class, Mockito.RETURNS_DEEP_STUBS);

      Mockito.when(mockSourceFactory
                  .createConnection()
                  .createSession(Mockito.anyBoolean(), Mockito.anyInt())
                  .createConsumer(mockDestination)
                  .receive(Mockito.anyLong()))
            .thenReturn(sourceMessage, (Message) null);

      JMSBridge jmsBridge = createJMSBridge(mockSourceFactory, mockDestination, mockDestinationFactory);

      jmsBridge.start();
      Thread.sleep(Duration.ofSeconds(2).toMillis());
      jmsBridge.stop();

      Mockito.verify(mockDestinationFactory
                        .createConnection()
                        .createSession(Mockito.anyBoolean(), Mockito.anyInt())
                        .createProducer(null),
                  Mockito.times(1))
            .send(Mockito.any(Destination.class), Mockito.any(Message.class), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyLong());
   }

   @Test
   public void addOriginalMessageId() throws Exception {
      var mockDestination = Mockito.mock(Destination.class);
      var mockSourceFactory = Mockito.mock(ConnectionFactory.class, Mockito.RETURNS_DEEP_STUBS);
      var mockDestinationFactory = Mockito.mock(ConnectionFactory.class, Mockito.RETURNS_DEEP_STUBS);

      Mockito.when(mockSourceFactory
                  .createConnection()
                  .createSession(Mockito.anyBoolean(), Mockito.anyInt())
                  .createConsumer(mockDestination)
                  .receive(Mockito.anyLong()))
            .thenReturn(sourceMessage, (Message) null);

      JMSBridge jmsBridge = createJMSBridge(mockSourceFactory, mockDestination, mockDestinationFactory);

      jmsBridge.setAddMessageIDInHeader(true);

      jmsBridge.start();
      Thread.sleep(Duration.ofSeconds(2).toMillis());
      jmsBridge.stop();

      ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);

      Mockito.verify(mockDestinationFactory
                        .createConnection()
                        .createSession(Mockito.anyBoolean(), Mockito.anyInt())
                        .createProducer(null),
                  Mockito.times(1))
            .send(Mockito.any(Destination.class), captor.capture(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyLong());

      Message sentMessage = captor.getValue();

      Assertions.assertTrue(Collections.list((Enumeration<String>) sentMessage.getPropertyNames()).contains("AMQ_BRIDGE_MSG_ID_LIST"));
      Assertions.assertEquals("ID:ae7d84f6-b32b-4135-9abf-96865914989b:1:1:1-1", sentMessage.getStringProperty("AMQ_BRIDGE_MSG_ID_LIST"));
   }
}
