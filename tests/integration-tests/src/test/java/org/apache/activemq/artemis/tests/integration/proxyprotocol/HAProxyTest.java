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
package org.apache.activemq.artemis.tests.integration.proxyprotocol;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueRequestor;
import javax.jms.QueueSession;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.management.JMSManagementHelper;
import org.apache.activemq.artemis.core.management.impl.view.predicate.ActiveMQFilterPredicate;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQQueueConnectionFactory;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.tests.integration.jms.multiprotocol.MultiprotocolJMSClientTestSupport;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.transport.netty.NettyHAProxyServer;
import org.apache.qpid.protonj2.test.driver.ProtonTestClient;
import org.apache.qpid.protonj2.test.driver.codec.security.SaslCode;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.tests.integration.mqtt5.MQTT5TestSupport.AT_LEAST_ONCE;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HAProxyTest extends MultiprotocolJMSClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int BROKER_PROXY_PORT = 61617;
   private static final int BROKER_PROXY_SSL_PORT = 61618;
   private static final int BROKER_STANDARD_PORT = 61616;

   // the following fake IPs and ports are injected by the NettyHAProxyServer to verify functionality on the broker
   private static final String HEADER_SOURCE_HOST = "9.9.9.9";
   private static final int HEADER_SOURCE_PORT = 9999;
   private static final String HEADER_DESTINATION_HOST = "8.8.8.8";
   private static final int HEADER_DESTINATION_PORT = 8888;
   private static final String REMOTE_ADDRESS_TO_VERIFY = HEADER_SOURCE_HOST + ":" + HEADER_SOURCE_PORT;
   private static final String PROXY_ADDRESS_TO_VERIFY = HEADER_DESTINATION_HOST + ":" + HEADER_DESTINATION_PORT;

   @Override
   protected ActiveMQServer createServer() throws Exception {
      server = createServer(false, createDefaultNettyConfig()
         .clearAcceptorConfigurations()
         .addAcceptorConfiguration("standard", "tcp://127.0.0.1:" + BROKER_STANDARD_PORT + "?protocols=CORE,AMQP")
         .addAcceptorConfiguration("proxyEnabled", "tcp://127.0.0.1:" + BROKER_PROXY_PORT + "?proxyProtocolEnabled=true")
         .addAcceptorConfiguration("proxyAndSslEnabled", "tcp://127.0.0.1:" + BROKER_PROXY_SSL_PORT + "?proxyProtocolEnabled=true;sslEnabled=true;protocols=CORE,AMQP,MQTT,OPENWIRE;supportAdvisory=false;suppressInternalManagementObjects=true;keyStorePath=server-keystore.jks;keyStorePassword=securepass"));

      server.start();

      return server;
   }

   /*
    * a non-proxied connection shouldn't be able to connect to an acceptor using proxyEnabled=true
    */
   @Test
   public void testNonProxiedConnectionToProxyAcceptor() {
      testFailure(() -> createConnection(BROKER_PROXY_PORT));
   }

   /*
    * a proxied connection shouldn't be able to connect to an acceptor using proxyEnabled=false
    */
   @Test
   public void testProxiedV1ConnectionToNonProxyAcceptor() throws Exception {
      testProxiedConnectionToNonProxyAcceptor(HAProxyProtocolVersion.V1);
   }

   /*
    * a proxied connection shouldn't be able to connect to an acceptor using proxyEnabled=false
    */
   @Test
   public void testProxiedV2ConnectionToNonProxyAcceptor() throws Exception {
      testProxiedConnectionToNonProxyAcceptor(HAProxyProtocolVersion.V2);
   }

   public void testProxiedConnectionToNonProxyAcceptor(HAProxyProtocolVersion version) throws Exception {
      NettyHAProxyServer proxy = new NettyHAProxyServer()
         .setBackEndPort(BROKER_STANDARD_PORT)
         .setSendProxyHeader(true)
         .setProxyProtocolVersion(version)
         .setHeaderSourceHost(HEADER_SOURCE_HOST)
         .setHeaderSourcePort(HEADER_SOURCE_PORT)
         .setHeaderDestinationHost(HEADER_DESTINATION_HOST)
         .setHeaderDestinationPort(HEADER_DESTINATION_PORT)
         .start();
      runAfter(proxy::stop);
      testFailure(() -> createConnection(proxy.getFrontendPortInUse()));
   }

   private void testFailure(ConnectionSupplier cf) {
      assertThrows(JMSException.class, () -> {
         assertTimeout(Duration.ofMillis(2000), () -> testSendReceive(cf, null));
      });
   }

   @Test
   public void testSendReceiveCoreV1() throws Exception {
      testSendReceiveCore(HAProxyProtocolVersion.V1);
   }

   @Test
   public void testSendReceiveCoreV2() throws Exception {
      testSendReceiveCore(HAProxyProtocolVersion.V2);
   }

   private void testSendReceiveCore(HAProxyProtocolVersion version) throws Exception {
      int proxyPort = startProxy(version, BROKER_PROXY_PORT);
      testSendReceive(() -> createCoreConnection(proxyPort), version);
   }

   @Test
   public void testSendReceiveAMQPV1() throws Exception {
      testSendReceiveAMQP(HAProxyProtocolVersion.V1);
   }

   @Test
   public void testSendReceiveAMQPV2() throws Exception {
      testSendReceiveAMQP(HAProxyProtocolVersion.V2);
   }

   private void testSendReceiveAMQP(HAProxyProtocolVersion version) throws Exception {
      int proxyPort = startProxy(version, BROKER_PROXY_PORT);
      testSendReceive(() -> createConnection(proxyPort), version);
   }

   @Test
   public void testSendReceiveOpenWireV1() throws Exception {
      testSendReceiveOpenWire(HAProxyProtocolVersion.V1);
   }

   @Test
   public void testSendReceiveOpenWireV2() throws Exception {
      testSendReceiveOpenWire(HAProxyProtocolVersion.V2);
   }

   private void testSendReceiveOpenWire(HAProxyProtocolVersion version) throws Exception {
      int proxyPort = startProxy(version, BROKER_PROXY_PORT);
      testSendReceive(() -> createOpenWireConnection(proxyPort), version);
   }

   @Test
   public void testSendReceiveCoreV1Ssl() throws Exception {
      testSendReceiveCoreSsl(HAProxyProtocolVersion.V1);
   }

   @Test
   public void testSendReceiveCoreV2Ssl() throws Exception {
      testSendReceiveCoreSsl(HAProxyProtocolVersion.V2);
   }

   private void testSendReceiveCoreSsl(HAProxyProtocolVersion version) throws Exception {
      int proxyPort = startProxy(version, BROKER_PROXY_SSL_PORT);
      testSendReceive(() -> createCoreConnection("tcp://localhost:" + proxyPort + "?sslEnabled=true;trustStorePath=server-ca-truststore.jks;trustStorePassword=securepass", null, null, null, true), version);
   }

   @Test
   public void testSendReceiveAmqpV1Ssl() throws Exception {
      testSendReceiveAmqpSsl(HAProxyProtocolVersion.V1);
   }

   @Test
   public void testSendReceiveAmqpV2Ssl() throws Exception {
      testSendReceiveAmqpSsl(HAProxyProtocolVersion.V2);
   }

   private void testSendReceiveAmqpSsl(HAProxyProtocolVersion version) throws Exception {
      int proxyPort = startProxy(version, BROKER_PROXY_SSL_PORT);
      URL truststorePath = Thread.currentThread().getContextClassLoader().getResource("server-ca-truststore.jks");
      assertNotNull(truststorePath, "Truststore file not found on classpath");
      String truststore = truststorePath.getPath();
      URI uri = new URI("amqps://localhost:" + proxyPort + "?transport.trustStoreLocation=" + truststore + "&transport.trustStorePassword=securepass");
      testSendReceive(() -> createConnection(uri, null, null, null, true), version);
   }

   @Test
   public void testSendReceiveOpenWireV1Ssl() throws Exception {
      testSendReceiveOpenWireSsl(HAProxyProtocolVersion.V1);
   }

   @Test
   public void testSendReceiveOpenWireV2Ssl() throws Exception {
      testSendReceiveOpenWireSsl(HAProxyProtocolVersion.V1);
   }

   private void testSendReceiveOpenWireSsl(HAProxyProtocolVersion version) throws Exception {
      int proxyPort = startProxy(version, BROKER_PROXY_SSL_PORT);
      testSendReceive(() -> {
         ActiveMQSslConnectionFactory cf = new ActiveMQSslConnectionFactory("ssl://localhost:" + proxyPort);
         try {
            cf.setTrustStore("server-ca-truststore.jks");
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
         cf.setTrustStorePassword("securepass");
         return cf.createConnection();
      }, version);
   }

   private void testSendReceive(ConnectionSupplier cf, HAProxyProtocolVersion version) throws Exception {
      int numberOfMessages = 100;

      for (int dest = 0; dest < 5; dest++) {
         Connection producerConnection = cf.createConnection();
         Wait.assertTrue(() -> verifyProxyConnectionCount(1, version));
         Session session = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue("queue.test" + dest);
         MessageProducer producer = session.createProducer(queue);
         producer.setDeliveryMode(DeliveryMode.PERSISTENT);

         for (int i = 0; i < numberOfMessages; i++) {
            producer.send(session.createTextMessage("hello " + i));
         }

         Connection consumerConnection = cf.createConnection();
         Wait.assertTrue(() -> verifyProxyConnectionCount(2, version));
         Session sessionConsumer = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queueConsumer = sessionConsumer.createQueue("queue.test" + dest);
         MessageConsumer consumer = sessionConsumer.createConsumer(queueConsumer);
         consumerConnection.start();

         for (int i = 0; i < numberOfMessages; i++) {
            Message message = consumer.receive(5000);
            assertNotNull(message);
         }

         producerConnection.close();
         consumerConnection.close();
      }
   }

   @Test
   public void testSendReceiveMqttV1() throws Exception {
      testSendReceiveMqtt(HAProxyProtocolVersion.V1);
   }

   @Test
   public void testSendReceiveMqttV2() throws Exception {
      testSendReceiveMqtt(HAProxyProtocolVersion.V2);
   }

   private void testSendReceiveMqtt(HAProxyProtocolVersion version) throws Exception {
      final int proxyPort = startProxy(version, BROKER_PROXY_PORT);
      final String url = "tcp://localhost:" + proxyPort;
      String topic = RandomUtil.randomUUIDString();
      MqttConnectionOptions connectionOptions = new MqttConnectionOptions();

      CountDownLatch latch = new CountDownLatch(1);
      MqttClient subscriber = new MqttClient(url, "subscriber", new MemoryPersistence());
      subscriber.connect(connectionOptions);
      subscriber.setCallback(new MqttCallback() {
         @Override
         public void disconnected(MqttDisconnectResponse mqttDisconnectResponse) {
         }

         @Override
         public void mqttErrorOccurred(MqttException e) {
         }

         @Override
         public void deliveryComplete(IMqttToken iMqttToken) {
         }

         @Override
         public void connectComplete(boolean b, String s) {
         }

         @Override
         public void authPacketArrived(int i, MqttProperties mqttProperties) {
         }

         @Override
         public void messageArrived(String topic, MqttMessage message) {
            logger.info("Message received from topic {}, message={}", topic, message);
            latch.countDown();
         }
      });
      subscriber.subscribe(topic, AT_LEAST_ONCE);

      MqttClient producer = new MqttClient(url, "producer", new MemoryPersistence());
      producer.connect(connectionOptions);
      producer.publish(topic, "myMessage".getBytes(StandardCharsets.UTF_8), 1, false);
      assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
      Wait.assertTrue(() -> verifyProxyConnectionCount(2, version));
      subscriber.disconnect();
      producer.disconnect();
   }

   private int startProxy(HAProxyProtocolVersion version, int backEndPort) {
      NettyHAProxyServer proxy = new NettyHAProxyServer()
         .setBackEndPort(backEndPort)
         .setProxyProtocolVersion(version)
         .setHeaderSourceHost(HEADER_SOURCE_HOST)
         .setHeaderSourcePort(HEADER_SOURCE_PORT)
         .setHeaderDestinationHost(HEADER_DESTINATION_HOST)
         .setHeaderDestinationPort(HEADER_DESTINATION_PORT)
         .start();
      runAfter(proxy::stop);
      return proxy.getFrontendPortInUse();
   }

   private boolean verifyProxyConnectionCount(int expectedConnections, HAProxyProtocolVersion version) throws Exception {
      // this connection goes directly to the broker so it won't be counted as a proxy connection
      try (ActiveMQQueueConnectionFactory cf = new ActiveMQQueueConnectionFactory("tcp://localhost:" + BROKER_STANDARD_PORT);
           QueueConnection c = cf.createQueueConnection()) {
         QueueSession s = c.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue managementQueue = ActiveMQJMSClient.createQueue("activemq.management");
         QueueRequestor requestor = new QueueRequestor(s, managementQueue);
         c.start();
         Message m = s.createMessage();
         String filter = createJsonFilter("proxyProtocolVersion", ActiveMQFilterPredicate.Operation.EQUALS.toString(), version.toString());
         JMSManagementHelper.putOperationInvocation(m, ResourceNames.BROKER, "listConnections", filter, 1, 50);
         JsonObject result = JsonUtil.readJsonObject((String) JMSManagementHelper.getResult(requestor.request(m), String.class));
         if (expectedConnections != result.getJsonNumber("count").intValue()) {
            return false;
         }
         JsonArray connections = result.getJsonArray("data");
         for (int i = 0; i < expectedConnections; i++) {
            if (!connections.getJsonObject(i).getString("proxyAddress").equals(PROXY_ADDRESS_TO_VERIFY)) {
               return false;
            }
            if (!connections.getJsonObject(i).getString("remoteAddress").equals(REMOTE_ADDRESS_TO_VERIFY)) {
               return false;
            }
         }
         return true;
      }
   }

   @Test
   @Timeout(30)
   public void testBrokerHandlesSplitAMQPHeaderBytesDuringConnectWithNoProxyHeader() throws Exception {
      try (ProtonTestClient receivingPeer = new ProtonTestClient()) {
         receivingPeer.connect("localhost", BROKER_STANDARD_PORT);
         receivingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         receivingPeer.expectSASLHeader();
         receivingPeer.expectSaslMechanisms().withSaslServerMechanism("ANONYMOUS");
         receivingPeer.remoteSaslInit().withMechanism("ANONYMOUS").queue();
         receivingPeer.expectSaslOutcome().withCode(SaslCode.OK);
         receivingPeer.remoteAMQPHeader().queue();
         receivingPeer.expectAMQPHeader();

         receivingPeer.remoteBytes().withBytes(new byte[] {'A', 'M'}).now();
         receivingPeer.remoteBytes().withBytes(new byte[] {'Q', 'P', 3, 1, 0, 0}).later(10);

         receivingPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         // Broker response after SASL anonymous connect
         receivingPeer.expectOpen();
         receivingPeer.expectBegin();

         // Create basic connection with session
         receivingPeer.remoteOpen().withContainerId("test-sender").now();
         receivingPeer.remoteBegin().withNextOutgoingId(100).now();

         receivingPeer.waitForScriptToComplete();
      }
   }
}
