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
package org.apache.activemq.artemis.tests.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
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
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * There is a bug in JDK1.3, 1.4 whereby writeUTF fails if more than 64K bytes are written we need to work with all size
 * of strings
 * <p>
 * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4806007
 */
public class String64KLimitTest extends ActiveMQTestBase {


   private ActiveMQServer server;

   private ClientSession session;
   private ServerLocator locator;



   protected String genString(final int len) {
      char[] chars = new char[len];
      for (int i = 0; i < len; i++) {
         chars[i] = (char) (65 + i % 26);
      }
      return new String(chars);
   }

   @Test
   public void test64KLimitWithWriteString() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));

      ClientProducer producer = session.createProducer(address);
      ClientConsumer consumer = session.createConsumer(queue);
      session.start();

      String s1 = genString(16 * 1024);

      String s2 = genString(32 * 1024);

      String s3 = genString(64 * 1024);

      String s4 = genString(10 * 64 * 1024);

      ClientMessage tm1 = session.createMessage(false);
      tm1.getBodyBuffer().writeString(s1);

      ClientMessage tm2 = session.createMessage(false);
      tm2.getBodyBuffer().writeString(s2);

      ClientMessage tm3 = session.createMessage(false);
      tm3.getBodyBuffer().writeString(s3);

      ClientMessage tm4 = session.createMessage(false);
      tm4.getBodyBuffer().writeString(s4);

      producer.send(tm1);

      producer.send(tm2);

      producer.send(tm3);

      producer.send(tm4);

      ClientMessage rm1 = consumer.receive(1000);

      assertNotNull(rm1);

      assertEquals(s1, rm1.getBodyBuffer().readString());

      ClientMessage rm2 = consumer.receive(1000);

      assertNotNull(rm2);

      assertEquals(s2, rm2.getBodyBuffer().readString());

      ClientMessage rm3 = consumer.receive(1000);

      assertEquals(s3, rm3.getBodyBuffer().readString());

      assertNotNull(rm3);

      ClientMessage rm4 = consumer.receive(1000);

      assertEquals(s4, rm4.getBodyBuffer().readString());

      assertNotNull(rm4);
   }

   @Test
   public void test64KLimitWithWriteUTF() throws Exception {
      SimpleString address = RandomUtil.randomUUIDSimpleString();
      SimpleString queue = RandomUtil.randomUUIDSimpleString();

      session.createQueue(QueueConfiguration.of(queue).setAddress(address).setDurable(false));

      ClientProducer producer = session.createProducer(address);
      ClientConsumer consumer = session.createConsumer(queue);

      session.start();

      String s1 = genString(16 * 1024);

      String s2 = genString(32 * 1024);

      String s3 = genString(64 * 1024);

      String s4 = genString(10 * 64 * 1024);

      ClientMessage tm1 = session.createMessage(false);
      tm1.getBodyBuffer().writeUTF(s1);

      ClientMessage tm2 = session.createMessage(false);
      tm2.getBodyBuffer().writeUTF(s2);

      try {
         ClientMessage tm3 = session.createMessage(false);
         tm3.getBodyBuffer().writeUTF(s3);
         fail("can not write UTF string bigger than 64K");
      } catch (Exception e) {
      }

      try {
         ClientMessage tm4 = session.createMessage(false);
         tm4.getBodyBuffer().writeUTF(s4);
         fail("can not write UTF string bigger than 64K");
      } catch (Exception e) {
      }

      producer.send(tm1);
      producer.send(tm2);

      ClientMessage rm1 = consumer.receive(1000);

      assertNotNull(rm1);

      ClientMessage rm2 = consumer.receive(1000);

      assertNotNull(rm2);

      assertEquals(s1, rm1.getBodyBuffer().readUTF());
      assertEquals(s2, rm2.getBodyBuffer().readUTF());
   }

   // Protected -----------------------------------------------------

   private ClientSessionFactory sf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      Configuration config = createBasicConfig().addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY));
      server = addServer(ActiveMQServers.newActiveMQServer(config, false));
      server.start();
      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
      session = sf.createSession();
   }
}
