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
import java.util.List;
import org.apache.artemis.jsonschema.ir.PropertyDescriptor;

/**
 * Common interface for all property extractors.
 *
 * <p>Each extractor analyzes a different source (reflection, XSD, source code constants, etc.) to
 * discover broker configuration properties and their metadata. Extractors produce
 * PropertyDescriptor objects that are later merged into the final JSON schema.
 *
 * <p>This architecture allows for modular, type-safe extraction with in-memory processing.
 */
public interface Extractor {
   /**
    * Extract properties from Artemis source/artifacts.
    *
    * @param artemisRoot Path to Artemis source repository root
    * @return List of property descriptors extracted by this source (never null, may be empty)
    * @throws ExtractionException if extraction fails critically
    */
   List<PropertyDescriptor> extract(Path artemisRoot) throws ExtractionException;

   /**
    * Get extractor name for logging.
    *
    * @return Extractor name (e.g., "ReflectionExtractor", "XsdExtractor")
    */
   String getName();

   /**
    * Indicates whether this extractor is required for schema generation.
    *
    * <p>Required extractors cause the pipeline to fail if extraction fails. Optional extractors log
    * warnings but allow the pipeline to continue with partial data.
    *
    * @return true if this extractor failure should fail the pipeline, false to log and continue
    */
   default boolean isRequired() {
      return false; // Most extractors are optional enrichments
   }
}
