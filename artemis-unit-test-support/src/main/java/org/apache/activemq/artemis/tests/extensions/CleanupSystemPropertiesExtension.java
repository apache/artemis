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
package org.apache.activemq.artemis.tests.extensions;

import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * This will make sure any properties changed through tests are cleaned up between tests.
 */
public class CleanupSystemPropertiesExtension implements BeforeEachCallback, AfterEachCallback, Extension {

   private Properties originalProperties;

   @Override
   public void beforeEach(ExtensionContext context) throws Exception {
      originalProperties = new Properties();
      originalProperties.putAll(System.getProperties());

   }

   @Override
   public void afterEach(ExtensionContext context) throws Exception {

      Properties changed = new Properties();
      Set<Object> newProperties = new HashSet<>();
      for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
         Object originalValue = originalProperties.get(entry.getKey());
         if (originalValue == null) {
            newProperties.add(entry.getKey());
         } else if (!originalValue.equals(entry.getValue())) {
            changed.put(entry.getKey(), originalValue);
         }
      }

      for (Map.Entry<Object, Object> entry : originalProperties.entrySet()) {
         if (System.getProperty((String) entry.getKey()) == null) {
            System.out.println("======================================================================================================");
            System.out.println("Reinstating property " + entry.getKey() + "=" + entry.getValue());
            System.setProperty((String) entry.getKey(), (String) entry.getValue());
            System.out.println("======================================================================================================");
         }

      }

      if (!newProperties.isEmpty() || !changed.isEmpty()) {

         System.out.println("======================================================================================================");

         if (!newProperties.isEmpty()) {
            System.out.println("Clearing system property...");

            int i = 1;
            for (Object key : newProperties) {
               System.out.printf("\t%3d. %s = %s%n", i++, key, System.getProperty(key.toString()));
               System.clearProperty(key.toString());
            }
         }

         if (!changed.isEmpty()) {
            System.out.println("Resetting system property...");

            int i = 1;
            for (Map.Entry<Object, Object> entry : changed.entrySet()) {
               System.out.printf("\t%3d. %s = %s (was %s)%n", i++, entry.getKey(), entry.getValue(), System.getProperty(entry.getKey().toString()));
               System.setProperty(entry.getKey().toString(), entry.getValue().toString());
            }
         }

         System.out.println("======================================================================================================");
      }

      // lets give GC a hand
      originalProperties.clear();
      originalProperties = null;

   }
}
