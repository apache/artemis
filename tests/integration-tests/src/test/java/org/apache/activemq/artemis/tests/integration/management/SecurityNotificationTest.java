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

import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.CONSUMER_CREATED;
import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.SECURITY_AUTHENTICATION_VIOLATION;
import static org.apache.activemq.artemis.api.core.management.CoreNotificationType.SECURITY_PERMISSION_VIOLATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.management.JMX;
import javax.security.auth.Subject;
import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SecurityNotificationTest extends ActiveMQTestBase {


   private ActiveMQServer server;

   private ClientSession adminSession;

   private ClientConsumer notifConsumer;

   private SimpleString notifQueue;



   @Test
   public void testSECURITY_AUTHENTICATION_VIOLATION() throws Exception {
      String unknownUser = RandomUtil.randomUUIDString();

      SecurityNotificationTest.flush(notifConsumer);

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);

      long start = System.currentTimeMillis();
      try {
         sf.createSession(unknownUser, RandomUtil.randomUUIDString(), false, true, true, false, 1);
         fail("authentication must fail and a notification of security violation must be sent");
      } catch (Exception e) {
      }

      ClientMessage[] notifications = SecurityNotificationTest.consumeMessages(1, notifConsumer);
      assertEquals(SECURITY_AUTHENTICATION_VIOLATION.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      assertEquals(unknownUser, notifications[0].getObjectProperty(ManagementHelper.HDR_USER).toString());
      assertEquals("unavailable", notifications[0].getObjectProperty(ManagementHelper.HDR_CERT_SUBJECT_DN).toString());
      assertEquals("invm:0", notifications[0].getObjectProperty(ManagementHelper.HDR_REMOTE_ADDRESS).toString());
      assertTrue(notifications[0].getTimestamp() >= start);
      assertTrue((long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      assertEquals(notifications[0].getTimestamp(), (long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));
   }

   @Test
   public void testSECURITY_PERMISSION_VIOLATION() throws Exception {
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString address = RandomUtil.randomUUIDSimpleString();

      // guest can not create queue
      Role role = new Role("roleCanNotCreateQueue", true, true, false, true, false, true, true, true, true, true, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      server.getSecurityRepository().addMatch(address.toString(), roles);
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addRole("guest", "roleCanNotCreateQueue");

      SecurityNotificationTest.flush(notifConsumer);

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession guestSession = sf.createSession("guest", "guest", false, true, true, false, 1);

      long start = System.currentTimeMillis();
      try {
         guestSession.createQueue(QueueConfiguration.of(queue).setAddress(address));
         fail("session creation must fail and a notification of security violation must be sent");
      } catch (Exception e) {
      }

      ClientMessage[] notifications = SecurityNotificationTest.consumeMessages(2, notifConsumer);

      int i = 0;
      for (i = 0; i < notifications.length; i++) {
         if (SECURITY_PERMISSION_VIOLATION.toString().equals(notifications[i].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString())) {
            break;
         }
      }
      assertTrue(i < notifications.length);
      assertEquals(SECURITY_PERMISSION_VIOLATION.toString(), notifications[i].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      assertEquals("guest", notifications[i].getObjectProperty(ManagementHelper.HDR_USER).toString());
      assertEquals(address.toString(), notifications[i].getObjectProperty(ManagementHelper.HDR_ADDRESS).toString());
      assertEquals(CheckType.CREATE_DURABLE_QUEUE.toString(), notifications[i].getObjectProperty(ManagementHelper.HDR_CHECK_TYPE).toString());
      assertTrue(notifications[i].getTimestamp() >= start);
      assertTrue((long) notifications[i].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      assertEquals(notifications[i].getTimestamp(), (long) notifications[i].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));

      guestSession.close();
   }

   @Test
   public void testSubjectSECURITY_PERMISSION_VIOLATION() throws Exception {

      SecurityNotificationTest.flush(notifConsumer);

      Subject guestSubject = new Subject();
      guestSubject.getPrincipals().add(new UserPrincipal("guest"));

      final AddressControl addressControl = JMX.newMBeanProxy(
         ManagementFactory.getPlatformMBeanServer(),
         ObjectNameBuilder.DEFAULT.getAddressObjectName(ActiveMQDefaultConfiguration.getDefaultManagementNotificationAddress()), AddressControl.class, false);

      Exception e = SecurityManagerShim.callAs(guestSubject, (Callable<Exception>) () -> {
         try {
            addressControl.sendMessage(null, 1, "hi", false, null, null);
            fail("need Send permission");
         } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("guest"));
            assertTrue(expected.getMessage().contains("SEND"));
            return expected;
         }
         return null;
      });
      assertNotNull(e, "expect exception");

      ClientMessage[] notifications = SecurityNotificationTest.consumeMessages(3, notifConsumer);
      int i = 0;
      for (i = 0; i < notifications.length; i++) {
         if (SECURITY_PERMISSION_VIOLATION.toString().equals(notifications[i].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString())) {
            break;
         }
      }
      assertTrue(i < notifications.length);
      assertEquals(SECURITY_PERMISSION_VIOLATION.toString(), notifications[i].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      assertEquals("guest", notifications[i].getObjectProperty(ManagementHelper.HDR_USER).toString());
      assertEquals(ActiveMQDefaultConfiguration.getDefaultManagementNotificationAddress().toString(), notifications[i].getObjectProperty(ManagementHelper.HDR_ADDRESS).toString());
      assertEquals(CheckType.SEND.toString(), notifications[i].getObjectProperty(ManagementHelper.HDR_CHECK_TYPE).toString());
   }

   @Test
   public void testCONSUMER_CREATED() throws Exception {
      SimpleString queue = RandomUtil.randomUUIDSimpleString();
      SimpleString address = RandomUtil.randomUUIDSimpleString();

      Role role = new Role("role", true, true, true, true, false, true, true, true, true, true, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      server.getSecurityRepository().addMatch(address.toString(), roles);
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addRole("guest", "role");

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession guestSession = sf.createSession("guest", "guest", false, true, true, false, 1);

      guestSession.createQueue(QueueConfiguration.of(queue).setAddress(address).setRoutingType(RoutingType.ANYCAST));
      SecurityNotificationTest.flush(notifConsumer);

      long start = System.currentTimeMillis();
      guestSession.createConsumer(queue);

      ClientMessage[] notifications = SecurityNotificationTest.consumeMessages(1, notifConsumer);
      assertEquals(CONSUMER_CREATED.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
      assertEquals("guest", notifications[0].getObjectProperty(ManagementHelper.HDR_USER).toString());
      assertEquals("guest", notifications[0].getObjectProperty(ManagementHelper.HDR_VALIDATED_USER).toString());
      assertEquals(address.toString(), notifications[0].getObjectProperty(ManagementHelper.HDR_ADDRESS).toString());
      assertEquals(SimpleString.of("unavailable"), notifications[0].getSimpleStringProperty(ManagementHelper.HDR_CERT_SUBJECT_DN));
      assertTrue(notifications[0].getTimestamp() >= start);
      assertTrue((long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP) >= start);
      assertEquals(notifications[0].getTimestamp(), (long) notifications[0].getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TIMESTAMP));

      guestSession.close();
   }



   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      Configuration config = createDefaultInVMConfig().setSecurityEnabled(true).setJMXManagementEnabled(true);
      server = addServer(ActiveMQServers.newActiveMQServer(config, false));
      server.start();

      notifQueue = RandomUtil.randomUUIDSimpleString();

      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("admin", "admin");
      securityManager.getConfiguration().addUser("guest", "guest");
      securityManager.getConfiguration().setDefaultUser("guest");

      Role role = new Role("notif", true, true, true, true, true, true, true, true, true, true, false, false);
      Set<Role> roles = new HashSet<>();
      roles.add(role);
      server.getSecurityRepository().addMatch(ActiveMQDefaultConfiguration.getDefaultManagementNotificationAddress().toString(), roles);

      securityManager.getConfiguration().addRole("admin", "notif");

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);
      adminSession = sf.createSession("admin", "admin", false, true, true, false, 1);
      adminSession.start();

      adminSession.createQueue(QueueConfiguration.of(notifQueue).setAddress(ActiveMQDefaultConfiguration.getDefaultManagementNotificationAddress()).setDurable(false).setTemporary(true));

      notifConsumer = adminSession.createConsumer(notifQueue);
   }


   private static void flush(final ClientConsumer notifConsumer) throws ActiveMQException {
      ClientMessage message = null;
      do {
         message = notifConsumer.receive(500);
      }
      while (message != null);
   }

   protected static ClientMessage[] consumeMessages(final int expected,
                                                    final ClientConsumer consumer) throws Exception {
      ClientMessage[] messages = new ClientMessage[expected];

      ClientMessage m = null;
      for (int i = 0; i < expected; i++) {
         m = consumer.receive(500);
         assertNotNull(m, "expected to received " + expected + " messages, got only " + i);
         messages[i] = m;
         m.acknowledge();
      }
      m = consumer.receiveImmediate();
      assertNull(m, "received one more message than expected (" + expected + ")");

      return messages;
   }


}
