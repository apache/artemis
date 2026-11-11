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
package org.apache.activemq.artemis.tests.unit.core.remoting.impl.invm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnection;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class InVMConnectionTest {

   @Test
   public void testIsTargetNode() throws Exception {

      int serverID = 0;
      InVMConnection conn = new InVMConnection(serverID, null, null, null);

      Map<String, Object> config0 = new HashMap<>();
      config0.put(TransportConstants.SERVER_ID_PROP_NAME, 0);
      TransportConfiguration tf0 = new TransportConfiguration(InVMConnectorFactory.class.getName(), config0, "tf0");

      Map<String, Object> config1 = new HashMap<>();
      config1.put(TransportConstants.SERVER_ID_PROP_NAME, 1);
      TransportConfiguration tf1 = new TransportConfiguration(InVMConnectorFactory.class.getName(), config1, "tf1");

      Map<String, Object> config2 = new HashMap<>();
      config2.put(TransportConstants.SERVER_ID_PROP_NAME, 2);
      TransportConfiguration tf2 = new TransportConfiguration(InVMConnectorFactory.class.getName(), config2, "tf2");

      assertTrue(conn.isSameTarget(tf0));
      assertFalse(conn.isSameTarget(tf1));
      assertFalse(conn.isSameTarget(tf2));
      assertTrue(conn.isSameTarget(tf0, tf1));
      assertTrue(conn.isSameTarget(tf2, tf0));
      assertFalse(conn.isSameTarget(tf2, tf1));
   }
}
