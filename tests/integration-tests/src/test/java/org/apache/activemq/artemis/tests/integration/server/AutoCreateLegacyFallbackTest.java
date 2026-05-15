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
package org.apache.activemq.artemis.tests.integration.server;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;

import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.OLD_QUEUE_PREFIX;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.OLD_TOPIC_PREFIX;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(ParameterizedTestExtension.class)
public class AutoCreateLegacyFallbackTest extends ActiveMQTestBase {

   private static final String DESTINATION_NAME = "a.b";

   private static final String SPECIFIC_WILDCARD = "a.#";
   private static final String GENERIC_WILDCARD = "#";

   private static final int COMPAT_ACCEPTOR_PORT = TransportConstants.DEFAULT_PORT + 1;

   @Parameters(name = "destinationType={0}")
   public static Collection<DestinationType> getParameters() {
      return Arrays.asList(DestinationType.values());
   }

   private final DestinationType destinationType;

   private ActiveMQServer server;

   public AutoCreateLegacyFallbackTest(DestinationType destinationType) {
      this.destinationType = destinationType;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(false);
   }

   @TestTemplate
   public void testSpecificMatchFalseGenericMatchTrue() throws Exception {
      configureAutoCreate(false, true);
      server.start();
      sendMessage(true);
      assertFalse(destinationType.exists(server, destinationType.prefixed(DESTINATION_NAME)));
      assertFalse(destinationType.exists(server, DESTINATION_NAME));
   }

   @TestTemplate
   public void testSpecificMatchTrueGenericMatchTrue() throws Exception {
      configureAutoCreate(true, true);
      server.start();
      sendMessage(false);
      assertTrue(destinationType.exists(server, DESTINATION_NAME));
      assertFalse(destinationType.exists(server, destinationType.prefixed(DESTINATION_NAME)));
   }

   @TestTemplate
   public void testSpecificMatchTrueGenericMatchFalse() throws Exception {
      configureAutoCreate(true, false);
      server.start();
      sendMessage(false);
      assertTrue(destinationType.exists(server, DESTINATION_NAME));
      assertFalse(destinationType.exists(server, destinationType.prefixed(DESTINATION_NAME)));
   }

   @TestTemplate
   public void testSpecificMatchFalseGenericMatchFalse() throws Exception {
      configureAutoCreate(false, false);
      server.start();
      sendMessage(true);
      assertFalse(destinationType.exists(server, DESTINATION_NAME));
      assertFalse(destinationType.exists(server, destinationType.prefixed(DESTINATION_NAME)));
   }

   @TestTemplate
   public void testSpecificMatchFalseGenericMatchTrueWithAcceptorPrefix() throws Exception {
      configureAutoCreate(false, true);
      server.getConfiguration().addAcceptorConfiguration("compat", compatAcceptorUrl());
      server.start();
      sendMessage("tcp://localhost:" + COMPAT_ACCEPTOR_PORT, DESTINATION_NAME, true);
      assertFalse(destinationType.exists(server, DESTINATION_NAME));
      assertFalse(destinationType.exists(server, destinationType.prefixed(DESTINATION_NAME)));
   }

   @TestTemplate
   public void testSpecificMatchFalseGenericMatchTrueWithAcceptorPrefixAndEnable1xPrefixes() throws Exception {
      configureAutoCreate(false, true);
      server.getConfiguration().addAcceptorConfiguration("compat", compatAcceptorUrl());
      server.start();
      sendMessage("tcp://localhost:" + COMPAT_ACCEPTOR_PORT + "?enable1xPrefixes=true", DESTINATION_NAME, true);
      assertFalse(destinationType.exists(server, DESTINATION_NAME));
      assertFalse(destinationType.exists(server, destinationType.prefixed(DESTINATION_NAME)));
   }

   @TestTemplate
   public void testSpecificMatchTrueGenericMatchTrueWithAcceptorPrefixAndEnable1xPrefixes() throws Exception {
      configureAutoCreate(true, true);
      server.getConfiguration().addAcceptorConfiguration("compat", compatAcceptorUrl());
      server.start();
      sendMessage("tcp://localhost:" + COMPAT_ACCEPTOR_PORT + "?enable1xPrefixes=true", DESTINATION_NAME, false);
      assertTrue(destinationType.exists(server, DESTINATION_NAME));
      assertFalse(destinationType.exists(server, destinationType.prefixed(DESTINATION_NAME)));
   }

   @TestTemplate
   public void testSpecificMatchFalseGenericMatchFalseWithAcceptorPrefixAndEnable1xPrefixes() throws Exception {
      configureAutoCreate(false, false);
      server.getConfiguration().addAcceptorConfiguration("compat", compatAcceptorUrl());
      server.start();
      sendMessage("tcp://localhost:" + COMPAT_ACCEPTOR_PORT + "?enable1xPrefixes=true", DESTINATION_NAME, true);
      assertFalse(destinationType.exists(server, DESTINATION_NAME));
      assertFalse(destinationType.exists(server, destinationType.prefixed(DESTINATION_NAME)));
   }

   @TestTemplate
   public void testSpecificMatchFalseGenericMatchFalseWithPrecreatedPrefixedDestination() throws Exception {
      configureAutoCreate(false, false);
      server.start();
      destinationType.precreate(server, destinationType.prefixed(DESTINATION_NAME));
      sendMessage(false);
      assertTrue(destinationType.exists(server, destinationType.prefixed(DESTINATION_NAME)));
      assertFalse(destinationType.exists(server, DESTINATION_NAME));
   }

   @TestTemplate
   public void testSpecificMatchFalseGenericMatchFalseWithPrecreatedPrefixedDestinationWithEnable1xPrefixes() throws Exception {
      configureAutoCreate(destinationType.prefixed(SPECIFIC_WILDCARD), false, false);
      server.getConfiguration().addAcceptorConfiguration("legacy", "tcp://localhost:" + COMPAT_ACCEPTOR_PORT);
      server.start();
      destinationType.precreate(server, destinationType.prefixed(DESTINATION_NAME));
      sendMessage("tcp://localhost:" + COMPAT_ACCEPTOR_PORT + "?enable1xPrefixes=true", DESTINATION_NAME, false);
      assertTrue(destinationType.exists(server, destinationType.prefixed(DESTINATION_NAME)));
      assertFalse(destinationType.exists(server, DESTINATION_NAME));
   }

   private String compatAcceptorUrl() {
      return "tcp://localhost:" + COMPAT_ACCEPTOR_PORT + "?" + destinationType.acceptorPrefixParam + "=" + destinationType.prefix;
   }

   private void configureAutoCreate(boolean specificAutoCreate, boolean genericAutoCreate) {
      configureAutoCreate(SPECIFIC_WILDCARD, specificAutoCreate, genericAutoCreate);
   }

   private void configureAutoCreate(String specificMatch, boolean specificAutoCreate, boolean genericAutoCreate) {
      server.getAddressSettingsRepository().addMatch(specificMatch, new AddressSettings()
         .setAutoCreateQueues(specificAutoCreate)
         .setAutoCreateAddresses(specificAutoCreate));
      server.getAddressSettingsRepository().addMatch(GENERIC_WILDCARD, new AddressSettings()
         .setAutoCreateQueues(genericAutoCreate)
         .setAutoCreateAddresses(genericAutoCreate));
   }

   private void sendMessage(boolean expectException) throws Exception {
      sendMessage("vm://0", DESTINATION_NAME, expectException);
   }

   private void sendMessage(String brokerUrl, String name, boolean expectException) throws Exception {
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory(brokerUrl);
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Destination destination = destinationType.create(session, name);
         MessageProducer producer = session.createProducer(destination);
         producer.send(session.createMessage());
         if (expectException) {
            fail("expected an exception");
         }
      } catch (JMSException e) {
         if (!expectException) {
            throw e;
         }
      }
   }

   public enum DestinationType {
      QUEUE(OLD_QUEUE_PREFIX.toString(), "anycastPrefix") {
         @Override
         Destination create(Session session, String name) throws JMSException {
            return session.createQueue(name);
         }

         @Override
         boolean exists(ActiveMQServer server, String address) {
            return server.locateQueue(SimpleString.of(address)) != null;
         }

         @Override
         void precreate(ActiveMQServer server, String prefixedAddress) throws Exception {
            server.addAddressInfo(new AddressInfo(SimpleString.of(prefixedAddress), RoutingType.ANYCAST));
            server.createQueue(QueueConfiguration.of(prefixedAddress).setRoutingType(RoutingType.ANYCAST));
         }
      },
      TOPIC(OLD_TOPIC_PREFIX.toString(), "multicastPrefix") {
         @Override
         Destination create(Session session, String name) throws JMSException {
            return session.createTopic(name);
         }

         @Override
         boolean exists(ActiveMQServer server, String address) {
            return server.getAddressInfo(SimpleString.of(address)) != null;
         }

         @Override
         void precreate(ActiveMQServer server, String prefixedAddress) throws Exception {
            server.addAddressInfo(new AddressInfo(SimpleString.of(prefixedAddress), RoutingType.MULTICAST));
         }
      };

      final String prefix;
      final String acceptorPrefixParam;

      DestinationType(String prefix, String acceptorPrefixParam) {
         this.prefix = prefix;
         this.acceptorPrefixParam = acceptorPrefixParam;
      }

      String prefixed(String name) {
         return prefix + name;
      }

      abstract Destination create(Session session, String name) throws JMSException;

      abstract boolean exists(ActiveMQServer server, String address);

      abstract void precreate(ActiveMQServer server, String prefixedAddress) throws Exception;
   }
}
