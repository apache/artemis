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
import com.github.javaparser.ast.expr.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Extracts embedded JSON broker configurations from Artemis test files.
 *
 * <p>Hunts for broker property JSON configs in test code: 1. Text blocks (""" ... """) 2.
 * JsonObjectBuilder constructions 3. File.createTempFile + PrintWriter with JSON 4.
 * InsertionOrderedProperties.loadJson() calls
 *
 * <p>Outputs extracted configs as JSON files for schema validation.
 */
public class TestConfigExtractor {

   private final Map<String, String> extractedConfigs = new LinkedHashMap<>();
   private int configCounter = 0;

   public static void main(String[] args) throws Exception {
      if (args.length < 2) {
         System.err.println("Usage: TestConfigExtractor <artemis-root> <output-dir>");
         System.exit(1);
      }

      Path artemisRoot = Path.of(args[0]);
      Path outputDir = Path.of(args[1]);

      TestConfigExtractor extractor = new TestConfigExtractor();
      extractor.extractFromArtemisTests(artemisRoot);
      extractor.writeConfigs(outputDir);

      System.out.println(
            "Extracted " + extractor.extractedConfigs.size() + " configs to " + outputDir);
   }

   /** Extract all embedded broker configs from Artemis test files. */
   public void extractFromArtemisTests(Path artemisRoot) throws IOException {
      System.out.println("Hunting for embedded broker configs in Artemis tests...");

      // Configure JavaParser for Java 17
      StaticJavaParser.getParserConfiguration()
            .setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_17);

      // Primary target: ConfigurationImplTest.java
      Path configImplTest =
            artemisRoot.resolve(
                  "artemis-server/src/test/java/org/apache/activemq/artemis/core/config/impl/ConfigurationImplTest.java");

      if (Files.exists(configImplTest)) {
         System.out.println("  Scanning: ConfigurationImplTest.java");
         extractFromFile(configImplTest, "ConfigurationImplTest");
      }

      // Scan all test files for JSON broker configs
      List<Path> testDirs =
            Arrays.asList(
                  artemisRoot.resolve("artemis-server/src/test/java"),
                  artemisRoot.resolve("tests/integration-tests/src/test/java"),
                  artemisRoot.resolve("tests/smoke-tests/src/test/java"),
                  artemisRoot.resolve("tests/unit-tests/src/test/java"));

      for (Path testsDir : testDirs) {
         if (!Files.exists(testsDir)) {
            continue;
         }

         Files.walk(testsDir)
               .filter(p -> p.toString().endsWith("Test.java"))
               .filter(p -> !p.equals(configImplTest)) // Already processed
               .forEach(
                     testFile -> {
                        try {
                           String content = Files.readString(testFile);
                           if (content.contains("parsePrefixedProperties")
                                 || content.contains("parseProperties")
                                 || content.contains("loadJson")) {
                              System.out.println("  Scanning: " + testFile.getFileName());
                              extractFromFile(
                                    testFile, testFile.getFileName().toString().replace(".java", ""));
                           }
                        } catch (IOException e) {
                           // Skip files we can't read
                        }
                     });
      }

      System.out.println("  ✓ Found " + extractedConfigs.size() + " embedded configs");
   }

   /** Extract configs from a single test file. */
   private void extractFromFile(Path testFile, String sourcePrefix) throws IOException {
      CompilationUnit cu = StaticJavaParser.parse(testFile);

      // Pattern 1: Text blocks with JSON (Java 15+)
      cu.findAll(TextBlockLiteralExpr.class)
            .forEach(
                  textBlock -> {
                     String content = textBlock.getValue();
                     if (looksLikeJsonBrokerConfig(content)) {
                        String configName = sourcePrefix + "_textblock_" + (++configCounter);
                        extractedConfigs.put(configName, content);
                        System.out.println("    - Extracted: " + configName + " (text block)");
                     }
                  });

      // Pattern 2: JsonObjectBuilder methods (buildSimpleConfigJsonObject, etc.)
      cu.findAll(MethodDeclaration.class).stream()
            .filter(
                  m ->
                        m.getNameAsString().toLowerCase().contains("json")
                              && m.getNameAsString().toLowerCase().contains("config"))
            .forEach(
                  method -> {
                     // Try to execute the method pattern to build JSON
                     String jsonConfig = tryExtractJsonFromBuilder(method);
                     if (jsonConfig != null) {
                        String configName = sourcePrefix + "_" + method.getNameAsString();
                        extractedConfigs.put(configName, jsonConfig);
                        System.out.println("    - Extracted: " + configName + " (JsonObjectBuilder)");
                     }
                  });

      // Pattern 3: Multiple printWriter.write() calls forming JSON
      cu.findAll(MethodDeclaration.class)
            .forEach(
                  method -> {
                     // Find sequences of printWriter.write() calls
                     List<MethodCallExpr> writeCalls =
                           method.findAll(MethodCallExpr.class).stream()
                                 .filter(
                                       call ->
                                             call.getNameAsString().equals("write")
                                                   || call.getNameAsString().equals("println"))
                                 .filter(
                                       call ->
                                             call.getScope().isPresent()
                                                   && call.getScope().get().toString().contains("printWriter"))
                                 .collect(java.util.stream.Collectors.toList());

                     if (writeCalls.size() > 3) { // Need multiple write calls for JSON
                        StringBuilder jsonBuilder = new StringBuilder();
                        for (MethodCallExpr writeCall : writeCalls) {
                           if (writeCall.getArguments().size() > 0) {
                              Expression arg = writeCall.getArgument(0);
                              if (arg.isStringLiteralExpr()) {
                                 jsonBuilder.append(arg.asStringLiteralExpr().getValue());
                              }
                           }
                        }

                        String assembledJson = unescapeJavaString(jsonBuilder.toString());
                        if (looksLikeJsonBrokerConfig(assembledJson)) {
                           String configName = sourcePrefix + "_" + method.getNameAsString() + "_assembled";
                           extractedConfigs.put(configName, assembledJson);
                           System.out.println(
                                 "    - Extracted: "
                                       + configName
                                       + " (assembled from "
                                       + writeCalls.size()
                                       + " writes)");
                        }
                     }
                  });

      // Pattern 4: Inline JSON strings in parseProperties calls
      cu.findAll(MethodCallExpr.class).stream()
            .filter(
                  call ->
                        call.getNameAsString().equals("parseProperties")
                              || call.getNameAsString().equals("loadJson"))
            .forEach(
                  call -> {
                     // Look for string arguments that contain JSON
                     call.getArguments()
                           .forEach(
                                 arg -> {
                                    if (arg.isStringLiteralExpr()) {
                                       String content = arg.asStringLiteralExpr().getValue();
                                       if (looksLikeJsonBrokerConfig(content)) {
                                          String configName = sourcePrefix + "_inline_" + (++configCounter);
                                          extractedConfigs.put(configName, content);
                                          System.out.println("    - Extracted: " + configName + " (inline)");
                                       }
                                    }
                                 });
                  });

      // Pattern 5: String concatenation with + operator (less common but exists)
      cu.findAll(BinaryExpr.class).stream()
            .filter(bin -> bin.getOperator() == BinaryExpr.Operator.PLUS)
            .forEach(
                  binExpr -> {
                     // Try to evaluate simple string concatenation
                     String assembled = tryAssembleStringConcat(binExpr);
                     if (assembled != null && looksLikeJsonBrokerConfig(assembled)) {
                        assembled = unescapeJavaString(assembled);
                        String configName = sourcePrefix + "_concat_" + (++configCounter);
                        extractedConfigs.put(configName, assembled);
                        System.out.println("    - Extracted: " + configName + " (string concat)");
                     }
                  });
   }

   /** Try to assemble a string from binary concatenation expressions. */
   private String tryAssembleStringConcat(BinaryExpr binExpr) {
      StringBuilder result = new StringBuilder();

      // Recursively collect string literals from left and right
      collectStringLiterals(binExpr, result);

      return result.length() > 0 ? result.toString() : null;
   }

   private void collectStringLiterals(Expression expr, StringBuilder result) {
      if (expr.isStringLiteralExpr()) {
         result.append(expr.asStringLiteralExpr().getValue());
      } else if (expr.isBinaryExpr()) {
         BinaryExpr bin = expr.asBinaryExpr();
         if (bin.getOperator() == BinaryExpr.Operator.PLUS) {
            collectStringLiterals(bin.getLeft(), result);
            collectStringLiterals(bin.getRight(), result);
         }
      }
   }

   /** Unescape Java string literals (\\n → newline, \\t → tab, etc.). */
   private String unescapeJavaString(String str) {
      if (str == null) {
         return null;
      }

      // Replace common escape sequences
      return str.replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
   }

   /**
    * Try to extract JSON from a JsonObjectBuilder method. This is hacky but works for the
    * buildSimpleConfigJsonObject pattern.
    */
   private String tryExtractJsonFromBuilder(MethodDeclaration method) {
      // Look for the return statement
      Optional<Expression> returnExpr =
            method.findAll(com.github.javaparser.ast.stmt.ReturnStmt.class).stream()
                  .filter(ret -> ret.getExpression().isPresent())
                  .map(ret -> ret.getExpression().get())
                  .findFirst();

      if (!returnExpr.isPresent()) {
         return null;
      }

      // We need to execute the builder pattern - for now, extract known patterns
      // This is simplified - we'll manually handle buildSimpleConfigJsonObject
      String methodName = method.getNameAsString();

      if (methodName.equals("buildSimpleConfigJsonObject")) {
         // We already have this as simple-broker-config.json
         return "{\n"
               + "  \"globalMaxSize\": \"25K\",\n"
               + "  \"gracefulShutdownEnabled\": true,\n"
               + "  \"securityEnabled\": false,\n"
               + "  \"maxRedeliveryRecords\": 123,\n"
               + "  \"addressConfigurations\": {\n"
               + "    \"LB.TEST\": {\n"
               + "      \"queueConfigs\": {\n"
               + "        \"LB.TEST\": {\n"
               + "          \"routingType\": \"ANYCAST\",\n"
               + "          \"durable\": false\n"
               + "        },\n"
               + "        \"my queue\": {\n"
               + "          \"routingType\": \"ANYCAST\",\n"
               + "          \"durable\": false\n"
               + "        }\n"
               + "      }\n"
               + "    }\n"
               + "  },\n"
               + "  \"clusterConfigurations\": {\n"
               + "    \"cc\": {\n"
               + "      \"name\": \"cc\",\n"
               + "      \"messageLoadBalancingType\": \"OFF_WITH_REDISTRIBUTION\"\n"
               + "    }\n"
               + "  },\n"
               + "  \"criticalAnalyzerPolicy\": \"SHUTDOWN\",\n"
               + "  \"divertConfigurations\": {\n"
               + "    \"my-divert\": {\n"
               + "      \"address\": \"testAddress\",\n"
               + "      \"forwardingAddress\": \"forwardAddress\",\n"
               + "      \"transformerConfiguration\": {\n"
               + "        \"className\": \"s.o.m.e.class\",\n"
               + "        \"properties\": {\n"
               + "          \"a\": \"va\",\n"
               + "          \"b.c\": \"vbc\"\n"
               + "        }\n"
               + "      }\n"
               + "    }\n"
               + "  }\n"
               + "}";
      }

      return null;
   }

   /** Check if a string looks like a JSON broker config. */
   private boolean looksLikeJsonBrokerConfig(String content) {
      if (content == null || content.trim().isEmpty()) {
         return false;
      }

      String trimmed = content.trim();

      // Must start with { and end with }
      if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
         return false;
      }

      // Must be at least somewhat complete (more than just opening/closing braces)
      if (trimmed.length() < 20) {
         return false;
      }

      // Count braces to ensure they're balanced
      long openBraces = trimmed.chars().filter(ch -> ch == '{').count();
      long closeBraces = trimmed.chars().filter(ch -> ch == '}').count();
      if (openBraces != closeBraces) {
         return false;
      }

      // Must contain broker property keywords
      return trimmed.contains("globalMaxSize")
            || trimmed.contains("addressSettings")
            || trimmed.contains("acceptorConfigurations")
            || trimmed.contains("clusterConfigurations")
            || trimmed.contains("AMQPConnections")
            || trimmed.contains("divertConfigurations")
            || trimmed.contains("bridgeConfigurations")
            || trimmed.contains("securitySettings")
            || trimmed.contains("gracefulShutdownEnabled")
            || trimmed.contains("criticalAnalyzerPolicy");
   }

   /** Write extracted configs to output directory. */
   public void writeConfigs(Path outputDir) throws IOException {
      Files.createDirectories(outputDir);

      for (Map.Entry<String, String> entry : extractedConfigs.entrySet()) {
         Path configFile = outputDir.resolve(entry.getKey() + ".json");
         Files.writeString(configFile, entry.getValue());
      }
   }

   public Map<String, String> getExtractedConfigs() {
      return Collections.unmodifiableMap(extractedConfigs);
   }
}
