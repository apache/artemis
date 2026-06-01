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

package org.apache.artemis.jsonschema.factories;

import java.util.*;

/**
 * Registry mapping factory classes to their discoverable parameters.
 *
 * <p>Populated by FactoryDiscovery during Pipeline Phase 1. Consumed by
 * TransportFactoryVariantBuilder to inject factory-specific parameter properties into the IR.
 */
public class FactoryParameterRegistry {

   /** Map: fully qualified factory class name → parameter info */
   private final Map<String, FactoryInfo> factories = new LinkedHashMap<>();

   /**
    * Add a factory and its parameters to the registry.
    *
    * @param fullClassName Fully qualified class name (e.g., "org.apache...NettyAcceptorFactory")
    * @param simpleClassName Simple class name (e.g., "NettyAcceptorFactory")
    * @param parameters List of parameter names this factory supports
    * @param interfaceType Interface this factory implements (e.g., "LoginModule", "AcceptorFactory")
    */
   public void registerFactory(
         String fullClassName, String simpleClassName, List<String> parameters, String interfaceType) {
      factories.put(fullClassName, new FactoryInfo(simpleClassName, parameters, interfaceType));
   }

   /**
    * Get factory info by full class name.
    *
    * @param fullClassName fully qualified class name
    * @return factory info, or {@code null} if not registered
    */
   public FactoryInfo getFactory(String fullClassName) {
      return factories.get(fullClassName);
   }

   /**
    * Get all registered factory class names.
    *
    * @return live set of fully qualified class names (iteration order is insertion order)
    */
   public Set<String> getFactoryClassNames() {
      return factories.keySet();
   }

   /**
    * Check if a class name is a registered factory.
    *
    * @param className fully qualified class name to test
    * @return {@code true} if the class has been registered
    */
   public boolean isFactory(String className) {
      return factories.containsKey(className);
   }

   /**
    * Get parameters for a specific factory.
    *
    * @param fullClassName fully qualified class name
    * @return parameter names, or empty list if factory not found
    */
   public List<String> getParameters(String fullClassName) {
      FactoryInfo info = factories.get(fullClassName);
      return info != null ? info.getParameters() : Collections.emptyList();
   }

   /**
    * Number of registered factory classes.
    *
    * @return count of distinct registered factories
    */
   public int getFactoryCount() {
      return factories.size();
   }

   /**
    * Sum of all parameter names across all factories (for logging).
    *
    * @return total parameter count across all factories
    */
   public int getTotalParameterCount() {
      return factories.values().stream().mapToInt(f -> f.getParameters().size()).sum();
   }

   /**
    * Get factory class names filtered by the property that selects the factory. "factoryClassName" →
    * returns AcceptorFactory/ConnectorFactory types. "loginModuleClass" → returns LoginModule types.
    * Unknown property name → returns all (safe fallback).
    *
    * @param propertyName config property name that discriminates the factory type
    * @return matching fully qualified class names
    */
   public List<String> getFactoriesForProperty(String propertyName) {
      List<String> result = new ArrayList<>();

      for (Map.Entry<String, FactoryInfo> entry : factories.entrySet()) {
         FactoryInfo info = entry.getValue();

         // Filter by property name pattern
         if (propertyName.contains("loginModule") || propertyName.contains("LoginModule")) {
            if (info.interfaceType.contains("LoginModule")) {
               result.add(entry.getKey());
            }
         } else if (propertyName.contains("factory") || propertyName.contains("Factory")) {
            if (info.interfaceType.contains("Factory")) {
               result.add(entry.getKey());
            }
         } else {
            // Unknown pattern - include all
            result.add(entry.getKey());
         }
      }

      return result;
   }

   /**
    * Get factory class names filtered by the interface they implement.
    *
    * @param interfaceSimpleName simple name of the interface (e.g. "LoginModule", "AcceptorFactory")
    * @return matching fully qualified class names
    */
   public List<String> getFactoriesByInterface(String interfaceSimpleName) {
      List<String> result = new ArrayList<>();
      for (Map.Entry<String, FactoryInfo> entry : factories.entrySet()) {
         if (entry.getValue().interfaceType.contains(interfaceSimpleName)) {
            result.add(entry.getKey());
         }
      }
      return result;
   }

   /**
    * Holds the metadata for one discovered factory: its simple name, the parameter names it
    * supports, and which interface it implements.
    */
   public static class FactoryInfo {
      private final String simpleClassName;
      private final List<String> parameters;
      private final String interfaceType;

      /**
       * Construct factory info with the given metadata.
       *
       * @param simpleClassName unqualified class name
       * @param parameters parameter names (defensively copied)
       * @param interfaceType simple name of the interface this factory implements
       */
      public FactoryInfo(String simpleClassName, List<String> parameters, String interfaceType) {
         this.simpleClassName = simpleClassName;
         this.parameters = new ArrayList<>(parameters);
         this.interfaceType = interfaceType;
      }

      public String getSimpleClassName() {
         return simpleClassName;
      }

      public List<String> getParameters() {
         return Collections.unmodifiableList(parameters);
      }

      public String getInterfaceType() {
         return interfaceType;
      }
   }
}
