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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Tests for extractor error handling and ExtractionException. */
public class ErrorHandlingTest {

   @Test
   public void testXsdExtractorThrowsExtractionExceptionWhenXsdMissing() {
      XsdExtractor extractor = new XsdExtractor();
      Path nonExistentPath = Paths.get("/tmp/nonexistent-artemis-root");

      ExtractionException exception =
            assertThrows(
                  ExtractionException.class,
                  () -> {
                     extractor.extract(nonExistentPath);
                  });

      String message = exception.getMessage();
      assertTrue(
            message.contains("XsdExtractor"), "Message should contain 'XsdExtractor', got: " + message);
      assertTrue(
            message.contains("artemis-configuration.xsd"),
            "Message should mention xsd file, got: " + message);
      assertEquals("XsdExtractor", exception.getExtractorName());
   }

   @Test
   public void testTypeConverterExtractorThrowsOnMissingSource() {
      TypeConverterExtractor extractor = new TypeConverterExtractor();
      Path nonExistentPath = Paths.get("/tmp/nonexistent-artemis-root");

      assertThrows(ExtractionException.class, () -> extractor.extract(nonExistentPath));
   }

   @Test
   public void testDefaultExtractorsAreOptional() {
      // Most extractors should default to optional (isRequired = false)
      assertFalse(new SetterGetterJavadocExtractor().isRequired());
      assertFalse(new XsdExtractor().isRequired());
      assertFalse(new MetadataExtractor().isRequired());
   }

   @Test
   public void testExtractionExceptionIncludesExtractorName() {
      ExtractionException exception = new ExtractionException("TestExtractor", "test failure");

      assertEquals("TestExtractor", exception.getExtractorName());
      assertTrue(exception.getMessage().contains("TestExtractor"));
      assertTrue(exception.getMessage().contains("test failure"));
   }

   @Test
   public void testExtractionExceptionIncludesCause() {
      RuntimeException cause = new RuntimeException("underlying error");
      ExtractionException exception = new ExtractionException("TestExtractor", "test failure", cause);

      assertEquals("TestExtractor", exception.getExtractorName());
      assertEquals(cause, exception.getCause());
      assertTrue(exception.getMessage().contains("TestExtractor"));
      assertTrue(exception.getMessage().contains("test failure"));
   }
}
