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
package org.apache.activemq.artemis.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.version.Version;
import org.apache.activemq.artemis.core.version.impl.VersionImpl;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;

/**
 * This loads the version info in from a version.properties file.
 */
public final class VersionLoader {

   public static final String VERSION_PROP_FILE_KEY = "activemq.version.property.filename";

   public static final String DEFAULT_PROP_FILE_NAME = "activemq-version.properties";

   private static String PROP_FILE_NAME;

   private static Version[] versions;

   static {
      try {

         try {
            PROP_FILE_NAME = SecurityManagerShim.doPrivileged((PrivilegedAction<String>) () -> System.getProperty(VersionLoader.VERSION_PROP_FILE_KEY));
         } catch (Throwable e) {
            ActiveMQClientLogger.LOGGER.unableToInitVersionLoader(e);
            PROP_FILE_NAME = null;
         }

         if (PROP_FILE_NAME == null) {
            PROP_FILE_NAME = VersionLoader.DEFAULT_PROP_FILE_NAME;
         }

         VersionLoader.versions = VersionLoader.load();
      } catch (Throwable e) {
         VersionLoader.versions = null;
         ActiveMQClientLogger.LOGGER.unableToInitVersionLoaderError(e);
      }

   }

   public static Version[] getClientVersions() {
      if (VersionLoader.versions == null) {
         throw new RuntimeException(VersionLoader.PROP_FILE_NAME + " is not available");
      }

      return VersionLoader.versions;
   }

   public static Version getVersion() {
      if (VersionLoader.versions == null) {
         throw new RuntimeException(VersionLoader.PROP_FILE_NAME + " is not available");
      }

      return VersionLoader.versions[0];
   }

   public static String getClasspathString() {
      StringBuilder classpath = new StringBuilder();
      ClassLoader applicationClassLoader = VersionImpl.class.getClassLoader();
      URL[] urls = ((URLClassLoader) applicationClassLoader).getURLs();
      for (URL url : urls) {
         classpath.append(url.getFile()).append("\r\n");
      }

      return classpath.toString();
   }

   private static Version[] load() {
      Properties versionProps = new Properties();
      final InputStream in = VersionImpl.class.getClassLoader().getResourceAsStream(VersionLoader.PROP_FILE_NAME);
      try {
         if (in == null) {
            ActiveMQClientLogger.LOGGER.noVersionOnClasspath(getClasspathString());
            throw new RuntimeException(VersionLoader.PROP_FILE_NAME + " is not available");
         }
         try {
            versionProps.load(in);
            String versionName = versionProps.getProperty("activemq.version.versionName");
            int majorVersion = Integer.parseInt(versionProps.getProperty("activemq.version.majorVersion"));
            int minorVersion = Integer.parseInt(versionProps.getProperty("activemq.version.minorVersion"));
            int microVersion = Integer.parseInt(versionProps.getProperty("activemq.version.microVersion"));
            int[] incrementingVersions = parseCompatibleVersionList(versionProps.getProperty("activemq.version.incrementingVersion"));
            int[] compatibleVersionArray = parseCompatibleVersionList(versionProps.getProperty("activemq.version.compatibleVersionList"));
            List<Version> definedVersions = new ArrayList<>(incrementingVersions.length);
            for (int incrementingVersion : incrementingVersions) {
               definedVersions.add(new VersionImpl(versionName, majorVersion, minorVersion, microVersion, incrementingVersion, compatibleVersionArray));
            }
            //We want the higher version to be the first
            Collections.sort(definedVersions, (version1, version2) -> version2.getIncrementingVersion() - version1.getIncrementingVersion());
            return definedVersions.toArray(new Version[incrementingVersions.length]);
         } catch (IOException e) {
            // if we get here then the messaging hasn't been built properly and the version.properties is skewed in some
            // way
            throw new RuntimeException("unable to load " + VersionLoader.PROP_FILE_NAME, e);
         }
      } finally {
         try {
            if (in != null) {
               in.close();
            }
         } catch (Throwable ignored) {
         }
      }

   }

   private static int[] parseCompatibleVersionList(String property) throws IOException {
      int[] verArray = new int[0];
      StringTokenizer tokenizer = new StringTokenizer(property, ",");
      while (tokenizer.hasMoreTokens()) {
         int from = -1, to = -1;
         String token = tokenizer.nextToken();

         int cursor = 0;
         char firstChar = token.charAt(0);
         if (firstChar == '-') {
            // "-n" pattern
            from = 0;
            cursor++;
            for (; cursor < token.length() && Character.isDigit(token.charAt(cursor)); cursor++) {
               // do nothing
            }
            if (cursor > 1) {
               to = Integer.parseInt(token.substring(1, cursor));
            }
         } else if (Character.isDigit(firstChar)) {
            for (; cursor < token.length() && Character.isDigit(token.charAt(cursor)); cursor++) {
               // do nothing
            }
            from = Integer.parseInt(token.substring(0, cursor));

            if (cursor == token.length()) {
               // just "n" pattern
               to = from;
            } else if (token.charAt(cursor) == '-') {
               cursor++;
               if (cursor == token.length()) {
                  // "n-" pattern
                  to = Integer.MAX_VALUE;
               } else {
                  // "n-n" pattern
                  to = Integer.parseInt(token.substring(cursor));
               }
            }
         }

         if (from != -1 && to != -1) {
            // merge version array
            int[] newArray = new int[verArray.length + to - from + 1];
            System.arraycopy(verArray, 0, newArray, 0, verArray.length);
            for (int i = 0; i < to - from + 1; i++) {
               newArray[verArray.length + i] = from + i;
            }
            verArray = newArray;
         }
      }

      return verArray;
   }
}
