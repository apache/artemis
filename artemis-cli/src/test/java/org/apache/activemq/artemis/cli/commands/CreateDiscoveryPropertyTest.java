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
package org.apache.activemq.artemis.cli.commands;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.activemq.artemis.cli.Artemis;
import org.apache.activemq.artemis.api.config.ServerLocatorConfig;
import org.apache.activemq.cli.test.CliTestBase;
import org.junit.jupiter.api.Test;

public class CreateDiscoveryPropertyTest extends CliTestBase {

   private static final String DISCOVERY_JVM_ARG = "-D" + ServerLocatorConfig.DISCOVERY_ENABLED_PROPERTY + "=true";

   @Test
   public void testClusteredCreateFailsWhenDiscoveryDisabled() {
      File testInstance = new File(temporaryFolder, "clustered-discovery-disabled");

      assertThrows(IllegalArgumentException.class,
         () -> Artemis.internalExecute(clusteredCreateArgs(testInstance)));
   }

   @Test
   public void testClusteredCreateSucceedsWithDiscoveryEnabledFlag() throws Exception {
      File testInstance = new File(temporaryFolder, "clustered-discovery-enabled");

      Artemis.internalExecute(clusteredCreateArgsWithDiscoveryFlag(testInstance));

      assertTrue(new File(testInstance, "etc/broker.xml").exists());

      String brokerProfile = Files.readString(new File(testInstance, "etc/artemis.profile").toPath(), StandardCharsets.UTF_8);
      String utilityProfile = Files.readString(new File(testInstance, "etc/artemis-utility.profile").toPath(), StandardCharsets.UTF_8);

      assertTrue(brokerProfile.contains(DISCOVERY_JVM_ARG));
      assertTrue(utilityProfile.contains(DISCOVERY_JVM_ARG));
   }

   @Test
   public void testStaticClusterCreateSucceedsWhenDiscoveryDisabled() throws Exception {
      File testInstance = new File(temporaryFolder, "static-cluster");

      Artemis.internalExecute(staticClusterCreateArgs(testInstance));

      assertTrue(new File(testInstance, "etc/broker.xml").exists());
   }

   private static String[] clusteredCreateArgs(File instance) {
      return new String[] {
         "create", instance.getAbsolutePath(), "--silent", "--no-autotune", "--force",
         "--user", "admin", "--password", "admin",
         "--cluster-user", "admin", "--cluster-password", "admin", "--clustered"
      };
   }

   private static String[] clusteredCreateArgsWithDiscoveryFlag(File instance) {
      return new String[] {
         "create", instance.getAbsolutePath(), "--silent", "--no-autotune", "--force",
         "--user", "admin", "--password", "admin",
         "--cluster-user", "admin", "--cluster-password", "admin", "--clustered", "--discovery-enabled"
      };
   }

   private static String[] staticClusterCreateArgs(File instance) {
      return new String[] {
         "create", instance.getAbsolutePath(), "--silent", "--no-autotune", "--force",
         "--user", "admin", "--password", "admin",
         "--cluster-user", "admin", "--cluster-password", "admin",
         "--static-cluster", "tcp://localhost:61616,tcp://localhost:61617"
      };
   }
}
