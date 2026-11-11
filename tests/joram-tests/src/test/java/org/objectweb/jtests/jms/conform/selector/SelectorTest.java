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
package org.objectweb.jtests.jms.conform.selector;

import javax.jms.DeliveryMode;
import javax.jms.TextMessage;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.jtests.jms.framework.PTPTestCase;
import org.objectweb.jtests.jms.framework.TestConfig;

/**
 * Test the message selector features of JMS
 */
public class SelectorTest extends PTPTestCase {

   /**
    * Test that an empty string as a message selector indicates that there is no message selector for the message
    * consumer.
    */
   @Test
   public void testEmptyStringAsSelector() throws Exception {
      if (receiver != null) {
         receiver.close();
      }
      receiver = receiverSession.createReceiver(receiverQueue, "");

      TextMessage message = senderSession.createTextMessage();
      message.setText("testEmptyStringAsSelector");
      sender.send(message);

      TextMessage msg = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
      Assert.assertNotNull("No message was received", msg);
      Assert.assertEquals("testEmptyStringAsSelector", msg.getText());
   }

   /**
    * Tats that String literals are well handled by the message selector.
    * <ul>
    * <li>{@code "string = 'literal''s;"} is {@code true} for "literal's" and {@code false} for "literal"</li>
    * </ul>
    */
   @Test
   public void testStringLiterals() throws Exception {
      if (receiver != null) {
         receiver.close();
      }
      receiver = receiverSession.createReceiver(receiverQueue, "string = 'literal''s'");

      TextMessage dummyMessage = senderSession.createTextMessage();
      dummyMessage.setStringProperty("string", "literal");
      dummyMessage.setText("testStringLiterals:1");
      sender.send(dummyMessage);

      TextMessage message = senderSession.createTextMessage();
      message.setStringProperty("string", "literal's");
      message.setText("testStringLiterals:2");
      sender.send(message);

      TextMessage msg = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
      Assert.assertNotNull("No message was received", msg);
      Assert.assertEquals("testStringLiterals:2", msg.getText());
   }

   /**
    * Test that the JMS property {@code JMSDeliveryMode} is treated as having the values {@code 'PERSISTENT'} or
    * {@code 'NON_PERSISTENT'} when used in a message selector (chapter 3.8.1.3).
    */
   @Test
   public void testJMSDeliveryModeInSelector() throws Exception {
      if (receiver != null) {
         receiver.close();
      }
      receiver = receiverSession.createReceiver(receiverQueue, "JMSDeliveryMode = 'PERSISTENT'");

      TextMessage dummyMessage = senderSession.createTextMessage();
      dummyMessage.setText("testJMSDeliveryModeInSelector:1");
      // send a dummy message in *non persistent* mode
      sender.send(dummyMessage, DeliveryMode.NON_PERSISTENT, sender.getPriority(), sender.getTimeToLive());

      TextMessage message = senderSession.createTextMessage();
      message.setText("testJMSDeliveryModeInSelector:2");
      // send a message in *persistent*
      sender.send(message, DeliveryMode.PERSISTENT, sender.getPriority(), sender.getTimeToLive());

      TextMessage msg = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
      Assert.assertNotNull("No message was received", msg);
      // only the message sent in persistent mode should be received.
      Assert.assertEquals(DeliveryMode.PERSISTENT, msg.getJMSDeliveryMode());
      Assert.assertEquals("testJMSDeliveryModeInSelector:2", msg.getText());
   }

   /**
    * Test that conversions that apply to the {@code get} methods for properties do not apply when a property is used in
    * a message selector expression. Based on the example of chapter 3.8.1.1 about identifiers.
    */
   @Test
   public void testIdentifierConversion() throws Exception {
      if (receiver != null) {
         receiver.close();
      }
      receiver = receiverSession.createReceiver(receiverQueue, "NumberOfOrders > 1");

      TextMessage dummyMessage = senderSession.createTextMessage();
      dummyMessage.setStringProperty("NumberOfOrders", "2");
      dummyMessage.setText("testIdentifierConversion:1");
      sender.send(dummyMessage);

      TextMessage message = senderSession.createTextMessage();
      message.setIntProperty("NumberOfOrders", 2);
      message.setText("testIdentifierConversion:2");
      sender.send(message);

      TextMessage msg = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
      Assert.assertEquals("testIdentifierConversion:2", msg.getText());
   }

   /**
    * Test the message selector using the filter example provided by the JMS specifications.
    * <ul>
    * <li>{@code "JMSType = 'car' AND color = 'blue' AND weight > 2500"}</li>
    * </ul>
    */
   @Test
   public void testSelectorExampleFromSpecs() throws Exception {
      if (receiver != null) {
         receiver.close();
      }
      receiver = receiverSession.createReceiver(receiverQueue, "JMSType = 'car' AND color = 'blue' AND weight > 2500");

      TextMessage dummyMessage = senderSession.createTextMessage();
      dummyMessage.setJMSType("car");
      dummyMessage.setStringProperty("color", "red");
      dummyMessage.setLongProperty("weight", 3000);
      dummyMessage.setText("testSelectorExampleFromSpecs:1");
      sender.send(dummyMessage);

      TextMessage message = senderSession.createTextMessage();
      message.setJMSType("car");
      message.setStringProperty("color", "blue");
      message.setLongProperty("weight", 3000);
      message.setText("testSelectorExampleFromSpecs:2");
      sender.send(message);

      TextMessage msg = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
      Assert.assertEquals("testSelectorExampleFromSpecs:2", msg.getText());
   }

   /**
    * Test the ">" condition in message selector.
    * <ul>
    * <li>{@code "weight > 2500"} is {@code true} for 3000 and {@code false} for 1000</li>
    * </ul>
    */
   @Test
   public void testGreaterThan() throws Exception {
      if (receiver != null) {
         receiver.close();
      }
      receiver = receiverSession.createReceiver(receiverQueue, "weight > 2500");

      TextMessage dummyMessage = senderSession.createTextMessage();
      dummyMessage.setLongProperty("weight", 1000);
      dummyMessage.setText("testGreaterThan:1");
      sender.send(dummyMessage);

      TextMessage message = senderSession.createTextMessage();
      message.setLongProperty("weight", 3000);
      message.setText("testGreaterThan:2");
      sender.send(message);

      TextMessage msg = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
      Assert.assertEquals("testGreaterThan:2", msg.getText());
   }

   /**
    * Test the "=" condition in message selector.
    * <ul>
    * <li>{@code "weight = 2500"}  is {@code true} for 2500 and {@code false} for 1000</li>
    * </ul>
    */
   @Test
   public void testEquals() throws Exception {
      if (receiver != null) {
         receiver.close();
      }
      receiver = receiverSession.createReceiver(receiverQueue, "weight = 2500");

      TextMessage dummyMessage = senderSession.createTextMessage();
      dummyMessage.setLongProperty("weight", 1000);
      dummyMessage.setText("testEquals:1");
      sender.send(dummyMessage);

      TextMessage message = senderSession.createTextMessage();
      message.setLongProperty("weight", 2500);
      message.setText("testEquals:2");
      sender.send(message);

      TextMessage msg = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
      Assert.assertEquals("testEquals:2", msg.getText());
   }

   /**
    * Test the "<>" (not equal) condition in message selector.
    * <ul>
    * <li>{@code "weight <> 2500"}  is {@code true} for 1000 and {@code false} for 2500</li>
    * </ul>
    */
   @Test
   public void testNotEquals() throws Exception {
      if (receiver != null) {
         receiver.close();
      }
      receiver = receiverSession.createReceiver(receiverQueue, "weight <> 2500");

      TextMessage dummyMessage = senderSession.createTextMessage();
      dummyMessage.setLongProperty("weight", 2500);
      dummyMessage.setText("testEquals:1");
      sender.send(dummyMessage);

      TextMessage message = senderSession.createTextMessage();
      message.setLongProperty("weight", 1000);
      message.setText("testEquals:2");
      sender.send(message);

      TextMessage msg = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
      Assert.assertEquals("testEquals:2", msg.getText());
   }

   /**
    * Test the BETWEEN condition in message selector.
    * <ul>
    * <li>"age BETWEEN 15 and 19" is {@code true} for 17 and {@code false} for 20</li>
    * </ul>
    */
   @Test
   public void testBetween() throws Exception {
      if (receiver != null) {
         receiver.close();
      }
      receiver = receiverSession.createReceiver(receiverQueue, "age BETWEEN 15 and 19");

      TextMessage dummyMessage = senderSession.createTextMessage();
      dummyMessage.setIntProperty("age", 20);
      dummyMessage.setText("testBetween:1");
      sender.send(dummyMessage);

      TextMessage message = senderSession.createTextMessage();
      message.setIntProperty("age", 17);
      message.setText("testBetween:2");
      sender.send(message);

      TextMessage msg = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
      Assert.assertNotNull("Message not received", msg);
      Assert.assertTrue("Message of another test: " + msg.getText(), msg.getText().startsWith("testBetween"));
      Assert.assertEquals("testBetween:2", msg.getText());
   }

   /**
    * Test the IN condition in message selector.
    * <ul>
    * <li>"Country IN ('UK', 'US', 'France')" is {@code true} for 'UK' and {@code false} for 'Peru'</li>
    * </ul>
    */
   @Test
   public void testIn() throws Exception {
      if (receiver != null) {
         receiver.close();
      }
      receiver = receiverSession.createReceiver(receiverQueue, "Country IN ('UK', 'US', 'France')");

      TextMessage dummyMessage = senderSession.createTextMessage();
      dummyMessage.setStringProperty("Country", "Peru");
      dummyMessage.setText("testIn:1");
      sender.send(dummyMessage);

      TextMessage message = senderSession.createTextMessage();
      message.setStringProperty("Country", "UK");
      message.setText("testIn:2");
      sender.send(message);

      TextMessage msg = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
      Assert.assertNotNull("Message not received", msg);
      Assert.assertTrue("Message of another test: " + msg.getText(), msg.getText().startsWith("testIn"));
      Assert.assertEquals("testIn:2", msg.getText());
   }

   /**
    * Test the LIKE ... ESCAPE condition in message selector
    * <ul>
    * <li>"underscored LIKE '\_%' ESCAPE '\'" is {@code true} for '_foo' and {@code false} for 'bar'</li>
    * </ul>
    */
   @Test
   public void testLikeEscape() throws Exception {
      if (receiver != null) {
         receiver.close();
      }
      receiver = receiverSession.createReceiver(receiverQueue, "underscored LIKE '\\_%' ESCAPE '\\'");

      TextMessage dummyMessage = senderSession.createTextMessage();
      dummyMessage.setStringProperty("underscored", "bar");
      dummyMessage.setText("testLikeEscape:1");
      sender.send(dummyMessage);

      TextMessage message = senderSession.createTextMessage();
      message.setStringProperty("underscored", "_foo");
      message.setText("testLikeEscape:2");
      sender.send(message);

      TextMessage msg = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
      Assert.assertNotNull("Message not received", msg);
      Assert.assertTrue("Message of another test: " + msg.getText(), msg.getText().startsWith("testLikeEscape"));
      Assert.assertEquals("testLikeEscape:2", msg.getText());
   }

   /**
    * Test the LIKE condition with '_' in the pattern.
    * <ul>
    * <li>"word LIKE 'l_se'" is {@code true} for 'lose' and {@code false} for 'loose'</li>
    * </ul>
    */
   @Test
   public void testLike_2() throws Exception {
      if (receiver != null) {
         receiver.close();
      }
      receiver = receiverSession.createReceiver(receiverQueue, "word LIKE 'l_se'");

      TextMessage dummyMessage = senderSession.createTextMessage();
      dummyMessage.setStringProperty("word", "loose");
      dummyMessage.setText("testLike_2:1");
      sender.send(dummyMessage);

      TextMessage message = senderSession.createTextMessage();
      message.setStringProperty("word", "lose");
      message.setText("testLike_2:2");
      sender.send(message);

      TextMessage msg = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
      Assert.assertNotNull("Message not received", msg);
      Assert.assertTrue("Message of another test: " + msg.getText(), msg.getText().startsWith("testLike_2"));
      Assert.assertEquals("testLike_2:2", msg.getText());
   }

   /**
    * Test the LIKE condition with '%' in the pattern.
    * <ul>
    * <li>"phone LIKE '12%3'" is {@code true} for '12993' and {@code false} for '1234'</li>
    * </ul>
    */
   @Test
   public void testLike_1() throws Exception {
      if (receiver != null) {
         receiver.close();
      }
      receiver = receiverSession.createReceiver(receiverQueue, "phone LIKE '12%3'");

      TextMessage dummyMessage = senderSession.createTextMessage();
      dummyMessage.setStringProperty("phone", "1234");
      dummyMessage.setText("testLike_1:1");
      sender.send(dummyMessage);

      TextMessage message = senderSession.createTextMessage();
      message.setStringProperty("phone", "12993");
      message.setText("testLike_1:2");
      sender.send(message);

      TextMessage msg = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
      Assert.assertNotNull("Message not received", msg);
      Assert.assertTrue("Message of another test: " + msg.getText(), msg.getText().startsWith("testLike_1"));
      Assert.assertEquals("testLike_1:2", msg.getText());
   }

   /**
    * Test the {@code NULL} value in message selector.
    * <ul>
    * <li>{@code "prop IS NULL"}</li>
    * </ul>
    */
   @Test
   public void testNull() throws Exception {
      if (receiver != null) {
         receiver.close();
      }
      receiver = receiverSession.createReceiver(receiverQueue, "prop_name IS NULL");

      TextMessage dummyMessage = senderSession.createTextMessage();
      dummyMessage.setStringProperty("prop_name", "not null");
      dummyMessage.setText("testNull:1");
      sender.send(dummyMessage);

      TextMessage message = senderSession.createTextMessage();
      message.setText("testNull:2");
      sender.send(message);

      TextMessage msg = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
      Assert.assertNotNull(msg);
      Assert.assertEquals("testNull:2", msg.getText());
   }
}
