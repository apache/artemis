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
package org.apache.activemq.artemis.tests.integration.plugin;

import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_ADD_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_ADD_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CLOSE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CLOSE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_CONNECTION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_QUEUE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_DELIVER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_DEPLOY_BRIDGE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_DESTROY_CONNECTION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_MESSAGE_ROUTE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_REMOVE_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_REMOVE_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_SEND;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_ADD_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_ADD_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CLOSE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CLOSE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CREATE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CREATE_QUEUE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CREATE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_DELIVER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_DEPLOY_BRIDGE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_MESSAGE_ROUTE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_REMOVE_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_REMOVE_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_SEND;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.MESSAGE_ACKED;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.MESSAGE_EXPIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.core.protocol.stomp.StompConnection;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.stomp.StompTestBase;
import org.apache.activemq.artemis.tests.integration.stomp.util.ClientStompFrame;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionV12;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class StompPluginTest extends StompTestBase {

   public static final String CLIENT_ID = "myclientid";

   private StompClientConnectionV12 conn;

   @Parameters(name = "{0}")
   public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][]{{"ws+v12.stomp"}, {"tcp+v12.stomp"}});
   }

   public StompPluginTest(String scheme) {
      super(scheme);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      conn = (StompClientConnectionV12)StompClientConnectionFactory.createClientConnection("1.2", hostname, port);
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      try {
         boolean connected = conn != null && conn.isConnected();
         if (connected) {
            conn.disconnect();
         }
      } finally {
         super.tearDown();
      }
   }

   private final Map<String, AtomicInteger> methodCalls = new ConcurrentHashMap<>();
   private final MethodCalledVerifier verifier = new MethodCalledVerifier(methodCalls);
   private final AtomicBoolean stompBeforeCreateSession = new AtomicBoolean();
   private final AtomicBoolean stompBeforeRemoveSession = new AtomicBoolean();

   @Override
   protected ActiveMQServer createServer() throws Exception {
      ActiveMQServer server = super.createServer();
      server.getConfiguration().setAddressQueueScanPeriod(100);
      server.registerBrokerPlugin(verifier);
      server.registerBrokerPlugin(new ActiveMQServerPlugin() {

         @Override
         public void beforeCreateSession(String name, String username, int minLargeMessageSize,
               RemotingConnection connection, boolean autoCommitSends, boolean autoCommitAcks, boolean preAcknowledge,
               boolean xa, String defaultAddress, SessionCallback callback, boolean autoCreateQueues,
               OperationContext context, Map<SimpleString, RoutingType> prefixes) throws ActiveMQException {

            if (connection instanceof StompConnection) {
               stompBeforeCreateSession.set(true);
            }
         }

         @Override
         public void beforeCloseSession(ServerSession session, boolean failed) throws ActiveMQException {
            if (session.getRemotingConnection() instanceof StompConnection) {
               stompBeforeRemoveSession.set(true);
            }
         }
      });
      return server;
   }

   @TestTemplate
   public void testSendAndReceive() throws Exception {

      URI uri = new URI(scheme + "://localhost:61613");
      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(uri);
      newConn.connect(defUser, defPass);
      subscribe(newConn, "a-sub");

      send(newConn, getQueuePrefix() + getQueueName(), "text/plain", "Hello World 1!");
      ClientStompFrame frame = newConn.receiveFrame();

      System.out.println("received " + frame);
      assertEquals(Stomp.Responses.MESSAGE, frame.getCommand());

      verifier.validatePluginMethodsAtLeast(1, MESSAGE_ACKED, BEFORE_SEND, AFTER_SEND, BEFORE_MESSAGE_ROUTE, AFTER_MESSAGE_ROUTE, BEFORE_DELIVER,
                                            AFTER_DELIVER);


      // unsub
      unsubscribe(newConn, "a-sub");

      newConn.disconnect();

      verifier.validatePluginMethodsEquals(0, MESSAGE_EXPIRED, BEFORE_DEPLOY_BRIDGE, AFTER_DEPLOY_BRIDGE, BEFORE_REMOVE_BINDING, AFTER_REMOVE_BINDING);
      verifier.validatePluginMethodsAtLeast(1, AFTER_CREATE_CONNECTION, AFTER_DESTROY_CONNECTION, BEFORE_CREATE_SESSION,
                                            AFTER_CREATE_SESSION, BEFORE_CLOSE_SESSION, AFTER_CLOSE_SESSION, BEFORE_CREATE_CONSUMER,
                                            AFTER_CREATE_CONSUMER, BEFORE_CLOSE_CONSUMER, AFTER_CLOSE_CONSUMER, BEFORE_CREATE_QUEUE, AFTER_CREATE_QUEUE,
                                            MESSAGE_ACKED, BEFORE_SEND, AFTER_SEND, BEFORE_MESSAGE_ROUTE, AFTER_MESSAGE_ROUTE, BEFORE_DELIVER,
                                            AFTER_DELIVER, BEFORE_ADD_ADDRESS, AFTER_ADD_ADDRESS, BEFORE_ADD_BINDING, AFTER_ADD_BINDING);
   }

   @TestTemplate
   public void testStompAutoCreateAddress() throws Exception {

      URI uri = new URI(scheme + "://localhost:61613");
      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(uri);
      newConn.connect(defUser, defPass);

      subscribeQueue(newConn, "a-sub", "autoCreated");

      // unsub
      unsubscribe(newConn, "a-sub");
      newConn.disconnect();

      verifier.validatePluginMethodsAtLeast(1, BEFORE_ADD_ADDRESS, AFTER_ADD_ADDRESS,
            BEFORE_REMOVE_ADDRESS, AFTER_REMOVE_ADDRESS);

   }

   @TestTemplate
   public void testConnect() throws Exception {

      URI uri = new URI(scheme + "://localhost:61613");
      StompClientConnection newConn = StompClientConnectionFactory.createClientConnection(uri);
      newConn.connect(defUser, defPass);

      //Make sure session is created on connect
      assertTrue(stompBeforeCreateSession.get());

      newConn.disconnect();

      Thread.sleep(500);

      //Make sure session is removed on disconnect
      assertTrue(stompBeforeRemoveSession.get());

   }
}
