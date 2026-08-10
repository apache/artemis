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
package org.apache.activemq.artemis.tests.integration.openwire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSSecurityException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.resource.spi.IllegalStateException;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.command.Command;
import org.apache.activemq.command.ConnectionId;
import org.apache.activemq.command.ConnectionInfo;
import org.apache.activemq.command.ExceptionResponse;
import org.apache.activemq.command.RemoveSubscriptionInfo;
import org.apache.activemq.command.Response;
import org.apache.activemq.command.WireFormatInfo;
import org.apache.activemq.openwire.OpenWireFormat;
import org.apache.activemq.openwire.OpenWireFormatFactory;
import org.apache.activemq.transport.netty.NettyTransport;
import org.apache.activemq.transport.netty.NettyTransportFactory;
import org.apache.activemq.transport.netty.NettyTransportListener;
import org.apache.activemq.util.ByteSequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;

@Timeout(20)
public class OpenWireRemoveSubscriptionInfoFrameTest extends BasicOpenWireTest {

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final String ALLOWED_USER = "allowedUser";
   private final String ALLOWED_ROLE = "allowedRole";
   private final String PASS = RandomUtil.randomUUIDString();
   private final String ADDRESS = "myAddress";

   private final String DENIED_USER = "deniedUser";
   private final String DENIED_ROLE = "deniedRole";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      realStore = false;
      enableSecurity = true;

      super.setUp();
   }

   @Override
   protected void extraServerConfig(Configuration configuration) {
      super.extraServerConfig(configuration);

      final Role allowed = new Role(ALLOWED_ROLE, true, true, true, true, false, false, false, false, true, false, false, false);
      final Role denied = new Role(DENIED_ROLE, false, false, false, false, false, false, false, false, false, false, false, false);

      final ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();

      securityManager.getConfiguration().addUser(ALLOWED_USER, PASS);
      securityManager.getConfiguration().addRole(ALLOWED_USER, ALLOWED_ROLE);
      securityManager.getConfiguration().addRole(ALLOWED_USER, "advisoryReceiver");
      securityManager.getConfiguration().addUser(DENIED_USER, PASS);
      securityManager.getConfiguration().addRole(DENIED_USER, DENIED_ROLE);
      securityManager.getConfiguration().addRole(DENIED_USER, "advisoryReceiver");

      configuration.putSecurityRoles(ADDRESS, Set.of(allowed, denied));
   }

   @Test
   public void testUnauthenticatedConnectionCannotDeleteSubscriptionQueue() throws Exception {
      final String CLIENT_ID = "test-id";
      final String SUBSCRIPTION_NAME = getTestMethodName();
      final SimpleString SUBSCRIPTION_QUEUE = org.apache.activemq.artemis.jms.client.ActiveMQDestination.createQueueNameForSubscription(true, CLIENT_ID,
         SUBSCRIPTION_NAME);

      try (Connection connection = factory.createConnection(ALLOWED_USER, PASS)) {

         connection.setClientID(CLIENT_ID);

         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Topic destination = session.createTopic(ADDRESS);

         final MessageProducer producer = session.createProducer(destination);
         final MessageConsumer consumer = session.createDurableConsumer(destination, SUBSCRIPTION_NAME);

         consumer.close();

         producer.setDeliveryMode(DeliveryMode.PERSISTENT); // ALLOWED USER
         producer.send(session.createTextMessage("durable-message"));

         Wait.assertTrue(() -> server.queueQuery(SUBSCRIPTION_QUEUE).isExists(), 1_000, 50);

         final Queue subscriptionQueue = server.locateQueue(SUBSCRIPTION_QUEUE);

         assertEquals(1, subscriptionQueue.getMessageCount());

         connection.close();

         final RemoveSubscriptionInfo removeSubscription = new RemoveSubscriptionInfo();
         removeSubscription.setClientId(CLIENT_ID);
         removeSubscription.setConnectionId(new ConnectionId("test:1"));
         removeSubscription.setResponseRequired(true);
         removeSubscription.setSubcriptionName(SUBSCRIPTION_NAME);

         try (OpenwireTestClient client = new OpenwireTestClient(OWHOST, OWPORT)) {
            client.connect().get(2, TimeUnit.SECONDS);

            try {
               client.send(removeSubscription).get(10, TimeUnit.MILLISECONDS);

               fail("Should have timed out waiting for the requested response");
            } catch (TimeoutException e) {
               // expected
            }

            client.awaitRemoteClose(2, TimeUnit.SECONDS);
         }

         Wait.assertTrue(() -> server.queueQuery(SUBSCRIPTION_QUEUE).isExists(), 1_000, 50);
      }
   }

   @Test
   public void testConnectionWithoutAuthorizationCannotDeleteSubscriptionQueue() throws Exception {
      final String CLIENT_ID = "test-id";
      final String SUBSCRIPTION_NAME = getTestMethodName();
      final SimpleString SUBSCRIPTION_QUEUE = org.apache.activemq.artemis.jms.client.ActiveMQDestination.createQueueNameForSubscription(true, CLIENT_ID,
         SUBSCRIPTION_NAME);

      try (Connection connection = factory.createConnection(ALLOWED_USER, PASS)) {

         connection.setClientID(CLIENT_ID);

         final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final Topic destination = session.createTopic(ADDRESS);

         final MessageProducer producer = session.createProducer(destination);
         final MessageConsumer consumer = session.createDurableConsumer(destination, SUBSCRIPTION_NAME);

         consumer.close();

         producer.setDeliveryMode(DeliveryMode.PERSISTENT); // ALLOWED USER
         producer.send(session.createTextMessage("durable-message"));

         connection.close();

         Wait.assertTrue(() -> server.queueQuery(SUBSCRIPTION_QUEUE).isExists(), 1_000, 50);

         final Queue subscriptionQueue = server.locateQueue(SUBSCRIPTION_QUEUE);

         assertEquals(1, subscriptionQueue.getMessageCount());

         final ConnectionInfo connectionInfo = new ConnectionInfo();
         connectionInfo.setConnectionId(new ConnectionId("test:1"));
         connectionInfo.setClientId(UUID.randomUUID().toString());
         connectionInfo.setUserName(DENIED_USER);
         connectionInfo.setPassword(PASS);
         connectionInfo.setResponseRequired(true);

         final RemoveSubscriptionInfo removeSubscription = new RemoveSubscriptionInfo();
         removeSubscription.setClientId(CLIENT_ID);
         removeSubscription.setConnectionId(new ConnectionId("test:1"));
         removeSubscription.setResponseRequired(true);
         removeSubscription.setSubcriptionName(SUBSCRIPTION_NAME);

         try (OpenwireTestClient client = new OpenwireTestClient(OWHOST, OWPORT)) {
            client.connect().get(2, TimeUnit.SECONDS);
            client.send(connectionInfo).get(20, TimeUnit.SECONDS);

            final Response response = client.send(removeSubscription).get(10, TimeUnit.SECONDS);

            assertTrue(response.isException());
            assertNull(client.getLastError());

            final ExceptionResponse exResponse = (ExceptionResponse) response;

            assertTrue(exResponse.getException() instanceof JMSSecurityException);
         }

         Thread.sleep(50); // Bit of buffer to allow the command to be handled.

         Wait.assertTrue(() -> server.queueQuery(SUBSCRIPTION_QUEUE).isExists(), 1_000, 50);
      }
   }

   @Test
   public void testCannotDeleteCreatedQueueWithRemoveSubscriptionInfoResponse() throws Exception {
      testCannotDeleteCreatedQueueWithRemoveSubscriptionInfo(true);
   }

   @Test
   public void testCannotDeleteCreatedQueueWithRemoveSubscriptionInfoNoResponse() throws Exception {
      testCannotDeleteCreatedQueueWithRemoveSubscriptionInfo(false);
   }

   private void testCannotDeleteCreatedQueueWithRemoveSubscriptionInfo(boolean requireResponse) throws Exception {
      final String QUEUE_NAME = getTestMethodName() + "deleteMe";

      server.createQueue(QueueConfiguration.of(QUEUE_NAME));

      Wait.assertTrue(() -> server.queueQuery(SimpleString.of(QUEUE_NAME)).isExists(), 1_000, 50);

      final RemoveSubscriptionInfo removeSubscription = new RemoveSubscriptionInfo();
      removeSubscription.setClientId(null);
      removeSubscription.setConnectionId(new ConnectionId("test:1"));
      removeSubscription.setResponseRequired(requireResponse);
      removeSubscription.setSubcriptionName(QUEUE_NAME);

      try (OpenwireTestClient client = new OpenwireTestClient(OWHOST, OWPORT)) {
         client.connect().get(2, TimeUnit.SECONDS);

         try {
            client.send(removeSubscription).get(10, TimeUnit.MILLISECONDS);

            if (removeSubscription.isResponseRequired()) {
               fail("Should have timed out waiting for the requested response");
            }
         } catch (TimeoutException e) {
            assertTrue(requireResponse);
         }

         client.awaitRemoteClose(2, TimeUnit.SECONDS);
      }

      Wait.assertTrue(() -> server.queueQuery(SimpleString.of(QUEUE_NAME)).isExists(), 500, 50);
   }

   protected class OpenwireTestClient implements AutoCloseable {

      private final NettyTransportListener listener = new InternalNettyTransportListener();
      private final CountDownLatch remoteClosed = new CountDownLatch(1);
      private final OpenWireFormatFactory wfFactory = new OpenWireFormatFactory();
      {
         wfFactory.setVersion(OpenWireFormat.DEFAULT_WIRE_VERSION);
         wfFactory.setMaxInactivityDuration(30);
         wfFactory.setTightEncodingEnabled(false);
         wfFactory.setCacheEnabled(false);
         wfFactory.setMaxFrameSize(8192);
      }
      private final List<Command> received = new CopyOnWriteArrayList<>();
      private final Map<Integer, CompletableFuture<Response>> requests = new ConcurrentHashMap<>();
      private final OpenWireFormat wf;
      private final CompletableFuture<Response> wireFormatInfoResponse = new CompletableFuture<>();

      private final String host;
      private final int port;

      private NettyTransport tcpClient;
      private Throwable lastError;
      private int commandId = 0;

      public OpenwireTestClient(String host, int port) {
         Objects.requireNonNull(host);

         if (port <= 0) {
            throw new IllegalArgumentException("Port value given is out of range.");
         }

         this.host = host;
         this.port = port;
         this.wf = (OpenWireFormat) wfFactory.createWireFormat();
      }

      public Future<Response> connect() throws Exception {
         if (tcpClient != null) {
            throw new IllegalStateException("This client can only be connected once");
         }

         final URI openwireURI = new URI("tcp://" + host + ":" + port);

         tcpClient = NettyTransportFactory.createTransport(openwireURI);
         tcpClient.setTransportListener(listener);
         tcpClient.connect();

         send(wf.getPreferedWireFormatInfo());

         return wireFormatInfoResponse;
      }

      public Future<Response> send(Command command) throws Exception {
         command.setCommandId(commandId++);

         final CompletableFuture<Response> pending;

         if (command.isWireFormatInfo()) {
            pending = wireFormatInfoResponse;
         } else if (command.isResponseRequired()) {
            pending = new CompletableFuture<Response>();
            requests.put(command.getCommandId(), pending);
         } else {
            pending = CompletableFuture.completedFuture(null);
         }

         final ByteSequence commandBytes = wf.marshal(command);
         final ByteBuf wrapper = Unpooled.wrappedBuffer(commandBytes.getData(), commandBytes.getOffset(), commandBytes.getLength());

         wrapper.writerIndex(commandBytes.getLength());

         tcpClient.send(wrapper).await();

         return pending;
      }

      public boolean isConnected() {
         return tcpClient.isConnected();
      }

      public void awaitRemoteClose(int timeout, TimeUnit units) throws Exception {
         assertTrue(remoteClosed.await(timeout, units));
      }

      public Throwable getLastError() {
         return lastError;
      }

      public int receivedCount() {
         return received.size();
      }

      public void foreach(Consumer<? super Command> commandConsumer) {
         received.forEach(commandConsumer);
      }

      @Override
      public void close() throws Exception {
         if (tcpClient != null) {
            tcpClient.close();
         }
      }

      private class InternalNettyTransportListener implements NettyTransportListener {

         private final FrameSizeParsingStage frameSizeParser = new FrameSizeParsingStage();
         private final FrameBufferingStage frameBufferingStage = new FrameBufferingStage();
         private final FrameBodyParsingStage frameBodyParsingStage = new FrameBodyParsingStage();

         private FrameParserStage stage = new FrameSizeParsingStage();

         @Override
         public void onData(ByteBuf incoming) {
            LOG.debug("Incoming data packet from server: {}", incoming);

            while (incoming.isReadable()) {
               try {
                  stage.parse(incoming);
               } catch (Exception e) {
                  LOG.error("Caught error while decoding incoming frame:", e);
                  lastError = e;
               }
            }
         }

         @Override
         public void onTransportClosed() {
            LOG.debug("Transport reports connection closed");
            remoteClosed.countDown();
         }

         @Override
         public void onTransportError(Throwable cause) {
            LOG.debug("Transport reports error: ", cause);
            lastError = cause;
            remoteClosed.countDown();
         }

         protected void onCommand(Command command) throws IOException {
            if (command.getDataStructureType() == WireFormatInfo.DATA_STRUCTURE_TYPE) {
               wf.renegotiateWireFormat((WireFormatInfo) command);
               wireFormatInfoResponse.complete(null);
            }

            if (command instanceof Response response) {
               final int correlationId = response.getCorrelationId();
               final CompletableFuture<Response> pending = requests.remove(correlationId);

               if (pending != null) {
                  pending.complete(response);
               }
            }

            received.add(command);
         }

         private FrameParserStage transitionToFrameSizeParsingStage() {
            return stage = frameSizeParser.reset(0);
         }

         private FrameParserStage transitionToFrameBufferingStage(int length) {
            return stage = frameBufferingStage.reset(length);
         }

         private FrameParserStage initializeFrameUnmarshalStage(int length) {
            return stage = frameBodyParsingStage.reset(length);
         }

         private interface FrameParserStage {

            void parse(ByteBuf input) throws IOException;

            FrameParserStage reset(int length);

         }

         private final class FrameSizeParsingStage implements FrameParserStage {

            private int frameSize;
            private int multiplier = Integer.BYTES;

            @Override
            public void parse(ByteBuf input) throws IOException {
               if (multiplier == Integer.BYTES && input.readableBytes() >= Integer.BYTES) {
                  frameSize = input.getInt(input.readerIndex());
                  multiplier = 0;
               } else {
                  readFrameSizeInChunks(input);
               }

               if (multiplier == 0) {
                  int length = frameSize + Integer.BYTES; // Ensure the size prefix is included

                  if (input.readableBytes() < length) {
                     transitionToFrameBufferingStage(length);
                  } else {
                     initializeFrameUnmarshalStage(length);
                  }

                  stage.parse(input);
               }
            }

            private void readFrameSizeInChunks(ByteBuf input) {
               while (input.isReadable()) {
                  frameSize |= ((input.getByte(input.readerIndex() + (Integer.MAX_VALUE - multiplier--)) & 0xFF) << multiplier * Byte.SIZE);
                  if (multiplier == 0) {
                     break;
                  }
               }
            }

            @Override
            public FrameSizeParsingStage reset(int frameSize) {
               multiplier = Integer.BYTES;
               this.frameSize = frameSize;
               return this;
            }
         }

         private final class FrameBufferingStage implements FrameParserStage {

            private ByteBuf buffer;
            private int frameBytesRemaining;
            private int frameSize;

            @Override
            public void parse(ByteBuf input) throws IOException {
               if (input.readableBytes() > frameBytesRemaining) {
                  buffer.writeBytes(input, frameBytesRemaining);
               } else {
                  buffer.writeBytes(input);
               }

               frameBytesRemaining = frameSize - buffer.readableBytes();

               if (frameBytesRemaining == 0) {
                  // Now we can consume the buffer frame body.
                  initializeFrameUnmarshalStage(buffer.readableBytes());
                  try {
                     stage.parse(buffer);
                  } finally {
                     buffer = null;
                  }
               }
            }

            @Override
            public FrameBufferingStage reset(int length) {
               buffer = Unpooled.buffer(length);
               frameBytesRemaining = length;
               frameSize = length;

               return this;
            }
         }

         private final class FrameBodyParsingStage implements FrameParserStage {

            private int length;

            @Override
            public void parse(ByteBuf input) throws IOException {
               try (ByteBufInputStream bbis = new ByteBufInputStream(input, length);
                    DataInputStream dais = new DataInputStream(bbis)) {

                  final Object result = wf.unmarshal(dais);

                  if (result instanceof Command command) {
                     onCommand(command);
                  } else {
                     throw new IOException("Unknown type returned from Openwire frame parser");
                  }
               } catch (Exception e) {
                  LOG.error("Failed while decoding command: ", e);
               } finally {
                  transitionToFrameSizeParsingStage();
               }
            }

            @Override
            public FrameBodyParsingStage reset(int length) {
               this.length = length;
               return this;
            }
         }
      }
   }
}
