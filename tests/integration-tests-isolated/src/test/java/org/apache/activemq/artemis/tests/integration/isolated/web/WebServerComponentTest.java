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
package org.apache.activemq.artemis.tests.integration.isolated.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import org.apache.activemq.artemis.component.WebServerComponent;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.dto.BindingDTO;
import org.apache.activemq.artemis.dto.RequestLogDTO;
import org.apache.activemq.artemis.dto.WebServerDTO;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test leaks a thread named org.eclipse.jetty.util.RolloverFileOutputStream which is why it is isolated now. In
 * the future Jetty might fix this.
 */
public class WebServerComponentTest {

   static final String URL = System.getProperty("url", "http://localhost:8161/WebServerComponentTest.txt");

   private List<ActiveMQComponent> testedComponents;

   @BeforeEach
   public void setupNetty() throws URISyntaxException {
      System.setProperty("jetty.base", "./target");
      // Configure the client.
      testedComponents = new ArrayList<>();
   }

   @AfterEach
   public void tearDown() throws Exception {
      System.clearProperty("jetty.base");
      for (ActiveMQComponent c : testedComponents) {
         c.stop();
      }
      testedComponents.clear();
   }

   @Test
   public void testRequestLog() throws Exception {
      String requestLogFileName = "target/httpRequest.log";
      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.uri = "http://localhost:0";
      WebServerDTO webServerDTO = new WebServerDTO();
      webServerDTO.setBindings(Collections.singletonList(bindingDTO));
      webServerDTO.path = "webapps";
      webServerDTO.webContentEnabled = true;
      RequestLogDTO requestLogDTO = new RequestLogDTO();
      requestLogDTO.filename = requestLogFileName;
      webServerDTO.setRequestLog(requestLogDTO);
      WebServerComponent webServerComponent = new WebServerComponent();
      assertFalse(webServerComponent.isStarted());
      webServerComponent.configure(webServerDTO, "src/test/resources/", "src/test/resources/");
      testedComponents.add(webServerComponent);
      webServerComponent.start();

      final int port = webServerComponent.getPort();
      // Make the connection attempt.
      CountDownLatch latch = new CountDownLatch(1);
      final ClientHandler clientHandler = new ClientHandler(latch);
      Channel ch = getChannel(port, clientHandler);

      URI uri = new URI(URL);
      // Prepare the HTTP request.
      HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath());
      request.headers().set(HttpHeaderNames.HOST, "localhost");

      // Send the HTTP request.
      ch.writeAndFlush(request);
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertEquals("12345", clientHandler.body.toString());
      assertNull(clientHandler.serverHeader);
      // Wait for the server to close the connection.
      ch.close();
      ch.eventLoop().shutdownGracefully();
      ch.eventLoop().awaitTermination(5, TimeUnit.SECONDS);
      assertTrue(webServerComponent.isStarted());
      webServerComponent.stop(true);
      assertFalse(webServerComponent.isStarted());
      File requestLog = new File(requestLogFileName);
      assertTrue(requestLog.exists());
      boolean logEntryFound = false;
      try (BufferedReader reader = new BufferedReader(new FileReader(requestLog))) {
         String line;
         while ((line = reader.readLine()) != null) {
            if (line.contains("\"GET /WebServerComponentTest.txt HTTP/1.1\" 200 5")) {
               logEntryFound = true;
               break;
            }
         }
      }
      assertTrue(logEntryFound);
   }

   @Test
   public void testLargeRequestHeader() throws Exception {
      testLargeRequestHeader(false);
   }

   @Test
   public void testLargeRequestHeaderNegative() throws Exception {
      testLargeRequestHeader(true);
   }

   private void testLargeRequestHeader(boolean fail) throws Exception {
      final int defaultRequestHeaderSize = new HttpConfiguration().getRequestHeaderSize();
      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.uri = "http://localhost:0";
      WebServerDTO webServerDTO = new WebServerDTO();
      webServerDTO.setBindings(Collections.singletonList(bindingDTO));
      webServerDTO.path = "webapps";
      webServerDTO.webContentEnabled = true;
      if (!fail) {
         webServerDTO.maxRequestHeaderSize = defaultRequestHeaderSize * 2;
      }
      WebServerComponent webServerComponent = new WebServerComponent();
      assertFalse(webServerComponent.isStarted());
      webServerComponent.configure(webServerDTO, "./src/test/resources/", "./src/test/resources/");
      testedComponents.add(webServerComponent);
      webServerComponent.start();

      final int port = webServerComponent.getPort();
      // Make the connection attempt.
      CountDownLatch latch = new CountDownLatch(1);
      final ClientHandler clientHandler = new ClientHandler(latch);
      Channel ch = getChannel(port, clientHandler);

      URI uri = new URI(URL);
      // Prepare the HTTP request.
      HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath());
      request.headers().set(HttpHeaderNames.HOST, "localhost");
      request.headers().set("foo", "a".repeat(defaultRequestHeaderSize + 1));

      // Send the HTTP request.
      ch.writeAndFlush(request);
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertEquals(fail, clientHandler.body.toString().contains("431"));
   }

   /*
    * It's not clear how to create a functional test for the response header size so this test simply ensures the
    * configuration is passed through as expected.
    */
   @Test
   public void testLargeResponseHeaderConfiguration() throws Exception {
      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.uri = "http://localhost:0";
      WebServerDTO webServerDTO = new WebServerDTO();
      webServerDTO.setBindings(Collections.singletonList(bindingDTO));
      webServerDTO.path = "webapps";
      webServerDTO.webContentEnabled = true;
      webServerDTO.maxResponseHeaderSize = 123;
      WebServerComponent webServerComponent = new WebServerComponent();
      assertFalse(webServerComponent.isStarted());
      webServerComponent.configure(webServerDTO, "./src/test/resources/", "./src/test/resources/");
      testedComponents.add(webServerComponent);
      webServerComponent.start();
      assertEquals(123, ((HttpConnectionFactory)webServerComponent.getWebServer().getConnectors()[0].getConnectionFactories().iterator().next()).getHttpConfiguration().getResponseHeaderSize());
   }

   private Channel getChannel(int port, ClientHandler clientHandler) throws InterruptedException {
      EventLoopGroup group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer() {
         @Override
         protected void initChannel(Channel ch) throws Exception {
            ch.pipeline().addLast(new HttpClientCodec());
            ch.pipeline().addLast(clientHandler);
         }
      });
      return bootstrap.connect("localhost", port).sync().channel();
   }

   class ClientHandler extends SimpleChannelInboundHandler<HttpObject> {

      private CountDownLatch latch;
      private StringBuilder body = new StringBuilder();
      private String serverHeader;

      ClientHandler(CountDownLatch latch) {
         this.latch = latch;
      }

      @Override
      public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
         if (msg instanceof HttpResponse response) {
            serverHeader = response.headers().get("Server");
         } else if (msg instanceof HttpContent content) {
            body.append(content.content().toString(CharsetUtil.UTF_8));
            if (msg instanceof LastHttpContent) {
               latch.countDown();
            }
         }
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
         cause.printStackTrace();
         ctx.close();
      }
   }
}
