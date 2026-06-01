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

package org.apache.artemis.jsonschema.enrichment;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.description.JavadocDescription;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import org.apache.artemis.jsonschema.config.SchemaGeneratorConfig;
import org.apache.artemis.jsonschema.ir.PropertyDescriptor;
import org.apache.artemis.jsonschema.ir.PropertySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts JavaDoc documentation from ALL *Configuration.java classes. Scans artemis source tree
 * for configuration classes and extracts property documentation.
 *
 * <p>Extracts from: - Configuration.java → root-level properties - Nested config classes (e.g.,
 * FederationConnectionConfiguration) → prefixed properties - Setter methods: setJMXDomain() →
 * JMXDomain property - Getter methods: getJMXDomain() → JMXDomain property (if no setter doc)
 *
 * <p>Cleanup: - Removes @param, @return, @deprecated tags (extract separately) - Cleans up {@link}
 * and {@code} tags - Removes "Sets " prefix from setter docs - Removes "Returns " prefix from
 * getter docs
 */
public class SetterGetterJavadocExtractor implements Extractor {

   private static final Logger LOG = LoggerFactory.getLogger(SetterGetterJavadocExtractor.class);

   /** {@inheritDoc} */
   @Override
   public List<PropertyDescriptor> extract(Path artemisRoot) throws ExtractionException {
      try {
         List<PropertyDescriptor> descriptors = new ArrayList<>();

         // Find all *Configuration.java files in artemis source tree
         List<Path> configFiles = findConfigurationFiles(artemisRoot);

         // Extract JavaDoc from each file
         for (Path configFile : configFiles) {
            extractFromFile(configFile, artemisRoot, descriptors);
         }

         return descriptors;
      } catch (IOException e) {
         throw new ExtractionException(getName(), "Failed to scan configuration files", e);
      }
   }

   /**
    * Find all *Configuration.java files in artemis source tree.
    *
    * @param artemisRoot path to Artemis source root
    * @return paths to all discovered Configuration source files
    * @throws IOException if directory walking fails
    */
   private List<Path> findConfigurationFiles(Path artemisRoot) throws IOException {
      List<Path> configFiles = new ArrayList<>();

      List<String> sourceDirs = SchemaGeneratorConfig.load().getJavadocSourceDirs();
      for (String dir : sourceDirs) {
         Path searchRoot = artemisRoot.resolve(dir);
         if (Files.exists(searchRoot)) {
            try (Stream<Path> paths = Files.walk(searchRoot)) {
               paths
                     .filter(p -> p.toString().endsWith("Configuration.java"))
                     .filter(Files::isRegularFile)
                     .forEach(configFiles::add);
            }
         }
      }

      return configFiles;
   }

   /** Extract JavaDoc from a single configuration file. Returns number of descriptors extracted. */
   /**
    * Extract JavaDoc from a single configuration file using a fallback chain: 1. Setter method
    * JavaDoc (most specific, "sets the X" documentation) 2. Getter method JavaDoc (often duplicates
    * setter, but sometimes only source) 3. Field-level JavaDoc (for classes that document fields
    * instead of methods) 4. Interface method JavaDoc (Configuration interface has docs for many
    * properties)
    *
    * @param configFile path to the Configuration source file to parse
    * @param artemisRoot Artemis source root (for locating interface files)
    * @param descriptors accumulator for extracted descriptors
    * @return number of new descriptors added
    */
   private int extractFromFile(
         Path configFile, Path artemisRoot, List<PropertyDescriptor> descriptors) {
      try {
         CompilationUnit cu = StaticJavaParser.parse(configFile);

         String className = configFile.getFileName().toString().replace(".java", "");
         String pathPrefix = getPropertyPathPrefix(className);

         Map<String, String> resolvedDescriptions = new LinkedHashMap<>();

         // Pass 1: Collect setter JavaDoc (highest priority)
         cu.findAll(MethodDeclaration.class)
               .forEach(
                     method -> {
                        String methodName = method.getNameAsString();
                        if (!methodName.startsWith("set") || methodName.length() <= 3) {
                           return;
                        }
                        String propertyName = decapitalize(methodName.substring(3));
                        if (resolvedDescriptions.containsKey(propertyName)) {
                           return;
                        }
                        String desc = extractDescriptionFromMethod(method);
                        if (desc != null) {
                           resolvedDescriptions.put(propertyName, desc);
                        }
                     });

         // Pass 2: Getter JavaDoc (fallback for properties without setter doc)
         cu.findAll(MethodDeclaration.class)
               .forEach(
                     method -> {
                        String methodName = method.getNameAsString();
                        String propertyName = null;
                        if (methodName.startsWith("get") && methodName.length() > 3) {
                           propertyName = decapitalize(methodName.substring(3));
                        } else if (methodName.startsWith("is") && methodName.length() > 2) {
                           propertyName = decapitalize(methodName.substring(2));
                        }
                        if (propertyName == null || resolvedDescriptions.containsKey(propertyName)) {
                           return;
                        }
                        String desc = extractDescriptionFromMethod(method);
                        if (desc != null) {
                           resolvedDescriptions.put(propertyName, desc);
                        }
                     });

         // Pass 3: Field-level JavaDoc (for properties with no method docs)
         cu.findAll(com.github.javaparser.ast.body.FieldDeclaration.class)
               .forEach(
                     field -> {
                        field
                              .getVariables()
                              .forEach(
                                    variable -> {
                                       String fieldName = variable.getNameAsString();
                                       if (resolvedDescriptions.containsKey(fieldName)) {
                                          return;
                                       }
                                       Optional<JavadocComment> javadocOpt = field.getJavadocComment();
                                       if (javadocOpt.isPresent()) {
                                          String desc = extractDescription(javadocOpt.get().parse());
                                          if (!desc.isEmpty()) {
                                             resolvedDescriptions.put(fieldName, desc);
                                          }
                                       }
                                    });
                     });

         // Pass 4: Interface method JavaDoc (scan implemented interfaces)
         if (className.endsWith("Impl") || className.equals("ConfigurationImpl")) {
            String interfaceName = className.replace("Impl", "");
            Path interfaceFile = findInterfaceFile(configFile, interfaceName, artemisRoot);
            if (interfaceFile != null) {
               extractInterfaceDoc(interfaceFile, resolvedDescriptions);
            }
         }

         // Emit descriptors for all resolved properties
         int beforeCount = descriptors.size();
         for (Map.Entry<String, String> entry : resolvedDescriptions.entrySet()) {
            String propertyName = entry.getKey();
            String description = entry.getValue();

            String propertyPath = pathPrefix.isEmpty() ? propertyName : pathPrefix + "." + propertyName;

            PropertyDescriptor descriptor =
                  new PropertyDescriptor(propertyPath, PropertySource.JAVADOC);
            descriptor.getMetadata().setDescription(description);
            descriptors.add(descriptor);

            if (propertyName.length() > 1
                  && Character.isUpperCase(propertyName.charAt(0))
                  && Character.isUpperCase(propertyName.charAt(1))) {

               String lowercaseVariant = createLowercaseAcronymVariant(propertyName);
               String lowercasePath =
                     pathPrefix.isEmpty() ? lowercaseVariant : pathPrefix + "." + lowercaseVariant;

               PropertyDescriptor lowercaseDesc =
                     new PropertyDescriptor(lowercasePath, PropertySource.JAVADOC);
               lowercaseDesc.getMetadata().setDescription(description);
               descriptors.add(lowercaseDesc);
            }
         }

         return descriptors.size() - beforeCount;
      } catch (Exception e) {
         LOG.warn("Failed to parse {}: {}", configFile.getFileName(), e.getMessage());
         return 0;
      }
   }

   /**
    * Extract the cleaned-up description text from a method's JavaDoc comment.
    *
    * @param method the AST method declaration
    * @return cleaned description text, or {@code null} if no usable JavaDoc
    */
   private String extractDescriptionFromMethod(MethodDeclaration method) {
      Optional<JavadocComment> javadocOpt = method.getJavadocComment();
      if (!javadocOpt.isPresent()) {
         return null;
      }
      String desc = extractDescription(javadocOpt.get().parse());
      return desc.isEmpty() ? null : desc;
   }

   /**
    * Locate the source file for a configuration interface by searching source dirs.
    *
    * @param implFile path to the implementation file (unused but available for context)
    * @param interfaceName simple name of the interface (e.g. "Configuration")
    * @param artemisRoot Artemis source root
    * @return path to the interface source file, or {@code null} if not found
    */
   private Path findInterfaceFile(Path implFile, String interfaceName, Path artemisRoot) {
      List<String> sourceDirs = SchemaGeneratorConfig.load().getJavadocSourceDirs();
      for (String dir : sourceDirs) {
         Path searchRoot = artemisRoot.resolve(dir);
         if (!Files.exists(searchRoot)) continue;
         try (Stream<Path> paths = Files.walk(searchRoot)) {
            Optional<Path> found =
                  paths
                        .filter(p -> p.getFileName().toString().equals(interfaceName + ".java"))
                        .findFirst();
            if (found.isPresent()) {
               return found.get();
            }
         } catch (IOException e) {
            // continue searching
         }
      }
      return null;
   }

   /**
    * Scan an interface source file for method JavaDoc and add missing descriptions.
    *
    * @param interfaceFile path to the interface source file
    * @param resolvedDescriptions mutable map; only properties absent from this map are added
    */
   private void extractInterfaceDoc(Path interfaceFile, Map<String, String> resolvedDescriptions) {
      try {
         CompilationUnit cu = StaticJavaParser.parse(interfaceFile);
         cu.findAll(MethodDeclaration.class)
               .forEach(
                     method -> {
                        String methodName = method.getNameAsString();
                        String propertyName = methodNameToPropertyPath(methodName);
                        if (propertyName == null || resolvedDescriptions.containsKey(propertyName)) {
                           return;
                        }
                        String desc = extractDescriptionFromMethod(method);
                        if (desc != null) {
                           resolvedDescriptions.put(propertyName, desc);
                        }
                     });
      } catch (Exception e) {
         LOG.debug(
               "Failed to parse interface file {}: {}", interfaceFile.getFileName(), e.getMessage());
      }
   }

   /**
    * Determine property path prefix for a configuration class. Configuration → "" (root level),
    * FederationConnectionConfiguration → "federationConnectionConfiguration".
    *
    * @param className simple class name (without package)
    * @return dotted path prefix, or empty string for the root Configuration class
    */
   private String getPropertyPathPrefix(String className) {
      // Configuration.java is root level (no prefix)
      if (className.equals("Configuration")) {
         return "";
      }

      // Remove "Configuration" suffix and decapitalize
      if (className.endsWith("Configuration")) {
         String baseName = className.substring(0, className.length() - "Configuration".length());
         return decapitalize(baseName) + "Configuration";
      }

      // Fallback: just decapitalize the class name
      return decapitalize(className);
   }

   /**
    * Convert method name to property path. setJMXDomain → JMXDomain, getJMXDomain → JMXDomain,
    * isEnabled → enabled.
    *
    * @param methodName Java method name
    * @return property name, or {@code null} if the method is not a getter/setter/is-accessor
    */
   private String methodNameToPropertyPath(String methodName) {
      if (methodName.startsWith("set") && methodName.length() > 3) {
         return decapitalize(methodName.substring(3));
      }
      if (methodName.startsWith("get") && methodName.length() > 3) {
         return decapitalize(methodName.substring(3));
      }
      if (methodName.startsWith("is") && methodName.length() > 2) {
         return decapitalize(methodName.substring(2));
      }
      return null;
   }

   /**
    * Decapitalize first letter, preserving acronyms. If first two chars are uppercase, the name
    * stays unchanged (JMXDomain stays JMXDomain).
    *
    * @param name identifier to decapitalize
    * @return decapitalized name, or the original if it starts with an acronym
    */
   private String decapitalize(String name) {
      if (name.length() == 0) return name;
      if (name.length() == 1) return name.toLowerCase();

      // If first two characters are uppercase, it's likely an acronym - keep as-is
      // Examples: JMXDomain, IDCacheSize, AMQPConnections
      if (Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))) {
         return name;
      }

      // Otherwise decapitalize first character
      return Character.toLowerCase(name.charAt(0)) + name.substring(1);
   }

   /**
    * Create lowercase variant for acronym-prefixed properties. AMQPConnectionConfigurations →
    * amqpConnectionConfigurations, JMXDomain → jmxDomain.
    *
    * @param propertyName property name starting with 2+ uppercase letters
    * @return lowercase-acronym variant
    */
   private String createLowercaseAcronymVariant(String propertyName) {
      if (propertyName.length() > 2 && Character.isUpperCase(propertyName.charAt(2))) {
         // 3+ uppercase chars: find where uppercase run ends
         int i = 2;
         while (i < propertyName.length() && Character.isUpperCase(propertyName.charAt(i))) {
            i++;
         }
         // Now i is at first lowercase or end of string
         if (i < propertyName.length()) {
            // AMQPConnectionConfigurations: i=5 at 'o' → "amqp" + "ConnectionConfigurations"
            return propertyName.substring(0, i - 1).toLowerCase() + propertyName.substring(i - 1);
         } else {
            // All uppercase: AMQP → amqp
            return propertyName.toLowerCase();
         }
      } else {
         // Only 2 uppercase: JMXDomain → jmxDomain
         return Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
      }
   }

   /**
    * Extract and clean up JavaDoc description: gets main description (before block tags), cleans up
    * inline tags ({@link}, {@code}), and strips "Sets"/"Returns"/"Gets" prefixes.
    *
    * @param javadoc parsed JavaDoc object
    * @return cleaned description text, or empty string if none
    */
   private String extractDescription(Javadoc javadoc) {
      // Get main description (before block tags)
      JavadocDescription description = javadoc.getDescription();
      String text = description.toText().trim();

      if (text.isEmpty()) {
         return "";
      }

      // Clean up JavaDoc tags
      text = cleanupJavaDocTags(text);

      // Remove common prefixes
      text = text.replaceFirst("^Sets? ", "");
      text = text.replaceFirst("^Returns? ", "");
      text = text.replaceFirst("^Gets? ", "");

      // Capitalize first letter after cleanup
      if (!text.isEmpty()) {
         text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
      }

      return text.trim();
   }

   /**
    * Strip JavaDoc inline tags down to their text content. {@code {@link Class}} → Class, {@code
    * {@code value}} → value.
    *
    * @param text raw JavaDoc text potentially containing inline tags
    * @return text with all inline tags replaced by their content
    */
   private String cleanupJavaDocTags(String text) {
      // Remove {@link ClassName} and {@link ClassName#method}
      text = text.replaceAll("\\{@link\\s+([^}#]+)(?:#[^}]+)?\\}", "$1");

      // Remove {@code value}
      text = text.replaceAll("\\{@code\\s+([^}]+)\\}", "$1");

      // Remove {@return ...} - not standard but sometimes used
      text = text.replaceAll("\\{@return\\s+([^}]+)\\}", "$1");

      // Remove {@literal value}
      text = text.replaceAll("\\{@literal\\s+([^}]+)\\}", "$1");

      // Clean up leftover braces
      text = text.replaceAll("[{}]", "");

      return text;
   }

   @Override
   public String getName() {
      return "SetterGetterJavadocExtractor";
   }
}
