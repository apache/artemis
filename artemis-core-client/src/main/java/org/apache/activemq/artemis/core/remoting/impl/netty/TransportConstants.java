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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.util.Version;
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.utils.DefaultSensitiveStringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class TransportConstants {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String SSL_CONTEXT_PROP_NAME = "sslContext";

   public static final String SSL_ENABLED_PROP_NAME = "sslEnabled";

   public static final String PROXY_PROTOCOL_ENABLED_PROP_NAME = "proxyProtocolEnabled";

   public static final String SSL_AUTO_RELOAD_PROP_NAME = "sslAutoReload";

   public static final boolean DEFAULT_SSL_AUTO_RELOAD = false;

   public static final String HTTP_ENABLED_PROP_NAME = "httpEnabled";

   @Deprecated(forRemoval = true)
   public static final String HTTP_CLIENT_IDLE_PROP_NAME = "httpClientIdleTime";

   @Deprecated(forRemoval = true)
   public static final String HTTP_CLIENT_IDLE_SCAN_PERIOD = "httpClientIdleScanPeriod";

   public static final String NETTY_HTTP_HEADER_PREFIX = "nettyHttpHeader.";

   @Deprecated(forRemoval = true)
   public static final String HTTP_RESPONSE_TIME_PROP_NAME = "httpResponseTime";

   @Deprecated(forRemoval = true)
   public static final String HTTP_SERVER_SCAN_PERIOD_PROP_NAME = "httpServerScanPeriod";

   public static final String HTTP_REQUIRES_SESSION_ID = "httpRequiresSessionId";

   public static final String HTTP_UPGRADE_ENABLED_PROP_NAME = "httpUpgradeEnabled";

   public static final String HTTP_UPGRADE_ENDPOINT_PROP_NAME = "httpUpgradeEndpoint";

   public static final String USE_SERVLET_PROP_NAME = "useServlet";

   public static final String SERVLET_PATH = "servletPath";

   public static final String USE_NIO_PROP_NAME = "useNio";

   public static final String USE_EPOLL_PROP_NAME = "useEpoll";

   public static final String USE_KQUEUE_PROP_NAME = "useKQueue";

   /**
    * @deprecated Use USE_GLOBAL_WORKER_POOL_PROP_NAME
    */
   @Deprecated
   public static final String USE_NIO_GLOBAL_WORKER_POOL_PROP_NAME = "useNioGlobalWorkerPool";

   public static final String USE_GLOBAL_WORKER_POOL_PROP_NAME = "useGlobalWorkerPool";

   public static final String USE_INVM_PROP_NAME = "useInvm";

   public static final String ACTIVEMQ_SERVER_NAME = "activemqServerName";

   /**
    * @deprecated use PROTOCOLS_PROP_NAME
    */
   @Deprecated
   public static final String PROTOCOL_PROP_NAME = "protocol";

   public static final String PROTOCOLS_PROP_NAME = "protocols";

   public static final String SCHEME_PROP_NAME = "scheme";

   public static final String HOST_PROP_NAME = "host";

   public static final String PORT_PROP_NAME = "port";

   public static final String LOCAL_ADDRESS_PROP_NAME = "localAddress";

   public static final String LOCAL_PORT_PROP_NAME = "localPort";

   public static final String KEYSTORE_PROVIDER_PROP_NAME = "keyStoreProvider";

   public static final String KEYSTORE_TYPE_PROP_NAME = "keyStoreType";

   public static final String KEYSTORE_PATH_PROP_NAME = "keyStorePath";

   public static final String KEYSTORE_PASSWORD_PROP_NAME = "keyStorePassword";

   public static final String KEYSTORE_ALIAS_PROP_NAME = "keyStoreAlias";

   public static final String TRUSTSTORE_PROVIDER_PROP_NAME = "trustStoreProvider";

   public static final String TRUSTSTORE_TYPE_PROP_NAME = "trustStoreType";

   public static final String TRUSTSTORE_PATH_PROP_NAME = "trustStorePath";

   public static final String TRUSTSTORE_PASSWORD_PROP_NAME = "trustStorePassword";

   public static final String CRL_PATH_PROP_NAME = "crlPath";

   public static final String ENABLED_CIPHER_SUITES_PROP_NAME = "enabledCipherSuites";

   public static final String ENABLED_PROTOCOLS_PROP_NAME = "enabledProtocols";

   public static final String NEED_CLIENT_AUTH_PROP_NAME = "needClientAuth";

   public static final String WANT_CLIENT_AUTH_PROP_NAME = "wantClientAuth";

   public static final String VERIFY_HOST_PROP_NAME = "verifyHost";

   public static final String TRUST_ALL_PROP_NAME = "trustAll";

   public static final String FORCE_SSL_PARAMETERS = "forceSSLParameters";

   public static final String SNIHOST_PROP_NAME = "sniHost";

   public static final String BACKLOG_PROP_NAME = "backlog";

   public static final String USE_DEFAULT_SSL_CONTEXT_PROP_NAME = "useDefaultSslContext";

   public static final String SSL_PROVIDER = "sslProvider";

   public static final String TRUST_MANAGER_FACTORY_PLUGIN_PROP_NAME = "trustManagerFactoryPlugin";

   public static final String NETTY_VERSION;

   /**
    * Disable Nagle's algorithm.
    * <p>
    * Valid for (client) Sockets.
    *
    * @see <a
    * href="https://netty.io/4.1/api/io/netty/channel/socket/SocketChannelConfig.html#setTcpNoDelay-boolean-">Netty note
    * on this option</a>
    * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/guides/net/socketOpt.html">Oracle doc on
    * tcpNoDelay</a>
    */
   public static final String TCP_NODELAY_PROPNAME = "tcpNoDelay";

   public static final String TCP_SENDBUFFER_SIZE_PROPNAME = "tcpSendBufferSize";

   public static final String TCP_RECEIVEBUFFER_SIZE_PROPNAME = "tcpReceiveBufferSize";

   /**
    * @deprecated Use REMOTING_THREADS_PROPNAME
    */
   @Deprecated
   public static final String NIO_REMOTING_THREADS_PROPNAME = "nioRemotingThreads";

   public static final String WRITE_BUFFER_LOW_WATER_MARK_PROPNAME = "writeBufferLowWaterMark";

   public static final String WRITE_BUFFER_HIGH_WATER_MARK_PROPNAME = "writeBufferHighWaterMark";

   public static final String REMOTING_THREADS_PROPNAME = "remotingThreads";

   public static final String BATCH_DELAY = "batchDelay";

   public static final String DIRECT_DELIVER = "directDeliver";

   public static final String CLUSTER_CONNECTION = "clusterConnection";

   @Deprecated
   public static final String STOMP_CONSUMERS_CREDIT = "stompConsumerCredits";

   public static final String STOMP_CONSUMER_WINDOW_SIZE = "stompConsumerWindowSize";

   public static final int STOMP_DEFAULT_CONSUMER_WINDOW_SIZE = 10 * 1024; // 10K

   public static final String SOCKS_ENABLED_PROP_NAME = "socksEnabled";

   public static final String SOCKS_HOST_PROP_NAME = "socksHost";

   public static final String SOCKS_PORT_PROP_NAME = "socksPort";

   public static final String SOCKS_VERSION_PROP_NAME = "socksVersion";

   public static final String SOCKS_USERNAME_PROP_NAME = "socksUsername";

   public static final String SOCKS_PASSWORD_PROP_NAME = "socksPassword";

   public static final String SOCKS_REMOTE_DNS_PROP_NAME = "socksRemoteDNS";

   public static final String CRC_OPTIONS_PROP_NAME = "crcOptions";

   public static final String OCSP_RESPONDER_URL_PROP_NAME = "ocspResponderURL";

   public static final String AUTO_START = "autoStart";

   public static final boolean DEFAULT_AUTO_START = true;

   public static final boolean DEFAULT_SSL_ENABLED = false;

   public static final boolean DEFAULT_PROXY_PROTOCOL_ENABLED = false;

   public static final String DEFAULT_SNIHOST_CONFIG = null;

   public static final boolean DEFAULT_USE_GLOBAL_WORKER_POOL = true;

   public static final boolean DEFAULT_USE_EPOLL = true;

   public static final boolean DEFAULT_USE_KQUEUE = true;

   public static final boolean DEFAULT_USE_INVM = false;

   public static final boolean DEFAULT_USE_SERVLET = false;

   public static final String DEFAULT_HOST = "localhost";

   public static final int DEFAULT_PORT = 61616;

   public static final String DEFAULT_LOCAL_ADDRESS = null;

   public static final int DEFAULT_LOCAL_PORT = 0;

   public static final int DEFAULT_STOMP_PORT = 61613;

   public static final String DEFAULT_KEYSTORE_PROVIDER = null;

   public static final String DEFAULT_KEYSTORE_TYPE = "JKS";

   public static final String DEFAULT_KEYSTORE_PATH = null;

   public static final String DEFAULT_KEYSTORE_PASSWORD = null;

   public static final String DEFAULT_TRUSTSTORE_PROVIDER = null;

   public static final String DEFAULT_TRUSTSTORE_TYPE = "JKS";

   public static final String DEFAULT_TRUSTSTORE_PATH = null;

   public static final String DEFAULT_TRUSTSTORE_PASSWORD = null;

   public static final String DEFAULT_CRL_PATH = null;

   public static final String DEFAULT_ENABLED_CIPHER_SUITES = null;

   public static final String DEFAULT_ENABLED_PROTOCOLS = null;

   public static final boolean DEFAULT_NEED_CLIENT_AUTH = false;

   public static final boolean DEFAULT_WANT_CLIENT_AUTH = false;

   public static final boolean DEFAULT_ACCEPTOR_VERIFY_HOST = false;

   public static final boolean DEFAULT_CONNECTOR_VERIFY_HOST = true;

   public static final String DEFAULT_SSL_PROVIDER = "JDK";

   public static final String OPENSSL_PROVIDER = "OPENSSL";

   public static final boolean DEFAULT_TRUST_ALL = false;

   public static final String DEFAULT_TRUST_MANAGER_FACTORY_PLUGIN = null;

   public static final boolean DEFAULT_FORCE_SSL_PARAMETERS = false;

   public static final boolean DEFAULT_USE_DEFAULT_SSL_CONTEXT = false;

   public static final String DEFAULT_KEYSTORE_ALIAS = null;

   public static final String DEFAULT_PASSWORD_CODEC_CLASS = DefaultSensitiveStringCodec.class.getName();

   public static final boolean DEFAULT_TCP_NODELAY = true;

   public static final int DEFAULT_TCP_SENDBUFFER_SIZE = 1024 * 1024;

   public static final int DEFAULT_TCP_RECEIVEBUFFER_SIZE = 1024 * 1024;

   public static final int DEFAULT_WRITE_BUFFER_LOW_WATER_MARK = 32 * 1024;

   public static final int DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK = 128 * 1024;

   public static final boolean DEFAULT_HTTP_ENABLED = false;

   public static final long DEFAULT_HTTP_CLIENT_IDLE_TIME = 500;

   public static final long DEFAULT_HTTP_CLIENT_SCAN_PERIOD = 500;

   public static final long DEFAULT_HTTP_RESPONSE_TIME = 10000;

   public static final long DEFAULT_HTTP_SERVER_SCAN_PERIOD = 5000;

   public static final boolean DEFAULT_HTTP_REQUIRES_SESSION_ID = false;

   public static final boolean DEFAULT_HTTP_UPGRADE_ENABLED = false;

   public static final String DEFAULT_SERVLET_PATH = "/messaging/ActiveMQServlet";

   public static final long DEFAULT_BATCH_DELAY = 0;

   public static final boolean DEFAULT_DIRECT_DELIVER = true;

   public static final Set<String> ALLOWABLE_CONNECTOR_KEYS;

   public static final Set<String> ALLOWABLE_ACCEPTOR_KEYS;

   public static final String CONNECTION_TTL = "connectionTtl";

   public static final String CONNECTION_TTL_MAX = "connectionTtlMax";

   public static final String CONNECTION_TTL_MIN = "connectionTtlMin";

   public static final String HEART_BEAT_TO_CONNECTION_TTL_MODIFIER = "heartBeatToConnectionTtlModifier";

   @Deprecated
   public static final String STOMP_ENABLE_MESSAGE_ID_DEPRECATED = "stomp-enable-message-id";

   public static final String STOMP_ENABLE_MESSAGE_ID = "stompEnableMessageId";

   @Deprecated
   public static final String STOMP_MIN_LARGE_MESSAGE_SIZE_DEPRECATED = "stomp-min-large-message-size";

   public static final String STOMP_MIN_LARGE_MESSAGE_SIZE = "stompMinLargeMessageSize";

   public static final String NETTY_CONNECT_TIMEOUT = "connect-timeout-millis";

   public static final int DEFAULT_NETTY_CONNECT_TIMEOUT = -1;

   public static final String CONNECTIONS_ALLOWED = "connectionsAllowed";

   public static final long DEFAULT_CONNECTIONS_ALLOWED = -1L;

   /**
    * @deprecated Use {@link TransportConstants#WEB_SOCKET_MAX_FRAME_PAYLOAD_LENGTH instead}.
    */
   @Deprecated
   public static final String STOMP_MAX_FRAME_PAYLOAD_LENGTH = "stompMaxFramePayloadLength";

   public static final String WEB_SOCKET_MAX_FRAME_PAYLOAD_LENGTH = "webSocketMaxFramePayloadLength";

   public static final int DEFAULT_WEB_SOCKET_MAX_FRAME_PAYLOAD_LENGTH = 65536;

   public static final String WEB_SOCKET_ENCODER_TYPE = "webSocketEncoderType";

   public static final String DEFAULT_WEB_SOCKET_ENCODER_TYPE = "binary";

   public static final String HANDSHAKE_TIMEOUT = "handshake-timeout";

   public static final int DEFAULT_HANDSHAKE_TIMEOUT = 10;

   /*
    * Defines if the WS acceptor allows a client to request compression via WS extensions for
    * per message deflate. By default this is not enabled and the WS upgrade response will not
    * carry any compression support headers when the client indicates it supports compression.
    */
   public static final String WEB_SOCKET_COMPRESSION_SUPPORTED = "webSocketCompressionSupported";

   public static final boolean DEFAULT_WEB_SOCKET_COMPRESSION_SUPPORTED = false;

   public static final String QUIET_PERIOD = "quietPeriod";

   public static final String DISABLE_STOMP_SERVER_HEADER = "disableStompServerHeader";

   /**
    * We let this to be defined as a System Variable, as we need a different timeout over our testsuite. When running on
    * a real server, this is the default we want. When running on a test suite, we need it to be 0, You should see a
    * property on the main pom.xml.
    */
   public static final int DEFAULT_QUIET_PERIOD = parseDefaultVariable("DEFAULT_QUIET_PERIOD", 100);

   public static final String SHUTDOWN_TIMEOUT = "shutdownTimeout";

   /**
    * We let this to be defined as a System Variable, as we need a different timeout over our testsuite. When running on
    * a real server, this is the default we want. When running on a test suite, we need it to be 0, You should see a
    * property on the main pom.xml
    */
   public static final int DEFAULT_SHUTDOWN_TIMEOUT = parseDefaultVariable("DEFAULT_SHUTDOWN_TIMEOUT", 3_000);

   public static final boolean DEFAULT_SOCKS_ENABLED = false;

   public static final String DEFAULT_SOCKS_HOST = null;

   public static final int DEFAULT_SOCKS_PORT = 0;

   public static final byte DEFAULT_SOCKS_VERSION = SocksVersion.SOCKS5.byteValue();

   public static final String DEFAULT_SOCKS_USERNAME = null;

   public static final String DEFAULT_SOCKS_PASSWORD = null;

   public static final boolean SOCKS_PROXY_REMOTE_DNS = false;

   public static final String ROUTER = "router";

   public static final String DEFAULT_ROUTER = null;

   public static final String DEFAULT_CRC_OPTIONS = null;

   public static final String DEFAULT_OCSP_RESPONDER_URL = null;

   private static int parseDefaultVariable(String variableName, int defaultValue) {
      try {
         String variable = System.getProperty(TransportConstants.class.getName() + "." + variableName);
         if (variable != null) {
            return Integer.parseInt(variable);
         }
      } catch (Throwable ignored) {
         logger.debug(ignored.getMessage(), ignored);
      }

      return defaultValue;
   }

   static {
      Set<String> allowableAcceptorKeys = new HashSet<>();
      allowableAcceptorKeys.add(TransportConstants.SSL_ENABLED_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.SSL_AUTO_RELOAD_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.HTTP_RESPONSE_TIME_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.HTTP_SERVER_SCAN_PERIOD_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.HTTP_UPGRADE_ENABLED_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.USE_NIO_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.USE_EPOLL_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.USE_KQUEUE_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.USE_INVM_PROP_NAME);
      //noinspection deprecation
      allowableAcceptorKeys.add(TransportConstants.PROTOCOL_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.PROTOCOLS_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.SCHEME_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.HOST_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.PORT_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.KEYSTORE_TYPE_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.KEYSTORE_PATH_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.KEYSTORE_ALIAS_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.TRUSTSTORE_TYPE_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.TRUSTSTORE_PATH_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.ENABLED_CIPHER_SUITES_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.ENABLED_PROTOCOLS_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.WANT_CLIENT_AUTH_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.VERIFY_HOST_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.SNIHOST_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.TCP_NODELAY_PROPNAME);
      allowableAcceptorKeys.add(TransportConstants.TCP_SENDBUFFER_SIZE_PROPNAME);
      allowableAcceptorKeys.add(TransportConstants.TCP_RECEIVEBUFFER_SIZE_PROPNAME);
      allowableAcceptorKeys.add(TransportConstants.WRITE_BUFFER_HIGH_WATER_MARK_PROPNAME);
      allowableAcceptorKeys.add(TransportConstants.WRITE_BUFFER_LOW_WATER_MARK_PROPNAME);
      allowableAcceptorKeys.add(TransportConstants.NIO_REMOTING_THREADS_PROPNAME);
      allowableAcceptorKeys.add(TransportConstants.REMOTING_THREADS_PROPNAME);
      allowableAcceptorKeys.add(TransportConstants.BATCH_DELAY);
      allowableAcceptorKeys.add(TransportConstants.DIRECT_DELIVER);
      allowableAcceptorKeys.add(TransportConstants.CLUSTER_CONNECTION);
      allowableAcceptorKeys.add(TransportConstants.STOMP_CONSUMERS_CREDIT);
      allowableAcceptorKeys.add(TransportConstants.STOMP_CONSUMER_WINDOW_SIZE);
      allowableAcceptorKeys.add(TransportConstants.STOMP_MIN_LARGE_MESSAGE_SIZE_DEPRECATED);
      allowableAcceptorKeys.add(TransportConstants.STOMP_MIN_LARGE_MESSAGE_SIZE);
      allowableAcceptorKeys.add(TransportConstants.CONNECTION_TTL);
      allowableAcceptorKeys.add(TransportConstants.CONNECTION_TTL_MAX);
      allowableAcceptorKeys.add(TransportConstants.CONNECTION_TTL_MIN);
      allowableAcceptorKeys.add(TransportConstants.HEART_BEAT_TO_CONNECTION_TTL_MODIFIER);
      allowableAcceptorKeys.add(TransportConstants.STOMP_ENABLE_MESSAGE_ID_DEPRECATED);
      allowableAcceptorKeys.add(TransportConstants.STOMP_ENABLE_MESSAGE_ID);
      allowableAcceptorKeys.add(TransportConstants.CONNECTIONS_ALLOWED);
      allowableAcceptorKeys.add(TransportConstants.STOMP_MAX_FRAME_PAYLOAD_LENGTH);
      allowableAcceptorKeys.add(TransportConstants.WEB_SOCKET_MAX_FRAME_PAYLOAD_LENGTH);
      allowableAcceptorKeys.add(ActiveMQDefaultConfiguration.getPropMaskPassword());
      allowableAcceptorKeys.add(ActiveMQDefaultConfiguration.getPropPasswordCodec());
      allowableAcceptorKeys.add(TransportConstants.BACKLOG_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.CRL_PATH_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.HANDSHAKE_TIMEOUT);
      allowableAcceptorKeys.add(TransportConstants.SSL_PROVIDER);
      allowableAcceptorKeys.add(TransportConstants.TRUST_MANAGER_FACTORY_PLUGIN_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.SHUTDOWN_TIMEOUT);
      allowableAcceptorKeys.add(TransportConstants.QUIET_PERIOD);
      allowableAcceptorKeys.add(TransportConstants.DISABLE_STOMP_SERVER_HEADER);
      allowableAcceptorKeys.add(TransportConstants.AUTO_START);
      allowableAcceptorKeys.add(TransportConstants.ROUTER);
      allowableAcceptorKeys.add(TransportConstants.PROXY_PROTOCOL_ENABLED_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.CRC_OPTIONS_PROP_NAME);
      allowableAcceptorKeys.add(TransportConstants.OCSP_RESPONDER_URL_PROP_NAME);

      ALLOWABLE_ACCEPTOR_KEYS = Collections.unmodifiableSet(allowableAcceptorKeys);

      Set<String> allowableConnectorKeys = new HashSet<>();
      allowableConnectorKeys.add(TransportConstants.ACTIVEMQ_SERVER_NAME);
      allowableConnectorKeys.add(TransportConstants.SSL_ENABLED_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.HTTP_ENABLED_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.HTTP_CLIENT_IDLE_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.HTTP_CLIENT_IDLE_SCAN_PERIOD);
      allowableConnectorKeys.add(TransportConstants.HTTP_REQUIRES_SESSION_ID);
      allowableConnectorKeys.add(TransportConstants.HTTP_UPGRADE_ENABLED_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.HTTP_UPGRADE_ENDPOINT_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.USE_SERVLET_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.SERVLET_PATH);
      allowableConnectorKeys.add(TransportConstants.USE_NIO_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.USE_NIO_GLOBAL_WORKER_POOL_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.USE_EPOLL_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.USE_KQUEUE_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.USE_GLOBAL_WORKER_POOL_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.HOST_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.PORT_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.LOCAL_ADDRESS_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.LOCAL_PORT_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.KEYSTORE_TYPE_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.KEYSTORE_PATH_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.KEYSTORE_ALIAS_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.TRUSTSTORE_TYPE_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.TRUSTSTORE_PATH_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.ENABLED_CIPHER_SUITES_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.ENABLED_PROTOCOLS_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.VERIFY_HOST_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.TRUST_ALL_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.FORCE_SSL_PARAMETERS);
      allowableConnectorKeys.add(TransportConstants.SNIHOST_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.TCP_NODELAY_PROPNAME);
      allowableConnectorKeys.add(TransportConstants.TCP_SENDBUFFER_SIZE_PROPNAME);
      allowableConnectorKeys.add(TransportConstants.TCP_RECEIVEBUFFER_SIZE_PROPNAME);
      allowableConnectorKeys.add(TransportConstants.WRITE_BUFFER_HIGH_WATER_MARK_PROPNAME);
      allowableConnectorKeys.add(TransportConstants.WRITE_BUFFER_LOW_WATER_MARK_PROPNAME);
      allowableConnectorKeys.add(TransportConstants.NIO_REMOTING_THREADS_PROPNAME);
      allowableConnectorKeys.add(TransportConstants.REMOTING_THREADS_PROPNAME);
      allowableConnectorKeys.add(TransportConstants.BATCH_DELAY);
      allowableConnectorKeys.add(TransportConstants.SOCKS_ENABLED_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.SOCKS_HOST_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.SOCKS_PORT_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.SOCKS_VERSION_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.SOCKS_USERNAME_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.SOCKS_PASSWORD_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.SOCKS_REMOTE_DNS_PROP_NAME);
      allowableConnectorKeys.add(ActiveMQDefaultConfiguration.getPropMaskPassword());
      allowableConnectorKeys.add(ActiveMQDefaultConfiguration.getPropPasswordCodec());
      allowableConnectorKeys.add(TransportConstants.NETTY_CONNECT_TIMEOUT);
      allowableConnectorKeys.add(TransportConstants.USE_DEFAULT_SSL_CONTEXT_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.SSL_PROVIDER);
      allowableConnectorKeys.add(TransportConstants.TRUST_MANAGER_FACTORY_PLUGIN_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.HANDSHAKE_TIMEOUT);
      allowableConnectorKeys.add(TransportConstants.CRL_PATH_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.CRC_OPTIONS_PROP_NAME);
      allowableConnectorKeys.add(TransportConstants.OCSP_RESPONDER_URL_PROP_NAME);

      ALLOWABLE_CONNECTOR_KEYS = Collections.unmodifiableSet(allowableConnectorKeys);

      String version;
      Version v = Version.identify().get("netty-transport");
      if (v == null) {
         version = "unknown";
      } else {
         version = v.artifactVersion();
      }
      NETTY_VERSION = version;
   }

}
