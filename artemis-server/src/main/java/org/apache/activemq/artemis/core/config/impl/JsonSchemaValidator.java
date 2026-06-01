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
package org.apache.activemq.artemis.core.config.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.InputStream;
import java.util.Set;

/**
 * Validates JSON broker configuration against the JSON Schema.
 * Schema validation is optional and controlled via system property.
 */
public class JsonSchemaValidator {
   private static final String SCHEMA_RESOURCE = "/org.apache.artemis/jsonschema/broker-config-schema.json";
   private static final String VALIDATION_ENABLED_PROPERTY = "artemis.config.validate-json";

   private static JsonSchema schema;
   private static final ObjectMapper objectMapper = new ObjectMapper();

   /**
    * Check if JSON schema validation is enabled.
    * Enabled via -Dartemis.config.validate-json=true system property.
    */
   public static boolean isValidationEnabled() {
      return Boolean.getBoolean(VALIDATION_ENABLED_PROPERTY);
   }

   /**
    * Validate JSON configuration content against the schema.
    *
    * @param jsonContent JSON configuration as string
    * @throws Exception if validation fails or schema cannot be loaded
    */
   public static void validateJsonConfig(String jsonContent) throws Exception {
      if (!isValidationEnabled()) {
         return;
      }

      if (schema == null) {
         loadSchema();
      }

      // Parse JSON content to JsonNode
      JsonNode jsonNode = objectMapper.readTree(jsonContent);

      // Validate against schema
      Set<ValidationMessage> errors = schema.validate(jsonNode);
      if (!errors.isEmpty()) {
         StringBuilder sb = new StringBuilder("JSON configuration validation failed:\n");
         for (ValidationMessage error : errors) {
            sb.append("  - ").append(error.getMessage()).append("\n");
         }
         throw new IllegalArgumentException(sb.toString());
      }
   }

   /**
    * Load JSON schema from classpath resource.
    * Schema is provided by artemis-jsonschema module.
    */
   private static synchronized void loadSchema() throws Exception {
      if (schema != null) {
         return;
      }

      InputStream schemaStream = JsonSchemaValidator.class.getResourceAsStream(SCHEMA_RESOURCE);
      if (schemaStream == null) {
         throw new IllegalStateException("JSON schema not found: " + SCHEMA_RESOURCE +
                                        ". Ensure artemis-jsonschema module was built with -Pgenerate-schema profile.");
      }

      JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
      schema = factory.getSchema(schemaStream);
   }
}
