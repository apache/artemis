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
package org.objectweb.jtests.jms.framework;

import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.naming.Context;

import org.junit.After;
import org.junit.Before;

/**
 * Creates convenient Point to Point JMS objects which can be needed for tests.
 * <p>
 * This class defines the setUp and tearDown methods so that JMS administrated objects and  other "ready to use" PTP
 * objects (that is to say queues, sessions, senders and receviers) are available conveniently for the test cases.
 * <p>
 * Classes which want that convenience should extend {@code PTPTestCase} instead of {@code JMSTestCase}.
 */
public abstract class PTPTestCase extends JMSTestCase {

   protected Context ctx;

   private static final String QCF_NAME = "testQCF";

   private static final String QUEUE_NAME = "testJoramQueue";

   protected Queue senderQueue;

   protected QueueSender sender;

   protected QueueConnectionFactory senderQCF;

   protected QueueConnection senderConnection;

   /**
    * QueueSession of the sender (non transacted, AUTO_ACKNOWLEDGE)
    */
   protected QueueSession senderSession;

   protected Queue receiverQueue;

   protected QueueReceiver receiver;

   protected QueueConnectionFactory receiverQCF;

   protected QueueConnection receiverConnection;

   /**
    * QueueSession of the receiver (non transacted, AUTO_ACKNOWLEDGE)
    */
   protected QueueSession receiverSession;

   /**
    * Create all administrated objects connections and sessions ready to use for tests.
    * <p>
    * Start connections.
    */
   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();

      try {
         // ...and creates administrated objects and binds them
         admin.createQueueConnectionFactory(PTPTestCase.QCF_NAME);
         admin.createQueue(PTPTestCase.QUEUE_NAME);

         Context ctx = admin.createContext();

         senderQCF = (QueueConnectionFactory) ctx.lookup(PTPTestCase.QCF_NAME);
         senderQueue = (Queue) ctx.lookup(PTPTestCase.QUEUE_NAME);
         senderConnection = senderQCF.createQueueConnection();
         senderSession = senderConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
         sender = senderSession.createSender(senderQueue);

         receiverQCF = (QueueConnectionFactory) ctx.lookup(PTPTestCase.QCF_NAME);
         receiverQueue = (Queue) ctx.lookup(PTPTestCase.QUEUE_NAME);
         receiverConnection = receiverQCF.createQueueConnection();
         receiverSession = receiverConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
         receiver = receiverSession.createReceiver(receiverQueue);

         senderConnection.start();
         receiverConnection.start();
         // end of client step
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Close connections and delete administrated objects
    */
   @Override
   @After
   public void tearDown() throws Exception {
      try {
         senderConnection.close();
         receiverConnection.close();

         admin.deleteQueueConnectionFactory(PTPTestCase.QCF_NAME);
         admin.deleteQueue(PTPTestCase.QUEUE_NAME);
      } catch (Exception ignored) {
         ignored.printStackTrace();
      } finally {
         senderQueue = null;
         sender = null;
         senderQCF = null;
         senderSession = null;
         senderConnection = null;

         receiverQueue = null;
         receiver = null;
         receiverQCF = null;
         receiverSession = null;
         receiverConnection = null;
      }

      super.tearDown();
   }
}
