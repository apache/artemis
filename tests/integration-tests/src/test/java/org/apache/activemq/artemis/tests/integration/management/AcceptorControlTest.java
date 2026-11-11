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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashMap;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.AcceptorControl;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.tests.integration.SimpleNotificationService;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

public class AcceptorControlTest extends ManagementTestBase {

   @Test
   public void testAttributes() throws Exception {
      TransportConfiguration acceptorConfig = new TransportConfiguration(InVMAcceptorFactory.class.getName(), new HashMap<>(), RandomUtil.randomUUIDString());
      acceptorConfig.getParams().put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "password");

      Configuration config = createBasicConfig().addAcceptorConfiguration(acceptorConfig);
      ActiveMQServer service = createServer(false, config);
      service.setMBeanServer(mbeanServer);
      service.start();

      AcceptorControl acceptorControl = createManagementControl(acceptorConfig.getName());

      assertEquals(acceptorConfig.getName(), acceptorControl.getName());
      assertEquals(acceptorConfig.getFactoryClassName(), acceptorControl.getFactoryClassName());
      assertNotEquals(acceptorConfig.getParams().get(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME), acceptorControl.getParameters().get(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME));
      assertEquals("****", acceptorControl.getParameters().get(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME));
   }

   @Test
   public void testStartStop() throws Exception {
      TransportConfiguration acceptorConfig = new TransportConfiguration(InVMAcceptorFactory.class.getName(), new HashMap<>(), RandomUtil.randomUUIDString());
      Configuration config = createBasicConfig().addAcceptorConfiguration(acceptorConfig);
      ActiveMQServer service = createServer(false, config);
      service.setMBeanServer(mbeanServer);
      service.start();

      AcceptorControl acceptorControl = createManagementControl(acceptorConfig.getName());

      // started by the server
      assertTrue(acceptorControl.isStarted());
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);
      assertNotNull(session);
      session.close();

      acceptorControl.stop();

      assertFalse(acceptorControl.isStarted());

      try {
         sf.createSession(false, true, true);
         fail("acceptor must not accept connections when stopped accepting");
      } catch (ActiveMQException e) {
      }

      acceptorControl.start();

      assertTrue(acceptorControl.isStarted());

      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
      session = sf.createSession(false, true, true);
      assertNotNull(session);
      session.close();

      acceptorControl.stop();

      assertFalse(acceptorControl.isStarted());

      try {
         sf.createSession(false, true, true);
         fail("acceptor must not accept connections when stopped accepting");
      } catch (ActiveMQException e) {
      }

   }

   @Test
   public void testNotifications() throws Exception {
      TransportConfiguration acceptorConfig = new TransportConfiguration(InVMAcceptorFactory.class.getName(), new HashMap<>(), RandomUtil.randomUUIDString());
      TransportConfiguration acceptorConfig2 = new TransportConfiguration(NettyAcceptorFactory.class.getName(), new HashMap<>(), RandomUtil.randomUUIDString());
      Configuration config = createBasicConfig().addAcceptorConfiguration(acceptorConfig).addAcceptorConfiguration(acceptorConfig2);
      ActiveMQServer service = createServer(false, config);
      service.setMBeanServer(mbeanServer);
      service.start();

      AcceptorControl acceptorControl = createManagementControl(acceptorConfig2.getName());

      SimpleNotificationService.Listener notifListener = new SimpleNotificationService.Listener();

      service.getManagementService().addNotificationListener(notifListener);

      assertEquals(0, notifListener.count(CoreNotificationType.ACCEPTOR_STOPPED));

      acceptorControl.stop();

      Notification notif = notifListener.findAny(CoreNotificationType.ACCEPTOR_STOPPED);
      assertEquals(CoreNotificationType.ACCEPTOR_STOPPED, notif.getType());
      assertEquals(NettyAcceptorFactory.class.getName(), notif.getProperties().getSimpleStringProperty(SimpleString.of("factory")).toString());

      acceptorControl.start();

      Wait.assertEquals(1, () -> notifListener.count(CoreNotificationType.ACCEPTOR_STARTED), 5000, 100);
      notif = notifListener.findAny(CoreNotificationType.ACCEPTOR_STARTED);
      assertEquals(CoreNotificationType.ACCEPTOR_STARTED, notif.getType());
      assertEquals(NettyAcceptorFactory.class.getName(), notif.getProperties().getSimpleStringProperty(SimpleString.of("factory")).toString());
   }


   protected AcceptorControl createManagementControl(final String name) throws Exception {
      return ManagementControlHelper.createAcceptorControl(name, mbeanServer);
   }
}
