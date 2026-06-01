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
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.artemis.jsonschema.ir.PropertyDescriptor;
import org.apache.artemis.jsonschema.ir.PropertyMetadata;
import org.apache.artemis.jsonschema.ir.PropertySource;
import org.apache.artemis.jsonschema.ir.SchemaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers BeanUtils type converter registrations from ConfigurationImpl and produces
 * PropertyDescriptors that widen numeric types to accept string notation (e.g. "25K", "10M" for
 * byte sizes via ByteUtil.convertTextBytes).
 *
 * <p>Replaces the post-emission TypeConverterEnricher with a standard Extractor that works through
 * the enrichment pipeline.
 */
public class TypeConverterExtractor implements Extractor {

   private static final Logger LOG = LoggerFactory.getLogger(TypeConverterExtractor.class);

   private static final Map<String, String> JAVA_TO_JSON_TYPE =
         Map.of(
               "long", "integer",
               "int", "integer",
               "double", "number",
               "float", "number");

   @Override
   public List<PropertyDescriptor> extract(Path artemisRoot) throws ExtractionException {
      Path configImplFile =
            artemisRoot.resolve(
                  "artemis-server/src/main/java/org/apache/activemq/artemis/core/config/impl/ConfigurationImpl.java");
      if (!Files.exists(configImplFile)) {
         throw new ExtractionException(getName(), "ConfigurationImpl.java not found");
      }

      Map<String, ConverterInfo> converters = discoverConverters(configImplFile);
      if (converters.isEmpty()) {
         return List.of();
      }
      LOG.info("Discovered {} type converters", converters.size());

      List<PropertyDescriptor> descriptors = new ArrayList<>();
      addDescriptorsForClass(
            "org.apache.activemq.artemis.core.config.impl.ConfigurationImpl",
            "",
            converters,
            descriptors);
      addDescriptorsForClass(
            "org.apache.activemq.artemis.core.settings.impl.AddressSettings",
            "addressSettings.*",
            converters,
            descriptors);

      LOG.info("Produced {} type converter enrichments", descriptors.size());
      return descriptors;
   }

   private void addDescriptorsForClass(
         String className,
         String pathPrefix,
         Map<String, ConverterInfo> converters,
         List<PropertyDescriptor> descriptors) {
      try {
         Class<?> clazz = Class.forName(className);
         java.beans.BeanInfo beanInfo = java.beans.Introspector.getBeanInfo(clazz);

         for (java.beans.PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
            Class<?> propType = pd.getPropertyType();
            if (propType == null) continue;

            String javaType =
                  propType.isPrimitive() ? propType.getName() : propType.getSimpleName().toLowerCase();

            ConverterInfo converter = converters.get(javaType);
            if (converter == null || !"string".equals(converter.inputType)) continue;

            String jsonType = JAVA_TO_JSON_TYPE.get(javaType);
            if (jsonType == null) continue;

            String path = pathPrefix.isEmpty() ? pd.getName() : pathPrefix + "." + pd.getName();
            PropertyDescriptor descriptor = new PropertyDescriptor(path, PropertySource.ENRICHMENT);
            PropertyMetadata meta = descriptor.getMetadata();

            meta.setType(SchemaType.of(SchemaType.Kind.fromSchema(jsonType), SchemaType.Kind.STRING));
            meta.setPattern(converter.pattern);
            meta.setConverter(converter.converterMethod);

            descriptors.add(descriptor);
         }
      } catch (Exception e) {
         LOG.debug("Could not introspect {}: {}", className, e.getMessage());
      }
   }

   private Map<String, ConverterInfo> discoverConverters(Path configImplFile)
         throws ExtractionException {
      Map<String, ConverterInfo> converters = new HashMap<>();
      try {
         CompilationUnit cu = StaticJavaParser.parse(configImplFile);
         Optional<MethodDeclaration> populateMethod =
               cu.findAll(MethodDeclaration.class).stream()
                     .filter(m -> m.getNameAsString().equals("populateWithProperties"))
                     .findFirst();

         if (populateMethod.isEmpty()) {
            throw new ExtractionException(getName(), "populateWithProperties method not found");
         }

         List<MethodCallExpr> registerCalls =
               populateMethod.get().findAll(MethodCallExpr.class).stream()
                     .filter(call -> call.getNameAsString().equals("register"))
                     .filter(
                           call ->
                                 call.getScope().isPresent()
                                       && call.getScope().get().toString().contains("getConvertUtils"))
                     .collect(Collectors.toList());

         for (MethodCallExpr registerCall : registerCalls) {
            if (registerCall.getArguments().size() < 2) continue;
            String targetType = extractTargetType(registerCall.getArgument(1));
            if (targetType == null) continue;
            ConverterInfo info = analyzeConverter(registerCall.getArgument(0));
            if (info != null) {
               converters.put(targetType, info);
            }
         }
      } catch (ExtractionException e) {
         throw e;
      } catch (Exception e) {
         throw new ExtractionException(getName(), "Failed to parse ConfigurationImpl", e);
      }
      return converters;
   }

   private String extractTargetType(Expression typeExpr) {
      String s = typeExpr.toString();
      if (s.endsWith(".TYPE")) return s.substring(0, s.length() - 5).toLowerCase();
      if (s.endsWith(".class")) return s.substring(0, s.length() - 6);
      return null;
   }

   private ConverterInfo analyzeConverter(Expression converterExpr) {
      String code = converterExpr.toString();
      if (code.contains("ByteUtil.convertTextBytes")) {
         return new ConverterInfo(
               "string",
               "^\\d+\\s*([KkMmGg][Ii]?[Bb]?)?$",
               "Accepts byte notation: plain numbers or with units (K, M, G, KB, MB, GB)",
               "ByteUtil.convertTextBytes");
      }
      if (code.contains("split(\",\")")) {
         return new ConverterInfo(
               "string",
               null,
               code.contains("Set") ? "Comma-separated values" : "Comma-separated key=value pairs",
               "CSV split");
      }
      return null;
   }

   @Override
   public String getName() {
      return "TypeConverterExtractor";
   }

   private record ConverterInfo(
         String inputType, String pattern, String description, String converterMethod) {}
}
