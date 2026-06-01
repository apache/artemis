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

/**
 * Exception thrown when an extractor fails to extract property metadata.
 *
 * <p>This exception provides context about which extractor failed and why, enabling the pipeline to
 * make intelligent decisions about whether to fail the entire schema generation or continue with
 * partial data.
 */
public class ExtractionException extends Exception {

   private final String extractorName;

   /**
    * Create an extraction exception with extractor context.
    *
    * @param extractorName Name of the extractor that failed
    * @param message Human-readable description of the failure
    */
   public ExtractionException(String extractorName, String message) {
      super(extractorName + ": " + message);
      this.extractorName = extractorName;
   }

   /**
    * Create an extraction exception with extractor context and root cause.
    *
    * @param extractorName Name of the extractor that failed
    * @param message Human-readable description of the failure
    * @param cause Underlying exception that caused the failure
    */
   public ExtractionException(String extractorName, String message, Throwable cause) {
      super(extractorName + ": " + message, cause);
      this.extractorName = extractorName;
   }

   /**
    * @return Name of the extractor that threw this exception
    */
   public String getExtractorName() {
      return extractorName;
   }
}
