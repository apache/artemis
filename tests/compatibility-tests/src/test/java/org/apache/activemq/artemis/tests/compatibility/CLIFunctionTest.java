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
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_28_0;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.activemq.artemis.tests.compatibility.base.ClasspathBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class CLIFunctionTest extends ClasspathBase {

   private final ClassLoader serverClassloader;

   private final ClassLoader clientClassloader;

   @Parameters(name = "Server={0}, Client={1}")
   public static Collection getParameters() {
      List<Object[]> combinations = new ArrayList<>();
      combinations.add(new Object[]{ARTEMIS_2_28_0, SNAPSHOT});
      combinations.add(new Object[]{SNAPSHOT, ARTEMIS_2_28_0});
      // this is to validate the test
      combinations.add(new Object[]{SNAPSHOT, SNAPSHOT});
      return combinations;
   }


   public CLIFunctionTest(String server, String client) throws Exception {
      this.serverClassloader = getClasspath(server);

      this.clientClassloader = getClasspath(client);
   }


   @TestTemplate
   public void testQueueStat() throws Throwable {
      try {
         setVariable(serverClassloader, "persistent", Boolean.FALSE);
         startServer(serverFolder, serverClassloader, "server", null,
                     false, "servers/artemisServer.groovy",
                     "ARTEMIS", "ARTEMIS", "ARTEMIS");


         evaluate(clientClassloader, "CliFunctionTest/testQueueStat.groovy");

      } finally {
         try {
            stopServer(serverClassloader);
         } catch (Throwable ignored) {
         }
      }

   }


}
