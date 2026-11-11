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
package org.apache.activemq.artemis.tests.integration.jms.jms2client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageFormatException;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BodyTest extends JMSTestBase {

   private static final String Q_NAME = "SomeQueue";
   private javax.jms.Queue queue;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      jmsServer.createQueue(false, Q_NAME, null, true, Q_NAME);
      queue = ActiveMQJMSClient.createQueue(Q_NAME);
   }

   @Test
   public void testBodyConversion() throws Throwable {
      try (
         Connection conn = cf.createConnection();
      ) {

         Session sess = conn.createSession();
         MessageProducer producer = sess.createProducer(queue);

         MessageConsumer cons = sess.createConsumer(queue);
         conn.start();

         BytesMessage bytesMessage = sess.createBytesMessage();
         producer.send(bytesMessage);

         Message msg = cons.receiveNoWait();
         assertNotNull(msg);

         try {
            msg.getBody(String.class);
            fail("Exception expected");
         } catch (MessageFormatException e) {
         }
      }

   }
}
