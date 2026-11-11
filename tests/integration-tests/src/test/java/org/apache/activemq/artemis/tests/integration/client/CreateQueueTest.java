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

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(ParameterizedTestExtension.class)
public class CreateQueueTest extends ActiveMQTestBase {

   private boolean legacyCreateQueue;

   public final SimpleString addressA = SimpleString.of("addressA");
   public final SimpleString addressB = SimpleString.of("addressB");
   public final SimpleString queueA = SimpleString.of("queueA");
   public final SimpleString queueB = SimpleString.of("queueB");
   public final SimpleString queueC = SimpleString.of("queueC");
   public final SimpleString queueD = SimpleString.of("queueD");

   private ServerLocator locator;
   private ActiveMQServer server;
   private ClientSessionFactory cf;

   @Parameters(name = "legacyCreateQueue={0}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{true}, {false}});
   }

   public CreateQueueTest(boolean legacyCreateQueue) {
      this.legacyCreateQueue = legacyCreateQueue;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      locator = createInVMNonHALocator();
      server = createServer(false);

      server.start();
      cf = createSessionFactory(locator);
   }

   @TestTemplate
   public void testUnsupportedRoutingType() throws Exception {
      ClientSession sendSession = cf.createSession(false, true, true);
      server.getAddressSettingsRepository().addMatch(addressA.toString(), new AddressSettings().setAutoCreateAddresses(false));
      server.getAddressSettingsRepository().addMatch(addressB.toString(), new AddressSettings().setAutoCreateAddresses(false));

      EnumSet<RoutingType> routingTypes = EnumSet.of(RoutingType.ANYCAST);
      sendSession.createAddress(addressA, routingTypes, false);
      try {
         if (legacyCreateQueue) {
            sendSession.createQueue(addressA, RoutingType.MULTICAST, queueA);
         } else {
            sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA));
         }
         fail("Creating a queue here should fail since the queue routing type differs from what is supported on the address.");
      } catch (Exception e) {
         assertInstanceOf(ActiveMQException.class, e);
         ActiveMQException ae = (ActiveMQException) e;
         assertEquals(ActiveMQExceptionType.INTERNAL_ERROR, ae.getType());
      }

      routingTypes = EnumSet.of(RoutingType.MULTICAST);
      sendSession.createAddress(addressB, routingTypes, false);
      try {
         if (legacyCreateQueue) {
            sendSession.createQueue(addressB, RoutingType.ANYCAST, queueB);
         } else {
            sendSession.createQueue(QueueConfiguration.of(queueB).setAddress(addressB).setRoutingType(RoutingType.ANYCAST));
         }
         fail("Creating a queue here should fail since the queue routing type differs from what is supported on the address.");
      } catch (Exception e) {
         assertInstanceOf(ActiveMQException.class, e);
         ActiveMQException ae = (ActiveMQException) e;
         assertEquals(ActiveMQExceptionType.INTERNAL_ERROR, ae.getType());
      }
      sendSession.close();
   }

   @TestTemplate
   public void testAddressDoesNotExist() throws Exception {
      ClientSession sendSession = cf.createSession(false, true, true);
      server.getAddressSettingsRepository().addMatch(addressA.toString(), new AddressSettings().setAutoCreateAddresses(false));
      Set<RoutingType> routingTypes = new HashSet<>();
      routingTypes.add(RoutingType.ANYCAST);
      try {
         if (legacyCreateQueue) {
            sendSession.createQueue(addressA, RoutingType.MULTICAST, queueA);
         } else {
            sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA));
         }
         fail("Creating a queue here should fail since the queue's address doesn't exist and auto-create-addresses = false.");
      } catch (Exception e) {
         assertInstanceOf(ActiveMQException.class, e);
         ActiveMQException ae = (ActiveMQException) e;
         assertEquals(ActiveMQExceptionType.ADDRESS_DOES_NOT_EXIST, ae.getType());
      }
      sendSession.close();
   }
}
