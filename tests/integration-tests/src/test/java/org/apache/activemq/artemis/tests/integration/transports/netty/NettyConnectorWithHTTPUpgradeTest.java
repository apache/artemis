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
package org.apache.activemq.artemis.tests.integration.transports.netty;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslHandler;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.remoting.impl.ssl.SSLSupport;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.netty.handler.codec.http.HttpHeaderNames.UPGRADE;
import static io.netty.handler.codec.http.HttpResponseStatus.SWITCHING_PROTOCOLS;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector.MAGIC_NUMBER;
import static org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector.SEC_ACTIVEMQ_REMOTING_ACCEPT;
import static org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector.SEC_ACTIVEMQ_REMOTING_KEY;
import static org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector.createExpectedResponse;
import static org.apache.activemq.artemis.utils.RandomUtil.randomUUIDString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test that Netty Connector can connect to a Web Server and upgrade from a HTTP request to its remoting protocol.
 *
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
@ExtendWith(ParameterizedTestExtension.class)
public class NettyConnectorWithHTTPUpgradeTest extends ActiveMQTestBase {

   private Boolean useSSL = false;

   @Parameters(name = "useSSL={0}")
   public static Collection getParameters() {
      return Arrays.asList(new Object[][]{{true}, {false}});
   }

   public NettyConnectorWithHTTPUpgradeTest(Boolean useSSL) {
      this.useSSL = useSSL;
   }

   private static final SimpleString QUEUE = SimpleString.of("NettyConnectorWithHTTPUpgradeTest");

   private static final int HTTP_PORT = 8789;

   private Configuration conf;
   private ActiveMQServer server;
   private ServerLocator locator;
   private String acceptorName;

   private ChannelFuture channelFuture;
   private EventLoopGroup bossGroup;
   private EventLoopGroup workerGroup;

   private String SERVER_SIDE_KEYSTORE = "server-keystore.jks";
   private String CLIENT_SIDE_TRUSTSTORE = "server-ca-truststore.jks";
   private final String PASSWORD = "securepass";

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      Map<String, Object> httpAcceptorParams = new HashMap<>();
      // This prop controls the usage of HTTP Get + Upgrade from Netty connector
      httpAcceptorParams.put(TransportConstants.HTTP_UPGRADE_ENABLED_PROP_NAME, true);
      httpAcceptorParams.put(TransportConstants.PORT_PROP_NAME, HTTP_PORT);
      acceptorName = randomUUIDString();

      conf = createDefaultNettyConfig().addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, httpAcceptorParams, acceptorName));

      server = addServer(ActiveMQServers.newActiveMQServer(conf, false));

      server.start();

      Map<String, Object> httpConnectorParams = new HashMap<>();
      httpConnectorParams.put(TransportConstants.HTTP_UPGRADE_ENABLED_PROP_NAME, true);
      httpConnectorParams.put(TransportConstants.PORT_PROP_NAME, HTTP_PORT);
      if (useSSL) {
         httpConnectorParams.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
         httpConnectorParams.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, CLIENT_SIDE_TRUSTSTORE);
         httpConnectorParams.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, PASSWORD);
      }
      locator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(NETTY_CONNECTOR_FACTORY, httpConnectorParams));
      addServerLocator(locator);

      // THe web server owns the HTTP port, not ActiveMQ Artemis.
      startWebServer(HTTP_PORT);
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      stopWebServer();
      super.tearDown();
   }

   @TestTemplate
   public void sendAndReceiveOverHTTPPort() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(ActiveMQTextMessage.TYPE, false, 0, System.currentTimeMillis(), (byte) 1);
         message.getBodyBuffer().writeString("sendAndReceiveOverHTTPPort");
         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);

      session.start();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message2 = consumer.receive();

         assertNotNull(message2);
         assertEquals("sendAndReceiveOverHTTPPort", message2.getBodyBuffer().readString());

         message2.acknowledge();
      }

      session.close();
   }

   @TestTemplate
   public void HTTPUpgradeConnectorUsingNormalAcceptor() throws Exception {
      Map<String, Object> params = new HashMap<>();

      // create a new locator that points an HTTP-upgrade connector to the normal acceptor
      params.put(TransportConstants.HTTP_UPGRADE_ENABLED_PROP_NAME, true);

      long start = System.currentTimeMillis();
      locator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(NETTY_CONNECTOR_FACTORY, params));

      Exception e = null;

      try {
         createSessionFactory(locator);

         // we shouldn't ever get here
         fail();
      } catch (Exception x) {
         e = x;
      }

      // make sure we failed *before* the HTTP hand-shake timeout elapsed (which is hard-coded to 30 seconds, see org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector.HttpUpgradeHandler.awaitHandshake())
      assertTrue((System.currentTimeMillis() - start) < 30000);
      assertNotNull(e);
      assertInstanceOf(ActiveMQNotConnectedException.class, e);
      assertEquals(ActiveMQExceptionType.NOT_CONNECTED, ((ActiveMQException) e).getType());
   }

   private void startWebServer(int port) throws Exception {
      bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
      workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
      ServerBootstrap b = new ServerBootstrap();
      final SSLContext context;
      if (useSSL) {
         context = new SSLSupport()
            .setKeystorePath(SERVER_SIDE_KEYSTORE)
            .setKeystorePassword(PASSWORD)
            .createContext();
      } else {
         context = null;
      }
      b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
         @Override
         protected void initChannel(SocketChannel ch) throws Exception {
            // create a HTTP server
            ChannelPipeline p = ch.pipeline();
            if (useSSL) {
               SSLEngine engine = context.createSSLEngine();
               engine.setUseClientMode(false);
               SslHandler handler = new SslHandler(engine);
               p.addLast("ssl", handler);
            }
            p.addLast("decoder", new HttpRequestDecoder());
            p.addLast("encoder", new HttpResponseEncoder());
            p.addLast("http-upgrade-handler", new SimpleChannelInboundHandler<>() {
               // handle HTTP GET + Upgrade with a handshake specific to ActiveMQ Artemis remoting.
               @Override
               protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                  if (msg instanceof HttpRequest request) {

                     for (Map.Entry<String, String> entry : request.headers()) {
                        System.out.println(entry);
                     }
                     String upgrade = request.headers().get(UPGRADE);
                     String secretKey = request.headers().get(SEC_ACTIVEMQ_REMOTING_KEY);

                     FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, SWITCHING_PROTOCOLS);
                     response.headers().set(UPGRADE, upgrade);
                     response.headers().set(SEC_ACTIVEMQ_REMOTING_ACCEPT, createExpectedResponse(MAGIC_NUMBER, secretKey));
                     ctx.writeAndFlush(response);

                     // when the handshake is successful, the HTTP handlers are removed
                     ctx.pipeline().remove("decoder");
                     ctx.pipeline().remove("encoder");
                     ctx.pipeline().remove(this);

                     System.out.println("HTTP handshake sent, transferring channel");
                     // transfer the control of the channel to the Netty Acceptor
                     NettyAcceptor acceptor = (NettyAcceptor) server.getRemotingService().getAcceptor(acceptorName);
                     acceptor.transfer(ctx.channel());
                     // at this point, the HTTP upgrade process is over and the netty acceptor behaves like regular ones.
                  }
               }
            });
         }

         @Override
         public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
         }
      });
      channelFuture = b.bind(port).sync();
   }

   private void stopWebServer() {
      channelFuture.channel().close();
      workerGroup.shutdownGracefully();
      bossGroup.shutdownGracefully();
   }
}
