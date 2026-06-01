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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.*;
import org.apache.artemis.jsonschema.config.SchemaGeneratorConfig;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovery service for factory/plugin implementations and their parameters.
 *
 * <p>Scans the classpath for implementations of configured factory interfaces (AcceptorFactory,
 * ConnectorFactory, LoginModule) and discovers their parameters from:
 *
 * <ul>
 *   <li>Public static final String *_PROP_NAME constants on the class itself
 *   <li>ConfigKey/ParamKey inner enums with getName()
 *   <li>Companion constant classes (e.g., TransportConstants in the same package)
 * </ul>
 *
 * @see TransportFactoryVariantBuilder
 * @see FactoryParameterRegistry
 */
public class FactoryDiscovery {

   private static final Logger LOG = LoggerFactory.getLogger(FactoryDiscovery.class);

   /**
    * Interfaces whose implementations expose configurable parameters via *_PROP_NAME constants.
    * Loaded from META-INF/schema-generator-config.json.
    *
    * <p>This list is exhaustive for Artemis's factory polymorphism model:
    *
    * <ul>
    *   <li>AcceptorFactory/ConnectorFactory — transport layer (Netty, InVM)
    *   <li>LoginModule — JAAS security (LDAP, Properties, Kerberos, etc.)
    * </ul>
    *
    * <p>This list should NOT grow unless Artemis introduces an entirely new plugin abstraction where
    * the implementation class is selected by name in broker.properties and each implementation has
    * its own parameter set. Standard broker plugins (ActiveMQServer*Plugin) do not belong here —
    * their parameters are configured via a different mechanism (init Map).
    */
   private static final List<String> FACTORY_INTERFACES =
         SchemaGeneratorConfig.load().getFactoryInterfaces();

   /**
    * Packages scanned by Reflections to find implementations of the above interfaces. Must cover the
    * packages where AcceptorFactory/ConnectorFactory impls and LoginModule impls reside. Loaded from
    * META-INF/schema-generator-config.json.
    */
   private static final List<String> FACTORY_SCAN_PACKAGES =
         SchemaGeneratorConfig.load().getFactoryScanPackages();

   private final FactoryParameterRegistry registry = new FactoryParameterRegistry();

   /**
    * Discover all factory classes and their parameters via classpath scanning.
    *
    * @param artemisRoot path to Artemis source root (currently unused; factories are found on
    *     classpath)
    * @return populated registry mapping factory classes to their parameter names
    * @throws Exception if classpath scanning or reflection fails
    */
   public FactoryParameterRegistry extractRegistry(Path artemisRoot) throws Exception {
      LOG.info("Running FactoryDiscovery");
      LOG.info("Scanning classpath for factory implementations");

      Reflections reflections =
            new Reflections(
                  new ConfigurationBuilder()
                        .forPackages(FACTORY_SCAN_PACKAGES.toArray(new String[0]))
                        .setScanners(Scanners.SubTypes));

      List<Class<?>> factoryClasses = new ArrayList<>();

      // Discover implementations - track which interface each implements
      Map<Class<?>, String> classToInterface = new LinkedHashMap<>();

      for (String interfaceName : FACTORY_INTERFACES) {
         try {
            Class<?> interfaceClass = Class.forName(interfaceName);
            Set<? extends Class<?>> implementations =
                  getSubTypesOfWildcard(reflections, interfaceClass);

            for (Class<?> impl : implementations) {
               if (!Modifier.isAbstract(impl.getModifiers())
                     && !Modifier.isInterface(impl.getModifiers())) {
                  factoryClasses.add(impl);
                  classToInterface.put(impl, interfaceClass.getSimpleName());
                  LOG.debug("Found: {}", impl.getSimpleName());
               }
            }
         } catch (ClassNotFoundException e) {
            LOG.debug("Factory interface not on classpath: {}", interfaceName);
         }
      }

      LOG.info("Discovered {} factory/plugin classes", factoryClasses.size());

      // Extract parameters from each class
      for (Class<?> factoryClass : factoryClasses) {
         List<String> params = extractParameters(factoryClass);

         if (!params.isEmpty() || classToInterface.containsKey(factoryClass)) {
            registry.registerFactory(
                  factoryClass.getName(),
                  factoryClass.getSimpleName(),
                  params,
                  classToInterface.getOrDefault(factoryClass, "Unknown"));
         }
      }

      LOG.info(
            "Extracted {} unique parameters from {} factories",
            registry.getTotalParameterCount(),
            registry.getFactoryCount());

      return registry;
   }

   /**
    * Extract all parameter names from a factory/plugin class. Supports multiple patterns:
    * *_PROP_NAME constants, ConfigKey/ParamKey enums, and companion constant classes in the same
    * package.
    *
    * @param factoryClass the factory implementation class to inspect
    * @return discovered parameter names (may be empty)
    */
   private List<String> extractParameters(Class<?> factoryClass) {
      String className = factoryClass.getSimpleName();
      List<String> params = new ArrayList<>();

      LOG.debug("Extracting params from {}", className);

      // Pattern 1: *_PROP_NAME constants in the class itself
      params.addAll(extractFromConstants(factoryClass));

      // Pattern 2: ConfigKey/ParamKey enums
      params.addAll(extractFromConfigKeyEnum(factoryClass));

      // Pattern 3: Companion constant classes (e.g., TransportConstants)
      params.addAll(extractFromCompanionClass(factoryClass));

      // Store results
      if (!params.isEmpty()) {
         LOG.debug("Found {} params: {}", params.size(), params);
      } else {
         LOG.debug("No params found");
      }

      return params;
   }

   /**
    * Extract parameter names from static final String *_PROP_NAME constants on the class.
    *
    * @param clazz the class to inspect for constant fields
    * @return discovered parameter name values
    */
   private List<String> extractFromConstants(Class<?> clazz) {
      List<String> params = new ArrayList<>();

      for (Field field : clazz.getDeclaredFields()) {
         if (Modifier.isStatic(field.getModifiers())
               && Modifier.isFinal(field.getModifiers())
               && field.getType() == String.class) {

            String fieldName = field.getName();

            // Match: *_PROP_NAME, *_PROP, *_PROPERTY, *_PARAM_NAME, etc.
            if (fieldName.endsWith("_PROP_NAME")
                  || fieldName.endsWith("_PROP")
                  || fieldName.endsWith("_PROPERTY")
                  || fieldName.endsWith("_PARAM_NAME")
                  || fieldName.endsWith("_PARAM")) {

               try {
                  field.setAccessible(true);
                  String paramValue = (String) field.get(null);

                  // For qualified property names (e.g., "org.apache.activemq.jaas.properties.user"),
                  // prefer the SHORT form for documentation since that's what users type
                  // The full form is an implementation detail
                  String paramToDocument = paramValue;
                  if (paramValue.contains(".")) {
                     String shortForm = paramValue.substring(paramValue.lastIndexOf(".") + 1);
                     if (!shortForm.isEmpty()) {
                        paramToDocument = shortForm;
                     }
                  }
                  params.add(paramToDocument);
               } catch (IllegalAccessException e) {
                  LOG.debug(
                        "Cannot access field {} in {}: {}",
                        field.getName(),
                        clazz.getSimpleName(),
                        e.getMessage());
               }
            }
         }
      }

      return params;
   }

   /**
    * Extract parameter names from inner ConfigKey/ParamKey enums via getName() or constant names.
    *
    * @param clazz the class whose inner enums to inspect
    * @return discovered parameter names
    */
   private List<String> extractFromConfigKeyEnum(Class<?> clazz) {
      List<String> params = new ArrayList<>();

      for (Class<?> innerClass : clazz.getDeclaredClasses()) {
         if (innerClass.isEnum()) {
            String enumName = innerClass.getSimpleName();

            if (enumName.equals("ConfigKey")
                  || enumName.equals("ParamKey")
                  || enumName.endsWith("Key")) {

               Object[] enumConstants = innerClass.getEnumConstants();

               try {
                  // Try getName() method (e.g., LDAPLoginModule)
                  java.lang.reflect.Method getNameMethod = innerClass.getMethod("getName");
                  for (Object enumConstant : enumConstants) {
                     String paramName = (String) getNameMethod.invoke(enumConstant);
                     params.add(paramName);
                  }
               } catch (NoSuchMethodException e) {
                  // No getName(), use enum constant names
                  for (Object enumConstant : enumConstants) {
                     String paramName = ((Enum<?>) enumConstant).name();
                     // Convert SCREAMING_SNAKE_CASE to camelCase
                     paramName = toCamelCase(paramName);
                     params.add(paramName);
                  }
               } catch (Exception e) {
                  LOG.debug(
                        "Failed to extract enum params from {}: {}",
                        innerClass.getSimpleName(),
                        e.getMessage());
               }
            }
         }
      }

      return params;
   }

   /**
    * Extract parameters from companion constant classes in the same package. E.g.,
    * NettyAcceptorFactory → look for TransportConstants.
    *
    * @param factoryClass the factory class whose package is searched for companions
    * @return discovered parameter names from the companion's *_PROP_NAME constants
    */
   private List<String> extractFromCompanionClass(Class<?> factoryClass) {
      List<String> params = new ArrayList<>();

      // Try common companion class names
      String[] companionNames = {
         "TransportConstants",
         "Constants",
         factoryClass.getSimpleName() + "Constants",
         factoryClass.getSimpleName() + "Params"
      };

      String packageName = factoryClass.getPackage().getName();

      for (String companionName : companionNames) {
         try {
            String fullClassName = packageName + "." + companionName;
            Class<?> companionClass = Class.forName(fullClassName);

            // Extract *_PROP_NAME constants from companion class
            for (Field field : companionClass.getDeclaredFields()) {
               if (Modifier.isStatic(field.getModifiers())
                     && Modifier.isFinal(field.getModifiers())
                     && field.getType() == String.class
                     && field.getName().endsWith("_PROP_NAME")) {

                  try {
                     field.setAccessible(true);
                     String paramValue = (String) field.get(null);
                     params.add(paramValue);
                  } catch (IllegalAccessException e) {
                     LOG.debug("Cannot access companion field {}: {}", field.getName(), e.getMessage());
                  }
               }
            }

            if (!params.isEmpty()) {
               LOG.debug("Found companion class: {}", companionName);
               break; // Found constants, don't look for other companion classes
            }
         } catch (ClassNotFoundException e) {
            // Companion class doesn't exist, try next
         }
      }

      return params;
   }

   /**
    * Convert SCREAMING_SNAKE_CASE to camelCase.
    *
    * @param screamingSnakeCase input in SCREAMING_SNAKE_CASE format
    * @return camelCase equivalent
    */
   private String toCamelCase(String screamingSnakeCase) {
      String[] parts = screamingSnakeCase.toLowerCase().split("_");
      StringBuilder result = new StringBuilder(parts[0]);
      for (int i = 1; i < parts.length; i++) {
         if (!parts[i].isEmpty()) {
            result.append(Character.toUpperCase(parts[i].charAt(0)));
            result.append(parts[i].substring(1));
         }
      }
      return result.toString();
   }

   /**
    * @return extractor name for logging
    */
   public String getName() {
      return "FactoryDiscovery";
   }

   /**
    * Bridge for Reflections.getSubTypesOf when the type is only known as Class&lt;?&gt;. The
    * unchecked cast is unavoidable when classes are loaded by name at runtime.
    *
    * @param reflections configured Reflections instance
    * @param type the interface or superclass to query
    * @return set of discovered subtypes
    */
   @SuppressWarnings("unchecked")
   private static Set<? extends Class<?>> getSubTypesOfWildcard(
         org.reflections.Reflections reflections, Class<?> type) {
      return reflections.getSubTypesOf((Class<Object>) type);
   }
}
