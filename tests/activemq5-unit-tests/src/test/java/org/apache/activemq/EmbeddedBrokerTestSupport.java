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
package org.apache.activemq;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import java.io.File;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.server.config.impl.JMSConfigurationImpl;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.junit.rules.TemporaryFolder;

/**
 * A useful base class which creates and closes an embedded broker
 */
public abstract class EmbeddedBrokerTestSupport extends CombinationTestSupport {

   protected BrokerService broker;
   protected EmbeddedJMS artemisBroker;
   protected String bindAddress = "tcp://localhost:61616";
   protected ConnectionFactory connectionFactory;
   protected boolean useTopic;
   protected ActiveMQDestination destination;
   protected boolean disableWrapper = false;

   public TemporaryFolder temporaryFolder;

   public String CLUSTER_PASSWORD = "OPENWIRECLUSTER";

   @Override
   protected void setUp() throws Exception {
      BrokerService.disableWrapper = disableWrapper;
      File tmpRoot = new File("./target/tmp");
      tmpRoot.mkdirs();
      temporaryFolder = new TemporaryFolder(tmpRoot);
      temporaryFolder.create();

      if (artemisBroker == null) {
         artemisBroker = createArtemisBroker();
      }
      startBroker();

      connectionFactory = createConnectionFactory();

      destination = createDestination();
   }

   @Override
   protected void tearDown() throws Exception {
      if (artemisBroker != null) {
         try {
            artemisBroker.stop();
            artemisBroker = null;
         } catch (Exception e) {
         }
      }
      temporaryFolder.delete();
   }

   public String getTmp() {
      return getTmpFile().getAbsolutePath();
   }

   public File getTmpFile() {
      return temporaryFolder.getRoot();
   }

   protected String getJournalDir(int serverID, boolean backup) {
      return getTmp() + "/journal_" + serverID + "_" + backup;
   }

   protected String getBindingsDir(int serverID, boolean backup) {
      return getTmp() + "/binding_" + serverID + "_" + backup;
   }

   protected String getPageDir(int serverID, boolean backup) {
      return getTmp() + "/paging_" + serverID + "_" + backup;
   }

   protected String getLargeMessagesDir(int serverID, boolean backup) {
      return getTmp() + "/paging_" + serverID + "_" + backup;
   }

   protected static String newURI(String localhostAddress, int serverID) {
      return "tcp://" + localhostAddress + ":" + (61616 + serverID);
   }

   /**
    * Factory method to create a new {@link Destination}
    *
    * @return newly created Destination
    */
   protected ActiveMQDestination createDestination() {
      return createDestination(getDestinationString());
   }

   /**
    * Factory method to create the destination in either the queue or topic space based on the value of the
    * {@link #useTopic} field
    */
   protected ActiveMQDestination createDestination(String subject) {
      if (useTopic) {
         return new ActiveMQTopic(subject);
      } else {
         return new ActiveMQQueue(subject);
      }
   }

   /**
    * {@return the name of the destination used in this test case}
    */
   protected String getDestinationString() {
      return getClass().getName() + "." + getName();
   }

   /**
    * Factory method to create a new {@link ConnectionFactory} instance
    *
    * @return a newly created connection factory
    */
   protected ConnectionFactory createConnectionFactory() throws Exception {
      return new ActiveMQConnectionFactory(bindAddress);
   }

   public EmbeddedJMS createArtemisBroker() throws Exception {
      Configuration config0 = createConfig("localhost", 0);
      EmbeddedJMS newbroker = new EmbeddedJMS().setConfiguration(config0).setJmsConfiguration(new JMSConfigurationImpl());
      return newbroker;
   }

   protected Configuration createConfig(final String hostAddress, final int serverID) throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl().setJMXManagementEnabled(false).
         setSecurityEnabled(false).setJournalMinFiles(2).setJournalFileSize(1000 * 1024).setJournalType(JournalType.NIO).
         setJournalDirectory(getJournalDir(serverID, false)).
         setBindingsDirectory(getBindingsDir(serverID, false)).
         setPagingDirectory(getPageDir(serverID, false)).
         setLargeMessagesDirectory(getLargeMessagesDir(serverID, false)).
         setJournalCompactMinFiles(0).
         setJournalCompactPercentage(0).
         setClusterPassword(CLUSTER_PASSWORD);

      configuration.addAddressesSetting("#", new AddressSettings().setAutoCreateJmsQueues(true).setAutoDeleteJmsQueues(true));

      configuration.addAcceptorConfiguration("netty", newURI(hostAddress, serverID));
      configuration.addConnectorConfiguration("netty-connector", newURI(hostAddress, serverID));

      return configuration;
   }

   //we keep this because some other tests uses it.
   //we'll delete this when those tests are dealt with.
   protected BrokerService createBroker() throws Exception {
      BrokerService answer = new BrokerService();
      answer.setPersistent(isPersistent());
      answer.getManagementContext().setCreateConnector(false);
      answer.addConnector(bindAddress);
      return answer;
   }

   protected void startBroker() throws Exception {
      artemisBroker.start();
   }

   protected boolean isPersistent() {
      return false;
   }

   protected Connection createConnection() throws Exception {
      return connectionFactory.createConnection();
   }
}
