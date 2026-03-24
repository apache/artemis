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
package org.apache.activemq.artemis.tests.unit.jms.misc;

import javax.jms.ConnectionMetaData;
import java.io.File;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionMetaData;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Test;

public class ManifestTest extends ActiveMQTestBase {

   private static final Logger log = Logger.getLogger(ManifestTest.class);



   @Test
   public void testManifestEntries() throws Exception {
      Properties props = System.getProperties();
      String userDir = props.getProperty("build.lib");

      log.trace("userDir is " + userDir);

      // The jar must be there
      File file = new File("build/jars", "activemq-core.jar");
      Assert.assertTrue(file.exists());

      // Open the jar and load MANIFEST.MF
      JarFile jar = new JarFile(file);
      Manifest manifest = jar.getManifest();

      ActiveMQServer server = ActiveMQServers.newActiveMQServer(createBasicConfig());

      ConnectionMetaData meta = new ActiveMQConnectionMetaData(server.getVersion());

      // Compare the value from ConnectionMetaData and MANIFEST.MF
      Attributes attrs = manifest.getMainAttributes();

      Assert.assertEquals(meta.getProviderVersion(), attrs.getValue("ActiveMQ-Version"));
   }


}
