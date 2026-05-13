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
package org.apache.activemq.artemis.tests.unit.core.remoting.impl.netty;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import io.netty.channel.ChannelPipeline;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.ActiveMQChannelHandler;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler;
import org.apache.activemq.artemis.spi.core.remoting.ClientConnectionLifeCycleListener;
import org.apache.activemq.artemis.spi.core.remoting.ClientProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.DefaultSensitiveStringCodec;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.apache.activemq.artemis.utils.SensitiveDataCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
public class NettyConnectorTest extends ActiveMQTestBase {

   private ActiveMQServer server;
   private ExecutorService executorService;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      executorService = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.SSL_PROVIDER, TransportConstants.OPENSSL_PROVIDER);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "server-keystore.jks");
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "client-ca-truststore.jks");
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, true);
      ConfigurationImpl config = createBasicConfig().addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params, "nettySSL"));
      server = createServer(false, config);
      server.start();
      waitForServerToStart(server);
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      executorService.shutdown();
      super.tearDown();
   }

   private ClientConnectionLifeCycleListener listener = new ClientConnectionLifeCycleListener() {
      @Override
      public void connectionException(final Object connectionID, final ActiveMQException me) {
      }

      @Override
      public void connectionDestroyed(final Object connectionID, boolean failed) {
      }

      @Override
      public void connectionCreated(final ActiveMQComponent component,
                                    final Connection connection,
                                    final ClientProtocolManager protocol) {
      }

      @Override
      public void connectionReadyForWrites(Object connectionID, boolean ready) {
      }
   };

   @Test
   public void testStartStop() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };
      Map<String, Object> params = new HashMap<>();

      NettyConnector connector = new NettyConnector(params, handler, listener, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      connector.start();
      assertTrue(connector.isStarted());
      connector.close();
      assertFalse(connector.isStarted());
   }

   @Test
   public void testNullParams() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };
      Map<String, Object> params = new HashMap<>();

      try {
         new NettyConnector(params, null, listener, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

         fail("Should throw Exception");
      } catch (IllegalArgumentException e) {
         // Ok
      }

      try {
         new NettyConnector(params, handler, null, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

         fail("Should throw Exception");
      } catch (IllegalArgumentException e) {
         // Ok
      }
   }

   /**
    * that java system properties are read
    */
   @Test
   public void testJavaSystemProperty() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };

      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);

      NettyConnector connector = new NettyConnector(params, handler, listener, executorService, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      connector.start();
      assertTrue(connector.isStarted());
      Connection c = connector.createConnection();
      assertNotNull(c);
      c.close();
      connector.close();
      assertFalse(connector.isStarted());

   }

   /**
    * that encrypted java system properties are read
    */
   @Test
   public void testEncryptedJavaSystemProperty() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };

      DefaultSensitiveStringCodec codec = PasswordMaskingUtil.getDefaultCodec();

      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PASSWORD_PROP_NAME, PasswordMaskingUtil.wrap(codec.encode("securepass")));
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PASSWORD_PROP_NAME, PasswordMaskingUtil.wrap(codec.encode("securepass")));

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);

      NettyConnector connector = new NettyConnector(params, handler, listener, executorService, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      connector.start();
      assertTrue(connector.isStarted());
      Connection c = connector.createConnection();
      assertNotNull(c);
      c.close();
      connector.close();
      assertFalse(connector.isStarted());

   }

   /**
    * that bad value encrypted java system properties are read but fail
    */
   @Test
   public void testEncryptedJavaSystemPropertyFail() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };

      DefaultSensitiveStringCodec codec = PasswordMaskingUtil.getDefaultCodec();

      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PASSWORD_PROP_NAME, PasswordMaskingUtil.wrap(codec.encode("bad password")));
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PASSWORD_PROP_NAME, PasswordMaskingUtil.wrap(codec.encode("bad password")));

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);

      NettyConnector connector = new NettyConnector(params, handler, listener, executorService, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      connector.start();
      assertTrue(connector.isStarted());
      assertNull(connector.createConnection());
      connector.close();
      assertFalse(connector.isStarted());

   }

   @Test
   public void testOverridesJavaSystemPropertyFail() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };

      //bad system properties will override the transport constants
      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PATH_PROP_NAME, "bad path");
      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PASSWORD_PROP_NAME, "bad password");
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PATH_PROP_NAME, "bad path");
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PASSWORD_PROP_NAME, "bad password");

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");

      NettyConnector connector = new NettyConnector(params, handler, listener, executorService, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      connector.start();
      assertTrue(connector.isStarted());
      Connection c = connector.createConnection();

      //Should have failed because SSL props override transport config options
      assertNull(c);
      connector.close();
      assertFalse(connector.isStarted());
   }

   @Test
   public void testOverridesJavaSystemProperty() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };

      //system properties will override the bad transport constants
      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "bad path");
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "bad password");
      params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "bad path");
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "bad password");

      NettyConnector connector = new NettyConnector(params, handler, listener, executorService, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      connector.start();
      assertTrue(connector.isStarted());
      Connection c = connector.createConnection();

      //Should not fail because SSL props override transport config options
      assertNotNull(c);
      c.close();
      connector.close();
      assertFalse(connector.isStarted());
   }

   @Test
   public void testOverridesJavaSystemPropertyForceSSLParameters() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };

      //bad system properties will override the transport constants
      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PATH_PROP_NAME, "bad path");
      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PASSWORD_PROP_NAME, "bad password");
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PATH_PROP_NAME, "bad path");
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PASSWORD_PROP_NAME, "bad password");

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.FORCE_SSL_PARAMETERS, true);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      params.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      params.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");

      NettyConnector connector = new NettyConnector(params, handler, listener, executorService, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      connector.start();
      assertTrue(connector.isStarted());
      Connection c = connector.createConnection();

      //Should not fail because forceSSLParameters is set
      assertNotNull(c);
      c.close();
      connector.close();
      assertFalse(connector.isStarted());
   }

   @Test
   public void testOverridesJavaSystemPropertyForceSSLParameters2() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };

      //bad system properties will override the transport constants
      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);

      // forcing SSL parameters will "undo" the values set by the system properties; all properties will be set to default values
      params.put(TransportConstants.FORCE_SSL_PARAMETERS, true);

      NettyConnector connector = new NettyConnector(params, handler, listener, executorService, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      connector.start();
      assertTrue(connector.isStarted());
      Connection c = null;

      try {
         connector.createConnection();
      } catch (Exception e) {
         // ignore
      }

      //Should fail because forceSSLParameters is set
      assertNull(c);
   }

   @Test
   public void testActiveMQSystemProperties() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);

      NettyConnector connector = new NettyConnector(params, handler, listener, executorService, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      System.setProperty(NettyConnector.ACTIVEMQ_KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      System.setProperty(NettyConnector.ACTIVEMQ_KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      System.setProperty(NettyConnector.ACTIVEMQ_TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      System.setProperty(NettyConnector.ACTIVEMQ_TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");

      connector.start();
      assertTrue(connector.isStarted());
      Connection c = connector.createConnection();
      assertNotNull(c);
      connector.close();
      assertFalse(connector.isStarted());
   }

   @Test
   public void testEncryptedActiveMQSystemProperties() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);

      NettyConnector connector = new NettyConnector(params, handler, listener, executorService, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      DefaultSensitiveStringCodec codec = PasswordMaskingUtil.getDefaultCodec();

      System.setProperty(NettyConnector.ACTIVEMQ_KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      System.setProperty(NettyConnector.ACTIVEMQ_KEYSTORE_PASSWORD_PROP_NAME, PasswordMaskingUtil.wrap(codec.encode("securepass")));
      System.setProperty(NettyConnector.ACTIVEMQ_TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      System.setProperty(NettyConnector.ACTIVEMQ_TRUSTSTORE_PASSWORD_PROP_NAME, PasswordMaskingUtil.wrap(codec.encode("securepass")));

      connector.start();
      assertTrue(connector.isStarted());
      Connection c = connector.createConnection();
      assertNotNull(c);
      connector.close();
      assertFalse(connector.isStarted());
   }

   @Test
   public void testEncryptedActiveMQSystemPropertiesFail() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);

      NettyConnector connector = new NettyConnector(params, handler, listener, executorService, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      DefaultSensitiveStringCodec codec = PasswordMaskingUtil.getDefaultCodec();

      System.setProperty(NettyConnector.ACTIVEMQ_KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      System.setProperty(NettyConnector.ACTIVEMQ_KEYSTORE_PASSWORD_PROP_NAME, PasswordMaskingUtil.wrap(codec.encode("bad password")));
      System.setProperty(NettyConnector.ACTIVEMQ_TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      System.setProperty(NettyConnector.ACTIVEMQ_TRUSTSTORE_PASSWORD_PROP_NAME, PasswordMaskingUtil.wrap(codec.encode("bad password")));

      connector.start();
      assertTrue(connector.isStarted());
      assertNull(connector.createConnection());
      connector.close();
      assertFalse(connector.isStarted());
   }

   public static class NettyConnectorTestPasswordCodec implements SensitiveDataCodec<String> {

      private static final String MASK = "supersecureish";
      private static final String CLEARTEXT = "securepass";

      @Override
      public String decode(Object mask) throws Exception {
         if (!MASK.equals(mask)) {
            return mask.toString();
         }
         return CLEARTEXT;
      }

      @Override
      public String encode(Object secret) throws Exception {
         if (!CLEARTEXT.equals(secret)) {
            return secret.toString();
         }
         return MASK;
      }
   }

   @Test
   public void testEncryptedActiveMQSystemPropertiesWithWrappedPasswordsAndCodec() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);

      NettyConnector connector = new NettyConnector(params, handler, listener, executorService, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      NettyConnectorTestPasswordCodec codec = new NettyConnectorTestPasswordCodec();

      System.setProperty(NettyConnector.ACTIVEMQ_SSL_PASSWORD_CODEC_CLASS_PROP_NAME, NettyConnectorTestPasswordCodec.class.getName());
      System.setProperty(NettyConnector.ACTIVEMQ_KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      System.setProperty(NettyConnector.ACTIVEMQ_KEYSTORE_PASSWORD_PROP_NAME, PasswordMaskingUtil.wrap(codec.encode("securepass")));
      System.setProperty(NettyConnector.ACTIVEMQ_TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      System.setProperty(NettyConnector.ACTIVEMQ_TRUSTSTORE_PASSWORD_PROP_NAME, PasswordMaskingUtil.wrap(codec.encode("securepass")));

      connector.start();
      assertTrue(connector.isStarted());
      Connection c = connector.createConnection();
      assertNotNull(c);
      connector.close();
      assertFalse(connector.isStarted());
   }

   @Test
   public void testEncryptedActiveMQSystemPropertiesWithBarePasswordsAndCodec() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);

      NettyConnector connector = new NettyConnector(params, handler, listener, executorService, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      System.setProperty(NettyConnector.ACTIVEMQ_SSL_PASSWORD_CODEC_CLASS_PROP_NAME, NettyConnectorTestPasswordCodec.class.getName());
      System.setProperty(NettyConnector.ACTIVEMQ_KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      System.setProperty(NettyConnector.ACTIVEMQ_KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      System.setProperty(NettyConnector.ACTIVEMQ_TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      System.setProperty(NettyConnector.ACTIVEMQ_TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");

      connector.start();
      assertTrue(connector.isStarted());
      Connection c = connector.createConnection();
      assertNotNull(c);
      connector.close();
      assertFalse(connector.isStarted());
   }

   @Test
   public void testEncryptedActiveMQSystemPropertiesWithCodecFail() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);

      NettyConnector connector = new NettyConnector(params, handler, listener, executorService, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      DefaultSensitiveStringCodec codec = PasswordMaskingUtil.getDefaultCodec();

      System.setProperty(NettyConnector.ACTIVEMQ_SSL_PASSWORD_CODEC_CLASS_PROP_NAME, NettyConnectorTestPasswordCodec.class.getName());
      System.setProperty(NettyConnector.ACTIVEMQ_KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      System.setProperty(NettyConnector.ACTIVEMQ_KEYSTORE_PASSWORD_PROP_NAME, PasswordMaskingUtil.wrap(codec.encode("securepass")));
      System.setProperty(NettyConnector.ACTIVEMQ_TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      System.setProperty(NettyConnector.ACTIVEMQ_TRUSTSTORE_PASSWORD_PROP_NAME, PasswordMaskingUtil.wrap(codec.encode("securepass")));

      connector.start();
      assertTrue(connector.isStarted());
      assertNull(connector.createConnection());
      connector.close();
      assertFalse(connector.isStarted());
   }

   @Test
   public void testActiveMQOverridesSystemProperty() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);

      NettyConnector connector = new NettyConnector(params, handler, listener, executorService, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      System.setProperty(NettyConnector.ACTIVEMQ_KEYSTORE_PATH_PROP_NAME, "client-keystore.jks");
      System.setProperty(NettyConnector.ACTIVEMQ_KEYSTORE_PASSWORD_PROP_NAME, "securepass");
      System.setProperty(NettyConnector.ACTIVEMQ_TRUSTSTORE_PATH_PROP_NAME, "server-ca-truststore.jks");
      System.setProperty(NettyConnector.ACTIVEMQ_TRUSTSTORE_PASSWORD_PROP_NAME, "securepass");

      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PATH_PROP_NAME, "bad path");
      System.setProperty(NettyConnector.JAVAX_KEYSTORE_PASSWORD_PROP_NAME, "bad password");
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PATH_PROP_NAME, "bad path");
      System.setProperty(NettyConnector.JAVAX_TRUSTSTORE_PASSWORD_PROP_NAME, "bad password");

      connector.start();
      assertTrue(connector.isStarted());
      Connection c = connector.createConnection();
      assertNotNull(c);
      connector.close();
      assertFalse(connector.isStarted());
   }

   @Test
   public void testBadCipherSuite() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.ENABLED_CIPHER_SUITES_PROP_NAME, "myBadCipherSuite");

      NettyConnector connector = new NettyConnector(params, handler, listener, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      connector.start();
      assertTrue(connector.isStarted());
      assertNull(connector.createConnection());
      connector.close();
      assertFalse(connector.isStarted());
   }

   @Test
   public void testBadProtocol() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.ENABLED_PROTOCOLS_PROP_NAME, "myBadProtocol");

      NettyConnector connector = new NettyConnector(params, handler, listener, Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())), Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName())));

      connector.start();
      assertTrue(connector.isStarted());
      assertNull(connector.createConnection());
      connector.close();
      assertFalse(connector.isStarted());
   }

   @Test
   public void testChannelHandlerRemovedWhileCreatingConnection() throws Exception {
      BufferHandler handler = (connectionID, buffer) -> {
      };
      Map<String, Object> params = new HashMap<>();
      final ExecutorService closeExecutor = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      final ExecutorService threadPool = Executors.newCachedThreadPool(ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      final ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(5, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      try {
         NettyConnector connector = new NettyConnector(params, handler, listener, closeExecutor, threadPool, scheduledThreadPool);
         connector.start();
         final Connection connection = connector.createConnection(future -> {
            future.awaitUninterruptibly();
            assertTrue(future.isSuccess());
            final ChannelPipeline pipeline = future.channel().pipeline();
            final ActiveMQChannelHandler activeMQChannelHandler = pipeline.get(ActiveMQChannelHandler.class);
            assertNotNull(activeMQChannelHandler);
            pipeline.remove(activeMQChannelHandler);
            assertNull(pipeline.get(ActiveMQChannelHandler.class));
         });
         assertNull(connection);
         connector.close();
      } finally {
         closeExecutor.shutdownNow();
         threadPool.shutdownNow();
         scheduledThreadPool.shutdownNow();
      }
   }

   @Test
   public void testCrcOptionsConfig() throws Exception {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.CRC_OPTIONS_PROP_NAME, "SOFT_FAIL,PREFER_CRLS");

      NettyConnector connector = new NettyConnector(params, (connectionID, buffer) -> { }, listener, null, null, null);
      try {
         assertEquals("SOFT_FAIL,PREFER_CRLS", connector.getCrcOptions());
      } finally {
         connector.close();
      }
   }

   @Test
   public void testOcspResponderURL() throws Exception {
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.OCSP_RESPONDER_URL_PROP_NAME, "http://localhost:8080");

      NettyConnector connector = new NettyConnector(params, (connectionID, buffer) -> { }, listener, null, null, null);
      try {
         assertEquals("http://localhost:8080", connector.getOcspResponderURL());
      } finally {
         connector.close();
      }
   }
}
