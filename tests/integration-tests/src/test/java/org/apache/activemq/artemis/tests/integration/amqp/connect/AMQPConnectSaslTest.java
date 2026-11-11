/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.amqp.connect;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.qpid.protonj2.test.driver.ProtonTestServer;
import org.apache.qpid.protonj2.test.driver.ProtonTestServerOptions;
import org.apache.qpid.protonj2.test.driver.codec.security.SaslCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * See the tests/security-resources/build.sh script for details on the security resources used.
 */
public class AMQPConnectSaslTest extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final int BROKER_PORT_NUM = AMQP_PORT + 1;

   private static final String SERVER_KEYSTORE_NAME = "server-keystore.jks";
   private static final String UNKNOWN_SERVER_KEYSTORE_NAME = "unknown-server-keystore.jks";
   private static final String SERVER_KEYSTORE_PASSWORD = "securepass";
   private static final String CLIENT_KEYSTORE_NAME = "client-keystore.jks";
   private static final String CLIENT_KEYSTORE_PASSWORD = "securepass";
   private static final String SERVER_TRUSTSTORE_NAME = "server-ca-truststore.jks";
   private static final String SERVER_TRUSTSTORE_PASSWORD = "securepass";
   private static final String CLIENT_TRUSTSTORE_NAME = "client-ca-truststore.jks";
   private static final String CLIENT_TRUSTSTORE_PASSWORD = "securepass";

   private static final String USER = "MY_USER";
   private static final String PASSWD = "PASSWD_VALUE";

   private static final String PLAIN = "PLAIN";
   private static final String ANONYMOUS = "ANONYMOUS";
   private static final String EXTERNAL = "EXTERNAL";
   private static final String SCRAM_SHA_512 = "SCRAM-SHA-512";
   private static final String XOAUTH2 = "XOAUTH2";

   @Override
   protected ActiveMQServer createServer() throws Exception {
      // Creates the broker used to make the outgoing connection. The port passed is for
      // that brokers acceptor. The test server connected to by the broker binds to a random port.
      return createServer(BROKER_PORT_NUM, false);
   }

   @Test
   @Timeout(20)
   public void testConnectsWithAnonymous() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect(PLAIN, ANONYMOUS);
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.debug("Connect test started, peer listening on: {}", remoteURI);

         // No user or pass given, it will have to select ANONYMOUS even though PLAIN also offered
         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(20)
   public void testConnectsWithPlain() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect(USER, PASSWD, PLAIN, ANONYMOUS);
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.debug("Connect test started, peer listening on: {}", remoteURI);

         // User and pass are given, it will select PLAIN
         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setUser(USER);
         amqpConnection.setPassword(PASSWD);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(20)
   public void testReconnectAfterSaslFailure() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         // First attempt, refuse
         peer.expectFailingSASLPlainConnect(SaslCode.SYS_TEMP.getValue().byteValue(), PLAIN, ANONYMOUS);
         peer.expectConnectionToDrop();

         // Second attempt, succeeds
         peer.expectSASLPlainConnect(USER, PASSWD, PLAIN, ANONYMOUS);
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.debug("Connect test started, peer listening on: {}", remoteURI);

         AMQPBrokerConnectConfiguration amqpConnection =
               new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(1); // Allow 1 reconnect
         amqpConnection.setRetryInterval(200);
         amqpConnection.setUser(USER);
         amqpConnection.setPassword(PASSWD);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(20)
   public void testConnectsWithXOauth2() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLHeader().respondWithSASLHeader();
         peer.remoteSaslMechanisms().withMechanisms(PLAIN, XOAUTH2, ANONYMOUS).queue();
         peer.expectSaslInit().withMechanism(XOAUTH2).withInitialResponse(peer.saslXOauth2InitialResponse(USER, PASSWD));
         peer.remoteSaslOutcome().withCode(SaslCode.OK).queue();
         peer.expectAMQPHeader().respondWithAMQPHeader();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.debug("Connect test started, peer listening on: {}", remoteURI);

         AMQPBrokerConnectConfiguration amqpConnection =
             new AMQPBrokerConnectConfiguration(getTestName(), "tcp://localhost:" + remoteURI.getPort() + "?saslMechanisms=" + XOAUTH2);
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setUser(USER);
         amqpConnection.setPassword(PASSWD);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(20)
   public void testXOAuth2NotSelectedWhenUserAndPasswordNotProvided() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLAnonymousConnect(XOAUTH2, ANONYMOUS);
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.debug("Connect test started, peer listening on: {}", remoteURI);

         AMQPBrokerConnectConfiguration amqpConnection =
             new AMQPBrokerConnectConfiguration(getTestName(),
                 "tcp://localhost:" + remoteURI.getPort() + "?saslMechanisms=" + XOAUTH2 + "," + ANONYMOUS);
         amqpConnection.setReconnectAttempts(0);// No reconnects

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(20)
   public void testConnectsWithPlainWhenPlainAndXOauth2IsValid() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLPlainConnect(USER, PASSWD, PLAIN, XOAUTH2, ANONYMOUS);
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.debug("Connect test started, peer listening on: {}", remoteURI);

         AMQPBrokerConnectConfiguration amqpConnection =
             new AMQPBrokerConnectConfiguration(getTestName(),
                 "tcp://localhost:" + remoteURI.getPort() + "?saslMechanisms=" + XOAUTH2 + "," + PLAIN);
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setUser(USER);
         amqpConnection.setPassword(PASSWD);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(20)
   public void testConnectionFailsIfFilterExcudesAnyOfferedMechanism() throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSASLHeader().respondWithSASLHeader();
         peer.remoteSaslMechanisms().withMechanisms(PLAIN, ANONYMOUS).queue();
         peer.expectConnectionToDrop();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.debug("Connect test started, peer listening on: {}", remoteURI);

         AMQPBrokerConnectConfiguration amqpConnection =
             new AMQPBrokerConnectConfiguration(getTestName(),
                 "tcp://localhost:" + remoteURI.getPort() + "?saslMechanisms=" + XOAUTH2);
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setUser(USER);
         amqpConnection.setPassword(PASSWD);

         server.getConfiguration().addAMQPConnection(amqpConnection);
         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(20)
   public void testAnonymousSelectedWhenNoCredentialsSupplied() throws Exception {
      doMechanismSelectedTestImpl(null, null, ANONYMOUS, new String[]{SCRAM_SHA_512, PLAIN, ANONYMOUS});
   }

   @Test
   @Timeout(20)
   public void testSelectsSCRAMWhenCredentialsPresent() throws Exception {
      doMechanismSelectedTestImpl(USER, PASSWD, SCRAM_SHA_512, new String[]{SCRAM_SHA_512, PLAIN, ANONYMOUS});
   }

   private void doMechanismSelectedTestImpl(String user, String passwd, String selectedMechanism, String[] offeredMechanisms) throws Exception {
      try (ProtonTestServer peer = new ProtonTestServer()) {
         peer.expectSaslConnectThatAlwaysFailsAuthentication(offeredMechanisms, selectedMechanism);
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.debug("Connect test started, peer listening on: {}", remoteURI);

         AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration(getTestName(),
               "tcp://localhost:" + remoteURI.getPort());
         amqpConnection.setReconnectAttempts(0);// No reconnects
         if (user != null) {
            amqpConnection.setUser(user);
         }
         if (passwd != null) {
            amqpConnection.setPassword(passwd);
         }

         server.getConfiguration().addAMQPConnection(amqpConnection);

         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(20)
   public void testConnectsWithExternal() throws Exception {
      doConnectWithExternalTestImpl(true);
   }

   @Test
   @Timeout(20)
   public void testExternalIgnoredWhenNoClientCertSupplied() throws Exception {
      doConnectWithExternalTestImpl(false);
   }

   private void doConnectWithExternalTestImpl(boolean requireClientCert) throws Exception {
      final String keyStorePath = this.getClass().getClassLoader().getResource(SERVER_KEYSTORE_NAME).getFile();
      final String trustStorePath = this.getClass().getClassLoader().getResource(CLIENT_TRUSTSTORE_NAME).getFile();

      ProtonTestServerOptions serverOptions = new ProtonTestServerOptions();
      serverOptions.setSecure(true);
      serverOptions.setKeyStoreLocation(keyStorePath);
      serverOptions.setKeyStorePassword(SERVER_KEYSTORE_PASSWORD);
      serverOptions.setVerifyHost(false);

      if (requireClientCert) {
         serverOptions.setNeedClientAuth(true);
         serverOptions.setTrustStoreLocation(trustStorePath);
         serverOptions.setTrustStorePassword(CLIENT_TRUSTSTORE_PASSWORD);
      }

      try (ProtonTestServer peer = new ProtonTestServer(serverOptions)) {
         // The test server always offers EXTERNAL, i.e sometimes mistakenly, to verify that the broker
         // only selects it when it actually has a client-cert. Real servers shouldn't actually offer
         // the mechanism to a client that didn't have to provide a cert.
         peer.expectSASLHeader().respondWithSASLHeader();
         peer.remoteSaslMechanisms().withMechanisms(EXTERNAL, PLAIN).queue();
         if (requireClientCert) {
            peer.expectSaslInit().withMechanism(EXTERNAL).withInitialResponse(new byte[0]);
         } else {
            peer.expectSaslInit().withMechanism(PLAIN).withInitialResponse(peer.saslPlainInitialResponse(USER, PASSWD));
         }
         peer.remoteSaslOutcome().withCode(SaslCode.OK).queue();
         peer.expectAMQPHeader().respondWithAMQPHeader();
         peer.expectOpen().respond();
         peer.expectBegin().respond();
         peer.start();

         final URI remoteURI = peer.getServerURI();
         logger.debug("Connect test started, peer listening on: {}", remoteURI);

         String amqpServerConnectionURI = "tcp://localhost:" + remoteURI.getPort() +
                  "?sslEnabled=true;trustStorePath=" + SERVER_TRUSTSTORE_NAME +
                  ";trustStorePassword=" + SERVER_TRUSTSTORE_PASSWORD;
         if (requireClientCert) {
            amqpServerConnectionURI +=
                     ";keyStorePath=" + CLIENT_KEYSTORE_NAME + ";keyStorePassword=" + CLIENT_KEYSTORE_PASSWORD;
         }

         AMQPBrokerConnectConfiguration amqpConnection =
                  new AMQPBrokerConnectConfiguration(getTestName(), amqpServerConnectionURI);
         amqpConnection.setReconnectAttempts(0);// No reconnects
         amqpConnection.setUser(USER); // Wont matter if EXTERNAL is offered and a client-certificate
                                       // is provided, but will otherwise.
         amqpConnection.setPassword(PASSWD);

         server.getConfiguration().addAMQPConnection(amqpConnection);

         server.start();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(20)
   public void testReconnectConnectsWithVerifyHostOffOnSecondURI() throws Exception {
      final String keyStorePath = this.getClass().getClassLoader().getResource(UNKNOWN_SERVER_KEYSTORE_NAME).getFile();

      ProtonTestServerOptions server1Options = new ProtonTestServerOptions();
      server1Options.setSecure(true);
      server1Options.setKeyStoreLocation(keyStorePath);
      server1Options.setKeyStorePassword(SERVER_KEYSTORE_PASSWORD);
      server1Options.setVerifyHost(false);

      ProtonTestServerOptions server2Options = new ProtonTestServerOptions();
      server2Options.setSecure(true);
      server2Options.setKeyStoreLocation(keyStorePath);
      server2Options.setKeyStorePassword(SERVER_KEYSTORE_PASSWORD);
      server2Options.setVerifyHost(false);

      try (ProtonTestServer firstPeer = new ProtonTestServer(server1Options);
           ProtonTestServer secondPeer = new ProtonTestServer(server2Options)) {

         firstPeer.expectConnectionToDrop();
         firstPeer.start();

         secondPeer.expectSASLHeader().respondWithSASLHeader();
         secondPeer.remoteSaslMechanisms().withMechanisms(EXTERNAL, PLAIN).queue();
         secondPeer.expectSaslInit().withMechanism(PLAIN).withInitialResponse(secondPeer.saslPlainInitialResponse(USER, PASSWD));
         secondPeer.remoteSaslOutcome().withCode(SaslCode.OK).queue();
         secondPeer.expectAMQPHeader().respondWithAMQPHeader();
         secondPeer.expectOpen().respond();
         secondPeer.expectBegin().respond();
         secondPeer.start();

         final URI firstPeerURI = firstPeer.getServerURI();
         logger.debug("Connect test started, first peer listening on: {}", firstPeerURI);

         final URI secondPeerURI = secondPeer.getServerURI();
         logger.debug("Connect test started, second peer listening on: {}", secondPeerURI);

         // First connection fails because we use a server certificate with whose common name
         // doesn't match the host, second connection should work as we disable host verification
         String amqpServerConnectionURI =
            "tcp://localhost:" + firstPeerURI.getPort() + "?verifyHost=true" +
               ";sslEnabled=true;trustStorePath=" + SERVER_TRUSTSTORE_NAME +
               ";trustStorePassword=" + SERVER_TRUSTSTORE_PASSWORD +
            "#tcp://localhost:" + secondPeerURI.getPort() + "?verifyHost=false";

         AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), amqpServerConnectionURI);
         amqpConnection.setReconnectAttempts(20); // Allow reconnects
         amqpConnection.setRetryInterval(100); // Allow reconnects
         amqpConnection.setUser(USER);
         amqpConnection.setPassword(PASSWD);

         server.getConfiguration().addAMQPConnection(amqpConnection);

         server.start();

         firstPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         secondPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(20)
   public void testReconnectionUsesConfigurationToReconnectToSecondHostAfterFirstFails() throws Exception {
      final String keyStore1Path = this.getClass().getClassLoader().getResource(UNKNOWN_SERVER_KEYSTORE_NAME).getFile();
      final String keyStore2Path = this.getClass().getClassLoader().getResource(SERVER_KEYSTORE_NAME).getFile();

      ProtonTestServerOptions server1Options = new ProtonTestServerOptions();
      server1Options.setSecure(true);
      server1Options.setKeyStoreLocation(keyStore1Path);
      server1Options.setKeyStorePassword(SERVER_KEYSTORE_PASSWORD);
      server1Options.setVerifyHost(false);

      ProtonTestServerOptions server2Options = new ProtonTestServerOptions();
      server2Options.setSecure(true);
      server2Options.setKeyStoreLocation(keyStore2Path);
      server2Options.setKeyStorePassword(SERVER_KEYSTORE_PASSWORD);
      server2Options.setVerifyHost(false);

      try (ProtonTestServer firstPeer = new ProtonTestServer(server1Options);
           ProtonTestServer secondPeer = new ProtonTestServer(server2Options)) {

         firstPeer.expectConnectionToDrop();
         firstPeer.start();

         secondPeer.expectSASLHeader().respondWithSASLHeader();
         secondPeer.remoteSaslMechanisms().withMechanisms(EXTERNAL, PLAIN).queue();
         secondPeer.expectSaslInit().withMechanism(PLAIN)
                                    .withInitialResponse(secondPeer.saslPlainInitialResponse(USER, PASSWD));
         secondPeer.remoteSaslOutcome().withCode(SaslCode.OK).queue();
         secondPeer.expectAMQPHeader().respondWithAMQPHeader();
         secondPeer.expectOpen().respond();
         secondPeer.expectBegin().respond();
         secondPeer.start();

         final URI firstPeerURI = firstPeer.getServerURI();
         logger.debug("Connect test started, first peer listening on: {}", firstPeerURI);

         final URI secondPeerURI = secondPeer.getServerURI();
         logger.debug("Connect test started, second peer listening on: {}", secondPeerURI);

         String amqpServerConnectionURI =
            "tcp://127.0.0.1:" + firstPeerURI.getPort() + "?sslEnabled=true;trustStorePath=" + SERVER_TRUSTSTORE_NAME +
                                                          ";trustStorePassword=" + SERVER_TRUSTSTORE_PASSWORD +
            "#tcp://localhost:" + secondPeerURI.getPort();

         AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), amqpServerConnectionURI);
         amqpConnection.setReconnectAttempts(20); // Allow reconnects
         amqpConnection.setRetryInterval(100); // Allow reconnects
         amqpConnection.setUser(USER);
         amqpConnection.setPassword(PASSWD);

         server.getConfiguration().addAMQPConnection(amqpConnection);

         server.start();

         firstPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         secondPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }

   @Test
   @Timeout(20)
   public void testReconnectionUsesHostSpecificConfigurationToReconnectToSecondHostAfterFirstFails() throws Exception {
      final String keyStore1Path = this.getClass().getClassLoader().getResource(UNKNOWN_SERVER_KEYSTORE_NAME).getFile();
      final String keyStore2Path = this.getClass().getClassLoader().getResource(SERVER_KEYSTORE_NAME).getFile();

      ProtonTestServerOptions server1Options = new ProtonTestServerOptions();
      server1Options.setSecure(true);
      server1Options.setKeyStoreLocation(keyStore1Path);
      server1Options.setKeyStorePassword(SERVER_KEYSTORE_PASSWORD);
      server1Options.setVerifyHost(false);

      ProtonTestServerOptions server2Options = new ProtonTestServerOptions();
      server2Options.setSecure(true);
      server2Options.setKeyStoreLocation(keyStore2Path);
      server2Options.setKeyStorePassword(SERVER_KEYSTORE_PASSWORD);
      server2Options.setVerifyHost(false);

      try (ProtonTestServer firstPeer = new ProtonTestServer(server1Options);
           ProtonTestServer secondPeer = new ProtonTestServer(server2Options)) {

         firstPeer.expectConnectionToDrop();
         firstPeer.start();

         secondPeer.expectSASLHeader().respondWithSASLHeader();
         secondPeer.remoteSaslMechanisms().withMechanisms(EXTERNAL, PLAIN).queue();
         secondPeer.expectSaslInit().withMechanism(PLAIN)
                                    .withInitialResponse(secondPeer.saslPlainInitialResponse(USER, PASSWD));
         secondPeer.remoteSaslOutcome().withCode(SaslCode.OK).queue();
         secondPeer.expectAMQPHeader().respondWithAMQPHeader();
         secondPeer.expectOpen().respond();
         secondPeer.expectBegin().respond();
         secondPeer.start();

         final URI firstPeerURI = firstPeer.getServerURI();
         logger.debug("Connect test started, first peer listening on: {}", firstPeerURI);

         final URI secondPeerURI = secondPeer.getServerURI();
         logger.debug("Connect test started, second peer listening on: {}", secondPeerURI);

         // First connection fails because we use the wrong trust store for the TLS handshake
         String amqpServerConnectionURI =
            "tcp://localhost:" + firstPeerURI.getPort() +
               "?sslEnabled=true;trustStorePath=" + CLIENT_TRUSTSTORE_NAME +
               ";trustStorePassword=" + CLIENT_TRUSTSTORE_PASSWORD +
            "#tcp://localhost:" + secondPeerURI.getPort() +
               "?sslEnabled=true;trustStorePath=" + SERVER_TRUSTSTORE_NAME +
               ";trustStorePassword=" + SERVER_TRUSTSTORE_PASSWORD;

         AMQPBrokerConnectConfiguration amqpConnection =
            new AMQPBrokerConnectConfiguration(getTestName(), amqpServerConnectionURI);
         amqpConnection.setReconnectAttempts(20); // Allow reconnects
         amqpConnection.setRetryInterval(100); // Allow reconnects
         amqpConnection.setUser(USER);
         amqpConnection.setPassword(PASSWD);

         server.getConfiguration().addAMQPConnection(amqpConnection);

         server.start();

         firstPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         secondPeer.waitForScriptToComplete(5, TimeUnit.SECONDS);
      }
   }
}
