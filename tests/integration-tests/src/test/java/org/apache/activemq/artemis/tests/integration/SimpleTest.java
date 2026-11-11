/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.tests.integration;

import java.util.UUID;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A simple test-case used for documentation purposes.
 */
public class SimpleTest extends ActiveMQTestBase {

   protected ActiveMQServer server;

   protected ClientSession session;

   protected ClientSessionFactory sf;

   protected ServerLocator locator;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      // Invoke org.apache.activemq.artemis.tests.util.ActiveMQTestBase's setUp() to bootstrap everything properly.
      super.setUp();

      /*
       * Create a configuration for an in-vm server. Use that configuration to instantiate a new server that doesn't use
       * persistence, and then start it. Note that creating the server instance using this method ensures that the
       * server will be cleaned up properly when the test is torn down.
       */
      server = createServer(false, createDefaultInVMConfig());
      server.start();

      /*
       * Create a ServerLocator for the in-vm server. Using this method instead of using, e.g.
       * ActiveMQClient.createServerLocatorWithHA(..), ensures that the locator will be cleaned up properly when the
       * test is torn down.
       */
      locator = createInVMNonHALocator();

      /*
       * Create a session factory from the server locator. Using this method instead of using, e.g.
       * ServerLocator.createSessionFactory(), ensures that the factory will be cleaned up properly when the test is
       * torn down.
       */
      sf = createSessionFactory(locator);

      /*
       * Create a session from the factory. The call to create the session is surrounded with addClientSession to ensure
       * the session will be cleaned up properly when the test is torn down.
       */
      session = addClientSession(sf.createSession(false, true, true));
   }

   @Test
   public void simpleTest() throws Exception {
      final String data = "Simple Text " + UUID.randomUUID().toString();
      final String queueName = "simpleQueue";
      final String addressName = "simpleAddress";

      // Create a queue bound to a particular address where the test will send to & consume from.
      session.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST));

      // Create a producer to send a message to the previously created address.
      ClientProducer producer = session.createProducer(addressName);

      // Create a non-durable message.
      ClientMessage message = session.createMessage(false);

      // Put some data into the message.
      message.getBodyBuffer().writeString(data);

      // Send the message. This send will be auto-committed based on the way the session was created in setUp()
      producer.send(message);

      // Close the producer.
      producer.close();

      // Create a consumer on the queue bound to the address where the message was sent.
      ClientConsumer consumer = session.createConsumer(queueName);

      // Start the session to allow messages to be consumed.
      session.start();

      // Receive the message we sent previously.
      message = consumer.receive(1000);

      // Ensure the message was received.
      assertNotNull(message);

      // Acknowledge the message.
      message.acknowledge();

      // Ensure the data in the message received matches the data in the message sent.
      assertEquals(data, message.getBodyBuffer().readString());
   }
}
