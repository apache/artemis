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

package org.apache.artemis.jsonschema.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates JSON broker properties files against the generated JSON Schema.
 *
 * <p>Uses networknt/json-schema-validator for JSON Schema Draft 7 validation.
 */
public class SchemaValidator {

   private static final Logger LOG = LoggerFactory.getLogger(SchemaValidator.class);

   private final JsonSchema schema;
   private final ObjectMapper mapper;

   /**
    * Create validator from schema file.
    *
    * @param schemaPath Path to JSON Schema file (must be transformed schema)
    * @throws IOException if schema cannot be loaded
    */
   public SchemaValidator(Path schemaPath) throws IOException {
      this.mapper = new ObjectMapper();

      // Load schema
      JsonNode schemaNode = mapper.readTree(schemaPath.toFile());

      // Create validator
      JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
      this.schema = factory.getSchema(schemaNode);
   }

   /**
    * Validate a JSON broker properties file.
    *
    * @param jsonPath Path to JSON file to validate
    * @return ValidationResult with validation outcome
    * @throws IOException if JSON file cannot be read
    */
   public ValidationResult validate(Path jsonPath) throws IOException {
      // Load JSON
      JsonNode jsonNode = mapper.readTree(jsonPath.toFile());

      // Validate
      Set<ValidationMessage> errors = schema.validate(jsonNode);

      return new ValidationResult(jsonPath, errors);
   }

   /**
    * Validate a JSON string.
    *
    * @param json JSON string to validate
    * @return ValidationResult with validation outcome
    * @throws IOException if JSON cannot be parsed
    */
   public ValidationResult validateString(String json) throws IOException {
      JsonNode jsonNode = mapper.readTree(json);
      Set<ValidationMessage> errors = schema.validate(jsonNode);
      return new ValidationResult(null, errors);
   }

   /** Result of validation. */
   public static class ValidationResult {
      private final Path file;
      private final Set<ValidationMessage> errors;

      /**
       * @param file path to the validated file, or {@code null} for string validation
       * @param errors validation errors (empty if valid)
       */
      public ValidationResult(Path file, Set<ValidationMessage> errors) {
         this.file = file;
         this.errors = errors;
      }

      /**
       * @return {@code true} if no validation errors were found
       */
      public boolean isValid() {
         return errors.isEmpty();
      }

      /**
       * @return the set of validation error messages
       */
      public Set<ValidationMessage> getErrors() {
         return errors;
      }

      /** Log the validation outcome — success or each error message. */
      public void printResult() {
         String target = file != null ? file.getFileName().toString() : "JSON string";
         if (isValid()) {
            LOG.info("Valid: {}", target);
         } else {
            LOG.warn("Invalid: {}", target);
            LOG.warn("Validation errors ({})", errors.size());
            for (ValidationMessage error : errors) {
               LOG.warn("  - {}", error.getMessage());
            }
         }
      }

      @Override
      public String toString() {
         if (isValid()) {
            return "Valid";
         } else {
            return "Invalid (" + errors.size() + " errors)";
         }
      }
   }
}
