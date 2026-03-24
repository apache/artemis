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
package org.apache.activemq.artemis.tests.integration.management;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.plugin.impl.NotificationActiveMQServerPlugin;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.CONNECTION_CREATED;
import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.CONNECTION_DESTROYED;
import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.SESSION_CREATED;
import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.SESSION_CLOSED;
import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.ADDRESS_ADDED;
import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.ADDRESS_REMOVED;
import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.BINDING_ADDED;
import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.BINDING_REMOVED;
import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.CONSUMER_CLOSED;
import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.CONSUMER_CREATED;
import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.MESSAGE_DELIVERED;
import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.MESSAGE_EXPIRED;

public class NotificationTest extends ActiveMQTestBase {


   private ActiveMQServer server;

   private ClientSession session;

   private ClientConsumer notifConsumer;

   private SimpleString notifQueue;
   private ServerLocator locator;




   @Test
   public void testBINDING_ADDED() throws Exception {
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();
      boolean durable = RandomUtil.randomBoolean();

      NotificationTest.flush(notifConsumer);

      long start = System.currentTimeMillis();
      session.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(durable));

      //the first message received will be for the address creation
      ClientMessage[] notifications = NotificationTest.consumeMessages(2, notifConsumer);
      Assert.assertEquals(BINDING_ADDED.toString(), notifications[1].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertEquals(queue.toString(), notifications[1].getObjectProperty(ManagementHelper.HDR_ROUTING_NAME).toString());
      Assert.assertEquals(address.toString(), notifications[1].getObjectProperty(ManagementHelper.HDR_ADDRESS).toString());
      Assert.assertTrue(notifications[1].getTimestamp() >= start);
      Assert.assertTrue((long) notifications[1].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      Assert.assertEquals(notifications[1].getTimestamp(), (long) notifications[1].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));

      session.deleteQueue(queue);
   }

   @Test
   public void testBINDING_ADDEDWithMatchingFilter() throws Exception {
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();
      boolean durable = RandomUtil.randomBoolean();

      notifConsumer.close();
      notifConsumer = session.createConsumer(notifQueue.toString(), ManagementHelper.HDR_ROUTING_NAME + "= '" +
         queue +
         "'");
      NotificationTest.flush(notifConsumer);

      long start = System.currentTimeMillis();
      session.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(durable));

      ClientMessage[] notifications = NotificationTest.consumeMessages(1, notifConsumer);
      Assert.assertEquals(BINDING_ADDED.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertEquals(queue.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_ROUTING_NAME).toString());
      Assert.assertEquals(address.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_ADDRESS).toString());
      Assert.assertTrue(notifications[0].getTimestamp() >= start);
      Assert.assertTrue((long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      Assert.assertEquals(notifications[0].getTimestamp(), (long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));

      session.deleteQueue(queue);
   }

   @Test
   public void testBINDING_ADDEDWithNonMatchingFilter() throws Exception {
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();
      boolean durable = RandomUtil.randomBoolean();

      notifConsumer.close();
      notifConsumer = session.createConsumer(notifQueue.toString(), ManagementHelper.HDR_ROUTING_NAME + " <> '" +
         queue + "' AND " + ManagementHelper.HDR_ADDRESS + " <> '" + address + "'");
      NotificationTest.flush(notifConsumer);

      session.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(durable));

      NotificationTest.consumeMessages(0, notifConsumer);

      session.deleteQueue(queue);
   }

   @Test
   public void testBINDING_REMOVED() throws Exception {
      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();
      boolean durable = RandomUtil.randomBoolean();

      session.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(durable));

      NotificationTest.flush(notifConsumer);

      long start = System.currentTimeMillis();
      session.deleteQueue(queue);

      //There will be 2 notifications, first is for binding removal, second is for address removal
      ClientMessage[] notifications = NotificationTest.consumeMessages(2, notifConsumer, 5000);
      Assert.assertEquals(BINDING_REMOVED.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertEquals(queue.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_ROUTING_NAME).toString());
      Assert.assertEquals(address.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_ADDRESS).toString());
      Assert.assertTrue(notifications[0].getTimestamp() >= start);
      Assert.assertTrue((long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      Assert.assertEquals(notifications[0].getTimestamp(), (long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));
   }

   @Test
   public void testCONSUMER_CREATED() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession mySession = sf.createSession("myUser", "myPassword", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());

      mySession.start();

      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();
      boolean durable = RandomUtil.randomBoolean();

      session.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(durable));

      NotificationTest.flush(notifConsumer);

      long start = System.currentTimeMillis();
      ClientConsumer consumer = mySession.createConsumer(queue);
      SimpleString consumerName = SimpleString.toSimpleString(((ClientSessionInternal) mySession).getName());

      ClientMessage[] notifications = NotificationTest.consumeMessages(1, notifConsumer);
      Assert.assertEquals(CONSUMER_CREATED.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertEquals(queue.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_ROUTING_NAME).toString());
      Assert.assertEquals(address.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_ADDRESS).toString());
      Assert.assertEquals(1, notifications[0].getObjectProperty(ManagementHelper.HDR_CONSUMER_COUNT));
      Assert.assertEquals(SimpleString.toSimpleString("myUser"), notifications[0].getSimpleStringProperty(ManagementHelper.HDR_USER));
      Assert.assertEquals(null, notifications[0].getSimpleStringProperty(ManagementHelper.HDR_VALIDATED_USER));
      Assert.assertEquals(SimpleString.toSimpleString("invm:0"), notifications[0].getSimpleStringProperty(ManagementHelper.HDR_REMOTE_ADDRESS));
      Assert.assertEquals(consumerName, notifications[0].getSimpleStringProperty(ManagementHelper.HDR_SESSION_NAME));
      Assert.assertEquals(SimpleString.toSimpleString("unavailable"), notifications[0].getSimpleStringProperty(ManagementHelper.HDR_CERT_SUBJECT_DN));
      Assert.assertTrue(notifications[0].getTimestamp() >= start);
      Assert.assertTrue((long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      Assert.assertEquals(notifications[0].getTimestamp(), (long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));

      consumer.close();
      session.deleteQueue(queue);
   }

   @Test
   public void testCONSUMER_CLOSED() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession mySession = sf.createSession("myUser", "myPassword", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());

      mySession.start();

      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();
      boolean durable = RandomUtil.randomBoolean();

      mySession.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(durable));
      ClientConsumer consumer = mySession.createConsumer(queue);
      SimpleString sessionName = SimpleString.toSimpleString(((ClientSessionInternal) mySession).getName());

      NotificationTest.flush(notifConsumer);

      long start = System.currentTimeMillis();
      consumer.close();

      ClientMessage[] notifications = NotificationTest.consumeMessages(1, notifConsumer);
      Assert.assertEquals(CONSUMER_CLOSED.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertEquals(queue.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_ROUTING_NAME).toString());
      Assert.assertEquals(address.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_ADDRESS).toString());
      Assert.assertEquals(0, notifications[0].getObjectProperty(ManagementHelper.HDR_CONSUMER_COUNT));
      Assert.assertEquals(SimpleString.toSimpleString("myUser"), notifications[0].getSimpleStringProperty(ManagementHelper.HDR_USER));
      Assert.assertEquals(SimpleString.toSimpleString("invm:0"), notifications[0].getSimpleStringProperty(ManagementHelper.HDR_REMOTE_ADDRESS));
      Assert.assertEquals(sessionName, notifications[0].getSimpleStringProperty(ManagementHelper.HDR_SESSION_NAME));
      Assert.assertTrue(notifications[0].getTimestamp() >= start);
      Assert.assertTrue((long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      Assert.assertEquals(notifications[0].getTimestamp(), (long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));

      session.deleteQueue(queue);
   }

   @Test
   public void testAddressAdded() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();

      NotificationTest.flush(notifConsumer);

      long start = System.currentTimeMillis();
      session.createAddress(address, RoutingType.ANYCAST, true);

      ClientMessage[] notifications = NotificationTest.consumeMessages(1, notifConsumer);
      Assert.assertEquals(ADDRESS_ADDED.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertEquals(RoutingType.ANYCAST.getType(), notifications[0].getObjectProperty(ManagementHelper.HDR_ROUTING_TYPE));
      Assert.assertEquals(address.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_ADDRESS).toString());
      Assert.assertTrue(notifications[0].getTimestamp() >= start);
      Assert.assertTrue((long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      Assert.assertEquals(notifications[0].getTimestamp(), (long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));

   }

   @Test
   public void testAddressRemoved() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      session.createAddress(address, RoutingType.ANYCAST, true);
      NotificationTest.flush(notifConsumer);

      long start = System.currentTimeMillis();
      server.getPostOffice().removeAddressInfo(address);

      ClientMessage[] notifications = NotificationTest.consumeMessages(1, notifConsumer);
      Assert.assertEquals(ADDRESS_REMOVED.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertEquals(RoutingType.ANYCAST.getType(), notifications[0].getObjectProperty(ManagementHelper.HDR_ROUTING_TYPE));
      Assert.assertEquals(address.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_ADDRESS).toString());
      Assert.assertTrue(notifications[0].getTimestamp() >= start);
      Assert.assertTrue((long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      Assert.assertEquals(notifications[0].getTimestamp(), (long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));
   }

   @Test
   public void testConnectionCreatedAndDestroyed() throws Exception {
      NotificationTest.flush(notifConsumer);

      long start = System.currentTimeMillis();
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession mySession = sf.createSession("myUser", "myPassword", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());
      mySession.start();

      ClientMessage[] notifications = NotificationTest.consumeMessages(2, notifConsumer);
      Assert.assertEquals(CONNECTION_CREATED.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertNotNull(notifications[0].getObjectProperty(ManagementHelper.HDR_CONNECTION_NAME));
      final String connectionId = notifications[0].getObjectProperty(ManagementHelper.HDR_CONNECTION_NAME).toString();

      Assert.assertEquals(SESSION_CREATED.toString(), notifications[1].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertNotNull(notifications[1].getObjectProperty(ManagementHelper.HDR_CONNECTION_NAME));
      Assert.assertNotNull(notifications[1].getObjectProperty(ManagementHelper.HDR_SESSION_NAME));
      Assert.assertEquals(SimpleString.toSimpleString("myUser"), notifications[1].getObjectProperty(ManagementHelper.HDR_USER));
      Assert.assertTrue(notifications[1].getTimestamp() >= start);
      Assert.assertTrue((long) notifications[1].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      Assert.assertEquals(notifications[1].getTimestamp(), (long) notifications[1].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));

      NotificationTest.flush(notifConsumer);

      start = System.currentTimeMillis();
      mySession.close();
      sf.close();

      notifications = NotificationTest.consumeMessages(2, notifConsumer);

      Assert.assertEquals(SESSION_CLOSED.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertNotNull(notifications[0].getObjectProperty(ManagementHelper.HDR_CONNECTION_NAME));
      Assert.assertNotNull(notifications[0].getObjectProperty(ManagementHelper.HDR_SESSION_NAME));
      Assert.assertEquals(SimpleString.toSimpleString("myUser"), notifications[0].getObjectProperty(ManagementHelper.HDR_USER));
      Assert.assertTrue(notifications[0].getTimestamp() >= start);
      Assert.assertTrue((Long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);

      Assert.assertEquals(CONNECTION_DESTROYED.toString(), notifications[1].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertNotNull(notifications[1].getObjectProperty(ManagementHelper.HDR_CONNECTION_NAME));
      Assert.assertEquals(connectionId, notifications[1].getObjectProperty(ManagementHelper.HDR_CONNECTION_NAME).toString());
      Assert.assertTrue(notifications[1].getTimestamp() >= start);
      Assert.assertTrue((long) notifications[1].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      Assert.assertEquals(notifications[1].getTimestamp(), (long) notifications[1].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));
   }

   @Test
   public void testMessageDelivered() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession mySession = sf.createSession("myUser", "myPassword", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());

      mySession.start();

      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();
      boolean durable = RandomUtil.randomBoolean();

      session.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(durable));

      ClientConsumer consumer = mySession.createConsumer(queue);
      ClientProducer producer = mySession.createProducer(address);

      NotificationTest.flush(notifConsumer);

      ClientMessage msg = session.createMessage(false);
      msg.putStringProperty("someKey", "someValue");
      producer.send(msg);

      long start = System.currentTimeMillis();
      consumer.receive(1000);

      ClientMessage[] notifications = NotificationTest.consumeMessages(1, notifConsumer);
      Assert.assertEquals(MESSAGE_DELIVERED.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertNotNull(notifications[0].getObjectProperty(ManagementHelper.HDR_MESSAGE_ID));
      Assert.assertNotNull(notifications[0].getObjectProperty(ManagementHelper.HDR_CONSUMER_NAME));
      Assert.assertEquals(address, notifications[0].getObjectProperty(ManagementHelper.HDR_ADDRESS));
      Assert.assertEquals(queue, notifications[0].getObjectProperty(ManagementHelper.HDR_ROUTING_NAME));
      Assert.assertEquals(RoutingType.MULTICAST.getType(), notifications[0].getObjectProperty(ManagementHelper.HDR_ROUTING_TYPE));
      Assert.assertTrue(notifications[0].getTimestamp() >= start);
      Assert.assertTrue((long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      Assert.assertEquals(notifications[0].getTimestamp(), (long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));

      consumer.close();
      session.deleteQueue(queue);
   }

   @Test
   public void testMessageExpired() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession mySession = sf.createSession("myUser", "myPassword", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());

      mySession.start();

      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();
      boolean durable = RandomUtil.randomBoolean();

      session.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(durable));

      ClientConsumer consumer = mySession.createConsumer(queue);
      ClientProducer producer = mySession.createProducer(address);

      NotificationTest.flush(notifConsumer);

      ClientMessage msg = session.createMessage(false);
      msg.putStringProperty("someKey", "someValue");
      msg.setExpiration(1);

      long start = System.currentTimeMillis();
      producer.send(msg);
      Assert.assertNull(consumer.receiveImmediate());

      ClientMessage[] notifications = NotificationTest.consumeMessages(1, notifConsumer);
      Assert.assertEquals(MESSAGE_EXPIRED.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertNotNull(notifications[0].getObjectProperty(ManagementHelper.HDR_MESSAGE_ID));
      Assert.assertEquals(address, notifications[0].getObjectProperty(ManagementHelper.HDR_ADDRESS));
      Assert.assertEquals(queue, notifications[0].getObjectProperty(ManagementHelper.HDR_ROUTING_NAME));
      Assert.assertEquals(RoutingType.MULTICAST.getType(), notifications[0].getObjectProperty(ManagementHelper.HDR_ROUTING_TYPE));
      Assert.assertTrue(notifications[0].getTimestamp() >= start);
      Assert.assertTrue((long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      Assert.assertEquals(notifications[0].getTimestamp(), (long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));

      consumer.close();
      session.deleteQueue(queue);
   }

   @Test
   public void testMessageExpiredWithoutConsumers() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession mySession = sf.createSession("myUser", "myPassword", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());

      mySession.start();

      SimpleString queue = RandomUtil.randomSimpleString();
      SimpleString address = RandomUtil.randomSimpleString();
      boolean durable = RandomUtil.randomBoolean();

      session.createQueue(new QueueConfiguration(queue).setAddress(address).setDurable(durable));
      ClientProducer producer = mySession.createProducer(address);

      NotificationTest.flush(notifConsumer);

      ClientMessage msg = session.createMessage(false);
      msg.putStringProperty("someKey", "someValue");
      msg.setExpiration(1);

      long start = System.currentTimeMillis();
      producer.send(msg);

      ClientMessage[] notifications = NotificationTest.consumeMessages(1, notifConsumer, 5000);
      Assert.assertEquals(MESSAGE_EXPIRED.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      Assert.assertNotNull(notifications[0].getObjectProperty(ManagementHelper.HDR_MESSAGE_ID));
      Assert.assertEquals(address, notifications[0].getObjectProperty(ManagementHelper.HDR_ADDRESS));
      Assert.assertEquals(queue, notifications[0].getObjectProperty(ManagementHelper.HDR_ROUTING_NAME));
      Assert.assertEquals(RoutingType.MULTICAST.getType(), notifications[0].getObjectProperty(ManagementHelper.HDR_ROUTING_TYPE));
      Assert.assertTrue(notifications[0].getTimestamp() >= start);
      Assert.assertTrue((long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      Assert.assertEquals(notifications[0].getTimestamp(), (long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));
   }



   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();

      server = addServer(ActiveMQServers.newActiveMQServer(createDefaultInVMConfig().setMessageExpiryScanPeriod(100), false));
      server.getConfiguration().setAddressQueueScanPeriod(100);
      NotificationActiveMQServerPlugin notificationPlugin = new NotificationActiveMQServerPlugin();
      notificationPlugin.setSendAddressNotifications(true);
      notificationPlugin.setSendConnectionNotifications(true);
      notificationPlugin.setSendDeliveredNotifications(true);
      notificationPlugin.setSendExpiredNotifications(true);

      server.registerBrokerPlugin(notificationPlugin);
      server.start();

      locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);
      session = sf.createSession(false, true, true);
      session.start();

      notifQueue = RandomUtil.randomSimpleString();

      session.createQueue(new QueueConfiguration(notifQueue).setAddress(ActiveMQDefaultConfiguration.getDefaultManagementNotificationAddress()).setDurable(false));

      notifConsumer = session.createConsumer(notifQueue);
   }


   private static void flush(final ClientConsumer notifConsumer) throws ActiveMQException {
      ClientMessage message = null;
      do {
         message = notifConsumer.receiveImmediate();
      }
      while (message != null);
   }

   protected static ClientMessage[] consumeMessages(final int expected,
                                                    final ClientConsumer consumer) throws Exception {
      return consumeMessages(expected, consumer, 500);
   }

   protected static ClientMessage[] consumeMessages(final int expected,
                                                    final ClientConsumer consumer,
                                                    final int timeout) throws Exception {
      ClientMessage[] messages = new ClientMessage[expected];

      ClientMessage m = null;
      for (int i = 0; i < expected; i++) {
         m = consumer.receive(timeout);
         Assert.assertNotNull("expected to received " + expected + " messages, got only " + i, m);
         messages[i] = m;
         m.acknowledge();
      }
      m = consumer.receiveImmediate();
      Assert.assertNull("received one more message than expected (" + expected + ")", m);

      return messages;
   }


}
