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

import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProducerCloseTest extends ActiveMQTestBase {

   private ActiveMQServer server;
   private ClientSessionFactory sf;
   private ClientSession session;
   private ServerLocator locator;

   @Test
   public void testCanNotUseAClosedProducer() throws Exception {
      final ClientProducer producer = session.createProducer(RandomUtil.randomUUIDSimpleString());

      assertFalse(producer.isClosed());

      producer.close();

      assertTrue(producer.isClosed());

      ActiveMQTestBase.expectActiveMQException(ActiveMQExceptionType.OBJECT_CLOSED, () -> producer.send(session.createMessage(false)));
   }



   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      Configuration config = createDefaultInVMConfig();
      server = createServer(false, config);
      server.start();
      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
      session = sf.createSession(false, true, true);
   }
}
