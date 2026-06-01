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
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.apache.artemis.jsonschema.config.SchemaGeneratorConfig;
import org.apache.artemis.jsonschema.ir.PropertyDescriptor;
import org.apache.artemis.jsonschema.ir.PropertySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts runtime metadata about broker properties by parsing ActiveMQServerImpl.
 *
 * <p>Discovers hot-reloadable properties by finding all {@code configuration.setXxx()} calls inside
 * {@code updateReloadableConfigurationFrom()} — the method that applies configuration changes
 * without broker restart.
 */
public class MetadataExtractor implements Extractor {

   private static final Logger LOG = LoggerFactory.getLogger(MetadataExtractor.class);

   /** {@inheritDoc} */
   @Override
   public List<PropertyDescriptor> extract(Path artemisRoot) throws ExtractionException {
      try {
         return extractReloadableProperties(artemisRoot);
      } catch (Exception e) {
         throw new ExtractionException(getName(), "Failed to extract metadata", e);
      }
   }

   /**
    * Parse ActiveMQServerImpl.updateReloadableConfigurationFrom() to find which properties are
    * hot-reloadable (updated without broker restart).
    *
    * @param artemisRoot path to Artemis source root
    * @return descriptors with hotReloadable=true for each property set in that method
    */
   private List<PropertyDescriptor> extractReloadableProperties(Path artemisRoot) {
      List<PropertyDescriptor> descriptors = new ArrayList<>();

      SchemaGeneratorConfig config = SchemaGeneratorConfig.load();
      String reloadSource = config.getReloadableConfigSource();
      Path serverImplFile = null;

      for (String dir : config.getJavadocSourceDirs()) {
         Path candidate = artemisRoot.resolve(dir).resolve(reloadSource);
         if (Files.exists(candidate)) {
            serverImplFile = candidate;
            break;
         }
      }

      if (serverImplFile == null) {
         LOG.warn("ActiveMQServerImpl.java not found — cannot extract reloadable properties");
         return descriptors;
      }

      try {
         CompilationUnit cu = StaticJavaParser.parse(serverImplFile);

         Optional<MethodDeclaration> reloadMethod =
               cu.findAll(MethodDeclaration.class).stream()
                     .filter(m -> m.getNameAsString().equals("updateReloadableConfigurationFrom"))
                     .findFirst();

         if (!reloadMethod.isPresent()) {
            LOG.warn("updateReloadableConfigurationFrom() not found in ActiveMQServerImpl");
            return descriptors;
         }

         // Find all configuration.setXxx(...) calls → property "xxx" is hot-reloadable
         reloadMethod
               .get()
               .findAll(MethodCallExpr.class)
               .forEach(
                     call -> {
                        String methodName = call.getNameAsString();
                        if (methodName.startsWith("set")
                              && methodName.length() > 3
                              && call.getScope().isPresent()
                              && call.getScope().get().toString().equals("configuration")) {

                           String rawName = methodName.substring(3);
                           String propertyName =
                                 Character.toLowerCase(rawName.charAt(0)) + rawName.substring(1);

                           PropertyDescriptor descriptor =
                                 new PropertyDescriptor(propertyName, PropertySource.METADATA);
                           descriptor.getMetadata().setHotReloadable(true);
                           descriptors.add(descriptor);

                           // BeanInfo property names may differ from setter names:
                           // - Acronym prefix: setAMQPX → BeanInfo has "AMQPx" not "aMQPx"
                           // - Suffix mismatch: setAMQPConnectionConfigurations → BeanInfo has
                           // "AMQPConnection"
                           // Emit variants to maximize match likelihood.
                           if (Character.isUpperCase(rawName.charAt(0))
                                 && rawName.length() > 1
                                 && Character.isUpperCase(rawName.charAt(1))) {
                              PropertyDescriptor acronymVariant =
                                    new PropertyDescriptor(rawName, PropertySource.METADATA);
                              acronymVariant.getMetadata().setHotReloadable(true);
                              descriptors.add(acronymVariant);
                           }
                           if (rawName.endsWith("Configurations")) {
                              String stripped =
                                    rawName.substring(0, rawName.length() - "Configurations".length());
                              PropertyDescriptor strippedVariant =
                                    new PropertyDescriptor(stripped, PropertySource.METADATA);
                              strippedVariant.getMetadata().setHotReloadable(true);
                              descriptors.add(strippedVariant);
                           }
                        }
                     });

         if (!descriptors.isEmpty()) {
            LOG.info("Found {} hot-reloadable properties", descriptors.size());
         }

      } catch (Exception e) {
         LOG.warn("Failed to parse ActiveMQServerImpl: {}", e.getMessage());
      }

      return descriptors;
   }

   @Override
   public String getName() {
      return "MetadataExtractor";
   }
}
