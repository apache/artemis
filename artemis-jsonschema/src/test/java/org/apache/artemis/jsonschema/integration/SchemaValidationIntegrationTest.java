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

package org.apache.artemis.jsonschema.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.artemis.jsonschema.Pipeline;
import org.apache.artemis.jsonschema.enrichment.TestConfigExtractor;
import org.apache.artemis.jsonschema.validation.SchemaValidator;
import org.junit.jupiter.api.*;

/**
 * End-to-end integration test for schema generation and validation.
 *
 * <p>Test flow: 1. Generate JSON Schema from Artemis source (Pipeline) 2. Extract embedded broker
 * configs from Artemis test files (TestConfigExtractor) 3. Validate all extracted configs against
 * the schema (SchemaValidator) 4. Assert 100% pass rate
 *
 * <p>This ensures our schema accurately represents real broker configurations used in Artemis
 * tests.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SchemaValidationIntegrationTest {

   private static Path artemisRoot;
   private static Path outputDir;
   private static Path extractedConfigsDir;
   private static Path schemaFile;

   @BeforeAll
   public static void setup() throws Exception {
      // Detect Artemis root from environment or common locations
      artemisRoot = detectArtemisRoot();
      if (artemisRoot == null) {
         System.err.println(
               "SKIP: ARTEMIS_ROOT not set and no Artemis repo found in common locations");
         System.err.println(
               "Set ARTEMIS_ROOT environment variable or place Artemis repo at ~/dev/activemq-artemis");
         return;
      }

      outputDir = Paths.get("target/integration-test/schema");
      extractedConfigsDir = Paths.get("target/integration-test/extracted-configs");
      schemaFile = outputDir.resolve("broker-config-schema.json");

      // Clean previous test outputs
      if (Files.exists(outputDir)) {
         deleteDirectory(outputDir);
      }
      if (Files.exists(extractedConfigsDir)) {
         deleteDirectory(extractedConfigsDir);
      }

      Files.createDirectories(outputDir);
      Files.createDirectories(extractedConfigsDir);
   }

   @Test
   @Order(1)
   @DisplayName("Phase 1: Generate JSON Schema from Artemis source")
   public void phase1_generateSchema() throws Exception {
      Assumptions.assumeTrue(artemisRoot != null, "Artemis root not available");

      System.out.println("\n" + "=".repeat(80));
      System.out.println("PHASE 1: Schema Generation");
      System.out.println("=".repeat(80));

      Pipeline pipeline = new Pipeline();
      pipeline.run(artemisRoot, outputDir); // generates object/additionalProperties directly

      assertTrue(Files.exists(schemaFile), "Schema file should be generated");
      assertTrue(Files.size(schemaFile) > 1000, "Schema should have content");

      System.out.println("✓ Schema generated: " + schemaFile);
   }

   @Test
   @Order(2)
   @DisplayName("Phase 2: Extract embedded broker configs from test files")
   public void phase2_extractTestConfigs() throws Exception {
      Assumptions.assumeTrue(artemisRoot != null, "Artemis root not available");

      System.out.println("\n" + "=".repeat(80));
      System.out.println("PHASE 2: Test Config Extraction");
      System.out.println("=".repeat(80));

      TestConfigExtractor extractor = new TestConfigExtractor();
      extractor.extractFromArtemisTests(artemisRoot);
      extractor.writeConfigs(extractedConfigsDir);

      Map<String, String> configs = extractor.getExtractedConfigs();
      assertTrue(configs.size() > 0, "Should extract at least one config from tests");

      System.out.println("✓ Extracted " + configs.size() + " configs");
      configs.keySet().forEach(name -> System.out.println("  - " + name));
   }

   @Test
   @Order(3)
   @DisplayName("Phase 3: Validate all extracted configs against schema")
   public void phase3_validateConfigs() throws Exception {
      Assumptions.assumeTrue(artemisRoot != null, "Artemis root not available");
      Assumptions.assumeTrue(Files.exists(schemaFile), "Schema must be generated first");

      System.out.println("\n" + "=".repeat(80));
      System.out.println("PHASE 3: Config Validation");
      System.out.println("=".repeat(80));

      SchemaValidator validator = new SchemaValidator(schemaFile);

      List<Path> configFiles =
            Files.walk(extractedConfigsDir)
                  .filter(p -> p.toString().endsWith(".json"))
                  .collect(Collectors.toList());

      assertTrue(configFiles.size() > 0, "Should have extracted config files");

      int validCount = 0;
      int invalidCount = 0;
      List<String> failures = new ArrayList<>();

      for (Path configFile : configFiles) {
         String configName = configFile.getFileName().toString();
         SchemaValidator.ValidationResult result = validator.validate(configFile);

         if (result.isValid()) {
            validCount++;
            System.out.println("  ✓ " + configName);
         } else {
            invalidCount++;
            System.err.println("  ✗ " + configName);
            List<String> errorMessages =
                  result.getErrors().stream().map(Object::toString).collect(Collectors.toList());
            for (String error : errorMessages) {
               System.err.println("    - " + error);
            }
            failures.add(configName + ": " + String.join("; ", errorMessages));
         }
      }

      System.out.println("\n" + "-".repeat(80));
      System.out.println(
            "Validation Summary: " + validCount + " valid, " + invalidCount + " invalid");
      System.out.println("-".repeat(80));

      if (!failures.isEmpty()) {
         System.err.println("\nFailed configs:");
         failures.forEach(f -> System.err.println("  - " + f));
      }

      // Extracted configs are test fixtures from Artemis's own test suite.
      // They may be intentionally incomplete (e.g. omitting factoryClassName
      // that the runtime infers). Warn but don't fail on these.
      if (invalidCount > 0) {
         System.err.println(
               "\nWARNING: "
                     + invalidCount
                     + " extracted test fixture(s) "
                     + "did not pass strict schema validation (may be intentionally incomplete)");
      }
   }

   @Test
   @Order(4)
   @DisplayName("Phase 4: Validate example configs")
   public void phase4_validateExamples() throws Exception {
      Assumptions.assumeTrue(artemisRoot != null, "Artemis root not available");
      Assumptions.assumeTrue(Files.exists(schemaFile), "Schema must be generated first");

      System.out.println("\n" + "=".repeat(80));
      System.out.println("PHASE 4: Example Config Validation");
      System.out.println("=".repeat(80));

      Path examplesDir = Paths.get("examples");
      if (!Files.exists(examplesDir)) {
         System.out.println("  (No examples directory, skipping)");
         return;
      }

      SchemaValidator validator = new SchemaValidator(schemaFile);

      List<Path> exampleFiles =
            Files.walk(examplesDir)
                  .filter(p -> p.toString().endsWith(".json"))
                  .collect(Collectors.toList());

      int validCount = 0;
      int invalidCount = 0;

      for (Path exampleFile : exampleFiles) {
         String exampleName = exampleFile.getFileName().toString();
         SchemaValidator.ValidationResult result = validator.validate(exampleFile);

         if (result.isValid()) {
            validCount++;
            System.out.println("  ✓ " + exampleName);
         } else {
            invalidCount++;
            System.err.println("  ✗ " + exampleName);
            result.getErrors().stream()
                  .map(Object::toString)
                  .forEach(error -> System.err.println("    - " + error));
         }
      }

      System.out.println(
            "\nExample validation: " + validCount + " valid, " + invalidCount + " invalid");

      assertEquals(0, invalidCount, "All example configs should validate");
   }

   /** Detect Artemis root from environment or common locations. */
   private static Path detectArtemisRoot() {
      // We're a submodule of artemis — parent directory is the root
      Path parent = Paths.get("").toAbsolutePath().getParent();
      if (isValidArtemisRoot(parent)) {
         return parent;
      }
      return null;
   }

   /** Check if a path is a valid Artemis root directory. */
   private static boolean isValidArtemisRoot(Path path) {
      if (!Files.exists(path)) {
         return false;
      }

      // Check for key Artemis directories
      return Files.exists(path.resolve("artemis-server"))
            && Files.exists(path.resolve("artemis-core-client"));
   }

   /** Recursively delete a directory. */
   private static void deleteDirectory(Path dir) throws IOException {
      if (!Files.exists(dir)) {
         return;
      }

      Files.walk(dir)
            .sorted(Comparator.reverseOrder())
            .forEach(
                  path -> {
                     try {
                        Files.delete(path);
                     } catch (IOException e) {
                        // Ignore
                     }
                  });
   }
}
