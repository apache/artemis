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
package org.apache.activemq.artemis.tests.integration.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.tests.util.SingleServerTestBase;
import org.junit.jupiter.api.Test;

public class AutoCloseCoreTest extends SingleServerTestBase {

   @Test
   public void testAutClose() throws Exception {
      ServerLocator locatorx;
      ClientSession sessionx;
      ClientSessionFactory factoryx;
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory factory = locator.createSessionFactory();
           ClientSession session = factory.createSession(false, false)) {
         locatorx = locator;
         sessionx = session;
         factoryx = factory;
      }

      assertTrue(locatorx.isClosed());
      assertTrue(sessionx.isClosed());
      assertTrue(factoryx.isClosed());
   }
}
