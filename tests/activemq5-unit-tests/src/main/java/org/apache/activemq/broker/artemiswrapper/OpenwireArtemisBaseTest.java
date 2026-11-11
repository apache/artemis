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
package org.apache.activemq.broker.artemiswrapper;

import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.server.config.impl.JMSConfigurationImpl;
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS;
import org.apache.activemq.artemis.utils.uri.URISupport;
import org.apache.activemq.broker.BrokerService;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

public class OpenwireArtemisBaseTest {

   @Rule
   public CleanupThreadRule cleanupRules = new CleanupThreadRule();

   @Rule
   public TemporaryFolder temporaryFolder;
   @Rule
   public TestName name = new TestName();

   public OpenwireArtemisBaseTest() {
      File tmpRoot = new File("./target/tmp");
      tmpRoot.mkdirs();
      temporaryFolder = new TemporaryFolder(tmpRoot);
      //The wrapper stuff will automatically create a default
      //server on a normal connection factory, which will
      //cause problems with clustering tests, which starts
      //all servers explicitly. Setting this to true
      //can prevent the auto-creation from happening.
      BrokerService.disableWrapper = true;
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

   public String CLUSTER_PASSWORD = "OPENWIRECLUSTER";

   protected Configuration createConfig(final int serverID) throws Exception {
      return createConfig("localhost", serverID, Collections.emptyMap());
   }

   protected Configuration createConfig(final String hostAddress, final int serverID, final int port) throws Exception {
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

      configuration.addAcceptorConfiguration("netty", newURIwithPort(hostAddress, port));
      configuration.addConnectorConfiguration("netty-connector", newURIwithPort(hostAddress, port));

      return configuration;
   }

   protected Configuration createConfig(final String hostAddress, final int serverID) throws Exception {
      return createConfig(hostAddress, serverID, Collections.emptyMap());
   }

   protected Configuration createConfig(final String hostAddress,
                                        final int serverID,
                                        Map<String, String> params) throws Exception {
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

      configuration.addAcceptorConfiguration("netty", newURI(hostAddress, serverID) + "?" + URISupport.createQueryString(params));
      configuration.addConnectorConfiguration("netty-connector", newURI(hostAddress, serverID));

      return configuration;
   }

   //extraAcceptor takes form: "?name=value&name1=value ..."
   protected Configuration createConfig(final int serverID, String extraAcceptorParams) throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl().setJMXManagementEnabled(false).
         setSecurityEnabled(false).setJournalMinFiles(2).setJournalFileSize(100 * 1024).setJournalType(JournalType.NIO).
         setJournalDirectory(getJournalDir(serverID, false)).
         setBindingsDirectory(getBindingsDir(serverID, false)).
         setPagingDirectory(getPageDir(serverID, false)).
         setLargeMessagesDirectory(getLargeMessagesDir(serverID, false)).
         setJournalCompactMinFiles(0).
         setJournalCompactPercentage(0).
         setClusterPassword(CLUSTER_PASSWORD);

      configuration.addAddressesSetting("#", new AddressSettings().setAutoCreateJmsQueues(true).setAutoDeleteJmsQueues(true));

      String fullAcceptorUri = newURI(serverID) + extraAcceptorParams;
      configuration.addAcceptorConfiguration("netty", fullAcceptorUri);

      configuration.addConnectorConfiguration("netty-connector", newURI(serverID));
      return configuration;
   }

   public void deployClusterConfiguration(Configuration config, Integer... targetIDs) throws Exception {
      StringBuilder sb = new StringBuilder();
      String separator = "";
      for (int x : targetIDs) {
         sb.append(separator + newURI(x));
         separator = ",";
      }

      String ccURI = "static://(" + sb.toString() + ")?connectorName=netty-connector;retryInterval=500;messageLoadBalancingType=STRICT;maxHops=1";

      config.addClusterConfiguration("clusterCC", ccURI);
   }

   protected static String newURI(int serverID) {
      return newURI("localhost", serverID);
   }

   protected static String newURI(String localhostAddress, int serverID) {
      return "tcp://" + localhostAddress + ":" + (61616 + serverID);
   }

   protected static String newURIwithPort(String localhostAddress, int port) throws Exception {
      return newURIwithPort(localhostAddress, port, Collections.emptyMap());
   }

   protected static String newURIwithPort(String localhostAddress,
                                          int port,
                                          Map<String, String> params) throws Exception {
      return "tcp://" + localhostAddress + ":" + port + "?" + URISupport.createQueryString(params);
   }

   private static Object createProxy(final ObjectName objectName,
                                     final Class mbeanInterface,
                                     final MBeanServer mbeanServer) {
      return MBeanServerInvocationHandler.newProxyInstance(mbeanServer, objectName, mbeanInterface, false);
   }

   protected void shutDownClusterServers(EmbeddedJMS[] servers) throws Exception {
      for (int i = 0; i < servers.length; i++) {
         try {
            servers[i].stop();
         } catch (Throwable t) {
            t.printStackTrace();
         }
      }
   }

   protected void shutDownNonClusterServers(EmbeddedJMS[] servers) throws Exception {
      shutDownClusterServers(servers);
   }

   protected void setUpNonClusterServers(EmbeddedJMS[] servers) throws Exception {

      Configuration[] serverCfgs = new Configuration[servers.length];
      for (int i = 0; i < servers.length; i++) {
         serverCfgs[i] = createConfig(i);
      }

      for (int i = 0; i < servers.length; i++) {
         servers[i] = new EmbeddedJMS().setConfiguration(serverCfgs[i]).setJmsConfiguration(new JMSConfigurationImpl());
      }

      for (int i = 0; i < servers.length; i++) {
         servers[i].start();
      }
   }

   protected void setUpClusterServers(EmbeddedJMS[] servers) throws Exception {

      Configuration[] serverCfgs = new Configuration[servers.length];
      for (int i = 0; i < servers.length; i++) {
         serverCfgs[i] = createConfig(i);
      }

      for (int i = 0; i < servers.length; i++) {
         deployClusterConfiguration(serverCfgs[i], getTargets(servers.length, i));
      }

      for (int i = 0; i < servers.length; i++) {
         servers[i] = new EmbeddedJMS().setConfiguration(serverCfgs[i]).setJmsConfiguration(new JMSConfigurationImpl());
      }

      for (int i = 0; i < servers.length; i++) {
         servers[i].start();
      }

      for (int i = 0; i < servers.length; i++) {
         Assert.assertTrue(servers[i].waitClusterForming(100, TimeUnit.MILLISECONDS, 20, servers.length));
      }
   }

   private Integer[] getTargets(int total, int self) {
      int lenTargets = total - self;
      List<Integer> targets = new ArrayList<>();
      for (int i = 0; i < lenTargets; i++) {
         if (i != self) {
            targets.add(i);
         }
      }
      return targets.toArray(new Integer[0]);
   }

   public EmbeddedJMS createBroker() throws Exception {
      Configuration config0 = createConfig(0);
      EmbeddedJMS newbroker = new EmbeddedJMS().setConfiguration(config0).setJmsConfiguration(new JMSConfigurationImpl());
      return newbroker;
   }

}
