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
package org.objectweb.jtests.jms.conform.message.properties;

import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import java.util.Enumeration;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.jtests.jms.framework.PTPTestCase;
import org.objectweb.jtests.jms.framework.TestConfig;

/**
 * Test the JMSX defined properties.
 * <p>
 * See JMS Specification, sec. 3.5.9 JMS Defined Properties
 */
public class JMSXPropertyTest extends PTPTestCase {

   /**
    * Test that the JMSX property {@code JMSXGroupID} is supported.
    */
   @Test
   public void testSupportsJMSXGroupID() {
      try {
         boolean found = false;
         ConnectionMetaData metaData = senderConnection.getMetaData();
         Enumeration enumeration = metaData.getJMSXPropertyNames();
         while (enumeration.hasMoreElements()) {
            String jmsxPropertyName = (String) enumeration.nextElement();
            if (jmsxPropertyName.equals("JMSXGroupID")) {
               found = true;
            }
         }
         Assert.assertTrue("JMSXGroupID property is not supported", found);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that the JMSX property {@code JMSXGroupID} works
    */
   @Test
   public void testJMSXGroupID_1() {
      try {
         String groupID = "testSupportsJMSXGroupID_1:group";
         TextMessage message = senderSession.createTextMessage();
         message.setStringProperty("JMSXGroupID", groupID);
         message.setText("testSupportsJMSXGroupID_1");
         sender.send(message);

         Message m = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertTrue(m instanceof TextMessage);
         TextMessage msg = (TextMessage) m;
         Assert.assertEquals(groupID, msg.getStringProperty("JMSXGroupID"));
         Assert.assertEquals("testSupportsJMSXGroupID_1", msg.getText());
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that the JMSX property {@code JMSXDeliveryCount} works.
    */
   @Test
   public void testJMSXDeliveryCount() throws Exception {
      if (!supportsJMSXDeliveryCount()) {
         return;
      }

      try {
         senderConnection.stop();
         // senderSession has been created as non transacted
         // we create it again but as a transacted session
         senderSession = senderConnection.createQueueSession(true, 0);
         Assert.assertTrue(senderSession.getTransacted());
         // we create again the sender
         sender = senderSession.createSender(senderQueue);
         senderConnection.start();

         receiverConnection.stop();
         // receiverSession has been created as non transacted
         // we create it again but as a transacted session
         receiverSession = receiverConnection.createQueueSession(true, 0);
         Assert.assertTrue(receiverSession.getTransacted());
         // we create again the receiver
         if (receiver != null) {
            receiver.close();
         }
         receiver = receiverSession.createReceiver(receiverQueue);
         receiverConnection.start();

         // we send a message...
         TextMessage message = senderSession.createTextMessage();
         message.setText("testJMSXDeliveryCount");
         sender.send(message);
         // ... and commit the *producer* transaction
         senderSession.commit();

         // we receive a message...
         Message m = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertNotNull(m);
         Assert.assertTrue(m instanceof TextMessage);
         TextMessage msg = (TextMessage) m;
         // ... which is the one which was sent...
         Assert.assertEquals("testJMSXDeliveryCount", msg.getText());
         // ...and has not been redelivered
         Assert.assertFalse(msg.getJMSRedelivered());
         // ... so it has been delivered once
         int jmsxDeliveryCount = msg.getIntProperty("JMSXDeliveryCount");
         Assert.assertEquals(1, jmsxDeliveryCount);
         // we rollback the *consumer* transaction
         receiverSession.rollback();

         // we receive again a message
         m = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertNotNull(m);
         Assert.assertTrue(m instanceof TextMessage);
         msg = (TextMessage) m;
         // ... which is still the one which was sent...
         Assert.assertEquals("testJMSXDeliveryCount", msg.getText());
         // .. but this time, it has been redelivered
         Assert.assertTrue(msg.getJMSRedelivered());
         // ... so it has been delivered a second time
         jmsxDeliveryCount = msg.getIntProperty("JMSXDeliveryCount");
         Assert.assertEquals(2, jmsxDeliveryCount);
      } catch (JMSException e) {
         fail(e);
      } catch (Exception e) {
         fail(e);
      }
   }

   /**
    * checks if the JMSX property {@code JMSXDeliveryCount} is supported.
    */
   private boolean supportsJMSXDeliveryCount() throws Exception {
      ConnectionMetaData metaData = senderConnection.getMetaData();
      Enumeration enumeration = metaData.getJMSXPropertyNames();
      while (enumeration.hasMoreElements()) {
         String jmsxPropertyName = (String) enumeration.nextElement();
         if (jmsxPropertyName.equals("JMSXDeliveryCount")) {
            return true;
         }
      }
      return false;
   }
}
