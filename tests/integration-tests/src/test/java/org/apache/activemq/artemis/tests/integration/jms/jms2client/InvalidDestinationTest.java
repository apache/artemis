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

import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.InvalidDestinationException;
import javax.jms.InvalidDestinationRuntimeException;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InvalidDestinationTest extends JMSTestBase {

   private JMSContext context;
   private Queue queue1;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      context = createContext();
      queue1 = createQueue(JmsContextTest.class.getSimpleName() + "Queue");
   }

   @Test
   public void invalidDestinationRuntimeExceptionTests() throws Exception {
      JMSProducer producer = context.createProducer();
      Destination invalidDestination = null;
      Topic invalidTopic = null;
      String message = "hello world";
      byte[] bytesMsgSend = message.getBytes();
      Map<String, Object> mapMsgSend = new HashMap<>();
      mapMsgSend.put("s", "foo");
      mapMsgSend.put("b", true);
      mapMsgSend.put("i", 1);
      TextMessage expTextMessage = context.createTextMessage(message);

      try {
         producer.send(invalidDestination, expTextMessage);
      } catch (InvalidDestinationRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("Expected InvalidDestinationRuntimeException, received " + e);
      }

      try {
         producer.send(invalidDestination, message);
      } catch (InvalidDestinationRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("Expected InvalidDestinationRuntimeException, received " + e);
      }

      ObjectMessage om = context.createObjectMessage();
      StringBuilder sb = new StringBuilder(message);
      om.setObject(sb);
      try {
         producer.send(invalidDestination, om);
      } catch (InvalidDestinationRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("Expected InvalidDestinationRuntimeException, received " + e);
      }

      try {
         producer.send(invalidDestination, bytesMsgSend);
      } catch (InvalidDestinationRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("Expected InvalidDestinationRuntimeException, received " + e);
      }

      try {
         producer.send(invalidDestination, mapMsgSend);
      } catch (InvalidDestinationRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("Expected InvalidDestinationRuntimeException, received " + e);
      }

      try {
         context.createConsumer(invalidDestination);
      } catch (InvalidDestinationRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("Expected InvalidDestinationRuntimeException, received " + e);
      }

      try {
         context.createConsumer(invalidDestination, "lastMessage = TRUE");
      } catch (InvalidDestinationRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("Expected InvalidDestinationRuntimeException, received " + e);
      }

      try {
         context.createConsumer(invalidDestination, "lastMessage = TRUE", false);
      } catch (InvalidDestinationRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("Expected InvalidDestinationRuntimeException, received " + e);
      }

      try {
         context.createDurableConsumer(invalidTopic, "InvalidDestinationRuntimeException");
      } catch (InvalidDestinationRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("Expected InvalidDestinationRuntimeException, received " + e);
      }

      try {
         context.createDurableConsumer(invalidTopic, "InvalidDestinationRuntimeException", "lastMessage = TRUE", false);
      } catch (InvalidDestinationRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("Expected InvalidDestinationRuntimeException, received " + e);
      }

      try {
         context.createSharedDurableConsumer(invalidTopic, "InvalidDestinationRuntimeException");
      } catch (InvalidDestinationRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("Expected InvalidDestinationRuntimeException, received " + e);
      }

      try {
         context.createSharedDurableConsumer(invalidTopic, "InvalidDestinationRuntimeException", "lastMessage = TRUE");
      } catch (InvalidDestinationRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("Expected InvalidDestinationRuntimeException, received " + e);
      }

      try {
         context.unsubscribe("InvalidSubscriptionName");
      } catch (InvalidDestinationRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("Expected InvalidDestinationRuntimeException, received " + e);
      }

      try {
         context.createSharedConsumer(invalidTopic, "InvalidDestinationRuntimeException");
      } catch (InvalidDestinationRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("Expected InvalidDestinationRuntimeException, received " + e);
      }

      try {
         context.createSharedConsumer(invalidTopic, "InvalidDestinationRuntimeException", "lastMessage = TRUE");
      } catch (InvalidDestinationRuntimeException e) {
         //pass
      } catch (Exception e) {
         fail("Expected InvalidDestinationRuntimeException, received " + e);
      }
   }

   @Test
   public void invalidDestinationExceptionTests() throws Exception {
      Topic invalidTopic = null;

      Connection conn = cf.createConnection();

      try {
         Session session = conn.createSession();

         try {
            session.createDurableSubscriber(invalidTopic, "InvalidDestinationException");
         } catch (InvalidDestinationException e) {
            //pass
         } catch (Exception e) {
            fail("Expected InvalidDestinationException, received " + e);
         }

         try {
            session.createDurableSubscriber(invalidTopic, "InvalidDestinationException", "lastMessage = TRUE", false);
         } catch (InvalidDestinationException e) {
            //pass
         } catch (Exception e) {
            fail("Expected InvalidDestinationException, received " + e);
         }

         try {
            session.createDurableConsumer(invalidTopic, "InvalidDestinationException");
         } catch (InvalidDestinationException e) {
            //pass
         } catch (Exception e) {
            fail("Expected InvalidDestinationException, received " + e);
         }

         try {
            session.createDurableConsumer(invalidTopic, "InvalidDestinationException", "lastMessage = TRUE", false);
         } catch (InvalidDestinationException e) {
            //pass
         } catch (Exception e) {
            fail("Expected InvalidDestinationException, received " + e);
         }

         try {
            session.createSharedConsumer(invalidTopic, "InvalidDestinationException");
         } catch (InvalidDestinationException e) {
            //pass
         } catch (Exception e) {
            fail("Expected InvalidDestinationException, received " + e);
         }

         try {
            session.createSharedConsumer(invalidTopic, "InvalidDestinationException", "lastMessage = TRUE");
         } catch (InvalidDestinationException e) {
            //pass
         } catch (Exception e) {
            fail("Expected InvalidDestinationException, received " + e);
         }

         try {
            session.createSharedDurableConsumer(invalidTopic, "InvalidDestinationException");
         } catch (InvalidDestinationException e) {
            //pass
         } catch (Exception e) {
            fail("Expected InvalidDestinationException, received " + e);
         }

         try {
            session.createSharedDurableConsumer(invalidTopic, "InvalidDestinationException", "lastMessage = TRUE");
         } catch (InvalidDestinationException e) {
            //pass
         } catch (Exception e) {
            fail("Expected InvalidDestinationException, received " + e);
         }
      } finally {
         conn.close();
      }
   }

}
