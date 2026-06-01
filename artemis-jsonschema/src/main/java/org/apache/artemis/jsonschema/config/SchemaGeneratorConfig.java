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

package org.apache.artemis.jsonschema.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton configuration loaded from META-INF/schema-generator-config.json. Access via {@link
 * #load()} which lazily initializes and caches the instance.
 *
 * <p>All configuration that would otherwise be hardcoded in Java source lives here, making it
 * editable without recompilation. A contributor adding a new XSD type or factory interface updates
 * the JSON file, not the code.
 */
public final class SchemaGeneratorConfig {

   private static final Logger LOG = LoggerFactory.getLogger(SchemaGeneratorConfig.class);
   private static final String CONFIG_PATH = "/META-INF/schema-generator-config.json";
   private static volatile SchemaGeneratorConfig instance;

   /** Properties excluded from IR traversal (e.g., "class", "parent"). */
   private List<String> ignoredProperties = new ArrayList<>();

   /**
    * Interfaces whose implementations expose configurable parameters (AcceptorFactory, LoginModule).
    */
   private List<String> factoryInterfaces = new ArrayList<>();

   /** Packages scanned by Reflections to discover factory implementations. */
   private List<String> factoryScanPackages = new ArrayList<>();

   /**
    * Source directories scanned for *Configuration.java files (setter/getter JavaDoc extraction).
    */
   private List<String> javadocSourceDirs = new ArrayList<>();

   /** Path to artemis-configuration.xsd (relative to artemisRoot). */
   private String xsdPath = "";

   /** FileConfigurationParser source (relative to javadocSourceDirs). */
   private String xmlParserSource = "";

   /** Maps parseXxx method names to property path prefixes for XML attribute extraction. */
   private Map<String, String> xmlParserMethodToPath = new LinkedHashMap<>();

   /** Source file containing updateReloadableConfigurationFrom() (relative to javadocSourceDirs). */
   private String reloadableConfigSource = "";

   /**
    * Directory containing JAAS LoginModule source files (scanned for options.get("key") patterns).
    */
   private String jaasSourceDir = "";

   /** Files containing *_PROP_NAME constants with JavaDoc for factory parameter descriptions. */
   private List<String> factoryParameterFiles = new ArrayList<>();

   /** Source files scanned for DEFAULT_* constants (relative to artemisRoot). */
   private List<String> constantSourceFiles = new ArrayList<>();

   /** Maps XSD complexType names to broker.properties path prefixes for enrichment. */
   private Map<String, String> xsdComplexTypeToPathPattern = new LinkedHashMap<>();

   /**
    * Maps constant classes to the schema paths of Map properties whose known keys they define.
    * Key: fully qualified class name, Value: list of schema paths to the map's .properties field.
    */
   private Map<String, List<String>> mapConstantKeys = new LinkedHashMap<>();

   /**
    * Explicit path rewrites for enrichments where XSD structure doesn't match Java nesting.
    * Key: XSD-derived path prefix, Value: corrected broker.properties path prefix.
    */
   private Map<String, String> enrichmentPathAliases = new LinkedHashMap<>();

   /** Whether JSON output is indented (true) or compact (false). */
   private boolean prettyPrint = true;

   SchemaGeneratorConfig() {}

   public List<String> getIgnoredProperties() {
      return ignoredProperties;
   }

   public void setIgnoredProperties(List<String> ignoredProperties) {
      this.ignoredProperties = ignoredProperties;
   }

   public List<String> getFactoryInterfaces() {
      return factoryInterfaces;
   }

   public void setFactoryInterfaces(List<String> factoryInterfaces) {
      this.factoryInterfaces = factoryInterfaces;
   }

   public List<String> getFactoryScanPackages() {
      return factoryScanPackages;
   }

   public void setFactoryScanPackages(List<String> factoryScanPackages) {
      this.factoryScanPackages = factoryScanPackages;
   }

   public List<String> getJavadocSourceDirs() {
      return javadocSourceDirs;
   }

   public void setJavadocSourceDirs(List<String> javadocSourceDirs) {
      this.javadocSourceDirs = javadocSourceDirs;
   }

   public String getXsdPath() {
      return xsdPath;
   }

   public void setXsdPath(String xsdPath) {
      this.xsdPath = xsdPath;
   }

   public String getXmlParserSource() {
      return xmlParserSource;
   }

   public void setXmlParserSource(String xmlParserSource) {
      this.xmlParserSource = xmlParserSource;
   }

   public Map<String, String> getXmlParserMethodToPath() {
      return xmlParserMethodToPath;
   }

   public void setXmlParserMethodToPath(Map<String, String> xmlParserMethodToPath) {
      this.xmlParserMethodToPath = xmlParserMethodToPath;
   }

   public String getReloadableConfigSource() {
      return reloadableConfigSource;
   }

   public void setReloadableConfigSource(String reloadableConfigSource) {
      this.reloadableConfigSource = reloadableConfigSource;
   }

   public String getJaasSourceDir() {
      return jaasSourceDir;
   }

   public void setJaasSourceDir(String jaasSourceDir) {
      this.jaasSourceDir = jaasSourceDir;
   }

   public List<String> getFactoryParameterFiles() {
      return factoryParameterFiles;
   }

   public void setFactoryParameterFiles(List<String> factoryParameterFiles) {
      this.factoryParameterFiles = factoryParameterFiles;
   }

   public List<String> getConstantSourceFiles() {
      return constantSourceFiles;
   }

   public void setConstantSourceFiles(List<String> constantSourceFiles) {
      this.constantSourceFiles = constantSourceFiles;
   }

   public boolean isPrettyPrint() {
      return prettyPrint;
   }

   public void setPrettyPrint(boolean prettyPrint) {
      this.prettyPrint = prettyPrint;
   }

   public Map<String, String> getXsdComplexTypeToPathPattern() {
      return xsdComplexTypeToPathPattern;
   }

   public void setXsdComplexTypeToPathPattern(Map<String, String> xsdComplexTypeToPathPattern) {
      this.xsdComplexTypeToPathPattern = xsdComplexTypeToPathPattern;
   }

   public Map<String, List<String>> getMapConstantKeys() {
      return mapConstantKeys;
   }

   public void setMapConstantKeys(Map<String, List<String>> mapConstantKeys) {
      this.mapConstantKeys = mapConstantKeys;
   }

   public Map<String, String> getEnrichmentPathAliases() {
      return enrichmentPathAliases;
   }

   public void setEnrichmentPathAliases(Map<String, String> enrichmentPathAliases) {
      this.enrichmentPathAliases = enrichmentPathAliases;
   }

   /**
    * Load the shared configuration instance from classpath. Returns a default empty config if the
    * file is missing or malformed.
    */
   public static synchronized SchemaGeneratorConfig load() {
      if (instance == null) {
         try (InputStream is = SchemaGeneratorConfig.class.getResourceAsStream(CONFIG_PATH)) {
            if (is != null) {
               ObjectMapper mapper = new ObjectMapper();
               mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
               instance = mapper.readValue(is, SchemaGeneratorConfig.class);
            } else {
               LOG.warn("Config file not found: {}", CONFIG_PATH);
               instance = new SchemaGeneratorConfig();
            }
         } catch (Exception e) {
            LOG.warn("Failed to load {}: {}", CONFIG_PATH, e.getMessage());
            instance = new SchemaGeneratorConfig();
         }
      }
      return instance;
   }
}
