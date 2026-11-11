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

package org.apache.activemq.artemis.tests.e2e.ha.replication.lockmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.management.remote.JMXServiceURL;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.tests.e2e.common.E2ETestBase;
import org.apache.activemq.artemis.tests.util.Jmx;
import org.apache.commons.io.FileUtils;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GCPauseSimulationTests extends LockManagerTestBase {

   private static final String PRIMARY_LOCATION = E2ETestBase.basedir + "/target/ha/replication/pluggablequorumvote/primary";
   private static final String BACKUP_LOCATION = E2ETestBase.basedir + "/target/ha/replication/pluggablequorumvote/backup";
   private static final String ARTEMIS_PRIMARY_NAME = "artemis-primary";
   private static final String ARTEMIS_BACKUP_NAME = "artemis-backup";
   private static final String CONNECTION_USER = "artemis";
   private static final String CONNECTION_PASSWORD = "artemis";

   private static final String CLIENT_QUEUE_NAME = "queueA";
   private static final String TEXT_MESSAGE = "test message - ";

   private Object artemisPrimary;
   private Object artemisBackup;
   private JMXServiceURL artemisPrimaryJmxServiceURL;
   private JMXServiceURL artemisBackupJmxServiceURL;
   private ObjectNameBuilder artemisPrimaryJmxObjBuilder;
   private ObjectNameBuilder artemisBackupJmxObjBuilder;

   @BeforeEach
   public void setup() throws Exception {
      // Start artemis primary and backup
      artemisPrimary = service.newBrokerImage();
      artemisBackup = service.newBrokerImage();
      service.setNetwork(artemisPrimary, network);
      service.setNetwork(artemisBackup, network);
      service.exposePorts(artemisPrimary, 8161, 61616, 1099);
      service.exposePorts(artemisBackup, 8161, 61616, 1099);
      service.prepareInstance(PRIMARY_LOCATION);
      service.prepareInstance(BACKUP_LOCATION);
      service.exposeBrokerHome(artemisPrimary, PRIMARY_LOCATION);
      service.exposeBrokerHome(artemisBackup, BACKUP_LOCATION);
      service.exposeHosts(artemisPrimary, ARTEMIS_PRIMARY_NAME);
      service.exposeHosts(artemisBackup, ARTEMIS_BACKUP_NAME);
      service.logWait(artemisBackup, ".*AMQ221031: backup announced\\n");

      service.startLogging(artemisPrimary, ARTEMIS_PRIMARY_NAME + " - ");
      service.startLogging(artemisBackup, ARTEMIS_BACKUP_NAME + " - ");

      service.start(artemisPrimary);
      service.start(artemisBackup);

      // Wait primary to start to accept connections
      service.waitForServerToStart(artemisPrimary, CONNECTION_USER, CONNECTION_PASSWORD, 10_000);

      // define some fields with artemis primary and backup info
      artemisPrimaryJmxServiceURL = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + service.getHost(artemisPrimary) + ":" + service.getPort(artemisPrimary, 1099) + "/jmxrmi");
      artemisBackupJmxServiceURL = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + service.getHost(artemisBackup) + ":" + service.getPort(artemisBackup, 1099) + "/jmxrmi");
      artemisPrimaryJmxObjBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), ARTEMIS_PRIMARY_NAME, true);
      artemisBackupJmxObjBuilder = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), ARTEMIS_BACKUP_NAME, true);
   }

   @AfterEach
   public void teardown() throws IOException {
      service.stop(artemisBackup);
      FileUtils.cleanDirectory(new File(BACKUP_LOCATION + "/data"));
      FileUtils.cleanDirectory(new File(BACKUP_LOCATION + "/lock"));
      FileUtils.cleanDirectory(new File(BACKUP_LOCATION + "/log"));
      FileUtils.cleanDirectory(new File(BACKUP_LOCATION + "/tmp"));
      service.stop(artemisPrimary);
      FileUtils.cleanDirectory(new File(PRIMARY_LOCATION + "/data"));
      FileUtils.cleanDirectory(new File(PRIMARY_LOCATION + "/lock"));
      FileUtils.cleanDirectory(new File(PRIMARY_LOCATION + "/log"));
      FileUtils.cleanDirectory(new File(PRIMARY_LOCATION + "/tmp"));
   }

   @Test
   public void scenario1aTest() throws Exception {

      int lastProducedMessage = 0;
      int lastConsumedMessage = 0;
      int totalMessages = 600;

      // ensure artemis-primary is the live
      assertTrue(Jmx.isActive(artemisPrimaryJmxServiceURL, artemisPrimaryJmxObjBuilder).orElse(false));

      // ensure artemis-backup is the backup
      assertTrue(Jmx.isBackup(artemisBackupJmxServiceURL, artemisBackupJmxObjBuilder).orElse(false));

      // client start to produce message on primary
      lastProducedMessage = produce(totalMessages / 2, lastProducedMessage);
      assertEquals(totalMessages / 2, lastProducedMessage);

      // ensure client can consume (just a few) messages from primary
      lastConsumedMessage = consume(lastProducedMessage / 3, lastConsumedMessage);
      assertEquals(lastProducedMessage / 3, lastConsumedMessage);

      // ensure replica is in sync
      assertTrue(isReplicaInSync());

      // pause the artemis-primary
      service.pause(artemisPrimary);
      assertEquals("paused", service.getStatus(artemisPrimary));

      // wait artemis-backup start to accept connections and became the live
      service.waitForServerToStart(artemisBackup, CONNECTION_USER, CONNECTION_PASSWORD, 40_000);
      TimeUnit.SECONDS.sleep(10);

      // ensure artemis-backup is live
      assertTrue(Jmx.isActive(artemisBackupJmxServiceURL, artemisBackupJmxObjBuilder).orElse(false));

      // client should be able to produce messages to backup
      lastProducedMessage += produce(totalMessages / 2, lastProducedMessage);
      assertEquals(totalMessages, lastProducedMessage);

      // client should be able to consume messages from backup
      lastConsumedMessage += consume(lastProducedMessage / 6, lastConsumedMessage);
      assertEquals(lastProducedMessage / 3, lastConsumedMessage);

      // unpause the artemis-primary
      service.unpause(artemisPrimary);
      assertEquals("running", service.getStatus(artemisPrimary));

      // wait artemis-primary shutdown
      TimeUnit.SECONDS.sleep(5);

      // artemis-primary should shutdown yourself as the journal has changed since it was paused.
      assertEquals("exited", service.getStatus(artemisPrimary));

      // restart artemis-primary
      service.restartWithStop(artemisPrimary);

      // wait for artemis-primary to accept connections
      service.waitForServerToStart(artemisPrimary, CONNECTION_USER, CONNECTION_PASSWORD, 20_000);
      TimeUnit.SECONDS.sleep(10);

      // ensure artemis-primary is the live
      artemisPrimaryJmxServiceURL = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + service.getHost(artemisPrimary) + ":" + service.getPort(artemisPrimary, 1099) + "/jmxrmi");
      assertTrue(Jmx.isActive(artemisPrimaryJmxServiceURL, artemisPrimaryJmxObjBuilder).orElse(false));

      // ensure artemis-backup is the backup
      assertTrue(Jmx.isBackup(artemisBackupJmxServiceURL, artemisBackupJmxObjBuilder).orElse(false));

      // ensure replica is in sync
      assertTrue(isReplicaInSync());

      // ensure all remaining produced messages were consumed from primary.
      lastConsumedMessage += consume((lastProducedMessage - lastConsumedMessage), lastConsumedMessage);
      assertEquals(totalMessages, lastConsumedMessage);

   }

   @Test
   public void scenario1bTest() throws Exception {

      int lastProducedMessage = 0;
      int lastConsumedMessage = 0;
      int totalMessages = 600;

      // ensure artemis-primary is the live
      assertTrue(Jmx.isActive(artemisPrimaryJmxServiceURL, artemisPrimaryJmxObjBuilder).orElse(false));

      // ensure artemis-backup is the backup
      assertTrue(Jmx.isBackup(artemisBackupJmxServiceURL, artemisBackupJmxObjBuilder).orElse(false));

      // client start to produce message on primary
      lastProducedMessage = produce(totalMessages / 2, lastProducedMessage);
      assertEquals(totalMessages / 2, lastProducedMessage);

      // ensure client can consume (just a few) messages from primary
      lastConsumedMessage = consume(lastProducedMessage / 3, lastConsumedMessage);
      assertEquals(lastProducedMessage / 3, lastConsumedMessage);

      // ensure replica is in sync
      assertTrue(isReplicaInSync());

      // pause the artemis-primary
      service.pause(artemisPrimary);
      assertEquals("paused", service.getStatus(artemisPrimary));

      // wait artemis-backup start to accept connections and became the live
      service.waitForServerToStart(artemisBackup, CONNECTION_USER, CONNECTION_PASSWORD, 40_000);
      TimeUnit.SECONDS.sleep(10);

      // ensure artemis-backup is live
      assertTrue(Jmx.isActive(artemisBackupJmxServiceURL, artemisBackupJmxObjBuilder).orElse(false));

      // client should be able to produce messages to backup
      lastProducedMessage += produce(totalMessages / 2, lastProducedMessage);
      assertEquals(totalMessages, lastProducedMessage);

      // client should be able to consume messages from backup
      lastConsumedMessage += consume(lastProducedMessage / 6, lastConsumedMessage);
      assertEquals(lastProducedMessage / 3, lastConsumedMessage);

      // unpause the artemis-primary
      service.unpause(artemisPrimary);
      assertEquals("running", service.getStatus(artemisPrimary));

      // wait artemis-primary shutdown
      TimeUnit.SECONDS.sleep(5);

      // artemis-primary should shutdown yourself as the journal has changed since it was paused.
      assertEquals("exited", service.getStatus(artemisPrimary));

      // kill artemis-backup
      service.kill(artemisBackup);

      //update artemis-primary log message as it should not be live
      service.logWait(artemisPrimary, ".*Not a candidate for NodeID.*");

      // restart artemis-primary
      service.restartWithStop(artemisPrimary);

      // restart artemis-backup
      service.start(artemisBackup);

      // wait for artemis-primary to became primary again and accept connections
      service.waitForServerToStart(artemisPrimary, CONNECTION_USER, CONNECTION_PASSWORD, 20_000);

      // ensure artemis-primary is the live
      artemisPrimaryJmxServiceURL = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + service.getHost(artemisPrimary) + ":" + service.getPort(artemisPrimary, 1099) + "/jmxrmi");
      assertTrue(Jmx.isActive(artemisPrimaryJmxServiceURL, artemisPrimaryJmxObjBuilder).orElse(false));

      // ensure artemis-backup is the backup
      artemisBackupJmxServiceURL = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + service.getHost(artemisBackup) + ":" + service.getPort(artemisBackup, 1099) + "/jmxrmi");
      assertTrue(Jmx.isBackup(artemisBackupJmxServiceURL, artemisBackupJmxObjBuilder).orElse(false));

      // ensure replica is in sync
      assertTrue(isReplicaInSync());

      // ensure all remaining produced messages were consumed from primary.
      lastConsumedMessage += consume((lastProducedMessage - lastConsumedMessage), lastConsumedMessage);
      assertEquals(totalMessages, lastConsumedMessage);

   }

   private boolean isReplicaInSync() throws Exception {
      for (int i = 0; i < 10; i++) {
         if (Jmx.isReplicaSync(artemisPrimaryJmxServiceURL, artemisPrimaryJmxObjBuilder).orElse(false)) {
            return true;
         }
         System.out.println("Replica is not in sync, waiting 1s and try again");
         TimeUnit.SECONDS.sleep(1);
      }
      return false;
   }

   private int produce(int numOfMessages, int lastProduced) throws JMSException {
      JmsConnectionFactory producerCF = new JmsConnectionFactory(CONNECTION_USER, CONNECTION_PASSWORD, getClientURL());
      Connection producerConnection = producerCF.createConnection(CONNECTION_USER, CONNECTION_PASSWORD);
      Session producerSession = producerConnection.createSession(true, Session.SESSION_TRANSACTED);
      Queue producerQueue = producerSession.createQueue(CLIENT_QUEUE_NAME);
      MessageProducer producer = producerSession.createProducer(producerQueue);

      int producedCounter = 0;
      while (producedCounter < numOfMessages) {
         producedCounter++;
         TextMessage message = producerSession.createTextMessage(TEXT_MESSAGE + (lastProduced + producedCounter));
         message.setJMSDeliveryMode(DeliveryMode.PERSISTENT);
         producer.send(message);
         System.out.println("produced " + message.getText());
         System.out.println("last produced: " + (lastProduced + producedCounter));
      }
      producerSession.commit();
      producerConnection.close();
      return producedCounter;
   }

   private int consume(int numOfMessages, int lastConsumed) throws JMSException {
      JmsConnectionFactory consumerCF = new JmsConnectionFactory(CONNECTION_USER, CONNECTION_PASSWORD, getClientURL());
      Connection consumerConnection = consumerCF.createConnection(CONNECTION_USER, CONNECTION_PASSWORD);
      Session consumerSession = consumerConnection.createSession(true, Session.SESSION_TRANSACTED);
      Queue consumerQueue = consumerSession.createQueue(CLIENT_QUEUE_NAME);
      MessageConsumer consumer = consumerSession.createConsumer(consumerQueue);
      consumerConnection.start();

      int consumedCounter = 0;
      while (consumedCounter < numOfMessages) {
         TextMessage message = (TextMessage) consumer.receive(5000);
         consumedCounter++;
         assertNotNull(message, "expected message at " + (lastConsumed + consumedCounter));
         assertEquals(TEXT_MESSAGE + (lastConsumed + consumedCounter), message.getText());
         System.out.println("consumed " + message.getText());
         System.out.println("last consumed: " + (lastConsumed + consumedCounter));
      }
      consumerSession.commit();
      consumerConnection.close();
      return consumedCounter;
   }

   private String getClientURL() {
      return "failover:(amqp://" + service.getHost(artemisPrimary) + ":" + service.getPort(artemisPrimary, 61616) + ",amqp://" + service.getHost(artemisBackup) + ":" + service.getPort(artemisBackup, 61616) + ")?failover.amqpOpenServerListAction=IGNORE";
   }
}
