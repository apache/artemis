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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class PropertiesLoader {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static String LOGIN_CONFIG_SYS_PROP_NAME = "java.security.auth.login.config";
   static final Map<FileNameKey, ReloadableProperties> staticCache = new HashMap<>();
   protected boolean debug;

   public void init(Map options) {
      debug = booleanOption("debug", options);
      if (debug) {
         logger.debug("Initialized debug");
      }
   }


   interface NormaliseStringValues {
      String normalize(String o);
   }

   public ReloadableProperties load(String nameProperty, String fallbackName, Map options) {
      return load(nameProperty, fallbackName, options, null);
   }

   public ReloadableProperties load(String nameProperty, String fallbackName, Map options, NormaliseStringValues valueNormaliser) {
      ReloadableProperties result;
      FileNameKey key = new FileNameKey(nameProperty, fallbackName, options);
      key.setDebug(debug);

      synchronized (staticCache) {
         result = staticCache.get(key);
         if (result == null) {
            if (valueNormaliser == null) {
               result = new ReloadableProperties(key);
            } else {
               result = new ReloadableProperties(key) {
                  @Override
                  protected String normaliseStringValue(String value) {
                     return valueNormaliser.normalize(value);
                  }
               };
            }
            staticCache.put(key, result);
         }
      }

      return result.obtained();
   }

   protected static boolean booleanOption(String name, Map options) {
      return Boolean.parseBoolean((String) options.get(name));
   }

   public static final class FileNameKey {

      final File file;
      final String absPath;
      final boolean reload;
      private boolean decrypt;
      private boolean debug;

      public FileNameKey(String nameProperty, String fallbackName, Map options) {
         this.file = new File(baseDir(options), stringOption(nameProperty, fallbackName, options));
         absPath = file.getAbsolutePath();
         reload = booleanOption("reload", options);
         decrypt = booleanOption("decrypt", options);
      }

      @Override
      public boolean equals(Object obj) {
         return obj instanceof FileNameKey other && this.absPath.equals(other.absPath);
      }

      @Override
      public int hashCode() {
         return this.absPath.hashCode();
      }

      public boolean isReload() {
         return reload;
      }

      public File file() {
         return file;
      }

      public boolean isDecrypt() {
         return decrypt;
      }

      public void setDecrypt(boolean decrypt) {
         this.decrypt = decrypt;
      }

      private String stringOption(String key, String nameDefault, Map options) {
         Object result = options.get(key);
         return result != null ? result.toString() : nameDefault;
      }

      private File baseDir(Map options) {
         File baseDir = null;
         if (options.get("baseDir") != null) {
            baseDir = new File((String) options.get("baseDir"));
         } else {
            baseDir = parentDirOfLoginConfigSystemProperty();
         }
         if (debug) {
            logger.debug("Using basedir={}", (baseDir == null ? null : baseDir.getAbsolutePath()));
         }
         return baseDir;
      }

      public static File parentDirOfLoginConfigSystemProperty() {
         String path = System.getProperty(LOGIN_CONFIG_SYS_PROP_NAME);
         if (path != null) {
            return new File(path).getParentFile();
         }
         return null;
      }

      @Override
      public String toString() {
         return "PropsFile=" + absPath;
      }

      public void setDebug(boolean debug) {
         this.debug = debug;
      }

      public boolean isDebug() {
         return debug;
      }
   }

   public static void reload() {
      logger.debug("reLoad");
      Collection<ReloadableProperties> currentSnapshot;
      synchronized (staticCache) {
         currentSnapshot = new ArrayList<>(staticCache.values());
      }
      for (ReloadableProperties reloadableProperties : currentSnapshot) {
         reloadableProperties.obtained();
      }
   }

   /**
    * For test-usage only.
    */
   public static void resetUsersAndGroupsCache() {
      staticCache.clear();
   }
}
