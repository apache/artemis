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

package org.apache.activemq.artemis.tests.compatibility;

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.jms.client.ActiveMQQueue;
import org.apache.activemq.artemis.tests.compatibility.base.ClasspathBase;
import org.junit.jupiter.api.Test;

public class ActiveMQJMSClientCompatibilityTest extends ClasspathBase {

   @Test
   public void testActiveMQJMSCompatibility_1XPrefix_SNAPSHOT() throws Exception {

      assertFalse(ActiveMQJMSClient.DEFAULT_ENABLE_1X_PREFIXES);
      ActiveMQQueue queue = (ActiveMQQueue)ActiveMQJMSClient.createQueue("t1");
      // this step is to guarantee the class is not affected when there's no property in place
      assertEquals("t1", queue.getAddress());

      ClassLoader loader = getClasspath(SNAPSHOT, true);

      System.setProperty(ActiveMQJMSClient.class.getName() + ".enable1xPrefixes", "true");

      try {

         evaluate(loader, "ActiveMQJMSClientCompatibilityTest/validateClient.groovy");

      } finally {
         System.clearProperty(ActiveMQJMSClient.class.getName() + ".enable1xPrefixes");
      }

      clearClassLoader(loader);

   }

   @Test
   public void testActiveMQJMSCompatibility_1XPrefix_SNAPSHOT_with_properties() throws Exception {

      assertFalse(ActiveMQJMSClient.DEFAULT_ENABLE_1X_PREFIXES);
      ActiveMQQueue queue = (ActiveMQQueue)ActiveMQJMSClient.createQueue("t1");
      // this step is to guarantee the class is not affected when there's no property in place
      assertEquals("t1", queue.getAddress());

      File file = File.createTempFile(ActiveMQJMSClient.class.getName() + ".properties", null, serverFolder);

      FileOutputStream fileOutputStream = new FileOutputStream(file);
      PrintStream stream = new PrintStream(fileOutputStream);
      stream.println("enable1xPrefixes=true");
      stream.close();

      String snapshotPath = System.getProperty(SNAPSHOT);
      assumeTrue(snapshotPath != null);

      String path = serverFolder.getAbsolutePath() + File.pathSeparator + snapshotPath;


      ClassLoader loader = defineClassLoader(path);

      evaluate(loader, "ActiveMQJMSClientCompatibilityTest/validateClient.groovy");

   }

}
