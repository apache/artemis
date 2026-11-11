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

package org.apache.activemq.artemis.tests.compatibility;

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_10_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_33_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_44_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.apache.activemq.artemis.tests.compatibility.base.ClasspathBase;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AMQPJournalCompatibilityTest extends ClasspathBase {

   private ClassLoader senderClassloader;
   private ClassLoader receiverClassloader;

   @BeforeEach
   public void setUp() throws Exception {
      FileUtil.deleteDirectory(serverFolder);
      serverFolder.mkdirs();
   }

   @AfterEach
   public void tearDown() {
      try {
         stopServer(senderClassloader);
      } catch (Throwable ignored) {
      }
      try {
         stopServer(receiverClassloader);
      } catch (Throwable ignored) {
      }
   }

   @Test
   public void testAMQPSendReceive_2_33_0() throws Throwable {
      testAMQPSendReceive(ARTEMIS_2_33_0);
   }

   @Test
   public void testAMQPSendReceive_2_10_0() throws Throwable {
      assumeTrue(getJavaVersion() <= 22, "2.10.0 server fails on JDK23+");
      testAMQPSendReceive(ARTEMIS_2_10_0);
   }

   @Test
   public void testAMQPSendReceive_2_44_0() throws Throwable {
      testAMQPSendReceive(ARTEMIS_2_44_0);
   }

   private void testAMQPSendReceive(String senderVersion) throws Throwable {
      senderClassloader = getClasspath(senderVersion);
      receiverClassloader = getClasspath(SNAPSHOT);

      setVariable(senderClassloader, "persistent", true);
      startServer(serverFolder, senderClassloader, "journalTest", null, true, "servers/artemisServer.groovy", senderVersion, senderVersion, SNAPSHOT);

      ConnectionFactory factory = new JmsConnectionFactory("amqp://localhost:61616");

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue("queue"));
         for (int i = 0; i < 10; i++) {
            TextMessage message = session.createTextMessage("hello " + i);
            message.setIntProperty("count", i);
            producer.send(message);
         }
         session.commit();
      }

      stopServer(senderClassloader);

      setVariable(receiverClassloader, "persistent", true);
      startServer(serverFolder, receiverClassloader, "journalTest", null, false, "servers/artemisServer.groovy", SNAPSHOT, senderVersion, SNAPSHOT);

      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumer = session.createConsumer(session.createQueue("queue"));
         connection.start();
         for (int i = 0; i < 10; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message);
            assertEquals("hello " + i, message.getText());
            assertEquals(i, message.getIntProperty("count"));
         }
         session.commit();
      }
   }
}
