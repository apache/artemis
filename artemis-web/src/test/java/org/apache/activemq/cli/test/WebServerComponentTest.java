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
package org.apache.activemq.cli.test;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.cli.factory.xml.XmlBrokerFactoryHandler;
import org.apache.activemq.artemis.component.WebServerComponent;
import org.apache.activemq.artemis.component.WebServerComponentTestAccessor;
import org.apache.activemq.artemis.core.remoting.impl.ssl.SSLSupport;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.dto.AppDTO;
import org.apache.activemq.artemis.dto.BindingDTO;
import org.apache.activemq.artemis.dto.BrokerDTO;
import org.apache.activemq.artemis.dto.WebServerDTO;
import org.apache.activemq.artemis.tests.util.ArtemisTestCase;
import org.apache.activemq.artemis.utils.PemConfigUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultRoutePlanner;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.eclipse.jetty.ee9.webapp.WebAppContext;
import org.eclipse.jetty.ee9.webapp.WebInfConfiguration;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebServerComponentTest extends ArtemisTestCase {

   @TempDir
   public File tempFolder;

   static final String URL = System.getProperty("url", "http://localhost:8161/WebServerComponentTest.txt");
   static final String SECURE_URL = System.getProperty("url", "https://localhost:8448/WebServerComponentTest.txt");

   static final String KEY_STORE_PATH = WebServerComponentTest.class.getClassLoader().getResource("server-keystore.p12").getFile();

   static final String PEM_KEY_STORE_PATH = WebServerComponentTest.class.getClassLoader().getResource("server-keystore.pemcfg").getFile();

   static final String KEY_STORE_PASSWORD = "securepass";

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
   public void simpleServer() throws Exception {
      internalSimpleServer(false);
   }

   @Test
   public void simpleServerWithCustomizer() throws Exception {
      internalSimpleServer(true);
   }

   private void internalSimpleServer(boolean useCustomizer) throws Exception {
      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.uri = "http://localhost:0";
      WebServerDTO webServerDTO = new WebServerDTO();
      webServerDTO.setBindings(Collections.singletonList(bindingDTO));
      webServerDTO.path = "webapps";
      webServerDTO.webContentEnabled = true;
      if (useCustomizer) {
         webServerDTO.customizer = TestCustomizer.class.getName();
      }
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
      if (useCustomizer) {
         assertEquals(1, TestCustomizer.count);
      }
      assertEquals("12345", clientHandler.body.toString());
      assertNull(clientHandler.serverHeader);
      // Wait for the server to close the connection.
      ch.close();
      ch.eventLoop().shutdownNow();
      assertTrue(webServerComponent.isStarted());
      webServerComponent.stop(true);
      assertFalse(webServerComponent.isStarted());
   }

   @Test
   public void testCompressionEnabled() throws Exception {
      testCompression(true);
   }

   @Test
   public void testCompressionDisabled() throws Exception {
      testCompression(false);
   }

   private void testCompression(boolean compressionEnabled) throws Exception {
      final String encoding = "gzip";

      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.uri = "http://localhost:0";
      WebServerDTO webServerDTO = new WebServerDTO();
      webServerDTO.setBindings(Collections.singletonList(bindingDTO));
      webServerDTO.path = "webapps";
      webServerDTO.webContentEnabled = true;
      webServerDTO.compressionEnabled = compressionEnabled;
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

      // this file is different from the other tests because it has to be above a certain size in order to be compressed
      URI uri = new URI("http://localhost/WebServerComponentCompressionTest.txt");
      // Prepare the HTTP request.
      HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath());
      request.headers().set(HttpHeaderNames.HOST, "localhost");
      request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, encoding);

      // Send the HTTP request.
      ch.writeAndFlush(request);
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      String contentEncoding = clientHandler.headers.get(HttpHeaderNames.CONTENT_ENCODING);
      if (compressionEnabled) {
         assertEquals(encoding, contentEncoding);
      } else {
         assertNull(contentEncoding);
      }

      // Wait for the server to close the connection.
      ch.close();
      ch.eventLoop().shutdownNow();
      assertTrue(webServerComponent.isStarted());
      webServerComponent.stop(true);
      assertFalse(webServerComponent.isStarted());
   }

   @Test
   public void testThreadPool() throws Exception {
      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.uri = "http://localhost:0";
      WebServerDTO webServerDTO = new WebServerDTO();
      webServerDTO.setBindings(Collections.singletonList(bindingDTO));
      webServerDTO.path = "webapps";
      webServerDTO.webContentEnabled = true;
      webServerDTO.maxThreads = 75;
      webServerDTO.minThreads = 50;
      WebServerComponent webServerComponent = new WebServerComponent();
      assertFalse(webServerComponent.isStarted());
      webServerComponent.configure(webServerDTO, "./src/test/resources/", "./src/test/resources/");
      testedComponents.add(webServerComponent);
      webServerComponent.start();
      ThreadPool.SizedThreadPool jettyPool = (ThreadPool.SizedThreadPool) webServerComponent.getWebServer().getThreadPool();
      assertEquals((long) webServerDTO.minThreads, jettyPool.getMinThreads());
      assertEquals((long) webServerDTO.maxThreads, jettyPool.getMaxThreads());
      webServerComponent.stop(true);
      assertFalse(webServerComponent.isStarted());
   }

   @Test
   public void testComponentStopBehavior() throws Exception {
      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.uri = "http://localhost:0";
      WebServerDTO webServerDTO = new WebServerDTO();
      webServerDTO.setBindings(Collections.singletonList(bindingDTO));
      webServerDTO.path = "webapps";
      webServerDTO.webContentEnabled = true;
      WebServerComponent webServerComponent = new WebServerComponent();
      assertFalse(webServerComponent.isStarted());
      webServerComponent.configure(webServerDTO, "src/test/resources/", "src/test/resources/");
      webServerComponent.start();
      // Make the connection attempt.
      verifyConnection(webServerComponent.getPort());
      assertTrue(webServerComponent.isStarted());

      //usual stop won't actually stop it
      webServerComponent.stop();
      assertTrue(webServerComponent.isStarted());

      webServerComponent.stop(true);
      assertFalse(webServerComponent.isStarted());
   }

   @Test
   public void testComponentStopStartBehavior() throws Exception {
      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.uri = "http://localhost:0";
      WebServerDTO webServerDTO = new WebServerDTO();
      webServerDTO.setBindings(Collections.singletonList(bindingDTO));
      webServerDTO.path = "webapps";
      webServerDTO.webContentEnabled = true;
      WebServerComponent webServerComponent = new WebServerComponent();
      assertFalse(webServerComponent.isStarted());
      webServerComponent.configure(webServerDTO, "src/test/resources/", "src/test/resources/");
      webServerComponent.start();
      // Make the connection attempt.
      verifyConnection(webServerComponent.getPort());
      assertTrue(webServerComponent.isStarted());

      //usual stop won't actually stop it
      webServerComponent.stop();
      assertTrue(webServerComponent.isStarted());

      webServerComponent.stop(true);
      assertFalse(webServerComponent.isStarted());

      webServerComponent.start();
      assertTrue(webServerComponent.isStarted());

      verifyConnection(webServerComponent.getPort());

      webServerComponent.stop(true);
      assertFalse(webServerComponent.isStarted());
   }

   private WebServerComponent startSimpleSecureServer(Boolean sniHostCheck, Boolean sniRequired) throws Exception {
      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.setKeyStorePath(KEY_STORE_PATH);
      bindingDTO.setKeyStorePassword(KEY_STORE_PASSWORD);
      if (sniHostCheck != null) {
         bindingDTO.setSniHostCheck(sniHostCheck);
      }
      if (sniRequired != null) {
         bindingDTO.setSniRequired(sniRequired);
      }
      return startSimpleSecureServer(bindingDTO);
   }

   private WebServerComponent startSimpleSecureServer(BindingDTO bindingDTO) throws Exception {
      bindingDTO.setUri("https://localhost:0");
      if (System.getProperty("java.vendor").contains("IBM")) {
         //By default on IBM Java 8 JVM, org.eclipse.jetty.util.ssl.SslContextFactory doesn't include TLSv1.2
         // while it excludes all TLSv1 and TLSv1.1 cipher suites.
         bindingDTO.setIncludedTLSProtocols("TLSv1.2");
         // Remove excluded cipher suites matching the prefix `SSL` because the names of the IBM Java 8 JVM cipher suites
         // have the prefix `SSL` while the `DEFAULT_EXCLUDED_CIPHER_SUITES` of org.eclipse.jetty.util.ssl.SslContextFactory
         // includes "^SSL_.*$". So all IBM JVM cipher suites are excluded by SslContextFactory using the `DEFAULT_EXCLUDED_CIPHER_SUITES`.
         bindingDTO.setExcludedCipherSuites(Arrays.stream(new SslContextFactory.Server().getExcludeCipherSuites()).filter(s -> !Pattern.matches(s, "SSL_")).toArray(String[]::new));
      }
      WebServerDTO webServerDTO = new WebServerDTO();
      webServerDTO.setBindings(Collections.singletonList(bindingDTO));
      webServerDTO.path = "webapps";
      webServerDTO.webContentEnabled = true;
      webServerDTO.setScanPeriod(1);

      WebServerComponent webServerComponent = new WebServerComponent();
      assertFalse(webServerComponent.isStarted());
      webServerComponent.configure(webServerDTO, "src/test/resources/", "src/test/resources/");
      testedComponents.add(webServerComponent);
      webServerComponent.start();

      return webServerComponent;
   }

   @Test
   public void simpleSecureServer() throws Exception {
      WebServerComponent webServerComponent = startSimpleSecureServer(null, null);
      final int port = webServerComponent.getPort();
      // Make the connection attempt.

      SSLContext context = new SSLSupport()
         .setKeystorePath(KEY_STORE_PATH)
         .setKeystorePassword(KEY_STORE_PASSWORD)
         .setTruststorePath(KEY_STORE_PATH)
         .setTruststorePassword(KEY_STORE_PASSWORD)
         .createContext();

      SSLEngine engine = context.createSSLEngine();
      engine.setUseClientMode(true);
      engine.setWantClientAuth(true);
      if (System.getProperty("java.vendor").contains("IBM")) {
         //By default on IBM Java 8 JVM, SSLEngine doesn't enable TLSv1.2 while
         // org.eclipse.jetty.util.ssl.SslContextFactory excludes all TLSv1 and TLSv1.1 cipher suites.
         engine.setEnabledProtocols(new String[] {"TLSv1.2"});
      }
      final SslHandler sslHandler = new SslHandler(engine);

      CountDownLatch latch = new CountDownLatch(1);
      final ClientHandler clientHandler = new ClientHandler(latch);

      Channel ch = getSslChannel(port, sslHandler, clientHandler);

      URI uri = new URI(SECURE_URL);
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
      ch.eventLoop().shutdownNow();
      assertTrue(webServerComponent.isStarted());
      webServerComponent.stop(true);
      assertFalse(webServerComponent.isStarted());
   }

   @Test
   public void testHttp2() throws Exception {
      testWithHttp2Client(null);
   }

   @Test
   public void testHttp2Enabled() throws Exception {
      testWithHttp2Client(true);
   }

   @Test
   public void testHttp2Disabled() throws Exception {
      testWithHttp2Client(false);
   }

   private void testWithHttp2Client(Boolean http2) throws Exception {
      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.setKeyStorePath(KEY_STORE_PATH);
      bindingDTO.setKeyStorePassword(KEY_STORE_PASSWORD);
      bindingDTO.setHttp2(http2);
      WebServerComponent webServerComponent = startSimpleSecureServer(bindingDTO);
      final int port = webServerComponent.getPort();

      SSLContext context = new SSLSupport()
         .setKeystorePath(KEY_STORE_PATH)
         .setKeystorePassword(KEY_STORE_PASSWORD)
         .setTruststorePath(KEY_STORE_PATH)
         .setTruststorePassword(KEY_STORE_PASSWORD)
         .createContext();

      HttpClient client = HttpClient.newBuilder()
         .version(HTTP_2)
         .sslContext(context)
         .build();

      java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
         .uri(new URI("https://localhost:" + port + "/WebServerComponentTest.txt"))
         .GET()
         .build();

      java.net.http.HttpResponse<String> response = null;
      try {
         response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
      } finally {
         assertTrue(webServerComponent.isStarted());
         webServerComponent.stop(true);
         assertFalse(webServerComponent.isStarted());
      }

      assertNotNull(response);
      if (Boolean.FALSE.equals(http2)) {
         assertEquals(HTTP_1_1, response.version());
      } else {
         assertEquals(HTTP_2, response.version());
      }
      assertEquals(200, response.statusCode());
      assertEquals("12345", response.body());
   }

   @Test
   public void testStoreTypeConfigAndProviderRegistration() throws Exception {

      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.setKeyStorePath(PEM_KEY_STORE_PATH);
      bindingDTO.setKeyStoreType("PEMCFG");

      WebServerComponent webServerComponent = startSimpleSecureServer(bindingDTO);
      try {
         int port = webServerComponent.getPort(0);
         assertEquals(200, testSimpleSecureServer("localhost", port, null, null));
      } finally {
         webServerComponent.stop(true);
      }
   }

   @Test
   public void testSimpleSecureServerWithSniHostCheckEnabled() throws Exception {
      testSimpleSecureServerWithSniHostCheck(true);
   }

   @Test
   public void testSimpleSecureServerWithSniHostCheckDisabled() throws Exception {
      testSimpleSecureServerWithSniHostCheck(false);
   }

   @Test
   public void testSimpleSecureServerWithSniHostCheckDefault() throws Exception {
      testSimpleSecureServerWithSniHostCheck(null);
   }

   private void testSimpleSecureServerWithSniHostCheck(Boolean enabled) throws Exception {
      WebServerComponent webServerComponent = startSimpleSecureServer(enabled, null);
      try {
         int port = webServerComponent.getPort(0);
         assertEquals(200, testSimpleSecureServer("localhost", port, "localhost", null));
         assertEquals(200, testSimpleSecureServer("localhost", port, "127.0.0.1", null));
         assertEquals(enabled == null || enabled ? 400 : 200, testSimpleSecureServer("localhost", port, "artemis", null));
      } finally {
         webServerComponent.stop(true);
      }
   }

   @Test
   public void testSimpleSecureServerWithSniRequiredEnabled() throws Exception {
      testSimpleSecureServerWithSniRequired(true);
   }

   @Test
   public void testSimpleSecureServerWithSniRequiredDisabled() throws Exception {
      testSimpleSecureServerWithSniRequired(false);
   }

   @Test
   public void testSimpleSecureServerWithSniRequiredDefault() throws Exception {
      testSimpleSecureServerWithSniRequired(null);
   }

   private void testSimpleSecureServerWithSniRequired(Boolean enabled) throws Exception {
      WebServerComponent webServerComponent = startSimpleSecureServer(null, enabled);
      try {
         int port = webServerComponent.getPort(0);
         assertEquals(200, testSimpleSecureServer("localhost", port, null, "localhost"));
         assertEquals(200, testSimpleSecureServer("localhost", port, null, "127.0.0.1"));
         assertEquals(enabled != null && enabled ? 400 : 200, testSimpleSecureServer("localhost", port, null, null));
         assertEquals(enabled != null && enabled ? 400 : 200, testSimpleSecureServer("localhost", port, null, "artemis"));
      } finally {
         webServerComponent.stop(true);
      }
   }

   @Test
   public void testSSLAutoReload() throws Exception {
      testSSLAutoReload(false);
   }
   @Test
   public void testSSLAutoReloadWithSymbolicLinks() throws Exception {
      testSSLAutoReload(true);
   }

   public void testSSLAutoReload(boolean useSymbolicLinks) throws Exception {
      File serverFolder = new File(tempFolder, "server");
      File keyStoreFile = new File(serverFolder, "server-keystore.p12");

      assertTrue(serverFolder.mkdir());

      Files.copy(WebServerComponentTest.class.getClassLoader().getResourceAsStream("server-keystore.p12"),
         keyStoreFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      File storeFolder = new File(tempFolder, "store");
      assertTrue(storeFolder.mkdir());

      String keyStorePath;
      if (useSymbolicLinks) {
         keyStorePath = Files.createSymbolicLink(storeFolder.toPath().resolve(
             "store-keystore.p12"), keyStoreFile.toPath()).toString();
      } else {
         keyStorePath = keyStoreFile.getAbsolutePath();
      }

      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.setSslAutoReload(true);
      bindingDTO.setKeyStorePath(keyStorePath);
      bindingDTO.setKeyStorePassword(KEY_STORE_PASSWORD);
      WebServerComponent webServerComponent = startSimpleSecureServer(bindingDTO);

      try {
         int port = webServerComponent.getPort(0);
         AtomicReference<SSLSession> sslSessionReference = new AtomicReference<>();
         HostnameVerifier hostnameVerifier = (s, sslSession) -> {
            sslSessionReference.set(sslSession);
            return true;
         };

         // check server certificate contains Apache Artemis Server
         assertTrue(testSimpleSecureServer("localhost", port, "localhost", null, hostnameVerifier) == 200 &&
            sslSessionReference.get().getPeerCertificates()[0].toString().contains("CN=ActiveMQ Artemis Server,"));

         // check server certificate doesn't contain Apache Artemis Server
         assertFalse(testSimpleSecureServer("localhost", port, "localhost", null, hostnameVerifier) == 200 &&
            sslSessionReference.get().getPeerCertificates()[0].toString().contains("CN=ActiveMQ Artemis Other Server,"));

         // update server keystore
         Files.copy(WebServerComponentTest.class.getClassLoader().getResourceAsStream("other-server-keystore.p12"),
            keyStoreFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

         // check server certificate contains Apache Artemis Other Server
         Wait.assertTrue(() -> testSimpleSecureServer("localhost", port, "localhost", null, hostnameVerifier) == 200 &&
            sslSessionReference.get().getPeerCertificates()[0].toString().contains("CN=ActiveMQ Artemis Other Server,"));

         // check server certificate doesn't contain Apache Artemis Server
         assertFalse(testSimpleSecureServer("localhost", port, "localhost", null, hostnameVerifier) == 200 &&
            sslSessionReference.get().getPeerCertificates()[0].toString().contains("CN=ActiveMQ Artemis Server,"));
      } finally {
         webServerComponent.stop(true);
      }
   }

   @Test
   public void testSSLAutoReloadPemConfigSources() throws Exception {
      testSSLAutoReloadPemConfigSources(false);
   }

   @Test
   public void testSSLAutoReloadPemConfigSourcesWithSymbolicLinks() throws Exception {
      testSSLAutoReloadPemConfigSources(true);
   }

   private void testSSLAutoReloadPemConfigSources(boolean useSymbolicLinks) throws Exception {
      File serverFolder = new File(tempFolder, "server");
      File serverKeyFile = new File(serverFolder, "server-key.pem");
      File serverCertFile = new File(serverFolder, "server-cert.pem");
      File serverPemConfigFile = new File(serverFolder, "server-pem-config.properties");

      assertTrue(serverFolder.mkdir());

      Files.copy(WebServerComponentTest.class.getClassLoader().getResourceAsStream("server-key.pem"),
         serverKeyFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      Files.copy(WebServerComponentTest.class.getClassLoader().getResourceAsStream("server-cert.pem"),
         serverCertFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      File storeFolder = new File(tempFolder, "store");
      assertTrue(storeFolder.mkdir());

      String sourceKey;
      String sourceCert;
      if (useSymbolicLinks) {
         sourceKey = Files.createSymbolicLink(storeFolder.toPath().resolve(
             "store-key.pem"), serverKeyFile.toPath()).toString();
         sourceCert = Files.createSymbolicLink(storeFolder.toPath().resolve(
             "store-cert.pem"), serverCertFile.toPath()).toString();
      } else {
         sourceKey = serverKeyFile.getAbsolutePath();
         sourceCert = serverCertFile.getAbsolutePath();
      }

      Files.write(serverPemConfigFile.toPath(), Arrays.asList(new String[]{
         "source.key=" + sourceKey,
         "source.cert=" + sourceCert
      }));

      String keyStorePath;
      if (useSymbolicLinks) {
         keyStorePath = Files.createSymbolicLink(storeFolder.toPath().resolve(
             "store-pem-config.properties"), serverPemConfigFile.toPath()).toString();
      } else {
         keyStorePath = serverPemConfigFile.getAbsolutePath();
      }

      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.setSslAutoReload(true);
      bindingDTO.setKeyStorePath(keyStorePath);
      bindingDTO.setKeyStoreType(PemConfigUtil.PEMCFG_STORE_TYPE);

      WebServerComponent webServerComponent = startSimpleSecureServer(bindingDTO);

      try {
         int port = webServerComponent.getPort(0);
         AtomicReference<SSLSession> sslSessionReference = new AtomicReference<>();
         HostnameVerifier hostnameVerifier = (s, sslSession) -> {
            sslSessionReference.set(sslSession);
            return true;
         };

         // check server certificate contains Apache Artemis Server
         assertTrue(testSimpleSecureServer("localhost", port, "localhost", null, hostnameVerifier) == 200 &&
            sslSessionReference.get().getPeerCertificates()[0].toString().contains("DNSName: server.artemis.activemq"));

         // check server certificate doesn't contain Apache Artemis Server
         assertFalse(testSimpleSecureServer("localhost", port, "localhost", null, hostnameVerifier) == 200 &&
            sslSessionReference.get().getPeerCertificates()[0].toString().contains("DNSName: other-server.artemis.activemq"));

         // update server PEM config sources
         Files.copy(WebServerComponentTest.class.getClassLoader().getResourceAsStream("other-server-key.pem"),
            serverKeyFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

         Files.copy(WebServerComponentTest.class.getClassLoader().getResourceAsStream("other-server-cert.pem"),
            serverCertFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

         // check server certificate contains Apache Artemis Other Server
         Wait.assertTrue(() -> testSimpleSecureServer("localhost", port, "localhost", null, hostnameVerifier) == 200 &&
            sslSessionReference.get().getPeerCertificates()[0].toString().contains("DNSName: other-server.artemis.activemq"));

         // check server certificate doesn't contain Apache Artemis Server
         assertFalse(testSimpleSecureServer("localhost", port, "localhost", null, hostnameVerifier) == 200 &&
            sslSessionReference.get().getPeerCertificates()[0].toString().contains("DNSName: server.artemis.activemq"));
      } finally {
         webServerComponent.stop(true);
      }
   }

   private int testSimpleSecureServer(String webServerHostname, int webServerPort, String requestHostname, String sniHostname) throws Exception {
      return testSimpleSecureServer(webServerHostname, webServerPort, requestHostname, sniHostname, null);
   }

   private int testSimpleSecureServer(String webServerHostname, int webServerPort, String requestHostname, String sniHostname, HostnameVerifier hostnameVerifier) throws Exception {
      HttpGet request = new HttpGet("https://" + (Objects.requireNonNullElse(requestHostname, webServerHostname)) + ":" + webServerPort + "/WebServerComponentTest.txt");

      HttpHost webServerHost = HttpHost.create("https://" + webServerHostname + ":" + webServerPort);
      SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, (certificate, authType) -> true).build();

      if (hostnameVerifier == null) {
         hostnameVerifier = new NoopHostnameVerifier();
      }

      SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier) {
         @Override
         protected void prepareSocket(SSLSocket socket) throws IOException {
            super.prepareSocket(socket);

            if (sniHostname != null) {
               SSLParameters sslParameters = socket.getSSLParameters();
               sslParameters.setServerNames(Collections.singletonList(new SNIHostName(sniHostname)));
               socket.setSSLParameters(sslParameters);
            }
         }
      };

      HttpRoutePlanner httpRoutePlanner = new DefaultRoutePlanner(null) {
         @Override
         public HttpRoute determineRoute(HttpHost host,
            org.apache.http.HttpRequest request,
            HttpContext context) throws HttpException {
            HttpRoute baseHttpRoute = super.determineRoute(host, request, context);
            return new HttpRoute(webServerHost, baseHttpRoute.getLocalAddress(), baseHttpRoute.isSecure());
         }
      };

      try (CloseableHttpClient client = HttpClientBuilder.create().setRoutePlanner(httpRoutePlanner).
         setSSLSocketFactory(sslConnectionSocketFactory).setSSLHostnameVerifier(new NoopHostnameVerifier()).build()) {
         try (CloseableHttpResponse response = client.execute(request)) {
            return response.getStatusLine().getStatusCode();
         }
      }
   }

   @Test
   public void simpleSecureServerWithClientAuth() throws Exception {
      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.uri = "https://localhost:0";
      bindingDTO.setKeyStorePath(KEY_STORE_PATH);
      bindingDTO.setKeyStorePassword(KEY_STORE_PASSWORD);
      bindingDTO.setClientAuth(true);
      bindingDTO.setTrustStorePath(KEY_STORE_PATH);
      bindingDTO.setTrustStorePassword(KEY_STORE_PASSWORD);
      if (System.getProperty("java.vendor").contains("IBM")) {
         //By default on IBM Java 8 JVM, org.eclipse.jetty.util.ssl.SslContextFactory doesn't include TLSv1.2
         // while it excludes all TLSv1 and TLSv1.1 cipher suites.
         bindingDTO.setIncludedTLSProtocols("TLSv1.2");
         // Remove excluded cipher suites matching the prefix `SSL` because the names of the IBM Java 8 JVM cipher suites
         // have the prefix `SSL` while the `DEFAULT_EXCLUDED_CIPHER_SUITES` of org.eclipse.jetty.util.ssl.SslContextFactory
         // includes "^SSL_.*$". So all IBM JVM cipher suites are excluded by SslContextFactory using the `DEFAULT_EXCLUDED_CIPHER_SUITES`.
         bindingDTO.setExcludedCipherSuites(Arrays.stream(new SslContextFactory.Server().getExcludeCipherSuites()).filter(s -> !Pattern.matches(s, "SSL_")).toArray(String[]::new));
      }
      WebServerDTO webServerDTO = new WebServerDTO();
      webServerDTO.setBindings(Collections.singletonList(bindingDTO));
      webServerDTO.path = "webapps";
      webServerDTO.webContentEnabled = true;

      WebServerComponent webServerComponent = new WebServerComponent();
      assertFalse(webServerComponent.isStarted());
      webServerComponent.configure(webServerDTO, "src/test/resources/", "src/test/resources/");
      testedComponents.add(webServerComponent);
      webServerComponent.start();
      final int port = webServerComponent.getPort();
      // Make the connection attempt.

      SSLContext context = new SSLSupport()
         .setKeystorePath(bindingDTO.keyStorePath)
         .setKeystorePassword(bindingDTO.getKeyStorePassword())
         .setTruststorePath(bindingDTO.trustStorePath)
         .setTruststorePassword(bindingDTO.getTrustStorePassword())
         .createContext();

      SSLEngine engine = context.createSSLEngine();
      engine.setUseClientMode(true);
      engine.setWantClientAuth(true);
      if (System.getProperty("java.vendor").contains("IBM")) {
         //By default on IBM Java 8 JVM, SSLEngine doesn't enable TLSv1.2 while
         // org.eclipse.jetty.util.ssl.SslContextFactory excludes all TLSv1 and TLSv1.1 cipher suites.
         engine.setEnabledProtocols(new String[] {"TLSv1.2"});
      }
      final SslHandler sslHandler = new SslHandler(engine);

      CountDownLatch latch = new CountDownLatch(1);
      final ClientHandler clientHandler = new ClientHandler(latch);
      Channel ch = getSslChannel(port, sslHandler, clientHandler);

      URI uri = new URI(SECURE_URL);
      // Prepare the HTTP request.
      HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath());
      request.headers().set(HttpHeaderNames.HOST, "localhost");

      // Send the HTTP request.
      ch.writeAndFlush(request);
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertEquals("12345", clientHandler.body.toString());
      // Wait for the server to close the connection.
      ch.close();
      ch.eventLoop().shutdownNow();
      assertTrue(webServerComponent.isStarted());
      webServerComponent.stop(true);
      assertFalse(webServerComponent.isStarted());
   }

   @Test
   public void testDefaultMaskPasswords() throws Exception {
      File bootstrap = new File("./target/test-classes/bootstrap_web.xml");
      File brokerHome = new File("./target");
      XmlBrokerFactoryHandler xmlHandler = new XmlBrokerFactoryHandler();
      BrokerDTO broker = xmlHandler.createBroker(bootstrap.toURI(), brokerHome.getAbsolutePath(), brokerHome.getAbsolutePath(), brokerHome.toURI());
      assertNotNull(broker.web);
      assertNull(broker.web.getDefaultBinding().passwordCodec);
   }

   @Test
   public void testMaskPasswords() throws Exception {
      final String keyPassword = "keypass";
      final String trustPassword = "trustpass";
      File bootstrap = new File("./target/test-classes/bootstrap_secure_web.xml");
      File brokerHome = new File("./target");
      XmlBrokerFactoryHandler xmlHandler = new XmlBrokerFactoryHandler();
      BrokerDTO broker = xmlHandler.createBroker(bootstrap.toURI(), brokerHome.getAbsolutePath(), brokerHome.getAbsolutePath(), brokerHome.toURI());
      assertNotNull(broker.web);
      assertEquals(keyPassword, broker.web.getDefaultBinding().getKeyStorePassword());
      assertEquals(trustPassword, broker.web.getDefaultBinding().getTrustStorePassword());
   }

   @Test
   public void testMaskPasswordCodec() throws Exception {
      final String keyPassword = "keypass";
      final String trustPassword = "trustpass";
      File bootstrap = new File("./target/test-classes/bootstrap_web_codec.xml");
      File brokerHome = new File("./target");
      XmlBrokerFactoryHandler xmlHandler = new XmlBrokerFactoryHandler();
      BrokerDTO broker = xmlHandler.createBroker(bootstrap.toURI(), brokerHome.getAbsolutePath(), brokerHome.getAbsolutePath(), brokerHome.toURI());
      assertNotNull(broker.web);
      assertNotNull(broker.web.getDefaultBinding().passwordCodec, "password codec not picked up!");

      assertEquals(keyPassword, broker.web.getDefaultBinding().getKeyStorePassword());
      assertEquals(trustPassword, broker.web.getDefaultBinding().getTrustStorePassword());
   }

   @Test
   public void testOldConfigurationStyle() throws Exception {
      final String keyPassword = "keypass";
      final String trustPassword = "trustpass";

      File bootstrap = new File("./target/test-classes/bootstrap_web_old_config.xml");
      File brokerHome = new File("./target");
      XmlBrokerFactoryHandler xmlHandler = new XmlBrokerFactoryHandler();
      BrokerDTO broker = xmlHandler.createBroker(bootstrap.toURI(), brokerHome.getAbsolutePath(), brokerHome.getAbsolutePath(), brokerHome.toURI());
      assertNotNull(broker.web);
      assertNotNull(broker.web.getDefaultBinding().passwordCodec, "password codec not picked up!");

      assertEquals("http://localhost:8161", broker.web.getDefaultBinding().uri);
      assertEquals(keyPassword, broker.web.getDefaultBinding().getKeyStorePassword());
      assertEquals(trustPassword, broker.web.getDefaultBinding().getTrustStorePassword());
   }

   @Test
   public void testWebServerCleanupAfterStop() throws Exception {
      final String warName = "simple-app.war";
      createTestWar(warName);
      final String url = "simple-app/";

      WebServerDTO webServerDTO = createDefaultWebServerDTO(warName, url);

      WebServerComponent webServerComponent = new WebServerComponent();
      assertFalse(webServerComponent.isStarted());
      testedComponents.add(webServerComponent);
      webServerComponent.configure(webServerDTO, "./target", "./target");
      WebAppContext ctxt = WebServerComponentTestAccessor.createWebAppContext(webServerComponent, url, warName, Paths.get(".").resolve("target").toAbsolutePath(), null);
      webServerComponent.getWebContextData().add(new Pair(ctxt, null));

      WebInfConfiguration cfg = new WebInfConfiguration();
      cfg.resolveTempDirectory(ctxt);

      webServerComponent.start();
      assertTrue(webServerComponent.isStarted());
      File warTempDir = new File(ctxt.getTempDirectory().getAbsolutePath());
      assertTrue(warTempDir.exists(), warTempDir + " doesn't exist!");
      webServerComponent.stop(true);
      assertFalse(webServerComponent.isStarted());

      assertFalse(warTempDir.exists(), warTempDir + " exists!");
   }

   @Test
   public void testWebServerCleanupBeforeStart() throws Exception {
      final String warName = "simple-app.war";
      createTestWar(warName);
      final String url = "simple-app/";

      WebServerDTO webServerDTO = createDefaultWebServerDTO(warName, url);

      WebServerComponent webServerComponent = new WebServerComponent();
      assertFalse(webServerComponent.isStarted());
      testedComponents.add(webServerComponent);
      webServerComponent.configure(webServerDTO, "./target", "./target");
      WebAppContext ctxt = WebServerComponentTestAccessor.createWebAppContext(webServerComponent, url, warName, Paths.get(".").resolve("target").toAbsolutePath(), null);
      webServerComponent.getWebContextData().add(new Pair(ctxt, null));

      if (!Files.exists(Paths.get(ctxt.getTempDirectory().getAbsolutePath()))) {
         Files.createDirectory(Paths.get(ctxt.getTempDirectory().getAbsolutePath()));
      }
      long originalLastModified = Files.getLastModifiedTime(Paths.get(ctxt.getTempDirectory().getAbsolutePath())).toMillis();
      Thread.sleep(5);
      webServerComponent.start();
      assertTrue(webServerComponent.isStarted());
      assertNotEquals(originalLastModified, Files.getLastModifiedTime(Paths.get(ctxt.getTempDirectory().getAbsolutePath())).toMillis());
      webServerComponent.stop(true);
      assertFalse(webServerComponent.isStarted());
      assertFalse(Files.exists(Paths.get(ctxt.getTempDirectory().getAbsolutePath())), ctxt.getTempDirectory().getAbsolutePath() + " exists!");
   }

   @Test
   public void testServerDeterministicWebappName() throws Exception {
      final String warName = "simple-app.war";
      createTestWar(warName);
      final String url = "simple-app/";

      WebServerDTO webServerDTO = createDefaultWebServerDTO(warName, url);
      WebServerComponent webServerComponent = new WebServerComponent();
      webServerComponent.configure(webServerDTO, "./target", "./target");
      WebAppContext ctxt = WebServerComponentTestAccessor.createWebAppContext(webServerComponent, url, warName, Paths.get(".").resolve("target").toAbsolutePath(), null);
      testedComponents.add(webServerComponent);

      assertFalse(webServerComponent.isStarted());
      webServerComponent.start();

      File tmpDir = ctxt.getTempDirectory();
      assertEquals(1, tmpDir.getParentFile().listFiles().length);
      assertEquals(warName, tmpDir.getName());
      assertTrue(webServerComponent.isStarted());

      webServerComponent.stop(true);
      assertFalse(webServerComponent.isStarted());
   }

   @Test
   public void testIsolationOfSessions() throws Exception {
      BindingDTO bindingDTO1 = new BindingDTO();
      bindingDTO1.uri = "http://localhost:0";
      bindingDTO1.apps = new ArrayList<>();
      bindingDTO1.apps.add(createSimpleAppDTO("app1"));
      bindingDTO1.apps.add(createSimpleAppDTO("app2"));

      BindingDTO bindingDTO2 = new BindingDTO();
      bindingDTO2.uri = "http://localhost:0";
      bindingDTO2.apps = new ArrayList<>();
      bindingDTO2.apps.add(createSimpleAppDTO("app1"));
      bindingDTO2.apps.add(createSimpleAppDTO("app2"));

      WebServerDTO webServerDTO = new WebServerDTO();
      webServerDTO.setBindings(List.of(bindingDTO1, bindingDTO2));
      webServerDTO.path = "";

      WebServerComponent webServerComponent = new WebServerComponent();
      assertFalse(webServerComponent.isStarted());
      testedComponents.add(webServerComponent);
      webServerComponent.configure(webServerDTO, "./target", "./target");
      webServerComponent.start();

      try {
         List<Pair<WebAppContext, String>> webContextData = webServerComponent.getWebContextData();
         assertEquals(4, webContextData.size());

         Set<String> sessionCookieNames = new HashSet<>();
         for (Pair<WebAppContext, String> contextData : webContextData) {
            WebAppContext webContext = contextData.getA();

            String sessionCookieName = webContext.getSessionHandler().getSessionCookieConfig().getName();
            String sessionPath = webContext.getSessionHandler().getSessionPath();
            assertTrue(sessionCookieNames.add(sessionCookieName + sessionPath),
                  "A session with the name cookie " + sessionCookieName + " and the path " + sessionPath + " already exists!");
         }
      } finally {
         webServerComponent.stop(true);
      }
   }

   @Test
   public void testDefaultRootRedirect() throws Exception {
      testRootRedirect(null, 404, null);
   }

   @Test
   public void testCustomRootRedirect() throws Exception {
      testRootRedirect("test-root-redirect", 302, "test-root-redirect");
   }

   public void testRootRedirect(String rootRedirectLocation, int expectedResponseCode, String expectedResponseLocation) throws Exception {
      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.uri = "http://localhost:0";
      WebServerDTO webServerDTO = new WebServerDTO();
      webServerDTO.setBindings(Collections.singletonList(bindingDTO));
      webServerDTO.path = "";
      webServerDTO.rootRedirectLocation = rootRedirectLocation;

      WebServerComponent webServerComponent = new WebServerComponent();
      webServerComponent.configure(webServerDTO, null, null);
      webServerComponent.start();
      try {
         int port = webServerComponent.getPort(0);
         java.net.URL url = new URL("http://localhost:" + port);
         HttpURLConnection conn = (HttpURLConnection) url.openConnection();
         conn.setInstanceFollowRedirects(false);
         try {
            assertEquals(expectedResponseCode, conn.getResponseCode());
            if (expectedResponseLocation != null) {
               assertTrue(conn.getHeaderField("Location").endsWith(webServerDTO.rootRedirectLocation));
            }
         } finally {
            conn.disconnect();
         }
      } finally {
         webServerComponent.stop(true);
      }
   }

   private static WebServerDTO createDefaultWebServerDTO(String warName, String url) {
      AppDTO app = new AppDTO();
      app.url = url;
      app.war = warName;
      BindingDTO bindingDTO = new BindingDTO();
      bindingDTO.uri = "http://localhost:0";
      bindingDTO.apps = new ArrayList<>();
      bindingDTO.apps.add(app);
      WebServerDTO webServerDTO = new WebServerDTO();
      webServerDTO.setBindings(Collections.singletonList(bindingDTO));
      webServerDTO.path = "";
      return webServerDTO;
   }

   private AppDTO createSimpleAppDTO(String name) throws Exception {
      final String warName = name + ".war";
      createTestWar(warName);

      AppDTO app = new AppDTO();
      app.url = name + "/";
      app.war = warName;
      return app;
   }

   private void createTestWar(String warName) throws Exception {
      File warFile = new File("target", warName);
      File srcFile = new File("src/test/webapp");
      createJarFile(srcFile, warFile);
   }

   private void createJarFile(File srcFile, File jarFile) throws IOException {
      Manifest manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
      try (JarOutputStream target = new JarOutputStream(new FileOutputStream(jarFile), manifest)) {
         addFile(srcFile, target, "src/test/webapp");
      }
   }

   private void addFile(File source, JarOutputStream target, String nameBase) throws IOException {
      if (source.isDirectory()) {
         String name = source.getPath().replace("\\", "/");
         if (!name.isEmpty()) {
            name = name.substring(nameBase.length());
            if (!name.endsWith("/")) {
               name += "/";
            }
            JarEntry entry = new JarEntry(name);
            entry.setTime(source.lastModified());
            target.putNextEntry(entry);
            target.closeEntry();
         }
         for (File nestedFile: source.listFiles()) {
            addFile(nestedFile, target, nameBase);
         }
         return;
      }

      String name = source.getPath().replace("\\", "/");
      name = name.substring(nameBase.length());
      JarEntry entry = new JarEntry(name);
      entry.setTime(source.lastModified());
      target.putNextEntry(entry);
      try (BufferedInputStream input  = new BufferedInputStream(new FileInputStream(source))) {
         byte[] buffer = new byte[1024];
         while (true) {
            int count = input.read(buffer);
            if (count == -1)
               break;
            target.write(buffer, 0, count);
         }
         target.closeEntry();
      }
   }

   private void createGarbagesInDir(File tempDirectory, List<File> garbage) throws IOException {
      if (!tempDirectory.exists()) {
         tempDirectory.mkdirs();
      }
      createRandomJettyFiles(tempDirectory, 10, garbage);
   }

   private void createRandomJettyFiles(File dir, int num, List<File> collector) throws IOException {
      for (int i = 0; i < num; i++) {
         String randomName = "jetty-" + UUID.randomUUID().toString();
         File file = new File(dir, randomName);
         if (i % 2 == 0) {
            //create a dir
            file.mkdir();
         } else {
            //normal file
            file.createNewFile();
         }
         collector.add(file);
      }
   }

   private Channel getChannel(int port, ClientHandler clientHandler) throws InterruptedException {
      EventLoopGroup group = new NioEventLoopGroup();
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

   private Channel getSslChannel(int port, SslHandler sslHandler, ClientHandler clientHandler) throws InterruptedException {
      EventLoopGroup group = new NioEventLoopGroup();
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer() {
         @Override
         protected void initChannel(Channel ch) throws Exception {
            ch.pipeline().addLast(sslHandler);
            ch.pipeline().addLast(new HttpClientCodec());
            ch.pipeline().addLast(clientHandler);
         }
      });
      return bootstrap.connect("localhost", port).sync().channel();
   }

   private void verifyConnection(int port) throws InterruptedException, URISyntaxException {
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
      // Wait for the server to close the connection.
      ch.close();
      ch.eventLoop().shutdownNow();
   }

   class ClientHandler extends SimpleChannelInboundHandler<HttpObject> {

      private CountDownLatch latch;
      private StringBuilder body = new StringBuilder();
      private String serverHeader;
      private HttpHeaders headers;

      ClientHandler(CountDownLatch latch) {
         this.latch = latch;
      }

      @Override
      public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
         if (msg instanceof HttpResponse response) {
            headers = response.headers();
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
