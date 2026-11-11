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
package org.apache.activemq.artemis.tests.integration.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class AddressInfoRestartTest extends ActiveMQTestBase {

   @Test
   public void testAddressInfoAutoCreatedAndRestart() throws Exception {
      ActiveMQServer server = createServer(true);

      server.start();

      SimpleString address = SimpleString.of("test.address");
      SimpleString queue = SimpleString.of("test.queue");

      server.createQueue(QueueConfiguration.of(queue).setAddress(address));

      AddressInfo addressInfo1 = server.getPostOffice().getAddressInfo(address);
      assertTrue(addressInfo1.isAutoCreated());

      server.stop();

      server.start();

      AddressInfo addressInfo2 = server.getPostOffice().getAddressInfo(address);
      assertTrue(addressInfo2.isAutoCreated());
   }
}
