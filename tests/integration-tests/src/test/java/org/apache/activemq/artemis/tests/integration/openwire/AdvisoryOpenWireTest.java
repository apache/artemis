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
package org.apache.activemq.artemis.tests.integration.openwire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.advisory.AdvisorySupport;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.config.Configuration;

public class AdvisoryOpenWireTest extends BasicOpenWireTest {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      //this system property is used to construct the executor in
      //org.apache.activemq.transport.AbstractInactivityMonitor.createExecutor()
      //and affects the pool's shutdown time. (default is 30 sec)
      //set it to 2 to make tests shutdown quicker.
      System.setProperty("org.apache.activemq.transport.AbstractInactivityMonitor.keepAliveTime", "2");
      this.realStore = true;
      super.setUp();
   }

   @Override
   protected void extraServerConfig(Configuration serverConfig) {
      // ensure advisory addresses are visible
      serverConfig.getAcceptorConfigurations().iterator().next().getExtraParams().put("suppressInternalManagementObjects", "false");
      super.extraServerConfig(serverConfig);
   }

   @Test
   public void testTempTopicLeak() throws Exception {

      try (Connection connection = factory.createConnection()) {
         connection.start();

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         TemporaryTopic temporaryTopic = session.createTemporaryTopic();
         temporaryTopic.delete();

         AddressControl advisoryAddress = assertNonNullAddressControl("ActiveMQ.Advisory.TempTopic");
         Wait.waitFor(() -> advisoryAddress.getMessageCount() == 0);

         Wait.assertEquals(0, advisoryAddress::getMessageCount);
         Wait.assertEquals(2, advisoryAddress::getUnRoutedMessageCount);

      }
   }

   private AddressControl assertNonNullAddressControl(String match) {
      AddressControl advisoryAddressControl = null;
      List<AddressControl> addressResources = server.getManagementService().getAddressControls();

      for (AddressControl addressResource : addressResources) {
         if (addressResource.getAddress().equals(match)) {
            advisoryAddressControl = addressResource;
         }
      }
      assertNotNull(advisoryAddressControl, "addressControl for temp advisory");
      return advisoryAddressControl;
   }

   @Test
   public void testTempQueueLeak() throws Exception {

      try (Connection connection = factory.createConnection()) {
         connection.start();

         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         TemporaryQueue temporaryQueue = session.createTemporaryQueue();
         temporaryQueue.delete();

         AddressControl advisoryAddress = assertNonNullAddressControl("ActiveMQ.Advisory.TempQueue");
         Wait.waitFor(() -> advisoryAddress.getMessageCount() == 0);

         Wait.assertEquals(0, advisoryAddress::getMessageCount);
         Wait.assertEquals(2, advisoryAddress::getUnRoutedMessageCount);

      }
   }

   @Test
   public void testTempQueueLeakManyConnections() throws Exception {
      final Connection[] connections = new Connection[20];

      try {
         for (int i = 0; i < connections.length; i++) {
            connections[i] = factory.createConnection();
            connections[i].start();
         }

         Session session = connections[0].createSession(false, Session.AUTO_ACKNOWLEDGE);

         for (int i = 0; i < connections.length; i++) {
            TemporaryQueue temporaryQueue = session.createTemporaryQueue();
            temporaryQueue.delete();
         }

         AddressControl advisoryAddress = assertNonNullAddressControl("ActiveMQ.Advisory.TempQueue");

         Wait.waitFor(() -> advisoryAddress.getMessageCount() == 0);
         Wait.assertEquals(0, advisoryAddress::getMessageCount);

      } finally {
         for (Connection conn : connections) {
            if (conn != null) {
               conn.close();
            }
         }
      }
   }

   @Test
   public void testLongLivedConnectionGetsAllPastPrefetch() throws Exception {
      final Connection[] connections = new Connection[2];

      final int numTempDestinations = 600;  // such that 2x exceeds default 1k prefetch for advisory consumer
      try {
         for (int i = 0; i < connections.length; i++) {
            connections[i] = factory.createConnection();
            connections[i].start();
         }

         Session session = connections[0].createSession(false, Session.AUTO_ACKNOWLEDGE);

         for (int i = 0; i < numTempDestinations; i++) {
            TemporaryQueue temporaryQueue = session.createTemporaryQueue();
            temporaryQueue.delete();
         }

         AddressControl advisoryAddress = assertNonNullAddressControl("ActiveMQ.Advisory.TempQueue");
         Wait.waitFor(() -> advisoryAddress.getMessageCount() == 0);
         Wait.assertEquals(0, advisoryAddress::getMessageCount);

         // there is an advisory for create and another for delete
         assertEquals(numTempDestinations * 2, advisoryAddress.getRoutedMessageCount(), "all routed");

      } finally {
         for (Connection conn : connections) {
            if (conn != null) {
               conn.close();
            }
         }
      }
   }

   @Test
   public void testConnectionAdvisory() throws Exception {
      final Connection[] connections = new Connection[20];

      connections[0] = factory.createConnection();
      connections[0].start();

      final CountDownLatch numConnectionsCreatedViaAdvisoryNotificationsLatch = new CountDownLatch(19);
      connections[0].createSession(false, Session.AUTO_ACKNOWLEDGE)
                    .createConsumer(AdvisorySupport.getConnectionAdvisoryTopic()).setMessageListener(message -> numConnectionsCreatedViaAdvisoryNotificationsLatch.countDown());

      try {
         for (int i = 1; i < connections.length; i++) {
            connections[i] = factory.createConnection();
            connections[i].start();
         }

         Session session = connections[0].createSession(false, Session.AUTO_ACKNOWLEDGE);
         session.close();

         assertTrue(numConnectionsCreatedViaAdvisoryNotificationsLatch.await(5, TimeUnit.SECONDS), "Got all the advisories on time");

      } finally {
         for (Connection conn : connections) {
            if (conn != null) {
               conn.close();
            }
         }
      }

   }

}
