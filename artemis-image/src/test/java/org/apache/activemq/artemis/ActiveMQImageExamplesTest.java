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

package org.apache.activemq.artemis;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.server.embedded.Main;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.core.server.embedded.Main.configureDataDirectory;

public class ActiveMQImageExamplesTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testSamlScram_etc() throws Exception {

      ConfigurationImpl configuration = new ConfigurationImpl();

      String dataDir = "./target/data";

      configureDataDirectory(configuration, dataDir);

      EmbeddedActiveMQ server = new EmbeddedActiveMQ();
      // look for properties files to augment configuration
      server.setPropertiesResourcePath("./src/main/jib/config/,./examples/amqp_sasl_scram_test__etc/");
      server.setConfiguration(configuration);

      server.start();

      server.stop();

   }

   @Test
   public void testBYOC_etc() throws Exception {

      final CountDownLatch done = new CountDownLatch(1);
      Thread thread = new Thread(() -> {
         try {
            // contents byoc__etc copied to ./target/ to satisfy etc/broker.xml
            Main.main(new String[] {"./target"});
            done.countDown();
         } catch (Exception e) {
            logger.info("unexpected", e);
         }
      });

      thread.start();

      // shut it down after it starts!
      do {
         if (Main.getEmbeddedServer() != null) {
            if (Main.getEmbeddedServer().getActiveMQServer() != null) {
               if (Main.getEmbeddedServer().getActiveMQServer().getState() == ActiveMQServer.SERVER_STATE.STARTED) {
                  logger.trace("stopping server, state={}", Main.getEmbeddedServer().getActiveMQServer().getState());
                  Main.getEmbeddedServer().stop();
               }
            }
         }
      }
      while (!done.await(200, TimeUnit.MILLISECONDS));
   }
}