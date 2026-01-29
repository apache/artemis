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

package org.apache.activemq.artemis.core.remoting.impl.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class TransportConstantTest {

   /**
    * We change the default on the main pom.xml This is just validating the pom still works
    */
   @Test
   public void testDefaultOnPom() {
      assertEquals(0, TransportConstants.DEFAULT_QUIET_PERIOD, "It is expected to have the default at 0 on the testsuite");
      assertEquals(7200000, TransportConstants.DEFAULT_SHUTDOWN_TIMEOUT, "It is expected to have the default at 0 on the testsuite");
   }
}
