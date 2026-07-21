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

package org.apache.activemq.artemis.utils.ssl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.security.PrivilegedAction;
import java.security.Security;

import org.apache.activemq.artemis.utils.ClassloadingUtil;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;

public class KeyStoreSupport {
   public static final String NONE = "NONE";
   public static final String PKCS_11 = "PKCS11";
   public static final String PEMCA = "PEMCA";

   public static KeyStore loadKeystore(final String keystoreProvider,
                                       final String keystoreType,
                                       final String keystorePath,
                                       final String keystorePassword) throws Exception {
      checkPemProviderLoaded(keystoreType);
      KeyStore ks = keystoreProvider == null ? KeyStore.getInstance(keystoreType) : KeyStore.getInstance(keystoreType, keystoreProvider);
      InputStream in = null;
      try {
         if (keystorePath != null && !keystorePath.isEmpty() && !keystorePath.equalsIgnoreCase(NONE)) {
            URL keystoreURL = KeyStoreSupport.validateStoreURL(keystorePath);
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


   public static URL validateStoreURL(final String storePath) throws Exception {
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

}
