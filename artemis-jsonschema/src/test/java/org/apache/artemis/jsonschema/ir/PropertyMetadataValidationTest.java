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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for PropertyMetadata validation and defensive copying. */
public class PropertyMetadataValidationTest {

   @Test
   public void testSetDescriptionRejectsEmptyString() {
      PropertyMetadata metadata = new PropertyMetadata();

      // Empty string should throw
      IllegalArgumentException exception =
            assertThrows(
                  IllegalArgumentException.class,
                  () -> {
                     metadata.setDescription("");
                  });
      assertEquals("Description cannot be empty string", exception.getMessage());

      // Whitespace-only should throw
      exception =
            assertThrows(
                  IllegalArgumentException.class,
                  () -> {
                     metadata.setDescription("   ");
                  });
      assertEquals("Description cannot be empty string", exception.getMessage());

      // Null should be accepted (means "no description")
      assertDoesNotThrow(() -> metadata.setDescription(null));

      // Valid description should be accepted
      assertDoesNotThrow(() -> metadata.setDescription("Valid description"));
      assertEquals("Valid description", metadata.getDescription());
   }

   @Test
   public void testSetMinimumRequiresNumber() {
      PropertyMetadata metadata = new PropertyMetadata();

      // Number types should be accepted
      assertDoesNotThrow(() -> metadata.setMinimum(42));
      assertDoesNotThrow(() -> metadata.setMinimum(42L));
      assertDoesNotThrow(() -> metadata.setMinimum(42.5));
      assertDoesNotThrow(() -> metadata.setMinimum(42.5f));

      // String should throw
      IllegalArgumentException exception =
            assertThrows(
                  IllegalArgumentException.class,
                  () -> {
                     metadata.setMinimum("42");
                  });
      assertTrue(exception.getMessage().contains("Minimum must be Number"));
      assertTrue(exception.getMessage().contains("String"));

      // Null should be accepted (means "no minimum")
      assertDoesNotThrow(() -> metadata.setMinimum(null));
   }

   @Test
   public void testSetMaximumRequiresNumber() {
      PropertyMetadata metadata = new PropertyMetadata();

      // Number types should be accepted
      assertDoesNotThrow(() -> metadata.setMaximum(100));
      assertDoesNotThrow(() -> metadata.setMaximum(100L));
      assertDoesNotThrow(() -> metadata.setMaximum(100.5));

      // String should throw
      IllegalArgumentException exception =
            assertThrows(
                  IllegalArgumentException.class,
                  () -> {
                     metadata.setMaximum("100");
                  });
      assertTrue(exception.getMessage().contains("Maximum must be Number"));

      // Null should be accepted
      assertDoesNotThrow(() -> metadata.setMaximum(null));
   }

   @Test
   public void testEnumValuesDefensiveCopy() {
      PropertyMetadata metadata = new PropertyMetadata();

      // Create mutable list
      List<String> original = new ArrayList<>(Arrays.asList("value1", "value2", "value3"));
      metadata.setEnumValues(original);

      // Modify original - should NOT affect metadata
      original.add("value4");
      original.set(0, "modified");

      List<String> retrieved = metadata.getEnumValues();
      assertEquals(3, retrieved.size());
      assertEquals("value1", retrieved.get(0));
      assertEquals("value2", retrieved.get(1));
      assertEquals("value3", retrieved.get(2));

      // Returned list should be unmodifiable
      assertThrows(
            UnsupportedOperationException.class,
            () -> {
               retrieved.add("should fail");
            });
   }

   @Test
   public void testExampleValuesDefensiveCopy() {
      PropertyMetadata metadata = new PropertyMetadata();

      List<Object> original = new ArrayList<>(Arrays.asList("ex1", 42, true));
      metadata.setExampleValues(original);

      original.add("ex2");

      List<Object> retrieved = metadata.getExampleValues();
      assertEquals(3, retrieved.size());

      // Should be unmodifiable
      assertThrows(
            UnsupportedOperationException.class,
            () -> {
               retrieved.add("should fail");
            });
   }

   @Test
   public void testExampleSourcesDefensiveCopy() {
      PropertyMetadata metadata = new PropertyMetadata();

      List<String> original = new ArrayList<>(Arrays.asList("source1", "source2"));
      metadata.setExampleSources(original);

      original.clear();

      List<String> retrieved = metadata.getExampleSources();
      assertEquals(2, retrieved.size());

      assertThrows(
            UnsupportedOperationException.class,
            () -> {
               retrieved.clear();
            });
   }

   @Test
   public void testFactorySpecificDefensiveCopy() {
      PropertyMetadata metadata = new PropertyMetadata();

      List<String> original = new ArrayList<>(Arrays.asList("NettyAcceptorFactory"));
      metadata.setFactorySpecific(original);

      original.add("InVMAcceptorFactory");

      List<String> retrieved = metadata.getFactorySpecific();
      assertEquals(1, retrieved.size());
      assertEquals("NettyAcceptorFactory", retrieved.get(0));

      assertThrows(
            UnsupportedOperationException.class,
            () -> {
               retrieved.add("should fail");
            });
   }

   @Test
   public void testNullListsReturnNull() {
      PropertyMetadata metadata = new PropertyMetadata();

      // Setting null should preserve null (not convert to empty list)
      metadata.setEnumValues(null);
      assertNull(metadata.getEnumValues());

      metadata.setExampleValues(null);
      assertNull(metadata.getExampleValues());

      metadata.setExampleSources(null);
      assertNull(metadata.getExampleSources());

      metadata.setFactorySpecific(null);
      assertNull(metadata.getFactorySpecific());
   }
}
