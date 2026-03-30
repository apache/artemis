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
package org.apache.activemq.artemis.core.remoting.impl.ssl;

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CRL;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TrustManagerFactoryPlugin;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextConfig;
import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;

/**
 * Please note, this class supports PKCS#11 keystores, but there are no specific tests in the Apache Artemis
 * test-suite to validate/verify this works because this requires a functioning PKCS#11 provider which is not available
 * by default (see java.security.Security#getProviders()).  The main thing to keep in mind is that PKCS#11 keystores
 * will either use null, and empty string, or NONE for their keystore path.
 */
public class SSLSupport {

   public static final String NONE = "NONE";
   private String keystoreProvider = TransportConstants.DEFAULT_KEYSTORE_PROVIDER;
   private String keystoreType = TransportConstants.DEFAULT_KEYSTORE_TYPE;
   private String keystorePath = TransportConstants.DEFAULT_KEYSTORE_PATH;
   private String keystorePassword = TransportConstants.DEFAULT_KEYSTORE_PASSWORD;
   private String keyPassword = TransportConstants.DEFAULT_KEY_PASSWORD;
   private String truststoreProvider = TransportConstants.DEFAULT_TRUSTSTORE_PROVIDER;
   private String truststoreType = TransportConstants.DEFAULT_TRUSTSTORE_TYPE;
   private String truststorePath = TransportConstants.DEFAULT_TRUSTSTORE_PATH;
   private String truststorePassword = TransportConstants.DEFAULT_TRUSTSTORE_PASSWORD;
   private String crlPath = TransportConstants.DEFAULT_CRL_PATH;
   private String sslProvider = TransportConstants.DEFAULT_SSL_PROVIDER;
   private boolean trustAll = TransportConstants.DEFAULT_TRUST_ALL;
   private String trustManagerFactoryPlugin = TransportConstants.DEFAULT_TRUST_MANAGER_FACTORY_PLUGIN;
   private String keystoreAlias = TransportConstants.DEFAULT_KEYSTORE_ALIAS;
   private String crcOptions = TransportConstants.DEFAULT_CRC_OPTIONS;
   private String ocspResponderURL = TransportConstants.DEFAULT_OCSP_RESPONDER_URL;

   public SSLSupport() {
   }

   public SSLSupport(final SSLContextConfig config) {
      keystoreProvider = config.getKeystoreProvider();
      keystorePath = config.getKeystorePath();
      keystoreType = config.getKeystoreType();
      keystorePassword = config.getKeystorePassword();
      keyPassword = config.getKeyPassword();
      truststoreProvider = config.getTruststoreProvider();
      truststorePath = config.getTruststorePath();
      truststoreType = config.getTruststoreType();
      truststorePassword = config.getTruststorePassword();
      crlPath = config.getCrlPath();
      trustAll = config.isTrustAll();
      trustManagerFactoryPlugin = config.getTrustManagerFactoryPlugin();
      keystoreAlias = config.getKeystoreAlias();
      crcOptions = config.getCrcOptions();
      ocspResponderURL = config.getOcspResponderURL();
   }

   public String getKeystoreProvider() {
      return keystoreProvider;
   }

   public SSLSupport setKeystoreProvider(String keystoreProvider) {
      this.keystoreProvider = keystoreProvider;
      return this;
   }

   public String getKeystoreType() {
      return keystoreType;
   }

   public SSLSupport setKeystoreType(String keystoreType) {
      this.keystoreType = keystoreType;
      return this;
   }

   public String getKeystorePath() {
      return keystorePath;
   }

   public SSLSupport setKeystorePath(String keystorePath) {
      this.keystorePath = keystorePath;
      return this;
   }

   public String getKeystorePassword() {
      return keystorePassword;
   }

   public SSLSupport setKeystorePassword(String keystorePassword) {
      this.keystorePassword = keystorePassword;
      return this;
   }

   public String getKeyPassword() {
      return keyPassword;
   }

   public SSLSupport setKeyPassword(String keyPassword) {
      this.keyPassword = keyPassword;
      return this;
   }

   public String getKeystoreAlias() {
      return keystoreAlias;
   }

   public SSLSupport setKeystoreAlias(String keystoreAlias) {
      this.keystoreAlias = keystoreAlias;
      return this;
   }

   public String getTruststoreProvider() {
      return truststoreProvider;
   }

   public SSLSupport setTruststoreProvider(String truststoreProvider) {
      this.truststoreProvider = truststoreProvider;
      return this;
   }

   public String getTruststoreType() {
      return truststoreType;
   }

   public SSLSupport setTruststoreType(String truststoreType) {
      this.truststoreType = truststoreType;
      return this;
   }

   public String getTruststorePath() {
      return truststorePath;
   }

   public SSLSupport setTruststorePath(String truststorePath) {
      this.truststorePath = truststorePath;
      return this;
   }

   public String getTruststorePassword() {
      return truststorePassword;
   }

   public SSLSupport setTruststorePassword(String truststorePassword) {
      this.truststorePassword = truststorePassword;
      return this;
   }

   public String getCrlPath() {
      return crlPath;
   }

   public SSLSupport setCrlPath(String crlPath) {
      this.crlPath = crlPath;
      return this;
   }

   public String getSslProvider() {
      return sslProvider;
   }

   public SSLSupport setSslProvider(String sslProvider) {
      this.sslProvider = sslProvider;
      return this;
   }

   public boolean isTrustAll() {
      return trustAll;
   }

   public SSLSupport setTrustAll(boolean trustAll) {
      this.trustAll = trustAll;
      return this;
   }

   public String getTrustManagerFactoryPlugin() {
      return trustManagerFactoryPlugin;
   }

   public SSLSupport setTrustManagerFactoryPlugin(String trustManagerFactoryPlugin) {
      this.trustManagerFactoryPlugin = trustManagerFactoryPlugin;
      return this;
   }

   public String getCrcOptions() {
      return crcOptions;
   }

   public SSLSupport setCrcOptions(String crcOptions) {
      this.crcOptions = crcOptions;
      return this;
   }

   public String getOcspResponderURL() {
      return ocspResponderURL;
   }

   public SSLSupport setOcspResponderURL(String ocspResponderURL) {
      this.ocspResponderURL = ocspResponderURL;
      return this;
   }

   public SSLContext createContext() throws Exception {
      SSLContext context = SSLContext.getInstance("TLS");
      KeyManager[] keyManagers = loadKeyManagers();
      TrustManager[] trustManagers = loadTrustManagers();
      context.init(keyManagers, trustManagers, new SecureRandom());
      return context;
   }

   public SslContext createNettyContext() throws Exception {
      KeyStore keyStore = SSLSupport.loadKeystore(keystoreProvider, keystoreType, keystorePath, keystorePassword);
      SslContextBuilder sslContextBuilder;
      if (keystoreAlias != null) {
         Pair<PrivateKey, X509Certificate[]> privateKeyAndCertChain = getPrivateKeyAndCertChain(keyStore);
         sslContextBuilder = SslContextBuilder.forServer(privateKeyAndCertChain.getA(), privateKeyAndCertChain.getB());
      } else {
         sslContextBuilder = SslContextBuilder.forServer(getKeyManagerFactory(keyStore, getKeyPasswordOrDefault()));
      }
      return sslContextBuilder
         .sslProvider(SslProvider.valueOf(sslProvider))
         .trustManager(loadTrustManagerFactory())
         .build();
   }

   public SslContext createNettyClientContext() throws Exception {
      KeyStore keyStore = SSLSupport.loadKeystore(keystoreProvider, keystoreType, keystorePath, keystorePassword);
      SslContextBuilder sslContextBuilder = SslContextBuilder
         .forClient()
         .sslProvider(SslProvider.valueOf(sslProvider))
         .trustManager(loadTrustManagerFactory());
      if (keystoreAlias != null) {
         Pair<PrivateKey, X509Certificate[]> privateKeyAndCertChain = getPrivateKeyAndCertChain(keyStore);
         sslContextBuilder.keyManager(privateKeyAndCertChain.getA(), privateKeyAndCertChain.getB());
      } else {
         sslContextBuilder.keyManager(getKeyManagerFactory(keyStore, getKeyPasswordOrDefault()));
      }

      return sslContextBuilder.build();
   }

   public static String[] parseCommaSeparatedListIntoArray(String suites) {
      String[] cipherSuites = suites.split(",");
      for (int i = 0; i < cipherSuites.length; i++) {
         cipherSuites[i] = cipherSuites[i].trim();
      }
      return cipherSuites;
   }

   public static String parseArrayIntoCommandSeparatedList(String[] suites) {
      StringBuilder supportedSuites = new StringBuilder();

      for (String suite : suites) {
         supportedSuites.append(suite);
         supportedSuites.append(", ");
      }

      // trim the last 2 characters (i.e. unnecessary comma and space)
      return supportedSuites.delete(supportedSuites.length() - 2, supportedSuites.length()).toString();
   }

   private TrustManagerFactory loadTrustManagerFactory() throws Exception {
      if (trustManagerFactoryPlugin != null) {
         return SecurityManagerShim.doPrivileged((PrivilegedAction<TrustManagerFactory>) () -> ((TrustManagerFactoryPlugin) ClassloadingUtil.newInstanceFromClassLoader(SSLSupport.class, trustManagerFactoryPlugin, TrustManagerFactoryPlugin.class)).getTrustManagerFactory());
      } else if (trustAll) {
         //This is useful for testing but not should be used outside of that purpose
         return InsecureTrustManagerFactory.INSTANCE;
      } else if ((truststorePath == null || truststorePath.isEmpty() || truststorePath.equalsIgnoreCase(NONE)) && (truststoreProvider == null || !truststoreProvider.toUpperCase().contains("PKCS11"))) {
         return null;
      } else {
         TrustManagerFactory trustMgrFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
         KeyStore trustStore = SSLSupport.loadKeystore(truststoreProvider, truststoreType, truststorePath, truststorePassword);

         ManagerFactoryParameters managerFactoryParameters = null;
         boolean ocsp = Boolean.parseBoolean(Security.getProperty("ocsp.enable"));
         if ((ocsp || crlPath != null || crcOptions != null || ocspResponderURL != null) && checkPKIXTrustManagerFactory(trustMgrFactory)) {
            PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(trustStore, new X509CertSelector());

            if (crlPath != null) {
               pkixParams.setRevocationEnabled(true);
               Collection<? extends CRL> crlList = loadCRL();
               if (crlList != null) {
                  pkixParams.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(crlList)));
               }
            }

            if (crcOptions != null || ocspResponderURL != null) {
               addCertPathCheckers(pkixParams);
            }

            managerFactoryParameters = new CertPathTrustManagerParameters(pkixParams);
         }

         if (managerFactoryParameters != null) {
            trustMgrFactory.init(managerFactoryParameters);
         } else {
            trustMgrFactory.init(trustStore);
         }

         return trustMgrFactory;
      }
   }

   private boolean checkPKIXTrustManagerFactory(TrustManagerFactory trustMgrFactory) {
      if (trustMgrFactory.getAlgorithm().equalsIgnoreCase("PKIX")) {
         return true;
      }

      if (crlPath != null) {
         throw new IllegalStateException("The crlPath parameter is not supported with the algorithm "
            + trustMgrFactory.getAlgorithm());
      }

      if (crcOptions != null) {
         throw new IllegalStateException("The crcOptions parameter is not supported with the algorithm "
            + trustMgrFactory.getAlgorithm());
      }

      if (ocspResponderURL != null) {
         throw new IllegalStateException("The ocspResponderURL parameter is not supported with the algorithm "
            + trustMgrFactory.getAlgorithm());
      }

      return false;
   }

   protected void addCertPathCheckers(PKIXBuilderParameters pkixParams) throws Exception {
      CertPathBuilder certPathBuilder = CertPathBuilder.getInstance("PKIX");
      PKIXRevocationChecker revocationChecker = (PKIXRevocationChecker) certPathBuilder.getRevocationChecker();
      if (crcOptions != null) {
         revocationChecker.setOptions(loadRevocationOptions());
      }
      if (ocspResponderURL != null) {
         revocationChecker.setOcspResponder(new java.net.URI(ocspResponderURL));
      }
      pkixParams.addCertPathChecker(revocationChecker);


      // Add a certPathChecker to log soft fail exceptions caught by the revocation checker.
      pkixParams.addCertPathChecker(new  PKIXCertPathChecker() {
         @Override
         public void init(boolean forward) throws CertPathValidatorException {
         }

         @Override
         public boolean isForwardCheckingSupported() {
            return revocationChecker.isForwardCheckingSupported();
         }

         @Override
         public Set<String> getSupportedExtensions() {
            return revocationChecker.getSupportedExtensions();
         }

         @Override
         public void check(Certificate cert, Collection<String> unresolvedCritExts) throws CertPathValidatorException {
            List<CertPathValidatorException> softFailExceptions = revocationChecker.getSoftFailExceptions();

            if (softFailExceptions != null) {
               for (CertPathValidatorException e : softFailExceptions) {
                  // Filter soft failure exceptions related to cert.
                  // The check method may be invoked for all certificates in the path and the list of
                  // the soft failure exceptions is cleared only before the first certificate in the path.
                  if (e.getIndex() >= 0 && e.getCertPath().getCertificates().get(e.getIndex()).equals(cert)) {
                     String certSubject = null;
                     if (cert instanceof X509Certificate) {
                        certSubject = ((X509Certificate) cert).getSubjectX500Principal().getName();
                     }

                     ActiveMQClientLogger.LOGGER.softFailException(certSubject, e);
                  }
               }
            }
         }
      });
   }

   protected Set<PKIXRevocationChecker.Option> loadRevocationOptions() {
      String[] revocationOptionNames = crcOptions.split(",");

      Set<PKIXRevocationChecker.Option>  revocationOptions = new HashSet<>();
      for (String revocationOptionName : revocationOptionNames) {
         revocationOptions.add(PKIXRevocationChecker.Option.valueOf(revocationOptionName));
      }

      return revocationOptions;
   }

   private TrustManager[] loadTrustManagers() throws Exception {
      TrustManagerFactory trustManagerFactory = loadTrustManagerFactory();
      if (trustManagerFactory == null) {
         return null;
      }
      return trustManagerFactory.getTrustManagers();
   }

   private Collection<? extends CRL> loadCRL() throws Exception {
      if (crlPath == null) {
         return null;
      }
      URL resource = validateStoreURL(crlPath);
      try (InputStream is = resource.openStream()) {
         return CertificateFactory.getInstance("X.509").generateCRLs(is);
      }
   }

   public static KeyStore loadKeystore(final String keystoreProvider,
                                       final String keystoreType,
                                       final String keystorePath,
                                       final String keystorePassword) throws Exception {
      checkPemProviderLoaded(keystoreType);
      KeyStore ks = keystoreProvider == null ? KeyStore.getInstance(keystoreType) : KeyStore.getInstance(keystoreType, keystoreProvider);
      InputStream in = null;
      try {
         if (keystorePath != null && !keystorePath.isEmpty() && !keystorePath.equalsIgnoreCase(NONE)) {
            URL keystoreURL = SSLSupport.validateStoreURL(keystorePath);
            in = keystoreURL.openStream();
         }
         ks.load(in, keystorePassword == null ? null : keystorePassword.toCharArray());
      } finally {
         if (in != null) {
            try {
               in.close();
            } catch (IOException ignored) {
            }
         }
      }
      return ks;
   }

   /**
    * This method calls out to a separate class in order to avoid a hard dependency on the provider's implementation.
    * This allows folks who don't use PEM to avoid using the corresponding dependency.
    */
   public static void checkPemProviderLoaded(String keystoreType) {
      if (keystoreType != null && keystoreType.startsWith("PEM")) {
         if (Security.getProvider("PEM") == null) {
            PemSupport.loadProvider();
         }
      }
   }

   private KeyManager[] loadKeyManagers() throws Exception {
      KeyManagerFactory factory = loadKeyManagerFactory();
      if (factory == null) {
         return null;
      }
      KeyManager[] keyManagers = factory.getKeyManagers();
      if (keystoreAlias != null) {
         for (int i = 0; i < keyManagers.length; i++) {
            if (keyManagers[i] instanceof X509KeyManager x509KeyManager) {
               keyManagers[i] = new AliasedKeyManager(x509KeyManager, keystoreAlias);
            }
         }
      }
      return keyManagers;
   }

   private KeyManagerFactory loadKeyManagerFactory() throws Exception {
      if ((keystorePath == null || keystorePath.isEmpty() || keystorePath.equalsIgnoreCase(NONE)) && (keystoreProvider == null || !keystoreProvider.toUpperCase().contains("PKCS11"))) {
         return null;
      } else {
         KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
         KeyStore ks = SSLSupport.loadKeystore(keystoreProvider, keystoreType, keystorePath, keystorePassword);
         kmf.init(ks, getKeyPasswordOrDefault());
         return kmf;
      }
   }

   private static URL validateStoreURL(final String storePath) throws Exception {
      assert storePath != null;

      // First see if this is a URL
      try {
         return new URL(storePath);
      } catch (MalformedURLException e) {
         File file = new File(storePath);
         if (file.exists() && file.isFile()) {
            return file.toURI().toURL();
         } else {
            URL url = findResource(storePath);
            if (url != null) {
               return url;
            }
         }
      }

      throw new Exception("Failed to find a store at " + storePath);
   }

   /**
    * This seems duplicate code all over the place, but for security reasons we can't let something like this to be open
    * in a utility class, as it would be a door to load anything you like in a safe VM. For that reason any class trying
    * to do a privileged block should do with the AccessController directly.
    */
   private static URL findResource(final String resourceName) {
      return SecurityManagerShim.doPrivileged((PrivilegedAction<URL>) () -> ClassloadingUtil.findResource(resourceName));
   }

   private Pair<PrivateKey, X509Certificate[]> getPrivateKeyAndCertChain(KeyStore keyStore) throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException {
      PrivateKey key = (PrivateKey) keyStore.getKey(keystoreAlias, getKeyPasswordOrDefault());
      if (key == null) {
         throw ActiveMQClientMessageBundle.BUNDLE.keystoreAliasNotFound(keystoreAlias, keystorePath);
      }

      Certificate[] chain = keyStore.getCertificateChain(keystoreAlias);
      X509Certificate[] certChain = new X509Certificate[chain.length];
      System.arraycopy(chain, 0, certChain, 0, chain.length);
      return new Pair(key, certChain);
   }

   private KeyManagerFactory getKeyManagerFactory(KeyStore keyStore, char[] keystorePassword) throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keyStore, keystorePassword);
      return keyManagerFactory;
   }

   private char[] getKeyPasswordOrDefault() {
      if (keyPassword != null) {
         return keyPassword.toCharArray();
      }
      return keystorePassword != null ? keystorePassword.toCharArray() : null;
   }

   /**
    * The changes ARTEMIS-3155 introduced an incompatibility with old clients using the keyStoreProvider and
    * trustStoreProvider URL properties. These old clients use these properties to set the *type* of store (e.g. PKCS12,
    * PKCS11, JKS, JCEKS, etc.), but new clients use these to set the *provider* (as the name implies). This method
    * checks to see if the provider property matches what is expected from old clients and if so returns they proper
    * provider and type properties to use with the new client implementation.
    *
    * @return a {@code Pair<String, String>} representing the provider and type to use (in that order)
    */
   public static Pair<String, String> getValidProviderAndType(String storeProvider, String storeType) {
      if (storeProvider != null && (storeProvider.startsWith("PKCS") || storeProvider.equals("JKS") || storeProvider.equals("JCEKS"))) {
         ActiveMQClientLogger.LOGGER.oldStoreProvider(storeProvider);
         return new Pair<>(null, storeProvider);
      }
      return new Pair<>(storeProvider, storeType);
   }
}
