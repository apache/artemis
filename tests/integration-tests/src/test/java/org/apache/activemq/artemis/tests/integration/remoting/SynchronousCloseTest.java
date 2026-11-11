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
package org.apache.activemq.artemis.tests.integration.remoting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SynchronousCloseTest extends ActiveMQTestBase {

   private ActiveMQServer server;





   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      Configuration config = createDefaultConfig(isNetty()).setSecurityEnabled(false);
      server = createServer(false, config);
      server.start();
   }

   protected boolean isNetty() {
      return false;
   }

   protected ClientSessionFactory createSessionFactory() throws Exception {
      ServerLocator locator;
      if (isNetty()) {
         locator = createNettyNonHALocator();
      } else {
         locator = createInVMNonHALocator();
      }

      return createSessionFactory(locator);
   }

   /*
    * Server side resources should be cleaned up befor call to close has returned or client could launch
    * DoS attack
    */
   @Test
   public void testSynchronousClose() throws Exception {
      assertEquals(0, server.getActiveMQServerControl().listRemoteAddresses().length);

      ClientSessionFactory sf = createSessionFactory();

      for (int i = 0; i < 2000; i++) {
         ClientSession session = sf.createSession(false, true, true);

         session.close();
      }

      sf.close();

      sf.getServerLocator().close();
   }
}
