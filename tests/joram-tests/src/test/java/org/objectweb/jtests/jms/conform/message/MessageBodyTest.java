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
package org.objectweb.jtests.jms.conform.message;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageNotWriteableException;
import javax.jms.TextMessage;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.jtests.jms.framework.PTPTestCase;
import org.objectweb.jtests.jms.framework.TestConfig;

/**
 * Tests on message body.
 */
public class MessageBodyTest extends PTPTestCase {

   /**
    * Test that the {@code TextMessage.clearBody()} method does not clear the message properties.
    */
   @Test
   public void testClearBody_2() {
      try {
         TextMessage message = senderSession.createTextMessage();
         message.setStringProperty("prop", "foo");
         message.clearBody();
         Assert.assertEquals("sec. 3.11.1 Clearing a message's body does not clear its property entries.\n", "foo", message.getStringProperty("prop"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that the {@code TextMessage.clearBody()} effectively clear the body of the message
    */
   @Test
   public void testClearBody_1() {
      try {
         TextMessage message = senderSession.createTextMessage();
         message.setText("bar");
         message.clearBody();
         Assert.assertNull("sec. 3 .11.1 the clearBody method of Message resets the value of the message body " + "to the 'empty' initial message value as set by the message type's create " + "method provided by Session.\n", message.getText());
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that a call to the {@code TextMessage.setText()} method on a received message raises a
    * {@code javax.jms.MessageNotWriteableException}.
    */
   @Test
   public void testWriteOnReceivedBody() {
      try {
         TextMessage message = senderSession.createTextMessage();
         message.setText("foo");
         sender.send(message);

         Message m = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertTrue("The message should be an instance of TextMessage.\n", m instanceof TextMessage);
         TextMessage msg = (TextMessage) m;
         msg.setText("bar");
         Assert.fail("should raise a MessageNotWriteableException (sec. 3.11.2)");
      } catch (MessageNotWriteableException e) {
      } catch (JMSException e) {
         fail(e);
      }
   }
}
