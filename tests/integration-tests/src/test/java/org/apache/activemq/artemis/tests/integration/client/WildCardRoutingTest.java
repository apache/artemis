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

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class WildCardRoutingTest extends ActiveMQTestBase {

   private ActiveMQServer server;
   private ServerLocator locator;
   private ClientSession clientSession;
   private ClientSessionFactory sf;

   @Test
   public void testBasicWildcardRouting() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("a.*");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testBasicWildcardRoutingQueuesDontExist() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("a.*");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();
      clientSession.deleteQueue(queueName);

      assertEquals(0, server.getPostOffice().getBindingsForAddress(addressAB).getBindings().size());
      assertEquals(0, server.getPostOffice().getBindingsForAddress(addressAC).getBindings().size());
      assertEquals(0, server.getPostOffice().getBindingsForAddress(address).getBindings().size());
   }

   @Test
   public void testBasicWildcardRoutingQueuesDontExist2() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("a.*");
      SimpleString queueName = SimpleString.of("Q");
      SimpleString queueName2 = SimpleString.of("Q2");
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();
      clientSession.deleteQueue(queueName);

      assertEquals(1, server.getPostOffice().getBindingsForAddress(addressAB).getBindings().size());
      assertEquals(1, server.getPostOffice().getBindingsForAddress(addressAC).getBindings().size());
      assertEquals(1, server.getPostOffice().getBindingsForAddress(address).getBindings().size());

      clientSession.deleteQueue(queueName2);

      assertEquals(0, server.getPostOffice().getBindingsForAddress(addressAB).getBindings().size());
      assertEquals(0, server.getPostOffice().getBindingsForAddress(addressAC).getBindings().size());
      assertEquals(0, server.getPostOffice().getBindingsForAddress(address).getBindings().size());
   }

   @Test
   public void testBasicWildcardRoutingWithHash() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("a.#");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testWildcardRoutingQueuesAddedAfter() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("a.*");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testWildcardRoutingQueuesAddedThenDeleted() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("a.*");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      clientSession.deleteQueue(queueName1);
      producer.send(createTextMessage(clientSession, "m1"));
      assertEquals(1, server.getPostOffice().getBindingsForAddress(addressAB).getBindings().size());
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      clientConsumer.close();
      clientSession.deleteQueue(queueName);
      assertEquals(0, server.getPostOffice().getBindingsForAddress(addressAB).getBindings().size());
   }

   @Test
   public void testWildcardRoutingLotsOfQueuesAddedThenDeleted() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString addressAD = SimpleString.of("a.d");
      SimpleString addressAE = SimpleString.of("a.e");
      SimpleString addressAF = SimpleString.of("a.f");
      SimpleString addressAG = SimpleString.of("a.g");
      SimpleString addressAH = SimpleString.of("a.h");
      SimpleString addressAJ = SimpleString.of("a.j");
      SimpleString addressAK = SimpleString.of("a.k");
      SimpleString address = SimpleString.of("a.*");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName3 = SimpleString.of("Q3");
      SimpleString queueName4 = SimpleString.of("Q4");
      SimpleString queueName5 = SimpleString.of("Q5");
      SimpleString queueName6 = SimpleString.of("Q6");
      SimpleString queueName7 = SimpleString.of("Q7");
      SimpleString queueName8 = SimpleString.of("Q8");
      SimpleString queueName9 = SimpleString.of("Q9");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName3).setAddress(addressAD).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName4).setAddress(addressAE).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName5).setAddress(addressAF).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName6).setAddress(addressAG).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName7).setAddress(addressAH).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName8).setAddress(addressAJ).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName9).setAddress(addressAK).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer();
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(addressAB, createTextMessage(clientSession, "m1"));
      producer.send(addressAC, createTextMessage(clientSession, "m2"));
      producer.send(addressAD, createTextMessage(clientSession, "m3"));
      producer.send(addressAE, createTextMessage(clientSession, "m4"));
      producer.send(addressAF, createTextMessage(clientSession, "m5"));
      producer.send(addressAG, createTextMessage(clientSession, "m6"));
      producer.send(addressAH, createTextMessage(clientSession, "m7"));
      producer.send(addressAJ, createTextMessage(clientSession, "m8"));
      producer.send(addressAK, createTextMessage(clientSession, "m9"));

      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m5", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m6", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m7", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m8", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m9", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
      // now remove all the queues
      clientSession.deleteQueue(queueName1);
      clientSession.deleteQueue(queueName2);
      clientSession.deleteQueue(queueName3);
      clientSession.deleteQueue(queueName4);
      clientSession.deleteQueue(queueName5);
      clientSession.deleteQueue(queueName6);
      clientSession.deleteQueue(queueName7);
      clientSession.deleteQueue(queueName8);
      clientSession.deleteQueue(queueName9);
      clientConsumer.close();
      clientSession.deleteQueue(queueName);
   }

   @Test
   public void testWildcardRoutingLotsOfQueuesAddedThenDeletedHash() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString addressAD = SimpleString.of("a.d");
      SimpleString addressAE = SimpleString.of("a.e");
      SimpleString addressAF = SimpleString.of("a.f");
      SimpleString addressAG = SimpleString.of("a.g");
      SimpleString addressAH = SimpleString.of("a.h");
      SimpleString addressAJ = SimpleString.of("a.j");
      SimpleString addressAK = SimpleString.of("a.k");
      SimpleString address = SimpleString.of("#");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName3 = SimpleString.of("Q3");
      SimpleString queueName4 = SimpleString.of("Q4");
      SimpleString queueName5 = SimpleString.of("Q5");
      SimpleString queueName6 = SimpleString.of("Q6");
      SimpleString queueName7 = SimpleString.of("Q7");
      SimpleString queueName8 = SimpleString.of("Q8");
      SimpleString queueName9 = SimpleString.of("Q9");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName3).setAddress(addressAD).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName4).setAddress(addressAE).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName5).setAddress(addressAF).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName6).setAddress(addressAG).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName7).setAddress(addressAH).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName8).setAddress(addressAJ).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName9).setAddress(addressAK).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer();
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(addressAB, createTextMessage(clientSession, "m1"));
      producer.send(addressAC, createTextMessage(clientSession, "m2"));
      producer.send(addressAD, createTextMessage(clientSession, "m3"));
      producer.send(addressAE, createTextMessage(clientSession, "m4"));
      producer.send(addressAF, createTextMessage(clientSession, "m5"));
      producer.send(addressAG, createTextMessage(clientSession, "m6"));
      producer.send(addressAH, createTextMessage(clientSession, "m7"));
      producer.send(addressAJ, createTextMessage(clientSession, "m8"));
      producer.send(addressAK, createTextMessage(clientSession, "m9"));

      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m5", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m6", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m7", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m8", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m9", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
      // now remove all the queues
      clientSession.deleteQueue(queueName1);
      clientSession.deleteQueue(queueName2);
      clientSession.deleteQueue(queueName3);
      clientSession.deleteQueue(queueName4);
      clientSession.deleteQueue(queueName5);
      clientSession.deleteQueue(queueName6);
      clientSession.deleteQueue(queueName7);
      clientSession.deleteQueue(queueName8);
      clientSession.deleteQueue(queueName9);
      clientConsumer.close();
      clientSession.deleteQueue(queueName);
   }

   @Test
   public void testWildcardRoutingWithSingleHash() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("#");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testWildcardRoutingWithHash() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b.f");
      SimpleString addressAC = SimpleString.of("a.c.f");
      SimpleString address = SimpleString.of("a.#.f");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testWildcardRoutingWithHashMultiLengthAddresses() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b.c.f");
      SimpleString addressAC = SimpleString.of("a.c.f");
      SimpleString address = SimpleString.of("a.#.f");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testWildcardRoutingWithDoubleStar() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("*.*");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testWildcardRoutingPartialMatchStar() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("*.b");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testWildcardRoutingVariableLengths() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b.c");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("a.#");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
   }

   @Test
   public void testWildcardRoutingVariableLengthsStar() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b.c");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("a.*");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testWildcardRoutingMultipleStars() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b.c");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("*.*");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testWildcardRoutingStarInMiddle() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b.c");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("*.b.*");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testWildcardRoutingStarAndHash() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b.c.d");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("*.b.#");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testWildcardRoutingHashAndStar() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b.c");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("#.b.*");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   @Test
   public void testLargeWildcardRouting() throws Exception {
      SimpleString addressAB = SimpleString.of("a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q.r.s.t.u.v.w.x.y.z");
      SimpleString addressAC = SimpleString.of("a.c");
      SimpleString address = SimpleString.of("a.#");
      SimpleString queueName1 = SimpleString.of("Q1");
      SimpleString queueName2 = SimpleString.of("Q2");
      SimpleString queueName = SimpleString.of("Q");
      clientSession.createQueue(QueueConfiguration.of(queueName1).setAddress(addressAB).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName2).setAddress(addressAC).setDurable(false));
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(address).setDurable(false));
      assertEquals(2, server.getPostOffice().getBindingsForAddress(addressAB).getBindings().size());
      assertEquals(2, server.getPostOffice().getBindingsForAddress(addressAC).getBindings().size());
      assertEquals(1, server.getPostOffice().getBindingsForAddress(address).getBindings().size());
      ClientProducer producer = clientSession.createProducer(addressAB);
      ClientProducer producer2 = clientSession.createProducer(addressAC);
      ClientConsumer clientConsumer = clientSession.createConsumer(queueName);
      clientSession.start();
      producer.send(createTextMessage(clientSession, "m1"));
      producer2.send(createTextMessage(clientSession, "m2"));
      ClientMessage m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receive(500);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m.acknowledge();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
      clientConsumer.close();
      clientSession.deleteQueue(queueName);
      assertEquals(1, server.getPostOffice().getBindingsForAddress(addressAB).getBindings().size());
      assertEquals(1, server.getPostOffice().getBindingsForAddress(addressAC).getBindings().size());
      assertEquals(0, server.getPostOffice().getBindingsForAddress(address).getBindings().size());
   }

   /**
    * Test that routing messages through wildcard subscriptions increments the
    * routedMessageCount on the target address. This is critical for auto-delete
    * functionality (ARTEMIS-5773) where addresses should be considered "used"
    * if messages were routed through them, even if no direct queue binding exists.
    */
   @Test
   public void testWildcardRoutingIncrementsMessageCount() throws Exception {
      SimpleString wildcardAddress = SimpleString.of("test.#");
      SimpleString specificAddress1 = SimpleString.of("test.foo.bar");
      SimpleString specificAddress2 = SimpleString.of("test.baz.qux");
      SimpleString queueName = SimpleString.of("wildcardQueue");

      // Create a wildcard queue
      clientSession.createQueue(QueueConfiguration.of(queueName).setAddress(wildcardAddress).setDurable(false));

      ClientProducer producer = clientSession.createProducer();
      ClientConsumer consumer = clientSession.createConsumer(queueName);
      clientSession.start();

      // Send messages to specific addresses that match the wildcard
      producer.send(specificAddress1, createTextMessage(clientSession, "message1"));
      producer.send(specificAddress2, createTextMessage(clientSession, "message2"));

      // Consume messages to ensure routing happened
      ClientMessage m = consumer.receive(500);
      assertNotNull(m);
      m.acknowledge();
      m = consumer.receive(500);
      assertNotNull(m);
      m.acknowledge();

      // Verify that the specific addresses were created and have their routed message count incremented
      assertNotNull(server.getAddressInfo(specificAddress1), "Address should be auto-created");
      assertNotNull(server.getAddressInfo(specificAddress2), "Address should be auto-created");

      // The routed message count should be > 0 for addresses that had messages routed through them
      assertEquals(1, server.getAddressInfo(specificAddress1).getRoutedMessageCount(),
                   "Address should have routed message count incremented");
      assertEquals(1, server.getAddressInfo(specificAddress2).getRoutedMessageCount(),
                   "Address should have routed message count incremented");

      consumer.close();
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      Configuration configuration = createDefaultInVMConfig().setWildcardRoutingEnabled(true).setTransactionTimeoutScanPeriod(500);
      server = addServer(ActiveMQServers.newActiveMQServer(configuration, false));
      server.start();
      server.getManagementService().enableNotifications(false);
      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
      clientSession = addClientSession(sf.createSession(false, true, true));
   }
}
