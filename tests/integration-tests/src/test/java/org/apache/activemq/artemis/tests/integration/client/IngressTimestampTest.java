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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.apache.qpid.jms.message.JmsTextMessage;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsMessageFacade;
import org.apache.qpid.proton.amqp.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class IngressTimestampTest extends ActiveMQTestBase {
   private ActiveMQServer server;

   private final SimpleString QUEUE = SimpleString.of("ConsumerTestQueue");

   @Parameters(name = "restart={0}, large={1}")
   public static Collection<Object[]> parameters() {
      return Arrays.asList(new Object[][] {
         {true, true},
         {false, false},
         {true, false},
         {false, true}
      });
   }

   @Parameter(index = 0)
   public boolean restart;

   @Parameter(index = 1)
   public boolean large;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(true, true);
      server.start();
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setEnableIngressTimestamp(true));
      server.createQueue(QueueConfiguration.of(QUEUE).setRoutingType(RoutingType.ANYCAST));
   }

   @TestTemplate
   public void testSendCoreReceiveAMQP() throws Throwable {
      internalSendReceive(Protocol.CORE, Protocol.AMQP);
   }

   @TestTemplate
   public void testSendAMQPReceiveAMQP() throws Throwable {
      internalSendReceive(Protocol.AMQP, Protocol.AMQP);
   }

   @TestTemplate
   public void testSendOpenWireReceiveAMQP() throws Throwable {
      internalSendReceive(Protocol.OPENWIRE, Protocol.AMQP);
   }

   @TestTemplate
   public void testSendCoreReceiveCore() throws Throwable {
      internalSendReceive(Protocol.CORE, Protocol.CORE);
   }

   @TestTemplate
   public void testSendAMQPReceiveCore() throws Throwable {
      internalSendReceive(Protocol.AMQP, Protocol.CORE);
   }

   @TestTemplate
   public void testSendOpenWireReceiveCore() throws Throwable {
      internalSendReceive(Protocol.OPENWIRE, Protocol.CORE);
   }

   @TestTemplate
   public void testSendCoreReceiveOpenwire() throws Throwable {
      internalSendReceive(Protocol.CORE, Protocol.OPENWIRE);
   }

   @TestTemplate
   public void testSendAMQPReceiveOpenWire() throws Throwable {
      internalSendReceive(Protocol.AMQP, Protocol.OPENWIRE);
   }

   @TestTemplate
   public void testSendOpenWireReceiveOpenWire() throws Throwable {
      internalSendReceive(Protocol.OPENWIRE, Protocol.OPENWIRE);
   }

   private void internalSendReceive(Protocol protocolSender, Protocol protocolConsumer) throws Throwable {
      ConnectionFactory factorySend = createFactory(protocolSender);
      ConnectionFactory factoryConsume = protocolConsumer == protocolSender ? factorySend : createFactory(protocolConsumer);

      long beforeSend, afterSend;
      try (Connection connection = factorySend.createConnection()) {
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            javax.jms.Queue queue = session.createQueue(QUEUE.toString());
            try (MessageProducer producer = session.createProducer(queue)) {
               producer.setDeliveryMode(DeliveryMode.PERSISTENT);
               TextMessage msg = session.createTextMessage(getMessagePayload());
               beforeSend = System.currentTimeMillis();
               producer.send(msg);
               afterSend = System.currentTimeMillis();
            }
         }
      }

      if (restart) {
         server.stop();
         server.start();
         assertTrue(server.waitForActivation(3, TimeUnit.SECONDS));
      }

      try (Connection connection = factoryConsume.createConnection()) {
         connection.start();
         try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            javax.jms.Queue queue = session.createQueue(QUEUE.toString());
            try (MessageConsumer consumer = session.createConsumer(queue)) {
               TextMessage message = (TextMessage) consumer.receive(1000);
               assertNotNull(message);
               Enumeration e = message.getPropertyNames();
               while (e.hasMoreElements()) {
                  System.out.println(e.nextElement());
               }
               Object ingressTimestampHeader = null;
               if (protocolConsumer == Protocol.AMQP) {
                  // Qpid JMS doesn't expose message annotations so we must use reflection here
                  Method getMessageAnnotation = AmqpJmsMessageFacade.class.getDeclaredMethod("getMessageAnnotation", Symbol.class);
                  getMessageAnnotation.setAccessible(true);
                  ingressTimestampHeader = getMessageAnnotation.invoke(((JmsTextMessage)message).getFacade(), Symbol.getSymbol(AMQPMessageSupport.X_OPT_INGRESS_TIME));
               } else {
                  ingressTimestampHeader = message.getObjectProperty(Message.HDR_INGRESS_TIMESTAMP.toString());
               }
               assertNotNull(ingressTimestampHeader);
               assertInstanceOf(Long.class, ingressTimestampHeader);
               long ingressTimestamp = (Long) ingressTimestampHeader;
               assertTrue(ingressTimestamp >= beforeSend && ingressTimestamp <= afterSend, "Ingress timstamp " + ingressTimestamp + " should be >= " + beforeSend + " and <= " + afterSend);
            }
         }
      }
   }

   private String getMessagePayload() {
      return large ? "AB".repeat(ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE * 20) : "AB";
   }

   private ConnectionFactory createFactory(Protocol protocol) {
      return switch (protocol) {
         case CORE -> new ActiveMQConnectionFactory(); // core protocol
         case AMQP -> new JmsConnectionFactory("amqp://localhost:61616"); // amqp
         case OPENWIRE -> new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616"); // openwire
         default -> null;
      };
   }

   private enum Protocol {
      CORE, AMQP, OPENWIRE
   }
}
