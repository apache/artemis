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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.naming.NameNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.registry.JndiBindingRegistry;
import org.apache.activemq.artemis.jms.server.config.ConnectionFactoryConfiguration;
import org.apache.activemq.artemis.jms.server.config.impl.ConnectionFactoryConfigurationImpl;
import org.apache.activemq.artemis.jms.server.impl.JMSServerManagerImpl;
import org.apache.activemq.artemis.tests.integration.cluster.failover.FailoverTestBase;
import org.apache.activemq.artemis.tests.unit.util.InVMNamingContext;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Validates if a JMS management operations will wait until the server is activated.  If the server is not active then
 * JMS management operations (e.g. create connection factory, create queue, etc.) should be stored in a cache and then
 * executed once the server becomes active.  The normal use-case for this involves a live/backup pair.
 */
public class ManagementActivationTest extends FailoverTestBase {

   private JMSServerManagerImpl backupJmsServer;
   private InVMNamingContext context;
   private String connectorName;

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(boolean live) {
      return TransportConfigurationUtils.getInVMAcceptor(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(boolean live) {
      TransportConfiguration inVMConnector = TransportConfigurationUtils.getInVMConnector(live);
      connectorName = inVMConnector.getName();
      return inVMConnector;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      backupJmsServer = new JMSServerManagerImpl(backupServer.getServer());
      context = new InVMNamingContext();
      backupJmsServer.setRegistry(new JndiBindingRegistry(context));
      backupJmsServer.start();
   }

   @Test
   public void testCreateConnectionFactory() throws Exception {
      List<String> connectorNames = new ArrayList<>();
      connectorNames.add(connectorName);

      ConnectionFactoryConfiguration config = new ConnectionFactoryConfigurationImpl().setName("test").setConnectorNames(connectorNames).setBindings("/myConnectionFactory");
      backupJmsServer.createConnectionFactory(true, config, "/myConnectionFactory");

      boolean exception = false;
      try {
         context.lookup("/myConnectionFactory");
      } catch (NameNotFoundException e) {
         exception = true;
      }

      assertTrue(exception, "exception expected");

      primaryServer.crash();

      long timeout = System.currentTimeMillis() + 5000;

      ConnectionFactory factory = null;
      while (timeout > System.currentTimeMillis()) {
         try {
            factory = (ConnectionFactory) context.lookup("/myConnectionFactory");
         } catch (Exception ignored) {
            // ignored.printStackTrace();
         }
         if (factory == null) {
            Thread.sleep(100);
         } else {
            break;
         }
      }

      assertNotNull(factory);
   }

   @Test
   public void testCreateQueue() throws Exception {
      backupJmsServer.createQueue(false, "myQueue", null, false, "/myQueue");

      boolean exception = false;
      try {
         context.lookup("/myQueue");
      } catch (NameNotFoundException e) {
         exception = true;
      }

      assertTrue(exception, "exception expected");

      primaryServer.crash();

      long timeout = System.currentTimeMillis() + 5000;

      Queue queue = null;
      while (timeout > System.currentTimeMillis()) {
         try {
            queue = (Queue) context.lookup("/myQueue");
         } catch (Exception ignored) {
            // ignored.printStackTrace();
         }
         if (queue == null) {
            Thread.sleep(100);
         } else {
            break;
         }
      }

      assertNotNull(queue);
   }

   @Test
   public void testCreateTopic() throws Exception {
      backupJmsServer.createTopic(false, "myTopic", "/myTopic");

      boolean exception = false;
      try {
         context.lookup("/myTopic");
      } catch (NameNotFoundException e) {
         exception = true;
      }

      assertTrue(exception, "exception expected");

      primaryServer.crash();

      long timeout = System.currentTimeMillis() + 5000;

      Topic topic = null;
      while (timeout > System.currentTimeMillis()) {
         try {
            topic = (Topic) context.lookup("/myTopic");
         } catch (Exception ignored) {
            // ignored.printStackTrace();
         }
         if (topic == null) {
            Thread.sleep(100);
         } else {
            break;
         }
      }

      assertNotNull(topic);
   }

   /**
    * Since the back-up server is *not* active the "destroyConnectionFactory" operation should be cached and not run. If
    * it was run we would receive an exception.
    */
   @Test
   public void testDestroyConnectionFactory() throws Exception {

      // This test was deadlocking one in 10, so running it a couple times to make sure that won't happen any longer
      for (int testrun = 0; testrun < 50; testrun++) {
         boolean exception = false;
         try {
            backupJmsServer.destroyConnectionFactory("fakeConnectionFactory");
         } catch (Exception e) {
            exception = true;
         }

         assertFalse(exception);

         tearDown();
         setUp();
      }
   }

   /**
    * Since the back-up server is *not* active the "removeQueueFromJNDI" operation should be cached and not run. If it
    * was run we would receive an exception.
    */
   @Test
   public void testRemoveQueue() throws Exception {
      boolean exception = false;
      try {
         backupJmsServer.removeQueueFromBindingRegistry("fakeQueue");
      } catch (Exception e) {
         exception = true;
      }

      assertFalse(exception);
   }

   /**
    * Since the back-up server is *not* active the "removeTopicFromJNDI" operation should be cached and not run. If it
    * was run we would receive an exception.
    */
   @Test
   public void testRemoveTopic() throws Exception {
      boolean exception = false;
      try {
         backupJmsServer.removeTopicFromBindingRegistry("fakeTopic");
      } catch (Exception e) {
         exception = true;
      }

      assertFalse(exception);
   }

}
