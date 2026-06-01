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

package org.apache.artemis.jsonschema.ir;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metadata enrichment for a property at a given Location. Contains key-value pairs like
 * description, default, minimum, x-deprecated, etc.
 *
 * <p>Extends LinkedHashMap to preserve insertion order (matches JSON output order) while giving the
 * concept a proper name in the type system.
 */
public class Enrichment extends LinkedHashMap<String, Object> {

   /** Construct an empty enrichment. */
   public Enrichment() {
      super();
   }

   /**
    * Construct an enrichment pre-populated with the given entries.
    *
    * @param initial initial key-value pairs to copy in
    */
   public Enrichment(Map<String, Object> initial) {
      super(initial);
   }

   /**
    * Merge another enrichment's entries into this one. Existing keys are overwritten by the incoming
    * values.
    *
    * @param other enrichment whose entries take precedence
    * @return new merged enrichment (this instance is not mutated)
    */
   public Enrichment merge(Enrichment other) {
      Enrichment merged = new Enrichment(this);
      merged.putAll(other);
      return merged;
   }

   /**
    * Merge raw map entries (from extractor-produced metadata).
    *
    * @param other raw entries whose values take precedence
    * @return new merged enrichment (this instance is not mutated)
    */
   public Enrichment merge(Map<String, Object> other) {
      Enrichment merged = new Enrichment(this);
      merged.putAll(other);
      return merged;
   }
}
