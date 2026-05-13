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
package org.apache.activemq.artemis.spi.core.security.jaas;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.impl.ServerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

import static org.apache.activemq.artemis.core.server.impl.ServerStatus.JAAS_COMPONENT;

public class ReloadableProperties {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   // use this whenever writing to the underlying properties files from another component
   public static final ReadWriteLock LOCK = new ReentrantReadWriteLock();

   private Properties props = new Properties();
   private Map<String, String> invertedProps;
   private Map<String, Set<String>> invertedValueProps;
   private Map<String, Pattern> regexpProps;
   private long reloadTime = -1;
   private final PropertiesLoader.FileNameKey key;
   private final Checksum adler32 = new Adler32();
   private long checksum = 0;

   public ReloadableProperties(PropertiesLoader.FileNameKey key) {
      this.key = key;
   }

   public synchronized Properties getProps() {
      return props;
   }

   public synchronized ReloadableProperties obtained() {
      if (reloadTime < 0 || (key.isReload() && hasModificationAfter(reloadTime))) {

         adler32.reset();
         props = new Properties() {
            // want to checksum in read order
            @Override
            public synchronized Object put(Object key, Object value) {
               String sKey = key instanceof String s ? s : null;
               String sValue = value instanceof String s ? s : null;
               if (sKey != null && sValue != null) {
                  adler32.update(sKey.getBytes(StandardCharsets.UTF_8));
                  adler32.update('=');
                  adler32.update(sValue.getBytes(StandardCharsets.UTF_8));
                  if (!looksLikeRegexp(sValue)) {
                     value = normaliseStringValue(sValue);
                  }
               }
               return super.put(key, value);
            }
         };
         try {
            load(key.file(), props);
            checksum = adler32.getValue();
            invertedProps = null;
            invertedValueProps = null;
            regexpProps = null;
            if (key.isDebug()) {
               logger.debug("Load of: {}", key);
            }
         } catch (IOException e) {
            ActiveMQServerLogger.LOGGER.failedToLoadProperty(key.toString(), e.getLocalizedMessage(), e);
            if (key.isDebug()) {
               logger.debug("Load of: {}, failure exception {}", key, e);
            }
         }
         reloadTime = System.currentTimeMillis();
         updateStatus();
      }
      return this;
   }

   protected String normaliseStringValue(String key) {
      return key;
   }

   private void updateStatus() {
      Map<String, String> statusAttributes = new HashMap<>();
      statusAttributes.put("Alder32", String.valueOf(checksum));
      statusAttributes.put("reloadTime", String.valueOf(reloadTime));
      ServerStatus.getInstance().update(JAAS_COMPONENT + "/properties/" + key.file.getName(), statusAttributes);
   }

   public synchronized Map<String, String> invertedPropertiesMap() {
      if (invertedProps == null) {
         invertedProps = new HashMap<>(props.size());
         for (Map.Entry<Object, Object> val : props.entrySet()) {
            String str = (String) val.getValue();
            if (!looksLikeRegexp(str)) {
               invertedProps.put(str, (String) val.getKey());
            }
         }
      }
      return invertedProps;
   }

   public synchronized Map<String, Set<String>> invertedPropertiesValuesMap() {
      if (invertedValueProps == null) {
         invertedValueProps = new HashMap<>(props.size());
         for (Map.Entry<Object, Object> val : props.entrySet()) {
            String[] userList = ((String) val.getValue()).split(",");
            for (String user : userList) {
               Set<String> set = invertedValueProps.get(user);
               if (set == null) {
                  set = new HashSet<>();
                  invertedValueProps.put(user, set);
               }
               set.add((String) val.getKey());
            }
         }
      }
      return invertedValueProps;
   }

   public synchronized Map<String, Pattern> regexpPropertiesMap() {
      if (regexpProps == null) {
         regexpProps = new HashMap<>(props.size());
         for (Map.Entry<Object, Object> val : props.entrySet()) {
            String str = (String) val.getValue();
            if (looksLikeRegexp(str)) {
               try {
                  Pattern p = Pattern.compile(str.substring(1, str.length() - 1));
                  regexpProps.put((String) val.getKey(), p);
               } catch (PatternSyntaxException e) {
                  logger.warn("Ignoring invalid regexp: {}", str, e);
               }
            }
         }
      }
      return regexpProps;
   }

   private void load(final File source, Properties props) throws IOException {
      LOCK.readLock().lock();
      try (FileInputStream in = new FileInputStream(source)) {
         props.load(in);
         //            if (key.isDecrypt()) {
         //                try {
         //                    EncryptionSupport.decrypt(this.props);
         //                } catch (NoClassDefFoundError e) {
         //                    // this Happens whe jasypt is not on the classpath..
         //                    key.setDecrypt(false);
         //                    logger.info("jasypt is not on the classpath: password decryption disabled.");
         //                }
         //            }
      } finally {
         LOCK.readLock().unlock();
      }
   }

   private boolean hasModificationAfter(long reloadTime) {
      /*
       * A bug in JDK 8/9 (i.e. https://bugs.openjdk.java.net/browse/JDK-8177809) causes java.io.File.lastModified() to
       * lose resolution past 1 second. Because of this, the value returned by java.io.File.lastModified() can appear to
       * be smaller than it actually is which can cause the broker to miss reloading the properties if the modification
       * happens close to another "reload" event (e.g. initial loading). In order to *not* miss file modifications that
       * need to be reloaded we artificially inflate the value returned by java.io.File.lastModified() by 1 second.
       */
      return key.file.lastModified() + 1000 > reloadTime;
   }

   private boolean looksLikeRegexp(String str) {
      int len = str.length();
      return len > 2 && str.charAt(0) == '/' && str.charAt(len - 1) == '/';
   }

}
