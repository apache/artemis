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
package org.apache.activemq.artemis.core.server.embedded;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   /*
    * Tests what happens when no workdir arg is given and the default can't be accessed as not in container env, expect
    * to throw IOE.
    */
   @Test
   @Timeout(5)
   public void testNull() throws Exception {
      try {
         Main.main(new String[] {""});
         fail("Should have thrown an exception");
      } catch (IOException expected) {
         // Expected
         logger.info("Caught expected IOException: " + expected.getMessage());
      } finally {
         EmbeddedActiveMQ server = Main.getEmbeddedServer();
         if (server != null) {
            // Happens if the startup succeeds unexpectedly, but is
            // interrupted before return, e.g during a test timeout.
            // Clean up to avoid impacting later tests.
            try {
               server.stop();
            } catch (Throwable t) {
               // Log but suppress, allowing original issue be reported as failure.
               logger.warn("Caught issue while stopping the unexpectedly-present server", t);
            }
         }
      }
   }
}
