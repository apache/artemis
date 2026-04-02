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
package org.apache.activemq.artemis.core.config.impl;

import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ScaleDownConfiguration;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.config.ha.LiveOnlyPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreMasterPolicyConfiguration;
import org.apache.activemq.artemis.core.deployers.impl.FileConfigurationParser;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.apache.activemq.artemis.utils.DefaultSensitiveStringCodec;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.apache.activemq.artemis.utils.StringPrintStream;
import org.junit.Assert;
import org.junit.Test;

public class FileConfigurationParserTest extends ActiveMQTestBase {

   /**
    * These "InvalidConfigurationTest*.xml" files are modified copies of {@value
    * ConfigurationTest-full-config.xml}, so just diff it for changes, e.g.
    * <p>
    * <pre>
    * diff ConfigurationTest-full-config.xml InvalidConfigurationTest4.xml
    * </pre>
    *
    * @throws Exception
    */
   @Test
   public void testSchemaValidation() throws Exception {
      for (int i = 0; i < 7; i++) {
         String filename = "InvalidConfigurationTest" + i + ".xml";
         FileConfiguration fc = new FileConfiguration();
         FileDeploymentManager deploymentManager = new FileDeploymentManager(filename);
         deploymentManager.addDeployable(fc);

         try {
            deploymentManager.readConfiguration();
            fail("parsing should have failed for " + filename);
         } catch (java.lang.IllegalStateException e) {
            Throwable cause = e.getCause();
            assertTrue("must have been org.xml.sax.SAXParseException", cause instanceof org.xml.sax.SAXParseException);
         }
      }
   }

   @Test
   public void testDivertRoutingNameIsNotRequired() throws Exception {
      String filename = "divertRoutingNameNotRequired.xml";
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(filename);
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();
   }

   @Test
   public void testDuplicateQueue() throws Exception {
      String filename = "FileConfigurationParser-duplicateQueue.xml";
      FileConfiguration fc = new FileConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(filename);
      deploymentManager.addDeployable(fc);
      deploymentManager.readConfiguration();
      ActiveMQServer server = addServer((ActiveMQServer) deploymentManager.buildService(null, null, null).get("core"));
      server.start();
      assertEquals(0, server.locateQueue(SimpleString.toSimpleString("q")).getMaxConsumers());
   }

   @Test
   public void testDuplicateAddressSettings() throws Exception {
      FileConfigurationParser parser = new FileConfigurationParser();
      Configuration config = parser.parseMainConfig(ClassloadingUtil.findResource("FileConfigurationParser-duplicateAddressSettings.xml").openStream());

      Assert.assertEquals(123, config.getAddressesSettings().get("foo").getRedistributionDelay());
   }

   @Test
   public void testParsingClusterConnectionURIs() throws Exception {
      FileConfigurationParser parser = new FileConfigurationParser();

      String configStr = firstPart + "<cluster-connections>\n" +
         "   <cluster-connection-uri name=\"my-cluster\" address=\"multicast://my-discovery-group?messageLoadBalancingType=STRICT;retryInterval=333;connectorName=netty-connector;maxHops=1\"/>\n" +
         "</cluster-connections>\n" + lastPart;
      ByteArrayInputStream input = new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8));

      Configuration config = parser.parseMainConfig(input);

      Assert.assertEquals(1, config.getClusterConfigurations().size());

      Assert.assertEquals("my-discovery-group", config.getClusterConfigurations().get(0).getDiscoveryGroupName());
      Assert.assertEquals(333, config.getClusterConfigurations().get(0).getRetryInterval());
   }

   @Test
   public void testWildcardConfiguration() throws Exception {
      FileConfigurationParser parser = new FileConfigurationParser();

      String configStr = firstPart + "<wildcard-addresses>\n<routing-enabled>true</routing-enabled>\n<delimiter>/</delimiter>\n<any-words>></any-words></wildcard-addresses>" + lastPart;
      ByteArrayInputStream input = new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8));

      Configuration config = parser.parseMainConfig(input);
      WildcardConfiguration wildCard = config.getWildcardConfiguration();
      assertEquals('/', wildCard.getDelimiter());
      assertTrue(wildCard.isRoutingEnabled());
      assertEquals('>', wildCard.getAnyWords());
      assertEquals('*', wildCard.getSingleWord());
   }

   @Test
   public void testParsingHaSharedStoreWaitForActivation() throws Exception {
      FileConfigurationParser parser = new FileConfigurationParser();

      String configStr = firstPart + "<ha-policy><shared-store><master><wait-for-activation>false</wait-for-activation></master></shared-store></ha-policy>" + lastPart;
      ByteArrayInputStream input = new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8));

      Configuration config = parser.parseMainConfig(input);
      HAPolicyConfiguration haConfig = config.getHAPolicyConfiguration();

      assertTrue(haConfig instanceof SharedStoreMasterPolicyConfiguration);

      SharedStoreMasterPolicyConfiguration masterConfig = (SharedStoreMasterPolicyConfiguration) haConfig;

      assertFalse(masterConfig.isWaitForActivation());
   }

   @Test
   public void testParsingDefaultServerConfig() throws Exception {
      FileConfigurationParser parser = new FileConfigurationParser();

      String configStr = firstPart + lastPart;
      ByteArrayInputStream input = new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8));

      Configuration config = parser.parseMainConfig(input);

      String clusterPassword = config.getClusterPassword();

      assertEquals(ActiveMQDefaultConfiguration.getDefaultClusterPassword(), clusterPassword);

      //if we add cluster-password, it should be default plain text
      String clusterPasswordPart = "<cluster-password>helloworld</cluster-password>";

      configStr = firstPart + clusterPasswordPart + lastPart;

      config = parser.parseMainConfig(new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8)));

      assertEquals("helloworld", config.getClusterPassword());

      //if we add mask, it should be able to decode correctly
      DefaultSensitiveStringCodec codec = new DefaultSensitiveStringCodec();
      String mask = codec.encode("helloworld");

      String maskPasswordPart = "<mask-password>true</mask-password>";
      clusterPasswordPart = "<cluster-password>" + mask + "</cluster-password>";

      configStr = firstPart + clusterPasswordPart + maskPasswordPart + lastPart;

      config = parser.parseMainConfig(new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8)));

      assertEquals("helloworld", config.getClusterPassword());

      //if we change key, it should be able to decode correctly
      codec = new DefaultSensitiveStringCodec();
      Map<String, String> prop = new HashMap<>();
      prop.put("key", "newkey");
      codec.init(prop);

      mask = codec.encode("newpassword");

      clusterPasswordPart = "<cluster-password>" + mask + "</cluster-password>";

      String codecPart = "<password-codec>" + "org.apache.activemq.artemis.utils.DefaultSensitiveStringCodec" +
         ";key=newkey</password-codec>";

      configStr = firstPart + clusterPasswordPart + maskPasswordPart + codecPart + lastPart;

      config = parser.parseMainConfig(new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8)));

      assertEquals("newpassword", config.getClusterPassword());
   }

   @Test
   public void testParsingDefaultServerConfigWithENCMaskedPwd() throws Exception {
      FileConfigurationParser parser = new FileConfigurationParser();

      String configStr = firstPart + lastPart;
      ByteArrayInputStream input = new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8));

      Configuration config = parser.parseMainConfig(input);

      String clusterPassword = config.getClusterPassword();

      assertEquals(ActiveMQDefaultConfiguration.getDefaultClusterPassword(), clusterPassword);

      //if we add cluster-password, it should be default plain text
      String clusterPasswordPart = "<cluster-password>ENC(5aec0780b12bf225a13ab70c6c76bc8e)</cluster-password>";

      configStr = firstPart + clusterPasswordPart + lastPart;

      config = parser.parseMainConfig(new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8)));

      assertEquals("helloworld", config.getClusterPassword());

      //if we add mask, it should be able to decode correctly
      DefaultSensitiveStringCodec codec = new DefaultSensitiveStringCodec();
      String mask = codec.encode("helloworld");

      clusterPasswordPart = "<cluster-password>" + PasswordMaskingUtil.wrap(mask) + "</cluster-password>";

      configStr = firstPart + clusterPasswordPart + lastPart;

      config = parser.parseMainConfig(new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8)));

      assertEquals("helloworld", config.getClusterPassword());

      //if we change key, it should be able to decode correctly
      codec = new DefaultSensitiveStringCodec();
      Map<String, String> prop = new HashMap<>();
      prop.put("key", "newkey");
      codec.init(prop);

      mask = codec.encode("newpassword");

      clusterPasswordPart = "<cluster-password>" + PasswordMaskingUtil.wrap(mask) + "</cluster-password>";

      String codecPart = "<password-codec>" + "org.apache.activemq.artemis.utils.DefaultSensitiveStringCodec" +
              ";key=newkey</password-codec>";

      configStr = firstPart + clusterPasswordPart + codecPart + lastPart;

      config = parser.parseMainConfig(new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8)));

      assertEquals("newpassword", config.getClusterPassword());

      configStr = firstPart + bridgePart + lastPart;
      config = parser.parseMainConfig(new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8)));

      List<BridgeConfiguration> bridgeConfigs = config.getBridgeConfigurations();
      assertEquals(1, bridgeConfigs.size());

      BridgeConfiguration bconfig = bridgeConfigs.get(0);

      assertEquals("helloworld", bconfig.getPassword());
   }

   @Test
   public void testDefaultBridgeProducerWindowSize() throws Exception {
      FileConfigurationParser parser = new FileConfigurationParser();

      String configStr = firstPart +
         "<bridges>\n" +
         "   <bridge name=\"my-bridge\">\n" +
         "      <queue-name>sausage-factory</queue-name>\n" +
         "      <forwarding-address>mincing-machine</forwarding-address>\n" +
         "      <static-connectors>\n" +
         "         <connector-ref>remote-connector</connector-ref>\n" +
         "      </static-connectors>\n" +
         "   </bridge>\n" +
         "</bridges>\n"
         + lastPart;
      ByteArrayInputStream input = new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8));

      Configuration config = parser.parseMainConfig(input);

      List<BridgeConfiguration> bridgeConfigs = config.getBridgeConfigurations();
      assertEquals(1, bridgeConfigs.size());

      BridgeConfiguration bconfig = bridgeConfigs.get(0);

      assertEquals(ActiveMQDefaultConfiguration.getDefaultBridgeProducerWindowSize(), bconfig.getProducerWindowSize());
   }

   @Test
   public void testParsingOverflowPageSize() throws Exception {
      testParsingOverFlow("<address-settings>" + "\n" + "<address-setting match=\"#\">" + "\n" + "<page-size-bytes>2147483648</page-size-bytes>\n" + "</address-setting>" + "\n" + "</address-settings>" + "\n");
      testParsingOverFlow("<journal-file-size>2147483648</journal-file-size>");
      testParsingOverFlow("<journal-buffer-size>2147483648</journal-buffer-size>");

      testParsingOverFlow("<cluster-connections> \n" + "  <cluster-connection name=\"my-cluster\"> \n" + "    <connector-ref>netty</connector-ref>  \n" + "    <min-large-message-size>2147483648</min-large-message-size>\n" + "    <discovery-group-ref discovery-group-name=\"my-discovery-group\"/> \n" + "  </cluster-connection> \n" + "</cluster-connections>");
      testParsingOverFlow("<cluster-connections> \n" + "  <cluster-connection name=\"my-cluster\"> \n" + "    <connector-ref>netty</connector-ref>  \n" + "    <confirmation-window-size>2147483648</confirmation-window-size>\n" + "    <discovery-group-ref discovery-group-name=\"my-discovery-group\"/> \n" + "  </cluster-connection> \n" + "</cluster-connections>");
      testParsingOverFlow("<cluster-connections> \n" + "  <cluster-connection name=\"my-cluster\"> \n" + "    <connector-ref>netty</connector-ref>  \n" + "    <producer-window-size>2147483648</producer-window-size>\n" + "    <discovery-group-ref discovery-group-name=\"my-discovery-group\"/> \n" + "  </cluster-connection> \n" + "</cluster-connections>");

      testParsingOverFlow("<bridges> \n" + "  <bridge name=\"price-forward-bridge\"> \n" + "    <queue-name>priceForwarding</queue-name>  \n" + "    <forwarding-address>newYorkPriceUpdates</forwarding-address>\n" + "    <min-large-message-size>2147483648</min-large-message-size>\n" + "    <static-connectors> \n" + "      <connector-ref>netty</connector-ref> \n" + "    </static-connectors> \n" + "  </bridge> \n" + "</bridges>");
      testParsingOverFlow("<bridges> \n" + "  <bridge name=\"price-forward-bridge\"> \n" + "    <queue-name>priceForwarding</queue-name>  \n" + "    <forwarding-address>newYorkPriceUpdates</forwarding-address>\n" + "    <confirmation-window-size>2147483648</confirmation-window-size>\n" + "    <static-connectors> \n" + "      <connector-ref>netty</connector-ref> \n" + "    </static-connectors> \n" + "  </bridge> \n" + "</bridges>\n");
      testParsingOverFlow("<bridges> \n" + "  <bridge name=\"price-forward-bridge\"> \n" + "    <queue-name>priceForwarding</queue-name>  \n" + "    <forwarding-address>newYorkPriceUpdates</forwarding-address>\n" + "    <producer-window-size>2147483648</producer-window-size>\n" + "    <static-connectors> \n" + "      <connector-ref>netty</connector-ref> \n" + "    </static-connectors> \n" + "  </bridge> \n" + "</bridges>\n");
   }

   @Test
   public void testParsingScaleDownConfig() throws Exception {
      FileConfigurationParser parser = new FileConfigurationParser();

      String configStr = firstPart + "<ha-policy>\n" +
               "   <live-only>\n" +
               "      <scale-down>\n" +
               "         <connectors>\n" +
               "            <connector-ref>server0-connector</connector-ref>\n" +
               "         </connectors>\n" +
               "      </scale-down>\n" +
               "   </live-only>\n" +
               "</ha-policy>\n" + lastPart;
      ByteArrayInputStream input = new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8));

      Configuration config = parser.parseMainConfig(input);

      HAPolicyConfiguration haConfig = config.getHAPolicyConfiguration();
      assertTrue(haConfig instanceof LiveOnlyPolicyConfiguration);

      LiveOnlyPolicyConfiguration liveOnlyCfg = (LiveOnlyPolicyConfiguration) haConfig;
      ScaleDownConfiguration scaledownCfg = liveOnlyCfg.getScaleDownConfiguration();
      List<String> connectors = scaledownCfg.getConnectors();
      assertEquals(1, connectors.size());
      String connector = connectors.get(0);
      assertEquals("server0-connector", connector);
   }


   private void testParsingOverFlow(String config) throws Exception {
      FileConfigurationParser parser = new FileConfigurationParser();
      String firstPartWithoutAddressSettings = firstPart.substring(0, firstPart.indexOf("<address-settings"));

      String configStr = firstPartWithoutAddressSettings + config + lastPart;
      ByteArrayInputStream input = new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8));

      try {
         Configuration configuration = parser.parseMainConfig(input);
         fail("parsing should have failed bcs of overflow page size");
      } catch (java.lang.IllegalArgumentException e) {
      }
   }

   private static String bridgePart = "<bridges>\n" +
           "            <bridge name=\"my-bridge\">\n" +
           "               <queue-name>sausage-factory</queue-name>\n" +
           "               <forwarding-address>mincing-machine</forwarding-address>\n" +
           "               <filter string=\"name='aardvark'\"/>\n" +
           "               <transformer-class-name>org.apache.activemq.artemis.jms.example.HatColourChangeTransformer</transformer-class-name>\n" +
           "               <reconnect-attempts>-1</reconnect-attempts>\n" +
           "               <user>bridge-user</user>" +
           "               <password>ENC(5aec0780b12bf225a13ab70c6c76bc8e)</password>" +
           "               <static-connectors>\n" +
           "                  <connector-ref>remote-connector</connector-ref>\n" +
           "               </static-connectors>\n" +
           "            </bridge>\n" +
           "</bridges>\n";

   @Test
   public void testParsingAddressSettings() throws Exception {
      long expected = 2147483648L;
      String firstPartWithoutAS = firstPart.substring(0, firstPart.indexOf("<address-settings"));
      String configStr = firstPartWithoutAS + ("<address-settings>\n"
                                               + "<address-setting match=\"#\">\n"
                                               + String.format("<max-size-bytes-reject-threshold>%d</max-size-bytes-reject-threshold>\n",
                                                               expected)
                                               + "</address-setting>\n"
                                               + "</address-settings>"
                                               + "\n") + lastPart;
      ByteArrayInputStream input = new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8));
      Configuration configuration = new FileConfigurationParser().parseMainConfig(input);
      assertEquals(1, configuration.getAddressesSettings().size());
      AddressSettings addressSettings = configuration.getAddressesSettings().get("#");
      assertEquals(expected, addressSettings.getMaxSizeBytesRejectThreshold());
   }

   @Test
   public void testParsingPageSyncTimeout() throws Exception {
      int expected = 1000;
      FileConfigurationParser parser = new FileConfigurationParser();

      String configStr = firstPart + String.format("<page-sync-timeout>%d</page-sync-timeout>\n", expected) + lastPart;
      ByteArrayInputStream input = new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8));

      Configuration config = parser.parseMainConfig(input);
      assertEquals(expected, config.getPageSyncTimeout());
   }


   @Test
   public void testMinimalXML() throws Exception {
      StringPrintStream stringPrintStream = new StringPrintStream();
      PrintStream stream = stringPrintStream.newStream();

      stream.println("<configuration><core>");
      stream.println("</core></configuration>");

      ByteArrayInputStream inputStream = new ByteArrayInputStream(stringPrintStream.getBytes());
      FileConfigurationParser parser = new FileConfigurationParser();
      Configuration configuration = parser.parseMainConfig(inputStream);
   }

   @Test
   public void testRetentionJournalOptionsDays() throws Exception {
      testStreamDatesOption("DAYS", TimeUnit.DAYS);
   }

   @Test
   public void testRetentionJournalOptionsHours() throws Exception {
      testStreamDatesOption("HOURS", TimeUnit.HOURS);
   }

   @Test
   public void testRetentionJournalOptionsMinutes() throws Exception {
      testStreamDatesOption("MINUTES", TimeUnit.MINUTES);
   }

   @Test
   public void testRetentionJournalOptionsSeconds() throws Exception {
      testStreamDatesOption("SECONDS", TimeUnit.SECONDS);
   }

   private void testStreamDatesOption(String option, TimeUnit expected) throws Exception {
      StringPrintStream stringPrintStream = new StringPrintStream();
      PrintStream stream = stringPrintStream.newStream();

      stream.println("<configuration><core>");
      stream.println("<journal-retention-directory unit=\"" + option + "\" period=\"365\" storage-limit=\"10G\">history</journal-retention-directory>");
      stream.println("</core></configuration>");

      ByteArrayInputStream inputStream = new ByteArrayInputStream(stringPrintStream.getBytes());
      FileConfigurationParser parser = new FileConfigurationParser();
      Configuration configuration = parser.parseMainConfig(inputStream);

      Assert.assertEquals("history", configuration.getJournalRetentionDirectory());

      Assert.assertEquals(expected.toMillis(365), configuration.getJournalRetentionPeriod());
   }


   @Test
   public void unlimitedJustHistory() throws Throwable {
      StringPrintStream stringPrintStream = new StringPrintStream();
      PrintStream stream = stringPrintStream.newStream();

      stream.println("<configuration><core>");
      stream.println("<journal-retention-directory>directory</journal-retention-directory>");
      stream.println("</core></configuration>");

      ByteArrayInputStream inputStream = new ByteArrayInputStream(stringPrintStream.getBytes());
      FileConfigurationParser parser = new FileConfigurationParser();
      Configuration configuration = null;
      boolean exceptionHappened = false;
      try {
         configuration = parser.parseMainConfig(inputStream);
      } catch (Exception e) {
         exceptionHappened = true;
      }

      Assert.assertTrue(exceptionHappened);
   }



   @Test
   public void noRetention() throws Throwable {
      StringPrintStream stringPrintStream = new StringPrintStream();
      PrintStream stream = stringPrintStream.newStream();

      stream.println("<configuration><core>");
      stream.println("<journal-directory>journal</journal-directory>");
      stream.println("</core></configuration>");

      ByteArrayInputStream inputStream = new ByteArrayInputStream(stringPrintStream.getBytes());
      FileConfigurationParser parser = new FileConfigurationParser();
      Configuration configuration = null;
      configuration = parser.parseMainConfig(inputStream);
      Assert.assertNull(configuration.getJournalRetentionLocation());
      Assert.assertNull(configuration.getJournalRetentionDirectory());
      Assert.assertEquals("journal", configuration.getJournalDirectory());
   }


   @Test
   public void noFolderOnRetention() throws Throwable {
      StringPrintStream stringPrintStream = new StringPrintStream();
      PrintStream stream = stringPrintStream.newStream();

      stream.println("<configuration><core>");
      stream.println("<journal-retention-directory period=\"3\"></journal-retention-directory>");
      stream.println("</core></configuration>");
      FileConfigurationParser parser = new FileConfigurationParser();
      ByteArrayInputStream inputStream = new ByteArrayInputStream(stringPrintStream.getBytes());
      boolean exception = false;
      try {
         Configuration configuration = parser.parseMainConfig(inputStream);
      } catch (Exception e) {
         exception = true;
      }

      Assert.assertTrue(exception);



   }

   @Test
   public void testParseFederationDownstreamAuthorization() throws Exception {
      String middlePart = "<federations downstream-authorization=\"a, b,c\"/>";
      String configStr = firstPart + middlePart + lastPart;

      final FileConfigurationParser parser = new FileConfigurationParser();
      final ByteArrayInputStream input = new ByteArrayInputStream(configStr.getBytes(StandardCharsets.UTF_8));

      final Configuration configuration = parser.parseMainConfig(input);
      assertTrue(configuration.getFederationDownstreamAuthorization().contains("a"));
      assertTrue(configuration.getFederationDownstreamAuthorization().contains("b"));
      assertTrue(configuration.getFederationDownstreamAuthorization().contains("c"));
   }

   private static String firstPart = "<core xmlns=\"urn:activemq:core\">" + "\n" +
      "<name>ActiveMQ.main.config</name>" + "\n" +
      "<log-delegate-factory-class-name>org.apache.activemq.artemis.integration.logging.Log4jLogDelegateFactory</log-delegate-factory-class-name>" + "\n" +
      "<bindings-directory>${jboss.server.data.dir}/activemq/bindings</bindings-directory>" + "\n" +
      "<journal-directory>${jboss.server.data.dir}/activemq/journal</journal-directory>" + "\n" +
      "<journal-min-files>10</journal-min-files>" + "\n" +
      "<large-messages-directory>${jboss.server.data.dir}/activemq/largemessages</large-messages-directory>" + "\n" +
      "<paging-directory>${jboss.server.data.dir}/activemq/paging</paging-directory>" + "\n" +
      "<connectors>" + "\n" +
      "<connector name=\"netty\">tcp://localhost:61616</connector>" + "\n" +
      "<connector name=\"netty-throughput\">tcp://localhost:5545</connector>" + "\n" +
      "<connector name=\"in-vm\">vm://0</connector>" + "\n" +
      "</connectors>" + "\n" +
      "<acceptors>" + "\n" +
      "<acceptor name=\"netty\">tcp://localhost:5545</acceptor>" + "\n" +
      "<acceptor name=\"netty-throughput\">tcp://localhost:5545</acceptor>" + "\n" +
      "<acceptor name=\"in-vm\">vm://0</acceptor>" + "\n" +
      "</acceptors>" + "\n" +
      "<security-settings>" + "\n" +
      "<security-setting match=\"#\">" + "\n" +
      "<permission type=\"createNonDurableQueue\" roles=\"guest\"/>" + "\n" +
      "<permission type=\"deleteNonDurableQueue\" roles=\"guest\"/>" + "\n" +
      "<permission type=\"createDurableQueue\" roles=\"guest\"/>" + "\n" +
      "<permission type=\"deleteDurableQueue\" roles=\"guest\"/>" + "\n" +
      "<permission type=\"consume\" roles=\"guest\"/>" + "\n" +
      "<permission type=\"send\" roles=\"guest\"/>" + "\n" +
      "</security-setting>" + "\n" +
      "</security-settings>" + "\n" +
      "<address-settings>" + "\n" +
      "<address-setting match=\"#\">" + "\n" + "<dead-letter-address>DLQ\n</dead-letter-address>" + "\n" + "<expiry-address>ExpiryQueue\n</expiry-address>" + "\n" + "<redelivery-delay>0\n</redelivery-delay>" + "\n" + "<max-size-bytes>10485760\n</max-size-bytes>" + "\n" + "<message-counter-history-day-limit>10</message-counter-history-day-limit>" + "\n" + "<address-full-policy>BLOCK</address-full-policy>" + "\n" +
      "</address-setting>" + "\n" +
      "</address-settings>" + "\n";

   private static String lastPart = "</core>";
}
