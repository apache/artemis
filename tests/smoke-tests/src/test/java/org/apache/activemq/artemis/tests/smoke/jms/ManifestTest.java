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
package org.apache.activemq.artemis.tests.smoke.jms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.ConnectionMetaData;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.version.Version;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionMetaData;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.junit.jupiter.api.Test;

public class ManifestTest extends SmokeTestBase {

   private static List<String> jarFiles = new ArrayList<>(Arrays.asList(
           "artemis-jms-client-", "artemis-jms-server-"));

   @Test
   public void testManifestEntries() throws Exception {

      Properties props = System.getProperties();
      String distributionLibDir = props.getProperty("distribution.lib");

      ActiveMQServer server = ActiveMQServers.newActiveMQServer(createBasicConfig());
      Version serverVersion = server.getVersion();
      String serverFullVersion = serverVersion.getFullVersion();
      ConnectionMetaData meta = new ActiveMQConnectionMetaData(serverVersion);

      for (String jarFile : jarFiles) {
         // The jar must be there
         File file = new File(distributionLibDir, jarFile + serverFullVersion + ".jar");
         assertTrue(file.exists());

         // Open the jar and load MANIFEST.MF
         JarFile jar = new JarFile(file);
         Manifest manifest = jar.getManifest();

         // Compare the value from ConnectionMetaData and MANIFEST.MF
         Attributes attrs = manifest.getMainAttributes();
         assertEquals(meta.getProviderVersion(), attrs.getValue("Implementation-Version"));
      }
   }

}
