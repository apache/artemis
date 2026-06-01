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

/**
 * Tracks which extractor(s) provided information for a property. Multiple sources may contribute to
 * a single property descriptor.
 */
public enum PropertySource {
   /** Java reflection via ConfigurationImpl introspection */
   REFLECTION,

   /** XSD schema (artemis-configuration.xsd) */
   XSD,

   /** XML parser analysis (FileConfigurationParser.java getAttribute calls) */
   XML_PARSER,

   /** Plugin/transport param enrichment discovery */
   ENRICHMENT,

   /** Runtime metadata (hot-reload whitelist, validators, etc.) */
   METADATA,

   /** JavaDoc documentation from Configuration.java interface */
   JAVADOC,

   /** Real-world examples from test XML configuration files */
   XML_EXAMPLES,

   /** Factory implementation discovery (valid factory classes and factory-specific params) */
   FACTORY_DISCOVERY
}
