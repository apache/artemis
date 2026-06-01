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

package org.apache.artemis.jsonschema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.apache.artemis.jsonschema.config.SchemaGeneratorConfig;
import org.apache.artemis.jsonschema.enrichment.Enricher;
import org.apache.artemis.jsonschema.enrichment.MetadataExtractor;
import org.apache.artemis.jsonschema.enrichment.SetterGetterJavadocExtractor;
import org.apache.artemis.jsonschema.enrichment.TypeConverterExtractor;
import org.apache.artemis.jsonschema.enrichment.XsdExtractor;
import org.apache.artemis.jsonschema.factories.FactoryVariantBuilder;
import org.apache.artemis.jsonschema.factories.MapConstantKeysBuilder;
import org.apache.artemis.jsonschema.ir.IRBuilder;
import org.apache.artemis.jsonschema.ir.SchemaEmitter;
import org.apache.artemis.jsonschema.ir.SchemaIR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main pipeline orchestrator for Apache Artemis broker JSON schema generation.
 *
 * <p>Generates a JSON Schema (Draft 7) that describes all valid broker configuration properties.
 * The schema is built by combining metadata from multiple sources:
 *
 * <ol>
 *   <li>Java reflection - Introspects Configuration classes to discover properties and types
 *   <li>JavaDoc extraction - Extracts documentation from source code comments
 *   <li>XSD metadata - Parses artemis-configuration.xsd for constraints and descriptions
 *   <li>Source analysis - Analyzes Java source files for constants and defaults
 *   <li>Factory discovery - Identifies plugin-specific configuration parameters
 * </ol>
 *
 * <p>Usage:
 *
 * <pre>
 *   java -jar artemis-jsonschema-generator.jar --artemis-root /path/to/artemis --output-dir output/
 * </pre>
 */
public class Pipeline {

   private static final Logger LOG = LoggerFactory.getLogger(Pipeline.class);

   /**
    * Configure JavaParser to use Java 17 language level globally. Must be called before any
    * JavaParser-based extractor runs.
    */
   private static void configureJavaParser() {
      StaticJavaParser.getParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
   }

   /**
    * CLI entry point. Parses {@code --artemis-root} and {@code --output-dir} arguments, then runs
    * the full schema generation pipeline.
    *
    * @param args command-line arguments
    * @throws Exception if the pipeline encounters a fatal error
    */
   public static void main(String[] args) throws Exception {
      // Parse arguments
      Path artemisRoot = null;
      Path outputDir = Paths.get("output");

      for (int i = 0; i < args.length; i++) {
         if ("--artemis-root".equals(args[i]) && i + 1 < args.length) {
            artemisRoot = Paths.get(args[i + 1]);
            i++;
         } else if ("--output-dir".equals(args[i]) && i + 1 < args.length) {
            outputDir = Paths.get(args[i + 1]);
            i++;
         }
      }

      if (artemisRoot == null) {
         System.err.println(
               "Usage: java -jar artemis-jsonschema-generator.jar --artemis-root <path> [--output-dir <path>]");
         System.exit(1);
      }

      LOG.info("--- Apache Artemis JSON Schema Generator ---");
      LOG.info("Artemis root: {}", artemisRoot);
      LOG.info("Output directory: {}", outputDir);

      // Run pipeline
      Pipeline pipeline = new Pipeline();
      pipeline.run(artemisRoot, outputDir);

      LOG.info("Pipeline complete!");
   }

   /**
    * Run the full extraction and schema generation pipeline.
    *
    * <p>Produces a JSON Schema file and documentation in the output directory. Pipeline failures in
    * required extractors propagate as exceptions; optional extractor failures are logged and
    * skipped.
    *
    * @param artemisRoot Path to Artemis source root (must contain standard module layout)
    * @param outputDir Output directory for generated schema and docs (created if absent)
    * @throws Exception if a required extractor fails or IR generation encounters a fatal error
    */
   public void run(Path artemisRoot, Path outputDir) throws Exception {
      configureJavaParser();

      LOG.info("IR Graph Generation");

      IRBuilder irBuilder = new IRBuilder();
      irBuilder.generateIR();
      irBuilder.logStats();
      SchemaIR ir = irBuilder.getIR();

      LOG.info("Factory Variant Discovery");

      for (FactoryVariantBuilder builder : FactoryVariantBuilder.createAll(ir, artemisRoot)) {
         builder.buildVariants(ir);
      }

      LOG.info("Map Constant Keys Discovery");

      new MapConstantKeysBuilder(ir, artemisRoot).build();

      LOG.info("Enrichment");

      Enricher enricher =
            new Enricher(
                  List.of(
                        new SetterGetterJavadocExtractor(),
                        new XsdExtractor(),
                        new MetadataExtractor(),
                        new TypeConverterExtractor()));
      enricher.extract(artemisRoot);
      enricher.enrich(ir);

      irBuilder.logDocumentationCoverage();

      LOG.info("Schema Emission");

      SchemaEmitter emitter = new SchemaEmitter(ir);
      Map<String, Object> baseSchema = emitter.emitSchema();

      irBuilder.logExtractionStats();

      LOG.info("Output Generation");

      Files.createDirectories(outputDir);
      Path schemaFile = outputDir.resolve("broker-config-schema.json");
      ObjectMapper mapper = new ObjectMapper();
      if (SchemaGeneratorConfig.load().isPrettyPrint()) {
         mapper.enable(SerializationFeature.INDENT_OUTPUT);
      }
      mapper.writeValue(schemaFile.toFile(), baseSchema);
      LOG.info("Schema written to: {}", schemaFile);
   }
}
