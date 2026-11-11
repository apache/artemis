/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.transport.failover;

import javax.jms.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.jms.server.config.impl.JMSConfigurationImpl;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.broker.artemiswrapper.OpenwireArtemisBaseTest;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for AMQ-3719
 */
public class ConnectionHangOnStartupTest extends OpenwireArtemisBaseTest {

   private static final Logger LOG = LoggerFactory.getLogger(ConnectionHangOnStartupTest.class);

   // short maxInactivityDurationInitalDelay to trigger the bug, short
   // maxReconnectDelay so that the test runs faster (because it will retry
   // connection sooner)
   protected String uriString = "failover://(tcp://localhost:62001?wireFormat.maxInactivityDurationInitalDelay=1,tcp://localhost:62002?wireFormat.maxInactivityDurationInitalDelay=1)?randomize=false&maxReconnectDelay=200";
   protected EmbeddedJMS primary = null;
   protected AtomicReference<EmbeddedJMS> backup = new AtomicReference<>();

   @After
   public void tearDown() throws Exception {

      EmbeddedJMS brokerService = backup.get();
      if (brokerService != null) {
         brokerService.stop();
      }
      if (primary != null)
         primary.stop();
   }

   protected ActiveMQConnectionFactory createConnectionFactory() throws Exception {
      return new ActiveMQConnectionFactory(uriString);
   }

   protected void createPrimary() throws Exception {
      Configuration config = createConfig("localhost", 0, 62001);
      primary = new EmbeddedJMS().setConfiguration(config).setJmsConfiguration(new JMSConfigurationImpl());
      primary.start();
   }

   protected void createBackup() throws Exception {
      Configuration config = createConfig("localhost", 1, 62002);
      EmbeddedJMS broker = new EmbeddedJMS().setConfiguration(config).setJmsConfiguration(new JMSConfigurationImpl());
      broker.start();
      backup.set(broker);
   }

   @Test(timeout = 60000)
   public void testInitialWireFormatNegotiationTimeout() throws Exception {
      final AtomicReference<Connection> conn = new AtomicReference<>();
      final CountDownLatch connStarted = new CountDownLatch(1);

      Thread t = new Thread(() -> {
         try {
            conn.set(createConnectionFactory().createConnection());
            conn.get().start();
         } catch (Exception ex) {
            LOG.error("could not create or start connection", ex);
         }
         connStarted.countDown();
      });
      t.start();
      createPrimary();

      // slave will never start unless the master dies!
      //createSlave();

      conn.get().close();
   }
}
