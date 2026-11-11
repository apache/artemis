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
package org.objectweb.jtests.jms.conform.message.headers;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Hashtable;

import org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory;
import org.junit.Assert;
import org.junit.Test;
import org.objectweb.jtests.jms.framework.PTPTestCase;
import org.objectweb.jtests.jms.framework.TestConfig;

/**
 * Test the headers of a message
 */
public class MessageHeaderTest extends PTPTestCase {

   /**
    * Test that the {@code MessageProducer.setPriority()} changes effectively priority of the message.
    */
   @Test
   public void testJMSPriority_2() {
      try {
         Message message = senderSession.createMessage();
         sender.send(message);
         sender.setPriority(9);
         sender.send(message);
         Assert.assertEquals("sec. 3.4.9 After completion of the send it holds the value specified by the " + "method sending the message.\n", 9, message.getJMSPriority());

         receiver.receive(TestConfig.TIMEOUT);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that the priority set by {@code Message.setJMSPriority()} is ignored when a message is sent and that it holds
    * the value specified when sending the message (i.e. {@code Message.DEFAULT_PRIORITY} in this test).
    */
   @Test
   public void testJMSPriority_1() {
      try {
         Message message = senderSession.createMessage();
         message.setJMSPriority(0);
         sender.send(message);
         Assert.assertTrue("sec. 3.4.9 When a message is sent this value is ignored.\n", message.getJMSPriority() != 0);
         Assert.assertEquals("sec. 3.4.9 After completion of the send it holds the value specified by the " + "method sending the message.\n", Message.DEFAULT_PRIORITY, message.getJMSPriority());

         receiver.receive(TestConfig.TIMEOUT);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that the value of the {@code JMSExpiration} header field is the same for the sent message and the
    * received one.
    */
   @Test
   public void testJMSExpiration() {
      try {
         Message message = senderSession.createMessage();
         sender.send(message);

         Message msg = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertEquals("sec. 3.4.9 When a message is received its JMSExpiration header field contains this same " + "value [i.e. set on return of the send method].\n", message.getJMSExpiration(), msg.getJMSExpiration());
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that the {@code JMSMessageID} is set by the provider when the {@code send} method returns and that it starts
    * with {@code "ID:"}.
    */
   @Test
   public void testJMSMessageID_2() {
      try {
         Message message = senderSession.createMessage();
         sender.send(message);
         Assert.assertNotNull("sec. 3.4.3 When the send method returns it contains a provider-assigned value.\n", message.getJMSMessageID());
         Assert.assertTrue("sec. 3.4.3 All JMSMessageID values must start with the prefix 'ID:'.\n", message.getJMSMessageID().startsWith("ID:"));

         Message msg = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertTrue("sec. 3.4.3 All JMSMessageID values must start with the prefix 'ID:'.\n", msg.getJMSMessageID().startsWith("ID:"));
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that the {@code JMSMessageID} header field value is ignored when the message is sent.
    */
   @Test
   public void testJMSMessageID_1() {
      try {
         Message message = senderSession.createMessage();
         message.setJMSMessageID("ID:foo");
         sender.send(message);
         Assert.assertNotEquals("sec. 3.4.3 When a message is sent this value is ignored.\n", "ID:foo", message.getJMSMessageID());
         receiver.receive(TestConfig.TIMEOUT);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that the {@code JMSDeliveryMode} header field value is ignored when the message is sent and that it holds the
    * value specified by the sending method (i.e. {@code Message.DEFAULT_ROUTING_TYPE} in this test when the message is
    * received.
    */
   @Test
   public void testJMSDeliveryMode() {
      try {
         // sender has been created with the DEFAULT_ROUTING_TYPE which is PERSISTENT
         Assert.assertEquals(DeliveryMode.PERSISTENT, sender.getDeliveryMode());
         Message message = senderSession.createMessage();
         // send a message specfiying NON_PERSISTENT for the JMSDeliveryMode header field
         message.setJMSDeliveryMode(DeliveryMode.NON_PERSISTENT);
         sender.send(message);
         Assert.assertTrue("sec. 3.4.2 When a message is sent this value is ignored", message.getJMSDeliveryMode() != DeliveryMode.NON_PERSISTENT);
         Assert.assertEquals("sec. 3.4.2 After completion of the send it holds the delivery mode specified " + "by the sending method (persistent by default).\n", Message.DEFAULT_DELIVERY_MODE, message.getJMSDeliveryMode());

         receiver.receive(TestConfig.TIMEOUT);
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that the {@code JMSDestination} header field value is ignored when the message is sent and that after
    * completion of the sending method, it holds the {@code Destination} specified by the sending method. Also test that
    * the value of the header on the received message is the same that on the sent message.
    */
   @Test
   public void testJMSDestination() {
      try {
         admin.createQueue("anotherQueue");

         Hashtable<String, String> props = new Hashtable<>();
         props.put(Context.INITIAL_CONTEXT_FACTORY, ActiveMQInitialContextFactory.class.getName());
         props.put("queue.anotherQueue", "anotherQueue");

         Context ctx = new InitialContext(props);
         Queue anotherQueue = (Queue) ctx.lookup("anotherQueue");
         Assert.assertNotSame(anotherQueue, senderQueue);

         // set the JMSDestination header field to the anotherQueue Destination
         Message message = senderSession.createMessage();
         message.setJMSDestination(anotherQueue);
         sender.send(message);
         Assert.assertNotSame("sec. 3.4.1 When a message is sent this value is ignored.\n", message.getJMSDestination(), anotherQueue);
         Assert.assertEquals("sec. 3.4.1 After completion of the send it holds the destination object specified " + "by the sending method.\n", senderQueue, message.getJMSDestination());

         Message msg = receiver.receive(TestConfig.TIMEOUT);
         String one = ((Queue) message.getJMSDestination()).getQueueName();
         String two = ((Queue) msg.getJMSDestination()).getQueueName();
         Assert.assertEquals("sec. 3.4.1 When a message is received, its destination value must be equivalent  " + " to the value assigned when it was sent.\n", one, two);

         admin.deleteQueue("anotherQueue");
      } catch (JMSException e) {
         fail(e);
      } catch (NamingException e) {
         Assert.fail(e.getMessage());
      }
   }

   /**
    * Test that a {@code Destination} set by the {@code setJMSReplyTo()} method on a sended message corresponds to the
    * {@code Destination} get by the {@code getJMSReplyTo()} method.
    */
   @Test
   public void testJMSReplyTo_1() {
      try {
         Message message = senderSession.createMessage();
         message.setJMSReplyTo(senderQueue);
         sender.send(message);

         Message msg = receiver.receive(TestConfig.TIMEOUT);
         Destination dest = msg.getJMSReplyTo();
         Assert.assertTrue("JMS ReplyTo header field should be a Queue", dest instanceof Queue);
         Queue replyTo = (Queue) dest;
         Assert.assertEquals("JMS ReplyTo header field should be equals to the sender queue", replyTo.getQueueName(), senderQueue.getQueueName());
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that if the JMS ReplyTo header field has been set as a {@code TemporaryQueue}, it will be rightly get also as
    * a {@code TemporaryQueue} (and not only as a {@code Queue}).
    */
   @Test
   public void testJMSReplyTo_2() {
      try {
         TemporaryQueue tempQueue = senderSession.createTemporaryQueue();
         Message message = senderSession.createMessage();
         message.setJMSReplyTo(tempQueue);
         sender.send(message);

         Message msg = receiver.receive(TestConfig.TIMEOUT);
         Destination dest = msg.getJMSReplyTo();
         Assert.assertTrue("JMS ReplyTo header field should be a TemporaryQueue", dest instanceof TemporaryQueue);
         Queue replyTo = (Queue) dest;
         Assert.assertEquals("JMS ReplyTo header field should be equals to the temporary queue", replyTo.getQueueName(), tempQueue.getQueueName());
      } catch (JMSException e) {
         fail(e);
      }
   }
}
