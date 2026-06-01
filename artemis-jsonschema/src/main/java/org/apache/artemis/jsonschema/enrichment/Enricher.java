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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.artemis.jsonschema.config.SchemaGeneratorConfig;
import org.apache.artemis.jsonschema.ir.Location;
import org.apache.artemis.jsonschema.ir.PropertyDescriptor;
import org.apache.artemis.jsonschema.ir.PropertyMetadata;
import org.apache.artemis.jsonschema.ir.SchemaIR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs all extractors and applies their enrichments to the IR. */
public class Enricher {

   private static final Logger LOG = LoggerFactory.getLogger(Enricher.class);

   private final List<Extractor> extractors;
   private final List<PropertyDescriptor> enrichments = new ArrayList<>();
   private final Map<String, String> pathAliases;

   public Enricher(List<Extractor> extractors) {
      this.extractors = extractors;
      this.pathAliases = SchemaGeneratorConfig.load().getEnrichmentPathAliases();
   }

   /**
    * Run all extractors against the Artemis source tree. Collects property descriptors from each;
    * optional extractors that fail are skipped.
    */
   public void extract(Path artemisRoot) throws ExtractionException {
      for (Extractor extractor : extractors) {
         try {
            List<PropertyDescriptor> descriptors = extractor.extract(artemisRoot);
            enrichments.addAll(descriptors);
            LOG.debug("{}: {} descriptors", extractor.getName(), descriptors.size());
         } catch (ExtractionException e) {
            if (extractor.isRequired()) {
               throw e;
            }
            LOG.warn("{} failed: {}", extractor.getName(), e.getMessage());
         }
      }
      LOG.info(
            "Total enrichments: {} property descriptors from {} extractors",
            enrichments.size(),
            extractors.size());
   }

   /** Apply collected enrichments to the IR. */
   public void enrich(SchemaIR ir) {
      for (PropertyDescriptor descriptor : enrichments) {
         Map<String, Object> metadata = new LinkedHashMap<>();
         PropertyMetadata propMeta = descriptor.getMetadata();

         if (propMeta.getType() != null) {
            metadata.put("type", propMeta.getType().toSchemaValue());
         }
         if (propMeta.getDescription() != null) metadata.put("description", propMeta.getDescription());
         if (propMeta.getDefaultValue() != null) metadata.put("default", propMeta.getDefaultValue());
         if (propMeta.getEnumValues() != null) metadata.put("enum", propMeta.getEnumValues());
         if (propMeta.getMinimum() != null) metadata.put("minimum", propMeta.getMinimum());
         if (propMeta.getMaximum() != null) metadata.put("maximum", propMeta.getMaximum());
         if (propMeta.getAccess() != null) metadata.put("x-access", propMeta.getAccess());
         if (propMeta.getDeprecated() != null && propMeta.getDeprecated())
            metadata.put("x-deprecated", true);
         if (propMeta.getFactorySpecific() != null)
            metadata.put("x-factory-specific", propMeta.getFactorySpecific());
         if (propMeta.getHotReloadable() != null && propMeta.getHotReloadable())
            metadata.put("x-hot-reloadable", true);
         if (propMeta.getPattern() != null) metadata.put("pattern", propMeta.getPattern());
         if (propMeta.getConverter() != null) metadata.put("x-converter", propMeta.getConverter());

         if (!metadata.isEmpty()) {
            Location path = descriptor.getPath();
            String dotted = path.toDotted();
            String aliased = pathAliases.get(dotted);
            if (aliased != null) {
               path = Location.of(aliased);
            }
            ir.enrich(path, metadata);
         }
      }
   }
}
