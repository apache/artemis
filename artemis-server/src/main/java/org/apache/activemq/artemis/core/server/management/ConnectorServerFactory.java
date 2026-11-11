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
package org.apache.activemq.artemis.core.server.management;

import org.apache.activemq.artemis.core.remoting.impl.ssl.SSLSupport;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Map;

public class ConnectorServerFactory {

   public void setkeyStoreProvider(String keyStoreProvider) {
      this.keyStoreProvider = keyStoreProvider;
   }

   public void setkeyStoreType(String keyStoreType) {
      this.keyStoreType = keyStoreType;
   }

   public void setKeyStorePassword(String keyStorePassword) {
      this.keyStorePassword = keyStorePassword;
   }

   public void setTrustStorePath(String trustStorePath) {
      this.trustStorePath = trustStorePath;
   }

   public void setTrustStoreProvider(String trustStoreProvider) {
      this.trustStoreProvider = trustStoreProvider;
   }

   public void setTrustStoreType(String trustStoreType) {
      this.trustStoreType = trustStoreType;
   }

   public void setTrustStorePassword(String trustStorePassword) {
      this.trustStorePassword = trustStorePassword;
   }

   private enum AuthenticatorType { NONE, PASSWORD, CERTIFICATE };

   private MBeanServer server;
   private String serviceUrl;
   private String rmiServerHost;
   private Map environment;
   private ObjectName objectName;
   private JMXConnectorServer connectorServer;

   private AuthenticatorType authenticatorType = AuthenticatorType.PASSWORD;

   private boolean secured;

   private String keyStoreProvider;

   private String keyStoreType;

   private String keyStorePath;

   private String keyStorePassword;

   private String trustStoreProvider;

   private String trustStoreType;

   private String trustStorePath;

   private String trustStorePassword;


   public String getKeyStoreProvider() {
      return keyStoreProvider;
   }

   public String getKeyStoreType() {
      return keyStoreType;
   }

   public String getKeyStorePath() {
      return keyStorePath;
   }

   public void setKeyStorePath(String keyStorePath) {
      this.keyStorePath = keyStorePath;
   }

   public String getKeyStorePassword() {
      return keyStorePassword;
   }

   public String getTrustStoreProvider() {
      return trustStoreProvider;
   }

   public String getTrustStoreType() {
      return trustStoreType;
   }

   public String getTrustStorePath() {
      return trustStorePath;
   }

   public String getTrustStorePassword() {
      return trustStorePassword;
   }

   public MBeanServer getServer() {
      return server;
   }

   public void setServer(MBeanServer server) {
      this.server = server;
   }

   public String getServiceUrl() {
      return serviceUrl;
   }

   public void setServiceUrl(String serviceUrl) {
      this.serviceUrl = serviceUrl;
   }

   public String getRmiServerHost() {
      return this.rmiServerHost;
   }

   public void setRmiServerHost(String rmiServerHost) {
      this.rmiServerHost = rmiServerHost;
   }

   public Map getEnvironment() {
      return environment;
   }

   public void setEnvironment(Map environment) {
      this.environment = environment;
   }

   public ObjectName getObjectName() {
      return objectName;
   }

   public void setObjectName(ObjectName objectName) {
      this.objectName = objectName;
   }

   public String getAuthenticatorType() {
      return this.authenticatorType.name().toLowerCase();
   }

   /**
    * Authenticator type to use. Acceptable values are "none", "password", and "certificate"
    */
   public void setAuthenticatorType(String value) {
      this.authenticatorType = AuthenticatorType.valueOf(value.toUpperCase());
   }

   public boolean isSecured() {
      return this.secured;
   }

   public void setSecured(boolean secured) {
      this.secured = secured;
   }

   private boolean isClientAuth() {
      return this.authenticatorType.equals(AuthenticatorType.CERTIFICATE);
   }

   public void init() throws Exception {

      if (this.server == null) {
         throw new IllegalArgumentException("server must be set");
      }
      JMXServiceURL url = new JMXServiceURL(this.serviceUrl);
      setupArtemisRMIServerSocketFactory();
      if (isClientAuth()) {
         this.secured = true;
      }

      if (this.secured) {
         this.setupSsl();
      }

      if (!AuthenticatorType.PASSWORD.equals(this.authenticatorType)) {
         this.environment.remove("jmx.remote.authenticator");
      }

      this.connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(url, this.environment, this.server);
      if (this.objectName != null) {
         this.server.registerMBean(this.connectorServer, this.objectName);
      }

      try {
         this.connectorServer.start();
      } catch (Exception ex) {
         doUnregister(this.objectName);
         throw ex;
      }
   }

   public void destroy() throws Exception {
      try {
         if (this.connectorServer != null) {
            this.connectorServer.stop();
         }
      } finally {
         doUnregister(this.objectName);
      }
   }

   protected void doUnregister(ObjectName objectName) {
      try {
         if (this.objectName != null && this.server.isRegistered(objectName)) {
            this.server.unregisterMBean(objectName);
         }
      } catch (JMException ex) {
         // Ignore
      }
   }

   //todo fix
   private void setupSsl() throws Exception {
      SSLContext context = new SSLSupport()
         .setKeystoreProvider(keyStoreProvider)
         .setKeystoreType(keyStoreType)
         .setKeystorePath(keyStorePath)
         .setKeystorePassword(keyStorePassword)
         .setTruststoreProvider(trustStoreProvider)
         .setTruststoreType(trustStoreType)
         .setTruststorePath(trustStorePath)
         .setTruststorePassword(trustStorePassword)
         .createContext();
      SSLServerSocketFactory sssf = context.getServerSocketFactory();
      RMIServerSocketFactory rssf = new ArtemisSslRMIServerSocketFactory(sssf, this.isClientAuth(), rmiServerHost);
      RMIClientSocketFactory rcsf = new SslRMIClientSocketFactory();
      environment.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE, rssf);
      environment.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE, rcsf);
   }

   private void setupArtemisRMIServerSocketFactory() {
      RMIServerSocketFactory rmiServerSocketFactory = new ArtemisRMIServerSocketFactory(getRmiServerHost());
      environment.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE, rmiServerSocketFactory);
   }

   private static class ArtemisSslRMIServerSocketFactory implements RMIServerSocketFactory {
      private SSLServerSocketFactory sssf;
      private boolean clientAuth;
      private String rmiServerHost;

      ArtemisSslRMIServerSocketFactory(SSLServerSocketFactory sssf, boolean clientAuth, String rmiServerHost) {
         this.sssf = sssf;
         this.clientAuth = clientAuth;
         this.rmiServerHost = rmiServerHost;
      }

      @Override
      public ServerSocket createServerSocket(int port) throws IOException {
         SSLServerSocket ss = (SSLServerSocket) sssf.createServerSocket(port, 50, InetAddress.getByName(rmiServerHost));
         ss.setNeedClientAuth(clientAuth);
         return ss;
      }
   }

   private static class ArtemisRMIServerSocketFactory implements RMIServerSocketFactory {
      private String rmiServerHost;

      ArtemisRMIServerSocketFactory(String rmiServerHost) {
         this.rmiServerHost = rmiServerHost;
      }

      @Override
      public ServerSocket createServerSocket(int port) throws IOException {
         ServerSocket serverSocket = ServerSocketFactory.getDefault().createServerSocket(port, 50, InetAddress.getByName(rmiServerHost));
         return serverSocket;
      }
   }


}
