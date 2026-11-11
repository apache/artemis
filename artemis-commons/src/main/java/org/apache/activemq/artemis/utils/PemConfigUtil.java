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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class PemConfigUtil {

   public static final String PEMCFG_STORE_TYPE = "PEMCFG";
   public static final String SOURCE_PREFIX = "source.";

   public static boolean isPemConfigStoreType(String storeType) {
      return PEMCFG_STORE_TYPE.equals(storeType);
   }
   public static String[] parseSources(final InputStream stream) throws IOException {
      List<String> sources = new ArrayList<>();
      Properties pemConfigProperties = new Properties();

      pemConfigProperties.load(stream);

      for (final String key : pemConfigProperties.stringPropertyNames()) {
         if (key.startsWith(SOURCE_PREFIX)) {
            String source = pemConfigProperties.getProperty(key);
            if (source != null) {
               sources.add(source);
            }
         }
      }

      return sources.toArray(new String[sources.size()]);
   }
}