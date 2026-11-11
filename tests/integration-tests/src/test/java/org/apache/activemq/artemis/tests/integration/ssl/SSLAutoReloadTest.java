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
package org.apache.activemq.artemis.tests.integration.ssl;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.PemConfigUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

/**
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
public class SSLAutoReloadTest extends ActiveMQTestBase {

   private final String PASSWORD = "securepass";

   @Test
   public void testOneWaySSLWithAutoReload() throws Exception {

      File parentDir = new File(temporaryFolder, "sub");
      parentDir.mkdirs();

      // reference keystore from temp location that we can update
      final File keyStoreToReload = new File(parentDir, "server-ks.p12");
      copyRecursive(new File(this.getClass().getClassLoader().getResource("unknown-server-keystore.p12").getFile()), keyStoreToReload);

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_AUTO_RELOAD_PROP_NAME, true);
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, keyStoreToReload.getAbsolutePath());
      params.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, PASSWORD);
      params.put(TransportConstants.HOST_PROP_NAME, "localhost");

      ConfigurationImpl config = createBasicConfig().addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params, "nettySSL"));
      ActiveMQServer server = createServer(false, config);
      server.getConfiguration().setConfigurationFileRefreshPeriod(50);
      server.start();
      waitForServerToStart(server);

      String url = "tcp://127.0.0.1:61616?sslEnabled=true;trustStorePath=server-ca-truststore.p12;trustStorePassword=" + PASSWORD;
      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocator(url)).setCallTimeout(3000);

      try {
         createSessionFactory(locator);
         fail("Creating session here should fail due to SSL handshake problems.");
      } catch (Exception ignored) {
      }

      // update the server side keystore
      copyRecursive(new File(this.getClass().getClassLoader().getResource("server-keystore.p12").getFile()), keyStoreToReload);

      // expect success after auto reload, which we wait for
      Wait.waitFor(() -> {
         try {
            addSessionFactory(createSessionFactory(locator));
            return true;
         } catch (Throwable ignored) {
         }
         return false;
      }, 5000, 100);
   }

   @Test
   public void testOneWaySSLWithAutoReloadPemConfigSources() throws Exception {
      File serverKeyFile = File.createTempFile("junit", null, temporaryFolder);
      File serverCertFile = File.createTempFile("junit", null, temporaryFolder);
      File serverPemConfigFile = File.createTempFile("junit", null, temporaryFolder);

      Files.copy(this.getClass().getClassLoader().getResourceAsStream("unknown-server-key.pem"),
         serverKeyFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      Files.copy(this.getClass().getClassLoader().getResourceAsStream("unknown-server-cert.pem"),
         serverCertFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      Files.write(serverPemConfigFile.toPath(), Arrays.asList(new String[]{
         "source.key=" + serverKeyFile.getAbsolutePath(),
         "source.cert=" + serverCertFile.getAbsolutePath()
      }));

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SSL_AUTO_RELOAD_PROP_NAME, true);
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      params.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, serverPemConfigFile.getAbsolutePath());
      params.put(TransportConstants.KEYSTORE_TYPE_PROP_NAME, PemConfigUtil.PEMCFG_STORE_TYPE);

      ConfigurationImpl config = createBasicConfig().addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params, "nettySSL"));
      ActiveMQServer server = createServer(false, config);
      server.getConfiguration().setConfigurationFileRefreshPeriod(50);
      server.start();
      waitForServerToStart(server);

      String url = "tcp://127.0.0.1:61616?sslEnabled=true;trustStorePath=server-ca-truststore.p12;trustStorePassword=" + PASSWORD;
      ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocator(url)).setCallTimeout(3000);

      try {
         createSessionFactory(locator);
         fail("Creating session here should fail due to SSL handshake problems.");
      } catch (Exception ignored) {
      }

      // update server PEM config sources
      Files.copy(this.getClass().getClassLoader().getResourceAsStream("server-key.pem"),
         serverKeyFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      Files.copy(this.getClass().getClassLoader().getResourceAsStream("server-cert.pem"),
         serverCertFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

      // expect success after auto reload, which we wait for
      Wait.waitFor(() -> {
         try {
            addSessionFactory(createSessionFactory(locator));
            return true;
         } catch (Throwable ignored) {
         }
         return false;
      }, 5000, 100);
   }
}
