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
package org.apache.activemq.artemis.core.remoting.impl.netty;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import io.netty.util.AttributeKey;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle;
import org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQClientProtocolManager;
import org.apache.activemq.artemis.core.remoting.impl.ssl.SSLSupport;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.spi.core.remoting.AbstractConnector;
import org.apache.activemq.artemis.spi.core.remoting.BaseConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler;
import org.apache.activemq.artemis.spi.core.remoting.ClientConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ssl.OpenSSLContextFactory;
import org.apache.activemq.artemis.spi.core.remoting.ssl.OpenSSLContextFactoryProvider;
import org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextConfig;
import org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextFactoryProvider;
import org.apache.activemq.artemis.utils.ConfigurationHelper;
import org.apache.activemq.artemis.utils.FutureLatch;
import org.apache.activemq.artemis.utils.IPV6Util;
import org.jboss.logging.Logger;

import static org.apache.activemq.artemis.utils.Base64.encodeBytes;

public class NettyConnector extends AbstractConnector {

   public static String NIO_CONNECTOR_TYPE = "NIO";
   public static String EPOLL_CONNECTOR_TYPE = "EPOLL";
   public static String KQUEUE_CONNECTOR_TYPE = "KQUEUE";

   private static final Logger logger = Logger.getLogger(NettyConnector.class);

   public static final String JAVAX_KEYSTORE_PATH_PROP_NAME = "javax.net.ssl.keyStore";
   public static final String JAVAX_KEYSTORE_PASSWORD_PROP_NAME = "javax.net.ssl.keyStorePassword";
   public static final String JAVAX_KEYSTORE_TYPE_PROP_NAME = "javax.net.ssl.keyStoreType";
   public static final String JAVAX_KEYSTORE_PROVIDER_PROP_NAME = "javax.net.ssl.keyStoreProvider";
   public static final String JAVAX_TRUSTSTORE_PATH_PROP_NAME = "javax.net.ssl.trustStore";
   public static final String JAVAX_TRUSTSTORE_PASSWORD_PROP_NAME = "javax.net.ssl.trustStorePassword";
   public static final String JAVAX_TRUSTSTORE_TYPE_PROP_NAME = "javax.net.ssl.trustStoreType";
   public static final String JAVAX_TRUSTSTORE_PROVIDER_PROP_NAME = "javax.net.ssl.trustStoreProvider";
   public static final String ACTIVEMQ_KEYSTORE_PROVIDER_PROP_NAME = "org.apache.activemq.ssl.keyStoreProvider";
   public static final String ACTIVEMQ_KEYSTORE_TYPE_PROP_NAME = "org.apache.activemq.ssl.keyStoreType";
   public static final String ACTIVEMQ_KEYSTORE_PATH_PROP_NAME = "org.apache.activemq.ssl.keyStore";
   public static final String ACTIVEMQ_KEYSTORE_PASSWORD_PROP_NAME = "org.apache.activemq.ssl.keyStorePassword";
   public static final String ACTIVEMQ_TRUSTSTORE_PROVIDER_PROP_NAME = "org.apache.activemq.ssl.trustStoreProvider";
   public static final String ACTIVEMQ_TRUSTSTORE_TYPE_PROP_NAME = "org.apache.activemq.ssl.trustStoreType";
   public static final String ACTIVEMQ_TRUSTSTORE_PATH_PROP_NAME = "org.apache.activemq.ssl.trustStore";
   public static final String ACTIVEMQ_TRUSTSTORE_PASSWORD_PROP_NAME = "org.apache.activemq.ssl.trustStorePassword";

   // Constants for HTTP upgrade
   // These constants are exposed publicly as they are used on the server-side to fetch
   // headers from the HTTP request, compute some values and fill the HTTP response
   public static final String MAGIC_NUMBER = "CF70DEB8-70F9-4FBA-8B4F-DFC3E723B4CD";
   public static final String SEC_ACTIVEMQ_REMOTING_KEY = "Sec-ActiveMQRemoting-Key";
   public static final String SEC_ACTIVEMQ_REMOTING_ACCEPT = "Sec-ActiveMQRemoting-Accept";
   public static final String ACTIVEMQ_REMOTING = "activemq-remoting";

   private static final AttributeKey<String> REMOTING_KEY = AttributeKey.valueOf(SEC_ACTIVEMQ_REMOTING_KEY);

   // Default Configuration
   public static final Map<String, Object> DEFAULT_CONFIG;

   static {
      // Disable resource leak detection for performance reasons by default
      ResourceLeakDetector.setLevel(Level.DISABLED);

      // Set default Configuration
      Map<String, Object> config = new HashMap<>();
      config.put(TransportConstants.HOST_PROP_NAME, TransportConstants.DEFAULT_HOST);
      config.put(TransportConstants.PORT_PROP_NAME, TransportConstants.DEFAULT_PORT);
      DEFAULT_CONFIG = Collections.unmodifiableMap(config);
   }


   private final boolean serverConnection;

   private Class<? extends Channel> channelClazz;

   private Bootstrap bootstrap;

   private ChannelGroup channelGroup;

   private final BufferHandler handler;

   private final BaseConnectionLifeCycleListener<?> listener;

   private boolean sslEnabled = TransportConstants.DEFAULT_SSL_ENABLED;

   private boolean httpEnabled;

   private long httpMaxClientIdleTime;

   private long httpClientIdleScanPeriod;

   private boolean httpRequiresSessionId;

   // if true, after the connection, the connector will send
   // a HTTP GET request (+ Upgrade: activemq-remoting) that
   // will be handled by the server's http server.
   private boolean httpUpgradeEnabled;

   private boolean proxyEnabled;

   private String proxyHost;

   private int proxyPort;

   private SocksVersion proxyVersion;

   private String proxyUsername;

   private String proxyPassword;

   private boolean proxyRemoteDNS;

   private boolean useServlet;

   private String host;

   private int port;

   private String localAddress;

   private int localPort;

   private String keyStoreProvider;

   private String keyStoreType;

   private String keyStorePath;

   private String keyStorePassword;

   private String trustStoreProvider;

   private String trustStoreType;

   private String trustStorePath;

   private String trustStorePassword;

   private String crlPath;

   private String enabledCipherSuites;

   private String enabledProtocols;

   private String sslProvider;

   private String trustManagerFactoryPlugin;

   private boolean verifyHost;

   private boolean trustAll;

   private boolean forceSSLParameters;

   private String sniHost;

   private boolean useDefaultSslContext;

   private boolean tcpNoDelay;

   private int tcpSendBufferSize;

   private int tcpReceiveBufferSize;

   private final int writeBufferLowWaterMark;

   private final int writeBufferHighWaterMark;

   private long batchDelay;

   private ConcurrentMap<Object, Connection> connections = new ConcurrentHashMap<>();

   private String servletPath;

   private boolean useEpoll;

   private boolean useKQueue;

   private int remotingThreads;

   private boolean useGlobalWorkerPool;

   private ScheduledExecutorService scheduledThreadPool;

   private Executor closeExecutor;

   private BatchFlusher flusher;

   private ScheduledFuture<?> batchFlusherFuture;

   private EventLoopGroup group;

   private int connectTimeoutMillis;

   private final ClientProtocolManager protocolManager;

   public NettyConnector(final Map<String, Object> configuration,
                         final BufferHandler handler,
                         final BaseConnectionLifeCycleListener<?> listener,
                         final Executor closeExecutor,
                         final Executor threadPool,
                         final ScheduledExecutorService scheduledThreadPool) {
      this(configuration, handler, listener, closeExecutor, threadPool, scheduledThreadPool, new ActiveMQClientProtocolManager());
   }

   public NettyConnector(final Map<String, Object> configuration,
                         final BufferHandler handler,
                         final BaseConnectionLifeCycleListener<?> listener,
                         final Executor closeExecutor,
                         final Executor threadPool,
                         final ScheduledExecutorService scheduledThreadPool,
                         final ClientProtocolManager protocolManager) {
      this(configuration, handler, listener, closeExecutor, threadPool, scheduledThreadPool, protocolManager, false);
   }
   public NettyConnector(final Map<String, Object> configuration,
                         final BufferHandler handler,
                         final BaseConnectionLifeCycleListener<?> listener,
                         final Executor closeExecutor,
                         final Executor threadPool,
                         final ScheduledExecutorService scheduledThreadPool,
                         final ClientProtocolManager protocolManager,
                         final boolean serverConnection) {
      super(configuration);

      this.serverConnection = serverConnection;

      this.protocolManager = protocolManager;

      if (listener == null) {
         throw ActiveMQClientMessageBundle.BUNDLE.nullListener();
      }

      if (!serverConnection && handler == null) {
         throw ActiveMQClientMessageBundle.BUNDLE.nullHandler();
      }

      this.listener = listener;

      this.handler = handler;

      sslEnabled = ConfigurationHelper.getBooleanProperty(TransportConstants.SSL_ENABLED_PROP_NAME, TransportConstants.DEFAULT_SSL_ENABLED, configuration);
      httpEnabled = ConfigurationHelper.getBooleanProperty(TransportConstants.HTTP_ENABLED_PROP_NAME, TransportConstants.DEFAULT_HTTP_ENABLED, configuration);
      servletPath = ConfigurationHelper.getStringProperty(TransportConstants.SERVLET_PATH, TransportConstants.DEFAULT_SERVLET_PATH, configuration);
      if (httpEnabled) {
         httpMaxClientIdleTime = ConfigurationHelper.getLongProperty(TransportConstants.HTTP_CLIENT_IDLE_PROP_NAME, TransportConstants.DEFAULT_HTTP_CLIENT_IDLE_TIME, configuration);
         httpClientIdleScanPeriod = ConfigurationHelper.getLongProperty(TransportConstants.HTTP_CLIENT_IDLE_SCAN_PERIOD, TransportConstants.DEFAULT_HTTP_CLIENT_SCAN_PERIOD, configuration);
         httpRequiresSessionId = ConfigurationHelper.getBooleanProperty(TransportConstants.HTTP_REQUIRES_SESSION_ID, TransportConstants.DEFAULT_HTTP_REQUIRES_SESSION_ID, configuration);
      } else {
         httpMaxClientIdleTime = 0;
         httpClientIdleScanPeriod = -1;
         httpRequiresSessionId = false;
      }

      httpUpgradeEnabled = ConfigurationHelper.getBooleanProperty(TransportConstants.HTTP_UPGRADE_ENABLED_PROP_NAME, TransportConstants.DEFAULT_HTTP_UPGRADE_ENABLED, configuration);

      proxyEnabled = ConfigurationHelper.getBooleanProperty(TransportConstants.PROXY_ENABLED_PROP_NAME, TransportConstants.DEFAULT_PROXY_ENABLED, configuration);
      if (proxyEnabled) {
         proxyHost = ConfigurationHelper.getStringProperty(TransportConstants.PROXY_HOST_PROP_NAME, TransportConstants.DEFAULT_PROXY_HOST, configuration);
         proxyPort = ConfigurationHelper.getIntProperty(TransportConstants.PROXY_PORT_PROP_NAME, TransportConstants.DEFAULT_PROXY_PORT, configuration);

         int socksVersionNumber = ConfigurationHelper.getIntProperty(TransportConstants.PROXY_VERSION_PROP_NAME, TransportConstants.DEFAULT_PROXY_VERSION, configuration);
         proxyVersion = SocksVersion.valueOf((byte) socksVersionNumber);

         proxyUsername = ConfigurationHelper.getStringProperty(TransportConstants.PROXY_USERNAME_PROP_NAME, TransportConstants.DEFAULT_PROXY_USERNAME, configuration);
         proxyPassword = ConfigurationHelper.getStringProperty(TransportConstants.PROXY_PASSWORD_PROP_NAME, TransportConstants.DEFAULT_PROXY_PASSWORD, configuration);

         proxyRemoteDNS = ConfigurationHelper.getBooleanProperty(TransportConstants.PROXY_REMOTE_DNS_PROP_NAME, TransportConstants.DEFAULT_PROXY_REMOTE_DNS, configuration);
      }

      remotingThreads = ConfigurationHelper.getIntProperty(TransportConstants.NIO_REMOTING_THREADS_PROPNAME, -1, configuration);
      remotingThreads = ConfigurationHelper.getIntProperty(TransportConstants.REMOTING_THREADS_PROPNAME, remotingThreads, configuration);

      useGlobalWorkerPool = ConfigurationHelper.getBooleanProperty(TransportConstants.USE_NIO_GLOBAL_WORKER_POOL_PROP_NAME, TransportConstants.DEFAULT_USE_GLOBAL_WORKER_POOL, configuration);
      useGlobalWorkerPool = ConfigurationHelper.getBooleanProperty(TransportConstants.USE_GLOBAL_WORKER_POOL_PROP_NAME, useGlobalWorkerPool, configuration);

      useEpoll = ConfigurationHelper.getBooleanProperty(TransportConstants.USE_EPOLL_PROP_NAME, TransportConstants.DEFAULT_USE_EPOLL, configuration);
      useKQueue = ConfigurationHelper.getBooleanProperty(TransportConstants.USE_KQUEUE_PROP_NAME, TransportConstants.DEFAULT_USE_KQUEUE, configuration);

      useServlet = ConfigurationHelper.getBooleanProperty(TransportConstants.USE_SERVLET_PROP_NAME, TransportConstants.DEFAULT_USE_SERVLET, configuration);
      host = ConfigurationHelper.getStringProperty(TransportConstants.HOST_PROP_NAME, TransportConstants.DEFAULT_HOST, configuration);
      port = ConfigurationHelper.getIntProperty(TransportConstants.PORT_PROP_NAME, TransportConstants.DEFAULT_PORT, configuration);
      localAddress = ConfigurationHelper.getStringProperty(TransportConstants.LOCAL_ADDRESS_PROP_NAME, TransportConstants.DEFAULT_LOCAL_ADDRESS, configuration);

      localPort = ConfigurationHelper.getIntProperty(TransportConstants.LOCAL_PORT_PROP_NAME, TransportConstants.DEFAULT_LOCAL_PORT, configuration);
      if (sslEnabled) {
         keyStoreProvider = ConfigurationHelper.getStringProperty(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, TransportConstants.DEFAULT_KEYSTORE_PROVIDER, configuration);

         keyStoreType = ConfigurationHelper.getStringProperty(TransportConstants.KEYSTORE_TYPE_PROP_NAME, TransportConstants.DEFAULT_KEYSTORE_TYPE, configuration);

         keyStorePath = ConfigurationHelper.getStringProperty(TransportConstants.KEYSTORE_PATH_PROP_NAME, TransportConstants.DEFAULT_KEYSTORE_PATH, configuration);

         keyStorePassword = ConfigurationHelper.getPasswordProperty(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, TransportConstants.DEFAULT_KEYSTORE_PASSWORD, configuration, ActiveMQDefaultConfiguration.getPropMaskPassword(), ActiveMQDefaultConfiguration.getPropPasswordCodec());

         trustStoreProvider = ConfigurationHelper.getStringProperty(TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME, TransportConstants.DEFAULT_TRUSTSTORE_PROVIDER, configuration);

         trustStoreType = ConfigurationHelper.getStringProperty(TransportConstants.TRUSTSTORE_TYPE_PROP_NAME, TransportConstants.DEFAULT_TRUSTSTORE_TYPE, configuration);

         trustStorePath = ConfigurationHelper.getStringProperty(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, TransportConstants.DEFAULT_TRUSTSTORE_PATH, configuration);

         trustStorePassword = ConfigurationHelper.getPasswordProperty(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TransportConstants.DEFAULT_TRUSTSTORE_PASSWORD, configuration, ActiveMQDefaultConfiguration.getPropMaskPassword(), ActiveMQDefaultConfiguration.getPropPasswordCodec());

         crlPath = ConfigurationHelper.getStringProperty(TransportConstants.CRL_PATH_PROP_NAME, TransportConstants.DEFAULT_CRL_PATH, configuration);

         enabledCipherSuites = ConfigurationHelper.getStringProperty(TransportConstants.ENABLED_CIPHER_SUITES_PROP_NAME, TransportConstants.DEFAULT_ENABLED_CIPHER_SUITES, configuration);

         enabledProtocols = ConfigurationHelper.getStringProperty(TransportConstants.ENABLED_PROTOCOLS_PROP_NAME, TransportConstants.DEFAULT_ENABLED_PROTOCOLS, configuration);

         verifyHost = ConfigurationHelper.getBooleanProperty(TransportConstants.VERIFY_HOST_PROP_NAME, TransportConstants.DEFAULT_CONNECTOR_VERIFY_HOST, configuration);

         trustAll = ConfigurationHelper.getBooleanProperty(TransportConstants.TRUST_ALL_PROP_NAME, TransportConstants.DEFAULT_TRUST_ALL, configuration);

         forceSSLParameters = ConfigurationHelper.getBooleanProperty(TransportConstants.FORCE_SSL_PARAMETERS, TransportConstants.DEFAULT_FORCE_SSL_PARAMETERS, configuration);

         sslProvider = ConfigurationHelper.getStringProperty(TransportConstants.SSL_PROVIDER, TransportConstants.DEFAULT_SSL_PROVIDER, configuration);

         sniHost = ConfigurationHelper.getStringProperty(TransportConstants.SNIHOST_PROP_NAME, TransportConstants.DEFAULT_SNIHOST_CONFIG, configuration);

         useDefaultSslContext = ConfigurationHelper.getBooleanProperty(TransportConstants.USE_DEFAULT_SSL_CONTEXT_PROP_NAME, TransportConstants.DEFAULT_USE_DEFAULT_SSL_CONTEXT, configuration);

         trustManagerFactoryPlugin = ConfigurationHelper.getStringProperty(TransportConstants.TRUST_MANAGER_FACTORY_PLUGIN_PROP_NAME, TransportConstants.DEFAULT_TRUST_MANAGER_FACTORY_PLUGIN, configuration);
      } else {
         keyStoreProvider = TransportConstants.DEFAULT_KEYSTORE_PROVIDER;
         keyStoreType = TransportConstants.DEFAULT_KEYSTORE_TYPE;
         keyStorePath = TransportConstants.DEFAULT_KEYSTORE_PATH;
         keyStorePassword = TransportConstants.DEFAULT_KEYSTORE_PASSWORD;
         trustStoreProvider = TransportConstants.DEFAULT_TRUSTSTORE_PROVIDER;
         trustStoreType = TransportConstants.DEFAULT_TRUSTSTORE_TYPE;
         trustStorePath = TransportConstants.DEFAULT_TRUSTSTORE_PATH;
         trustStorePassword = TransportConstants.DEFAULT_TRUSTSTORE_PASSWORD;
         crlPath = TransportConstants.DEFAULT_CRL_PATH;
         enabledCipherSuites = TransportConstants.DEFAULT_ENABLED_CIPHER_SUITES;
         enabledProtocols = TransportConstants.DEFAULT_ENABLED_PROTOCOLS;
         verifyHost = TransportConstants.DEFAULT_CONNECTOR_VERIFY_HOST;
         trustAll = TransportConstants.DEFAULT_TRUST_ALL;
         sniHost = TransportConstants.DEFAULT_SNIHOST_CONFIG;
         useDefaultSslContext = TransportConstants.DEFAULT_USE_DEFAULT_SSL_CONTEXT;
         trustManagerFactoryPlugin = TransportConstants.DEFAULT_TRUST_MANAGER_FACTORY_PLUGIN;
      }

      tcpNoDelay = ConfigurationHelper.getBooleanProperty(TransportConstants.TCP_NODELAY_PROPNAME, TransportConstants.DEFAULT_TCP_NODELAY, configuration);
      tcpSendBufferSize = ConfigurationHelper.getIntProperty(TransportConstants.TCP_SENDBUFFER_SIZE_PROPNAME, TransportConstants.DEFAULT_TCP_SENDBUFFER_SIZE, configuration);
      tcpReceiveBufferSize = ConfigurationHelper.getIntProperty(TransportConstants.TCP_RECEIVEBUFFER_SIZE_PROPNAME, TransportConstants.DEFAULT_TCP_RECEIVEBUFFER_SIZE, configuration);
      this.writeBufferLowWaterMark = ConfigurationHelper.getIntProperty(TransportConstants.WRITE_BUFFER_LOW_WATER_MARK_PROPNAME, TransportConstants.DEFAULT_WRITE_BUFFER_LOW_WATER_MARK, configuration);
      this.writeBufferHighWaterMark = ConfigurationHelper.getIntProperty(TransportConstants.WRITE_BUFFER_HIGH_WATER_MARK_PROPNAME, TransportConstants.DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK, configuration);
      batchDelay = ConfigurationHelper.getLongProperty(TransportConstants.BATCH_DELAY, TransportConstants.DEFAULT_BATCH_DELAY, configuration);

      connectTimeoutMillis = ConfigurationHelper.getIntProperty(TransportConstants.NETTY_CONNECT_TIMEOUT, TransportConstants.DEFAULT_NETTY_CONNECT_TIMEOUT, configuration);
      this.closeExecutor = closeExecutor;
      this.scheduledThreadPool = scheduledThreadPool;
   }

   @Override
   public String toString() {
      return "NettyConnector [host=" + host +
         ", port=" +
         port +
         ", httpEnabled=" +
         httpEnabled +
         ", httpUpgradeEnabled=" +
         httpUpgradeEnabled +
         ", useServlet=" +
         useServlet +
         ", servletPath=" +
         servletPath +
         ", sslEnabled=" +
         sslEnabled +
         ", useNio=" +
         true +
         getHttpUpgradeInfo() +
         "]";
   }

   public ChannelGroup getChannelGroup() {
      return channelGroup;
   }

   public boolean isServerConnection() {
      return serverConnection;
   }

   private String getHttpUpgradeInfo() {
      if (!httpUpgradeEnabled) {
         return "";
      }
      String serverName = ConfigurationHelper.getStringProperty(TransportConstants.ACTIVEMQ_SERVER_NAME, null, configuration);
      String acceptor = ConfigurationHelper.getStringProperty(TransportConstants.HTTP_UPGRADE_ENDPOINT_PROP_NAME, null, configuration);
      return ", activemqServerName=" + serverName + ", httpUpgradeEndpoint=" + acceptor;
   }

   @Override
   public synchronized void start() {
      if (channelClazz != null) {
         return;
      }

      if (remotingThreads == -1) {
         // Default to number of cores * 3
         remotingThreads = Runtime.getRuntime().availableProcessors() * 3;
      }

      String connectorType;

      if (useEpoll && CheckDependencies.isEpollAvailable()) {
         if (useGlobalWorkerPool) {
            group = SharedEventLoopGroup.getInstance((threadFactory -> new EpollEventLoopGroup(remotingThreads, threadFactory)));
         } else {
            group = new EpollEventLoopGroup(remotingThreads);
         }
         connectorType = EPOLL_CONNECTOR_TYPE;
         channelClazz = EpollSocketChannel.class;
         logger.debug("Connector " + this + " using native epoll");
      } else if (useKQueue && CheckDependencies.isKQueueAvailable()) {
         if (useGlobalWorkerPool) {
            group = SharedEventLoopGroup.getInstance((threadFactory -> new KQueueEventLoopGroup(remotingThreads, threadFactory)));
         } else {
            group = new KQueueEventLoopGroup(remotingThreads);
         }
         connectorType = KQUEUE_CONNECTOR_TYPE;
         channelClazz = KQueueSocketChannel.class;
         logger.debug("Connector " + this + " using native kqueue");
      } else {
         if (useGlobalWorkerPool) {
            channelClazz = NioSocketChannel.class;
            group = SharedEventLoopGroup.getInstance((threadFactory -> new NioEventLoopGroup(remotingThreads, threadFactory)));
         } else {
            channelClazz = NioSocketChannel.class;
            group = new NioEventLoopGroup(remotingThreads);
         }
         connectorType = NIO_CONNECTOR_TYPE;
         channelClazz = NioSocketChannel.class;
         logger.debug("Connector + " + this + " using nio");
      }
      // if we are a servlet wrap the socketChannelFactory

      bootstrap = new Bootstrap();
      bootstrap.channel(channelClazz);
      bootstrap.group(group);

      bootstrap.option(ChannelOption.TCP_NODELAY, tcpNoDelay);

      if (connectTimeoutMillis != -1) {
         bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis);
      }
      if (tcpReceiveBufferSize != -1) {
         bootstrap.option(ChannelOption.SO_RCVBUF, tcpReceiveBufferSize);
      }
      if (tcpSendBufferSize != -1) {
         bootstrap.option(ChannelOption.SO_SNDBUF, tcpSendBufferSize);
      }
      final int writeBufferLowWaterMark = this.writeBufferLowWaterMark != -1 ? this.writeBufferLowWaterMark : WriteBufferWaterMark.DEFAULT.low();
      final int writeBufferHighWaterMark = this.writeBufferHighWaterMark != -1 ? this.writeBufferHighWaterMark : WriteBufferWaterMark.DEFAULT.high();
      final WriteBufferWaterMark writeBufferWaterMark = new WriteBufferWaterMark(writeBufferLowWaterMark, writeBufferHighWaterMark);
      bootstrap.option(ChannelOption.WRITE_BUFFER_WATER_MARK, writeBufferWaterMark);
      bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
      bootstrap.option(ChannelOption.SO_REUSEADDR, true);
      channelGroup = new DefaultChannelGroup("activemq-connector", GlobalEventExecutor.INSTANCE);

      final String realKeyStorePath;
      final String realKeyStoreProvider;
      final String realKeyStoreType;
      final String realKeyStorePassword;
      final String realTrustStorePath;
      final String realTrustStoreProvider;
      final String realTrustStoreType;
      final String realTrustStorePassword;

      if (sslEnabled) {
         if (forceSSLParameters) {
            realKeyStorePath = keyStorePath;
            realKeyStoreProvider = keyStoreProvider;
            realKeyStoreType = keyStoreType;
            realKeyStorePassword = keyStorePassword;
            realTrustStorePath = trustStorePath;
            realTrustStoreProvider = trustStoreProvider;
            realTrustStoreType = trustStoreType;
            realTrustStorePassword = trustStorePassword;
         } else {
            realKeyStorePath = Stream.of(System.getProperty(ACTIVEMQ_KEYSTORE_PATH_PROP_NAME), System.getProperty(JAVAX_KEYSTORE_PATH_PROP_NAME), keyStorePath).map(v -> useDefaultSslContext ? keyStorePath : v).filter(Objects::nonNull).findFirst().orElse(null);
            realKeyStorePassword = Stream.of(System.getProperty(ACTIVEMQ_KEYSTORE_PASSWORD_PROP_NAME), System.getProperty(JAVAX_KEYSTORE_PASSWORD_PROP_NAME), keyStorePassword).map(v -> useDefaultSslContext ? keyStorePassword : v).filter(Objects::nonNull).findFirst().orElse(null);

            Pair<String, String> keyStoreCompat = SSLSupport.getValidProviderAndType(Stream.of(System.getProperty(ACTIVEMQ_KEYSTORE_PROVIDER_PROP_NAME), System.getProperty(JAVAX_KEYSTORE_PROVIDER_PROP_NAME), keyStoreProvider).map(v -> useDefaultSslContext ? keyStoreProvider : v).filter(Objects::nonNull).findFirst().orElse(null),
                                                                                     Stream.of(System.getProperty(ACTIVEMQ_KEYSTORE_TYPE_PROP_NAME), System.getProperty(JAVAX_KEYSTORE_TYPE_PROP_NAME), keyStoreType).map(v -> useDefaultSslContext ? keyStoreType : v).filter(Objects::nonNull).findFirst().orElse(null));
            realKeyStoreProvider = keyStoreCompat.getA();
            realKeyStoreType = keyStoreCompat.getB();

            realTrustStorePath = Stream.of(System.getProperty(ACTIVEMQ_TRUSTSTORE_PATH_PROP_NAME), System.getProperty(JAVAX_TRUSTSTORE_PATH_PROP_NAME), trustStorePath).map(v -> useDefaultSslContext ? trustStorePath : v).filter(Objects::nonNull).findFirst().orElse(null);
            realTrustStorePassword = Stream.of(System.getProperty(ACTIVEMQ_TRUSTSTORE_PASSWORD_PROP_NAME), System.getProperty(JAVAX_TRUSTSTORE_PASSWORD_PROP_NAME), trustStorePassword).map(v -> useDefaultSslContext ? trustStorePassword : v).filter(Objects::nonNull).findFirst().orElse(null);

            Pair<String, String> trustStoreCompat = SSLSupport.getValidProviderAndType(Stream.of(System.getProperty(ACTIVEMQ_TRUSTSTORE_PROVIDER_PROP_NAME), System.getProperty(JAVAX_TRUSTSTORE_PROVIDER_PROP_NAME), trustStoreProvider).map(v -> useDefaultSslContext ? trustStoreProvider : v).filter(Objects::nonNull).findFirst().orElse(null),
                                                                                       Stream.of(System.getProperty(ACTIVEMQ_TRUSTSTORE_TYPE_PROP_NAME), System.getProperty(JAVAX_TRUSTSTORE_TYPE_PROP_NAME), trustStoreType).map(v -> useDefaultSslContext ? trustStoreType : v).filter(Objects::nonNull).findFirst().orElse(null));
            realTrustStoreProvider = trustStoreCompat.getA();
            realTrustStoreType = trustStoreCompat.getB();
         }
      } else {
         realKeyStorePath = null;
         realKeyStoreProvider = null;
         realKeyStoreType = null;
         realKeyStorePassword = null;
         realTrustStorePath = null;
         realTrustStoreProvider = null;
         realTrustStoreType = null;
         realTrustStorePassword = null;
      }

      bootstrap.handler(new ChannelInitializer<Channel>() {
         @Override
         public void initChannel(Channel channel) throws Exception {
            final ChannelPipeline pipeline = channel.pipeline();

            if (proxyEnabled && (proxyRemoteDNS || !isTargetLocalHost())) {
               InetSocketAddress proxyAddress = new InetSocketAddress(proxyHost, proxyPort);
               ProxyHandler proxyHandler;
               switch (proxyVersion) {
                  case SOCKS5:
                     proxyHandler = new Socks5ProxyHandler(proxyAddress, proxyUsername, proxyPassword);
                     break;
                  case SOCKS4a:
                     proxyHandler = new Socks4ProxyHandler(proxyAddress, proxyUsername);
                     break;
                  default:
                     throw new IllegalArgumentException("Unknown SOCKS proxy version");
               }

               channel.pipeline().addLast(proxyHandler);

               logger.debug("Using a SOCKS proxy at " + proxyHost + ":" + proxyPort);

               if (proxyRemoteDNS) {
                  bootstrap.resolver(NoopAddressResolverGroup.INSTANCE);
               }
            }

            if (sslEnabled && !useServlet) {

               final SSLContextConfig sslContextConfig = SSLContextConfig.builder()
                  .keystoreProvider(realKeyStoreProvider)
                  .keystorePath(realKeyStorePath)
                  .keystoreType(realKeyStoreType)
                  .keystorePassword(realKeyStorePassword)
                  .truststoreProvider(realTrustStoreProvider)
                  .truststorePath(realTrustStorePath)
                  .truststoreType(realTrustStoreType)
                  .truststorePassword(realTrustStorePassword)
                  .trustManagerFactoryPlugin(trustManagerFactoryPlugin)
                  .crlPath(crlPath)
                  .trustAll(trustAll)
                  .build();

               final SSLEngine engine;
               if (sslProvider.equals(TransportConstants.OPENSSL_PROVIDER)) {
                  engine = loadOpenSslEngine(channel.alloc(), sslContextConfig);
               } else {
                  engine = loadJdkSslEngine(sslContextConfig);
               }

               engine.setUseClientMode(true);

               engine.setWantClientAuth(true);

               // setting the enabled cipher suites resets the enabled protocols so we need
               // to save the enabled protocols so that after the customer cipher suite is enabled
               // we can reset the enabled protocols if a customer protocol isn't specified
               String[] originalProtocols = engine.getEnabledProtocols();

               if (enabledCipherSuites != null) {
                  try {
                     engine.setEnabledCipherSuites(SSLSupport.parseCommaSeparatedListIntoArray(enabledCipherSuites));
                  } catch (IllegalArgumentException e) {
                     ActiveMQClientLogger.LOGGER.invalidCipherSuite(SSLSupport.parseArrayIntoCommandSeparatedList(engine.getSupportedCipherSuites()));
                     throw e;
                  }
               }

               if (enabledProtocols != null) {
                  try {
                     engine.setEnabledProtocols(SSLSupport.parseCommaSeparatedListIntoArray(enabledProtocols));
                  } catch (IllegalArgumentException e) {
                     ActiveMQClientLogger.LOGGER.invalidProtocol(SSLSupport.parseArrayIntoCommandSeparatedList(engine.getSupportedProtocols()));
                     throw e;
                  }
               } else {
                  engine.setEnabledProtocols(originalProtocols);
               }

               if (verifyHost) {
                  SSLParameters sslParameters = engine.getSSLParameters();
                  sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
                  engine.setSSLParameters(sslParameters);
               }

               if (sniHost != null) {
                  SSLParameters sslParameters = engine.getSSLParameters();
                  sslParameters.setServerNames(Arrays.asList(new SNIHostName(sniHost)));
                  engine.setSSLParameters(sslParameters);
               }

               SslHandler handler = new SslHandler(engine);

               pipeline.addLast("ssl", handler);
            }

            if (httpEnabled) {
               pipeline.addLast(new HttpRequestEncoder());

               pipeline.addLast(new HttpResponseDecoder());

               pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

               pipeline.addLast(new HttpHandler());
            }

            if (httpUpgradeEnabled) {
               // prepare to handle a HTTP 101 response to upgrade the protocol.
               final HttpClientCodec httpClientCodec = new HttpClientCodec();
               pipeline.addLast(httpClientCodec);
               pipeline.addLast("http-upgrade", new HttpUpgradeHandler(pipeline, httpClientCodec));
            }

            if (protocolManager != null) {
               protocolManager.addChannelHandlers(pipeline);
            }

            if (handler != null) {
               pipeline.addLast(new ActiveMQClientChannelHandler(channelGroup, handler, new Listener(), closeExecutor));
               logger.debugf("Added ActiveMQClientChannelHandler to Channel with id = %s ", channel.id());
            }
         }
      });

      if (batchDelay > 0) {
         flusher = new BatchFlusher();

         batchFlusherFuture = scheduledThreadPool.scheduleWithFixedDelay(flusher, batchDelay, batchDelay, TimeUnit.MILLISECONDS);
      }
      ActiveMQClientLogger.LOGGER.startedNettyConnector(connectorType, TransportConstants.NETTY_VERSION, host, port);
   }

   private SSLEngine loadJdkSslEngine(final SSLContextConfig sslContextConfig) throws Exception {
      final SSLContext context = SSLContextFactoryProvider.getSSLContextFactory()
         .getSSLContext(sslContextConfig, configuration);

      if (host != null && port != -1) {
         return context.createSSLEngine(host, port);
      } else {
         return context.createSSLEngine();
      }
   }

   private SSLEngine loadOpenSslEngine(final ByteBufAllocator alloc, final SSLContextConfig sslContextConfig) throws Exception {
      final OpenSSLContextFactory factory = OpenSSLContextFactoryProvider.getOpenSSLContextFactory();
      if (factory == null) {
         throw new IllegalStateException("No OpenSSLContextFactory registered!");
      }
      final SslContext context = factory.getClientSslContext(sslContextConfig, configuration);

      if (host != null && port != -1) {
         return context.newEngine(alloc, host, port);
      } else {
         return context.newEngine(alloc);
      }
   }

   @Override
   public synchronized void close() {
      if (channelClazz == null) {
         return;
      }

      if (batchFlusherFuture != null) {
         batchFlusherFuture.cancel(false);

         flusher.cancel();

         flusher = null;

         batchFlusherFuture = null;
      }

      bootstrap = null;
      channelGroup.close().awaitUninterruptibly();

      // Shutdown the EventLoopGroup if no new task was added for 100ms or if
      // 3000ms elapsed.
      group.shutdownGracefully(100, 3000, TimeUnit.MILLISECONDS);

      channelClazz = null;

      for (Connection connection : connections.values()) {
         listener.connectionDestroyed(connection.getID());
      }

      connections.clear();
   }

   @Override
   public boolean isStarted() {
      return channelClazz != null;
   }

   @Override
   public Connection createConnection() {
      return createConnection(null);
   }

   /**
    * Create and return a connection from this connector.
    * <p>
    * This method must NOT throw an exception if it fails to create the connection
    * (e.g. network is not available), in this case it MUST return null.<br>
    * This version can be used for testing purposes.
    *
    * @param onConnect a callback that would be called right after {@link Bootstrap#connect()}
    * @return The connection, or {@code null} if unable to create a connection (e.g. network is unavailable)
    */
   public final Connection createConnection(Consumer<ChannelFuture> onConnect) {
      if (channelClazz == null) {
         return null;
      }

      return createConnection(onConnect, host, port);
   }

   public NettyConnection createConnection(Consumer<ChannelFuture> onConnect, String host, int port) {
      InetSocketAddress remoteDestination;
      if (proxyEnabled && proxyRemoteDNS) {
         remoteDestination = InetSocketAddress.createUnresolved(IPV6Util.stripBracketsAndZoneID(host), port);
      } else {
         remoteDestination = new InetSocketAddress(IPV6Util.stripBracketsAndZoneID(host), port);
      }

      logger.debug("Remote destination: " + remoteDestination);

      ChannelFuture future;
      //port 0 does not work so only use local address if set
      if (localPort != 0) {
         SocketAddress localDestination;
         if (localAddress != null) {
            localDestination = new InetSocketAddress(localAddress, localPort);
         } else {
            localDestination = new InetSocketAddress(localPort);
         }
         future = bootstrap.connect(remoteDestination, localDestination);
      } else {
         future = bootstrap.connect(remoteDestination);
      }
      if (onConnect != null) {
         onConnect.accept(future);
      }
      future.awaitUninterruptibly();

      if (future.isSuccess()) {
         final Channel ch = future.channel();
         SslHandler sslHandler = ch.pipeline().get(SslHandler.class);
         if (sslHandler != null) {
            Future<Channel> handshakeFuture = sslHandler.handshakeFuture();
            if (handshakeFuture.awaitUninterruptibly(30000)) {
               if (handshakeFuture.isSuccess()) {
                  ChannelPipeline channelPipeline = ch.pipeline();
                  ActiveMQChannelHandler channelHandler = channelPipeline.get(ActiveMQChannelHandler.class);
                  if (!serverConnection) {
                     if (channelHandler != null) {
                        channelHandler.active = true;
                     } else {
                        ch.close().awaitUninterruptibly();
                        ActiveMQClientLogger.LOGGER.errorCreatingNettyConnection(new IllegalStateException("No ActiveMQChannelHandler has been found while connecting to " + remoteDestination + " from Channel with id = " + ch.id()));
                        return null;
                     }
                  }
               } else {
                  ch.close().awaitUninterruptibly();
                  ActiveMQClientLogger.LOGGER.errorCreatingNettyConnection(handshakeFuture.cause());
                  return null;
               }
            } else {
               //handshakeFuture.setFailure(new SSLException("Handshake was not completed in 30 seconds"));
               ch.close().awaitUninterruptibly();
               return null;
            }

         }
         if (httpUpgradeEnabled) {
            // Send a HTTP GET + Upgrade request that will be handled by the http-upgrade handler.
            try {
               //get this first incase it removes itself
               HttpUpgradeHandler httpUpgradeHandler = (HttpUpgradeHandler) ch.pipeline().get("http-upgrade");
               String scheme = "http";
               if (sslEnabled) {
                  scheme = "https";
               }
               String ipv6Host = IPV6Util.encloseHost(host);
               URI uri = new URI(scheme, null, ipv6Host, port, null, null, null);
               HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath());
               request.headers().set(HttpHeaderNames.HOST, ipv6Host);
               request.headers().set(HttpHeaderNames.UPGRADE, ACTIVEMQ_REMOTING);
               request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderNames.UPGRADE);
               final String serverName = ConfigurationHelper.getStringProperty(TransportConstants.ACTIVEMQ_SERVER_NAME, null, configuration);
               if (serverName != null) {
                  request.headers().set(TransportConstants.ACTIVEMQ_SERVER_NAME, serverName);
               }

               final String endpoint = ConfigurationHelper.getStringProperty(TransportConstants.HTTP_UPGRADE_ENDPOINT_PROP_NAME, null, configuration);
               if (endpoint != null) {
                  request.headers().set(TransportConstants.HTTP_UPGRADE_ENDPOINT_PROP_NAME, endpoint);
               }

               // Get 16 bit nonce and base 64 encode it
               byte[] nonce = randomBytes(16);
               String key = base64(nonce);
               request.headers().set(SEC_ACTIVEMQ_REMOTING_KEY, key);
               ch.attr(REMOTING_KEY).set(key);

               logger.debugf("Sending HTTP request %s", request);

               // Send the HTTP request.
               ch.writeAndFlush(request);

               if (!httpUpgradeHandler.awaitHandshake()) {
                  ch.close().awaitUninterruptibly();
                  return null;
               }
            } catch (URISyntaxException e) {
               ActiveMQClientLogger.LOGGER.errorCreatingNettyConnection(e);
               return null;
            }
         } else {
            ChannelPipeline channelPipeline = ch.pipeline();
            ActiveMQChannelHandler channelHandler = channelPipeline.get(ActiveMQChannelHandler.class);
            if (channelHandler != null) {
               channelHandler.active = true;
            } else if (!serverConnection) {
               ch.close().awaitUninterruptibly();
               ActiveMQClientLogger.LOGGER.errorCreatingNettyConnection(
                  new IllegalStateException("No ActiveMQChannelHandler has been found while connecting to " +
                                               remoteDestination + " from Channel with id = " + ch.id()));
               return null;
            }
         }

         // No acceptor on a client connection
         Listener connectionListener = new Listener();
         NettyConnection conn = new NettyConnection(configuration, ch, connectionListener, !httpEnabled && batchDelay > 0, false);
         connectionListener.connectionCreated(null, conn, protocolManager);
         return conn;
      } else {
         Throwable t = future.cause();

         if (t != null && !(t instanceof ConnectException)) {
            ActiveMQClientLogger.LOGGER.errorCreatingNettyConnection(future.cause());
         }

         return null;
      }
   }

   public int getConnectTimeoutMillis() {
      return connectTimeoutMillis;
   }

   public void setConnectTimeoutMillis(int connectTimeoutMillis) {
      this.connectTimeoutMillis = connectTimeoutMillis;
   }

   private static final class ActiveMQClientChannelHandler extends ActiveMQChannelHandler {

      ActiveMQClientChannelHandler(final ChannelGroup group,
                                   final BufferHandler handler,
                                   final ClientConnectionLifeCycleListener listener,
                                   final Executor executor) {
         super(group, handler, listener, executor);
      }
   }

   private static class HttpUpgradeHandler extends SimpleChannelInboundHandler<HttpObject> {

      private final ChannelPipeline pipeline;
      private final HttpClientCodec httpClientCodec;
      private final CountDownLatch latch = new CountDownLatch(1);
      private boolean handshakeComplete = false;

      private HttpUpgradeHandler(ChannelPipeline pipeline, HttpClientCodec httpClientCodec) {
         this.pipeline = pipeline;
         this.httpClientCodec = httpClientCodec;
      }

      /**
       * HTTP upgrade response will be decode by Netty as 2 objects:
       * - 1 HttpObject corresponding to the 101 SWITCHING PROTOCOL headers
       * - 1 EMPTY_LAST_CONTENT
       *
       * The HTTP upgrade is successful whne the 101 SWITCHING PROTOCOL has been received (handshakeComplete = true)
       * but the latch is count down only when the following EMPTY_LAST_CONTENT is also received.
       * Otherwise this ChannelHandler would be removed too soon and the ActiveMQChannelHandler would handle the
       * EMPTY_LAST_CONTENT (while it is expecitng only ByteBuf).
       */
      @Override
      public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
         if (logger.isDebugEnabled()) {
            logger.debug("Received msg=" + msg);
         }
         if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;
            if (response.status().code() == HttpResponseStatus.SWITCHING_PROTOCOLS.code() && response.headers().get(HttpHeaderNames.UPGRADE).equals(ACTIVEMQ_REMOTING)) {
               String accept = response.headers().get(SEC_ACTIVEMQ_REMOTING_ACCEPT);
               String expectedResponse = createExpectedResponse(MAGIC_NUMBER, ctx.channel().attr(REMOTING_KEY).get());

               if (expectedResponse.equals(accept)) {
                  // HTTP upgrade is successful but let's wait to receive the EMPTY_LAST_CONTENT to count down the latch
                  handshakeComplete = true;
               } else {
                  // HTTP upgrade failed
                  ActiveMQClientLogger.LOGGER.httpHandshakeFailed(msg);
                  ctx.close();
                  latch.countDown();
               }
               return;
            }
         } else if (msg == LastHttpContent.EMPTY_LAST_CONTENT && handshakeComplete) {
            // remove the http handlers and flag the activemq channel handler as active
            pipeline.remove(httpClientCodec);
            pipeline.remove(this);
            ActiveMQChannelHandler channelHandler = pipeline.get(ActiveMQChannelHandler.class);
            channelHandler.active = true;
         }
         if (!handshakeComplete) {
            ActiveMQClientLogger.LOGGER.httpHandshakeFailed(msg);
            ctx.close();
         }
         latch.countDown();
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
         ActiveMQClientLogger.LOGGER.errorCreatingNettyConnection(cause);
         ctx.close();
      }

      public boolean awaitHandshake() {
         try {
            if (!latch.await(30000, TimeUnit.MILLISECONDS)) {
               return false;
            }
         } catch (InterruptedException e) {
            return false;
         }
         return handshakeComplete;
      }
   }

   class HttpHandler extends ChannelDuplexHandler {

      private Channel channel;

      private long lastSendTime = 0;

      private boolean waitingGet = false;

      private HttpIdleTimer task;

      private final String url;

      private final FutureLatch handShakeFuture = new FutureLatch();

      private boolean active = false;

      private boolean handshaking = false;

      private String cookie;

      HttpHandler() throws Exception {
         url = new URI("http", null, host, port, servletPath, null, null).toString();
      }

      @Override
      public void channelActive(final ChannelHandlerContext ctx) throws Exception {
         super.channelActive(ctx);
         channel = ctx.channel();
         if (httpClientIdleScanPeriod > 0) {
            task = new HttpIdleTimer();
            java.util.concurrent.Future<?> future = scheduledThreadPool.scheduleAtFixedRate(task, httpClientIdleScanPeriod, httpClientIdleScanPeriod, TimeUnit.MILLISECONDS);
            task.setFuture(future);
         }
      }

      @Override
      public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
         if (task != null) {
            task.close();
         }

         super.channelInactive(ctx);
      }

      @Override
      public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
         FullHttpResponse response = (FullHttpResponse) msg;
         if (httpRequiresSessionId && !active) {
            final List<String> setCookieHeaderValues = response.headers().getAll(HttpHeaderNames.SET_COOKIE);
            for (String setCookieHeaderValue : setCookieHeaderValues) {
               final Cookie cookie = ClientCookieDecoder.LAX.decode(setCookieHeaderValue);
               if ("JSESSIONID".equals(cookie.name())) {
                  this.cookie = setCookieHeaderValue;
                  break;
               }
            }
            active = true;
            handShakeFuture.run();
         }
         waitingGet = false;
         ctx.fireChannelRead(response.content());
      }

      @Override
      public void write(final ChannelHandlerContext ctx, final Object msg, ChannelPromise promise) throws Exception {
         if (msg instanceof ByteBuf) {
            if (httpRequiresSessionId && !active) {
               if (handshaking) {
                  handshaking = true;
               } else {
                  if (!handShakeFuture.await(5000)) {
                     throw new RuntimeException("Handshake failed after timeout");
                  }
               }
            }

            ByteBuf buf = (ByteBuf) msg;
            FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, url, buf);
            httpRequest.headers().add(HttpHeaderNames.HOST, NettyConnector.this.host);
            if (cookie != null) {
               httpRequest.headers().add(HttpHeaderNames.COOKIE, cookie);
            }
            httpRequest.headers().add(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(buf.readableBytes()));
            ctx.write(httpRequest, promise);
            lastSendTime = System.currentTimeMillis();
         } else {
            ctx.write(msg, promise);
            lastSendTime = System.currentTimeMillis();
         }
      }

      private class HttpIdleTimer implements Runnable {

         private boolean closed = false;

         private java.util.concurrent.Future<?> future;

         @Override
         public synchronized void run() {
            if (closed) {
               return;
            }

            if (!waitingGet && System.currentTimeMillis() > lastSendTime + httpMaxClientIdleTime) {
               FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, url);
               httpRequest.headers().add(HttpHeaderNames.HOST, NettyConnector.this.host);
               waitingGet = true;
               channel.writeAndFlush(httpRequest);
            }
         }

         public synchronized void setFuture(final java.util.concurrent.Future<?> future) {
            this.future = future;
         }

         public void close() {
            if (future != null) {
               future.cancel(false);
            }

            closed = true;
         }
      }
   }

   private class Listener implements ClientConnectionLifeCycleListener {

      @Override
      public void connectionCreated(final ActiveMQComponent component,
                                    final Connection connection,
                                    final ClientProtocolManager protocol) {
         if (connections.putIfAbsent(connection.getID(), connection) != null) {
            throw ActiveMQClientMessageBundle.BUNDLE.connectionExists(connection.getID());
         }
         @SuppressWarnings("unchecked")
         final BaseConnectionLifeCycleListener<ClientProtocolManager> clientListener = (BaseConnectionLifeCycleListener<ClientProtocolManager>) listener;
         clientListener.connectionCreated(component, connection, protocol);
      }

      @Override
      public void connectionDestroyed(final Object connectionID) {
         if (connections.remove(connectionID) != null) {
            // Execute on different thread to avoid deadlocks
            closeExecutor.execute(new Runnable() {
               @Override
               public void run() {
                  listener.connectionDestroyed(connectionID);
               }
            });
         }
      }

      @Override
      public void connectionException(final Object connectionID, final ActiveMQException me) {
         // Execute on different thread to avoid deadlocks
         closeExecutor.execute(new Runnable() {
            @Override
            public void run() {
               listener.connectionException(connectionID, me);
            }
         });
      }

      @Override
      public void connectionReadyForWrites(Object connectionID, boolean ready) {
         NettyConnection connection = (NettyConnection) connections.get(connectionID);
         if (connection != null) {
            connection.fireReady(ready);
         }
         listener.connectionReadyForWrites(connectionID, ready);
      }

   }

   private class BatchFlusher implements Runnable {

      private boolean cancelled;

      @Override
      public synchronized void run() {
         if (!cancelled) {
            for (Connection connection : connections.values()) {
               connection.checkFlushBatchBuffer();
            }
         }
      }

      public synchronized void cancel() {
         cancelled = true;
      }
   }

   @Override
   public boolean isEquivalent(Map<String, Object> configuration) {
      boolean httpUpgradeEnabled = ConfigurationHelper.getBooleanProperty(TransportConstants.HTTP_UPGRADE_ENABLED_PROP_NAME, TransportConstants.DEFAULT_HTTP_UPGRADE_ENABLED, configuration);
      if (httpUpgradeEnabled) {
         // we need to look at the activemqServerName to distinguish between ActiveMQ servers that could be proxied behind the same
         // HTTP upgrade handler in the Web server
         String otherActiveMQServerName = ConfigurationHelper.getStringProperty(TransportConstants.ACTIVEMQ_SERVER_NAME, null, configuration);
         String activeMQServerName = ConfigurationHelper.getStringProperty(TransportConstants.ACTIVEMQ_SERVER_NAME, null, this.configuration);
         boolean equivalent = isSameHostAndPort(configuration) && otherActiveMQServerName != null && otherActiveMQServerName.equals(activeMQServerName);
         return equivalent;
      } else {
         return isSameHostAndPort(configuration);
      }
   }

   private boolean isSameHostAndPort(Map<String, Object> configuration) {
      //here we only check host and port because these two parameters
      //is sufficient to determine the target host
      String host = ConfigurationHelper.getStringProperty(TransportConstants.HOST_PROP_NAME, TransportConstants.DEFAULT_HOST, configuration);
      int port = ConfigurationHelper.getIntProperty(TransportConstants.PORT_PROP_NAME, TransportConstants.DEFAULT_PORT, configuration);

      if (port != this.port)
         return false;

      if (host.equals(this.host))
         return true;

      //The host may be an alias. We need to compare raw IP address.
      boolean result = false;
      try {
         InetAddress inetAddr1 = InetAddress.getByName(host);
         InetAddress inetAddr2 = InetAddress.getByName(this.host);
         String ip1 = inetAddr1.getHostAddress();
         String ip2 = inetAddr2.getHostAddress();
         logger.debug(this + " host 1: " + host + " ip address: " + ip1 + " host 2: " + this.host + " ip address: " + ip2);

         result = ip1.equals(ip2);
      } catch (UnknownHostException e) {
         ActiveMQClientLogger.LOGGER.unableToResolveHost(e);
      }

      return result;
   }

   private boolean isTargetLocalHost() {
      try {
         InetAddress address = InetAddress.getByName(host);
         return address.isLoopbackAddress();
      } catch (UnknownHostException e) {
         ActiveMQClientLogger.LOGGER.error("Cannot resolve host", e);
      }
      return false;
   }

   @Override
   public void finalize() throws Throwable {
      close();
      super.finalize();
   }

   //for test purpose only
   public Bootstrap getBootStrap() {
      return bootstrap;
   }

   public static void clearThreadPools() {
      SharedEventLoopGroup.forceShutdown();
   }

   private static String base64(byte[] data) {
      ByteBuf encodedData = Unpooled.wrappedBuffer(data);
      ByteBuf encoded = Base64.encode(encodedData);
      String encodedString = encoded.toString(StandardCharsets.UTF_8);
      encoded.release();
      return encodedString;
   }

   /**
    * Creates an arbitrary number of random bytes
    *
    * @param size the number of random bytes to create
    * @return An array of random bytes
    */
   private static byte[] randomBytes(int size) {
      byte[] bytes = new byte[size];

      for (int index = 0; index < size; index++) {
         bytes[index] = (byte) randomNumber(0, 255);
      }

      return bytes;
   }

   private static int randomNumber(int minimum, int maximum) {
      return (int) (Math.random() * maximum + minimum);
   }

   public static String createExpectedResponse(final String magicNumber, final String secretKey) throws IOException {
      try {
         final String concat = secretKey + magicNumber;
         final MessageDigest digest = MessageDigest.getInstance("SHA1");

         digest.update(concat.getBytes(StandardCharsets.UTF_8));
         final byte[] bytes = digest.digest();
         return encodeBytes(bytes);
      } catch (NoSuchAlgorithmException e) {
         throw new IOException(e);
      }
   }
}

