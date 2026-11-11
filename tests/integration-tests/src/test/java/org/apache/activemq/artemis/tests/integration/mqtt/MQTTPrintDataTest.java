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
package org.apache.activemq.artemis.tests.integration.mqtt;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.cli.commands.tools.PrintData;
import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTStateManager;
import org.apache.activemq.artemis.core.protocol.mqtt.PacketIdCorrelationKey;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.server.config.impl.FileJMSConfiguration;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MQTTPrintDataTest extends ActiveMQTestBase {

   @Test
   @Timeout(30)
   public void testPrintDataWithMQTTQoS2Correlation() throws Exception {
      ActiveMQServer server = addServer(getActiveMQServer("dataprint/etc/broker.xml"));
      try {
         server.getConfiguration().setPersistenceEnabled(true);
         server.start();
         String clientID = RandomUtil.randomUUIDString();
         SimpleString queueName = RandomUtil.randomUUIDSimpleString();
         long messageID = 3000L;
         MQTTStateManager.getInstance(server).putPacketIdCorrelation(clientID, PacketIdCorrelationKey.of(messageID, queueName), 1);
         server.stop();

         String previousInstance = System.getProperty("artemis.instance");
         try {
            System.setProperty("artemis.instance", this.getClass().getClassLoader().getResource("dataprint").getFile());

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            PrintStream printStream = new PrintStream(byteArrayOutputStream, true, StandardCharsets.UTF_8.name());
            PrintData.printData(server.getConfiguration().getBindingsLocation().getAbsoluteFile(),
                                server.getConfiguration().getJournalLocation().getAbsoluteFile(),
                                server.getConfiguration().getPagingLocation().getAbsoluteFile(),
                                printStream, false, false, false, false, -1, true);

            String output = byteArrayOutputStream.toString();
            System.out.println(output);
            assertTrue(output.contains("PacketIdCorrelation"), "Print data output should contain the MQTT PacketIdCorrelation record.\nOutput:\n" + output);
            assertTrue(output.contains(queueName.toString()), "Print data output should contain the MQTT PacketIdCorrelation record.\nOutput:\n" + output);
            assertTrue(output.contains(clientID), "Print data output should contain the MQTT PacketIdCorrelation record.\nOutput:\n" + output);
         } finally {
            if (previousInstance != null) {
               System.setProperty("artemis.instance", previousInstance);
            } else {
               System.clearProperty("artemis.instance");
            }
         }
      } finally {
         try {
            server.stop();
         } catch (Exception e) {
         }
      }
   }

   protected ActiveMQServer getActiveMQServer(String brokerConfig) throws Exception {
      FileConfiguration fc = new FileConfiguration();
      FileJMSConfiguration fileConfiguration = new FileJMSConfiguration();
      FileDeploymentManager deploymentManager = new FileDeploymentManager(brokerConfig);
      deploymentManager.addDeployable(fc);
      deploymentManager.addDeployable(fileConfiguration);
      deploymentManager.readConfiguration();

      ActiveMQJAASSecurityManager sm = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), new SecurityConfiguration());

      recreateDirectory(fc.getBindingsDirectory());
      recreateDirectory(fc.getJournalDirectory());
      recreateDirectory(fc.getPagingDirectory());
      recreateDirectory(fc.getLargeMessagesDirectory());

      return addServer(new ActiveMQServerImpl(fc, sm));
   }
}