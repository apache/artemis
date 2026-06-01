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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.artemis.jsonschema.config.SchemaGeneratorConfig;
import org.apache.artemis.jsonschema.ir.Location;
import org.apache.artemis.jsonschema.ir.SchemaIR;
import org.apache.artemis.jsonschema.ir.SchemaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers known keys for {@code Map<String, Object>} properties by scanning constant classes
 * that define the valid property key names (e.g., AMQPBridgeConstants, AMQPFederationConstants).
 *
 * <p>Reads mappings from {@code mapConstantKeys} in schema-generator-config.json and injects
 * the discovered keys as typed properties alongside additionalProperties on the target map fields.
 *
 * <p>This builder handles structural discovery only (key names and types). Descriptions are
 * added during the enrichment phase by extractors that process Javadoc from the same source files.
 */
public class MapConstantKeysBuilder {

   private static final Logger LOG = LoggerFactory.getLogger(MapConstantKeysBuilder.class);

   private final SchemaIR ir;
   private final Path artemisRoot;
   private final Map<String, List<String>> mappings;

   /**
    * @param ir the intermediate representation to inject discovered properties into
    * @param artemisRoot path to Artemis source root for Javadoc extraction
    */
   public MapConstantKeysBuilder(SchemaIR ir, Path artemisRoot) {
      this.ir = ir;
      this.artemisRoot = artemisRoot;
      this.mappings = SchemaGeneratorConfig.load().getMapConstantKeys();
   }

   /**
    * Process all configured constant classes and inject their keys into the IR as enrichments.
    * Each mapping entry associates a constants class with the schema paths where its keys apply.
    */
   public void build() {
      for (Map.Entry<String, List<String>> entry : mappings.entrySet()) {
         buildOne(entry.getKey(), entry.getValue());
      }
   }

   /**
    * Process a single constants class: extract its String constants, build a properties schema
    * from them, and inject it as an enrichment at each configured map property path.
    *
    * @param constantsClassName fully qualified class name of the constants class
    * @param mapPropertyPaths schema paths to the map properties that use these constants
    */
   private void buildOne(String constantsClassName, List<String> mapPropertyPaths) {
      try {
         Class<?> constantsClass = Class.forName(constantsClassName);
         Map<String, ConstantInfo> constants = extractConstants(constantsClass);

         if (constants.isEmpty()) {
            LOG.warn("No String constants found in {}", constantsClassName);
            return;
         }

         LOG.info("{}: {} property keys discovered", constantsClass.getSimpleName(), constants.size());

         Map<String, String> javadocs = extractJavadocs(constantsClassName);

         Map<String, Object> knownProperties = new LinkedHashMap<>();
         for (Map.Entry<String, ConstantInfo> entry : constants.entrySet()) {
            String key = entry.getKey();
            ConstantInfo info = entry.getValue();

            Map<String, Object> propSchema = new LinkedHashMap<>();
            propSchema.put("type", inferType(info));
            String javadoc = javadocs.get(info.fieldName);
            if (javadoc != null) {
               propSchema.put("description", javadoc);
            }
            knownProperties.put(key, propSchema);
         }

         for (String path : mapPropertyPaths) {
            Map<String, Object> enrichment = new LinkedHashMap<>();
            enrichment.put("properties", knownProperties);
            ir.enrich(Location.of(path), enrichment);
         }

      } catch (ClassNotFoundException e) {
         LOG.warn("Constants class not on classpath: {}", constantsClassName);
      }
   }

   /**
    * Extract all user-settable String constants from a class via reflection. Filters out
    * internal/protocol constants (SCREAMING_CASE values, addresses containing $ or /).
    * Pairs each constant with its default value (from DEFAULT_* fields) for type inference.
    *
    * @param clazz the constants class to scan
    * @return map of property key (the constant's String value) to its metadata
    */
   private Map<String, ConstantInfo> extractConstants(Class<?> clazz) {
      Map<String, ConstantInfo> constants = new LinkedHashMap<>();
      Map<String, Object> defaults = extractDefaults(clazz);

      for (Field field : clazz.getDeclaredFields()) {
         int mods = field.getModifiers();
         if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods) || !Modifier.isFinal(mods)) {
            continue;
         }
         if (field.getType() != String.class) {
            continue;
         }

         try {
            String value = (String) field.get(null);
            if (value == null || value.isEmpty()) {
               continue;
            }
            if (value.contains("_") && value.equals(value.toUpperCase())) {
               continue;
            }
            if (value.contains("$") || value.contains("/")) {
               continue;
            }

            Object defaultValue = defaults.get(field.getName());
            constants.put(value, new ConstantInfo(field.getName(), defaultValue));
         } catch (IllegalAccessException e) {
            // skip inaccessible fields
         }
      }
      return constants;
   }

   /**
    * Extract DEFAULT_* fields to infer types for their corresponding constants.
    * Values are used only for type inference, not surfaced in the schema.
    *
    * @param clazz the constants class to scan for DEFAULT_ fields
    * @return map of constant field name (without DEFAULT_ prefix) to default value (for type inference only)
    */
   private Map<String, Object> extractDefaults(Class<?> clazz) {
      Map<String, Object> defaults = new LinkedHashMap<>();
      for (Field field : clazz.getDeclaredFields()) {
         int mods = field.getModifiers();
         if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods) || !Modifier.isFinal(mods)) {
            continue;
         }
         String name = field.getName();
         if (!name.startsWith("DEFAULT_")) {
            continue;
         }
         try {
            Object value = field.get(null);
            String keyFieldName = name.substring("DEFAULT_".length());
            defaults.put(keyFieldName, value);
         } catch (IllegalAccessException e) {
            // skip inaccessible fields
         }
      }
      return defaults;
   }

   /**
    * Extract Javadoc comments for {@code public static final String} fields from the class's
    * source file. Searches {@code javadocSourceDirs} from config.
    *
    * @param className fully qualified class name
    * @return map of field name to cleaned Javadoc text
    */
   private Map<String, String> extractJavadocs(String className) {
      Map<String, String> javadocs = new LinkedHashMap<>();
      try {
         String classPath = className.replace('.', '/') + ".java";
         List<String> sourceDirs = SchemaGeneratorConfig.load().getJavadocSourceDirs();
         Path sourceFile = null;
         for (String dir : sourceDirs) {
            Path candidate = artemisRoot.resolve(dir).resolve(classPath);
            if (Files.exists(candidate)) {
               sourceFile = candidate;
               break;
            }
         }
         if (sourceFile == null) {
            return javadocs;
         }

         String source = Files.readString(sourceFile);
         Pattern fieldPattern = Pattern.compile(
               "/\\*\\*([^*]|\\*(?!/))*\\*/\\s*\n\\s*public static final String (\\w+)");
         Matcher m = fieldPattern.matcher(source);
         while (m.find()) {
            String rawDoc = m.group(0)
                  .replaceAll("public static final String \\w+", "")
                  .replaceAll("/\\*\\*|\\*/", "")
                  .replaceAll("\\n\\s*\\*\\s?", " ")
                  .replaceAll("\\{@link [^}]*}", "")
                  .replaceAll("\\{@code ([^}]*)}", "$1")
                  .trim();
            String fieldName = m.group(2);
            if (!rawDoc.isEmpty()) {
               javadocs.put(fieldName, rawDoc);
            }
         }
      } catch (Exception e) {
         LOG.debug("Could not extract Javadocs for {}: {}", className, e.getMessage());
      }
      return javadocs;
   }

   /**
    * Infer the JSON Schema type for a constant key based on its DEFAULT_* field type.
    * Falls back to "string" when no default exists.
    *
    * @param info the constant's metadata (default value for type inference)
    * @return the JSON Schema type string ("string", "integer", or "boolean")
    */
   private String inferType(ConstantInfo info) {
      if (info.defaultValue instanceof Boolean) {
         return new SchemaType(SchemaType.Kind.BOOLEAN).toSchemaValue().toString();
      } else if (info.defaultValue instanceof Number) {
         return new SchemaType(SchemaType.Kind.INTEGER).toSchemaValue().toString();
      }
      return new SchemaType(SchemaType.Kind.STRING).toSchemaValue().toString();
   }

   /**
    * Metadata for a single discovered constant key.
    */
   private static class ConstantInfo {
      final String fieldName;
      final Object defaultValue;

      /**
       * @param fieldName Java field name (used to look up Javadoc)
       * @param defaultValue value from the corresponding DEFAULT_* field, or null (for type inference)
       */
      ConstantInfo(String fieldName, Object defaultValue) {
         this.fieldName = fieldName;
         this.defaultValue = defaultValue;
      }
   }
}
