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
package org.apache.activemq.artemis.tests.integration.jms.client;

import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.Test;

public class SessionTest extends JMSTestBase {

   @Test
   public void testIllegalStateException() throws Exception {
      Connection defaultConn = null;
      QueueConnection qConn = null;
      Connection connClientID = null;
      ActiveMQConnectionFactory activeMQConnectionFactory = (ActiveMQConnectionFactory) cf;
      try {
         String clientID = "somethingElse" + name;
         defaultConn = cf.createConnection();
         qConn = activeMQConnectionFactory.createQueueConnection();

         connClientID = cf.createConnection();
         connClientID.setClientID(clientID);

         Topic topic = createTopic("topic");

         QueueSession qSess = qConn.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
         try {
            qSess.createDurableConsumer(topic, "mySub1");
            fail("exception expected");
         } catch (javax.jms.IllegalStateException ex) {
            //ok expected.
         }

         try {
            qSess.createDurableConsumer(topic, "mySub1", "TEST = 'test'", false);
            fail("exception expected");
         } catch (javax.jms.IllegalStateException ex) {
            //ok expected.
         }

         try {
            qSess.createSharedConsumer(topic, "mySub1");
            fail("exception expected");
         } catch (javax.jms.IllegalStateException ex) {
            //ok expected.
         }

         try {
            qSess.createSharedConsumer(topic, "mySub1", "TEST = 'test'");
            fail("exception expected");
         } catch (javax.jms.IllegalStateException ex) {
            //ok expected.
         }

         try {
            qSess.createSharedDurableConsumer(topic, "mySub1");
            fail("exception expected");
         } catch (javax.jms.IllegalStateException ex) {
            //ok expected.
         }

         try {
            qSess.createSharedDurableConsumer(topic, "mySub1", "TEST = 'test'");
            fail("exception expected");
         } catch (javax.jms.IllegalStateException ex) {
            //ok expected.
         }

         Session defaultSess = defaultConn.createSession();

         try {
            defaultSess.createDurableSubscriber(topic, "mySub1");
            fail("exception expected");
         } catch (javax.jms.IllegalStateException ex) {
            //ok expected.
         }

         try {
            defaultSess.createDurableSubscriber(topic, "mySub1", "TEST = 'test'", true);
            fail("exception expected");
         } catch (javax.jms.IllegalStateException ex) {
            //ok expected.
         }

         try {
            defaultSess.createDurableConsumer(topic, "mySub1");
            fail("exception expected");
         } catch (javax.jms.IllegalStateException ex) {
            //ok expected.
         }

         try {
            defaultSess.createDurableConsumer(topic, "mySub1", "TEST = 'test'", true);
            fail("exception expected");
         } catch (javax.jms.IllegalStateException ex) {
            //ok expected.
         }

      } finally {
         if (defaultConn != null) {
            defaultConn.close();
         }

         if (qConn != null) {
            qConn.close();
         }
         if (connClientID != null) {
            connClientID.close();
         }
      }
   }
}
