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
package org.apache.activemq.artemis.tests.integration.jms.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class JMSServerPropertyConfigTest extends ActiveMQTestBase {

   @Test
   public void testConfigViaBrokerPropertiesSystemProperty() throws Exception {
      EmbeddedActiveMQ server = new EmbeddedActiveMQ();
      ConfigurationImpl configuration = new ConfigurationImpl();

      configuration.setJournalDirectory(new File(getTestDir(), "./journal").getAbsolutePath()).
         setPagingDirectory(new File(getTestDir(), "./paging").getAbsolutePath()).
         setLargeMessagesDirectory(new File(getTestDir(), "./largemessages").getAbsolutePath()).
         setBindingsDirectory(new File(getTestDir(), "./bindings").getAbsolutePath()).setPersistenceEnabled(true);

      File bindingsDir = new File(configuration.getBindingsDirectory());
      bindingsDir.mkdirs();
      File propertiesInBindingsDir = new File(bindingsDir, ActiveMQDefaultConfiguration.BROKER_PROPERTIES_SYSTEM_PROPERTY_NAME);

      try (PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(propertiesInBindingsDir)))) {
         // use the same name property as from the classpath broker.properties to verify precedence of system prop
         out.println("gracefulShutdownTimeout=-3");
      }

      System.setProperty(ActiveMQDefaultConfiguration.BROKER_PROPERTIES_SYSTEM_PROPERTY_NAME, propertiesInBindingsDir.getAbsolutePath());
      try {

         server.setConfiguration(configuration);
         server.start();

         assertEquals(-3, server.getActiveMQServer().getConfiguration().getGracefulShutdownTimeout());

      } finally {
         System.getProperties().remove(ActiveMQDefaultConfiguration.BROKER_PROPERTIES_SYSTEM_PROPERTY_NAME);
         server.stop();
      }
   }

   @Test
   public void testConfigViaBrokerPropertiesFromClasspath() throws Exception {

      EmbeddedActiveMQ server = new EmbeddedActiveMQ();
      ConfigurationImpl configuration = new ConfigurationImpl();

      configuration.setJournalDirectory(new File(getTestDir(), "./journal").getAbsolutePath()).
         setPagingDirectory(new File(getTestDir(), "./paging").getAbsolutePath()).
         setLargeMessagesDirectory(new File(getTestDir(), "./largemessages").getAbsolutePath()).
         setBindingsDirectory(new File(getTestDir(), "./bindings").getAbsolutePath()).setPersistenceEnabled(true);

      try {

         server.setConfiguration(configuration);
         server.start();

         assertEquals(-2, server.getActiveMQServer().getConfiguration().getGracefulShutdownTimeout());

      } finally {
         server.stop();
      }
   }

   @Test
   public void testIgnoreConfigViaBrokerPropertiesFromClasspath() throws Exception {

      EmbeddedActiveMQ server = new EmbeddedActiveMQ();
      ConfigurationImpl configuration = new ConfigurationImpl();

      configuration.setJournalDirectory(new File(getTestDir(), "./journal").getAbsolutePath()).
         setPagingDirectory(new File(getTestDir(), "./paging").getAbsolutePath()).
         setLargeMessagesDirectory(new File(getTestDir(), "./largemessages").getAbsolutePath()).
         setBindingsDirectory(new File(getTestDir(), "./bindings").getAbsolutePath()).setPersistenceEnabled(true);

      try {

         server.setPropertiesResourcePath(null);
         server.setConfiguration(configuration);
         server.start();

         assertEquals(ActiveMQDefaultConfiguration.getDefaultGracefulShutdownTimeout(), server.getActiveMQServer().getConfiguration().getGracefulShutdownTimeout());

      } finally {
         server.stop();
      }
   }
}
