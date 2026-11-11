/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.utils.collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * These tests are based on <a
 * href="https://github.com/real-logic/agrona/blob/master/agrona/src/test/java/org/agrona/collections/IntHashSetTest.java">Agrona
 * IntHashSetTest</a> to guarantee a similar coverage to what's provided for a similar collection.
 */
public class LongHashSetTest {

   private static final int INITIAL_CAPACITY = 100;

   private final LongHashSet testSet = new LongHashSet(INITIAL_CAPACITY);

   @Test
   public void initiallyContainsNoElements() {
      for (long i = 0; i < 10_000; i++) {
         assertFalse(testSet.contains(i));
      }
   }

   @Test
   public void initiallyContainsNoBoxedElements() {
      for (long i = 0; i < 10_000; i++) {
         assertFalse(testSet.contains(Long.valueOf(i)));
      }
   }

   @Test
   public void containsAddedElement() {
      assertTrue(testSet.add(1L));

      assertTrue(testSet.contains(1L));
   }

   @Test
   public void addingAnElementTwiceDoesNothing() {
      assertTrue(testSet.add(1L));

      assertFalse(testSet.add(1L));
   }

   @Test
   public void containsAddedBoxedElements() {
      assertTrue(testSet.add(1L));
      assertTrue(testSet.add(Long.valueOf(2L)));

      assertTrue(testSet.contains(Long.valueOf(1L)));
      assertTrue(testSet.contains(2L));
   }

   @Test
   public void removingAnElementFromAnEmptyListDoesNothing() {
      assertFalse(testSet.remove(0L));
   }

   @Test
   public void removingAPresentElementRemovesIt() {
      assertTrue(testSet.add(1L));

      assertTrue(testSet.remove(1L));

      assertFalse(testSet.contains(1L));
   }

   @Test
   public void sizeIsInitiallyZero() {
      assertEquals(0, testSet.size());
   }

   @Test
   public void sizeIncrementsWithNumberOfAddedElements() {
      addTwoElements(testSet);

      assertEquals(2, testSet.size());
   }

   @Test
   public void sizeContainsNumberOfNewElements() {
      testSet.add(1L);
      testSet.add(1L);

      assertEquals(1, testSet.size());
   }

   @Test
   public void iteratorsListElements() {
      addTwoElements(testSet);

      assertIteratorHasElements();
   }

   @Test
   public void iteratorsStartFromTheBeginningEveryTime() {
      iteratorsListElements();

      assertIteratorHasElements();
   }

   @Test
   public void iteratorsListElementsWithoutHasNext() {
      addTwoElements(testSet);

      assertIteratorHasElementsWithoutHasNext();
   }

   @Test
   public void iteratorsStartFromTheBeginningEveryTimeWithoutHasNext() {
      iteratorsListElementsWithoutHasNext();

      assertIteratorHasElementsWithoutHasNext();
   }

   @Test
   public void iteratorsThrowNoSuchElementException() {
      assertThrows(NoSuchElementException.class, () -> {
         addTwoElements(testSet);

         exhaustIterator();
      });
   }

   @Test
   public void iteratorsThrowNoSuchElementExceptionFromTheBeginningEveryTime() {
      assertThrows(NoSuchElementException.class, () -> {
         addTwoElements(testSet);

         try {
            exhaustIterator();
         } catch (final NoSuchElementException ignore) {
         }

         exhaustIterator();
      });
   }

   @Test
   public void iteratorHasNoElements() {
      assertFalse(testSet.iterator().hasNext());
   }

   @Test
   public void iteratorThrowExceptionForEmptySet() {
      assertThrows(NoSuchElementException.class, () -> {
         testSet.iterator().next();
      });
   }

   @Test
   public void clearRemovesAllElementsOfTheSet() {
      addTwoElements(testSet);

      testSet.clear();

      assertEquals(0, testSet.size());
      assertFalse(testSet.contains(1L));
      assertFalse(testSet.contains(1001L));
   }

   @Test
   public void twoEmptySetsAreEqual() {
      final LongHashSet other = new LongHashSet(100);
      assertEquals(testSet, other);
   }

   @Test
   public void setsWithTheSameValuesAreEqual() {
      final LongHashSet other = new LongHashSet(100);

      addTwoElements(testSet);
      addTwoElements(other);

      assertEquals(testSet, other);
   }

   @Test
   public void setsWithTheDifferentSizesAreNotEqual() {
      final LongHashSet other = new LongHashSet(100);

      addTwoElements(testSet);

      other.add(1001L);

      assertNotEquals(testSet, other);
   }

   @Test
   public void setsWithTheDifferentValuesAreNotEqual() {
      final LongHashSet other = new LongHashSet(100);

      addTwoElements(testSet);

      other.add(2L);
      other.add(1001L);

      assertNotEquals(testSet, other);
   }

   @Test
   public void twoEmptySetsHaveTheSameHashcode() {
      assertEquals(testSet.hashCode(), new LongHashSet(100).hashCode());
   }

   @Test
   public void setsWithTheSameValuesHaveTheSameHashcode() {
      final LongHashSet other = new LongHashSet(100);

      addTwoElements(testSet);

      addTwoElements(other);

      assertEquals(testSet.hashCode(), other.hashCode());
   }

   @Test
   public void reducesSizeWhenElementRemoved() {
      addTwoElements(testSet);

      testSet.remove(1001L);

      assertEquals(1, testSet.size());
   }

   @SuppressWarnings("CollectionToArraySafeParameter")
   @Test
   public void toArrayThrowsArrayStoreExceptionForWrongType() {
      assertThrows(ArrayStoreException.class, () -> {
         testSet.toArray(new String[1]);
      });
   }

   @Test
   public void toArrayThrowsNullPointerExceptionForNullArgument() {
      assertThrows(NullPointerException.class, () -> {
         final Long[] into = null;
         testSet.toArray(into);
      });
   }

   @Test
   public void toArrayCopiesElementsIntoSufficientlySizedArray() {
      addTwoElements(testSet);

      final Long[] result = testSet.toArray(new Long[testSet.size()]);

      assertArrayContainingElements(result);
   }

   @Test
   public void toArrayCopiesElementsIntoNewArray() {
      addTwoElements(testSet);

      final Long[] result = testSet.toArray(new Long[testSet.size()]);

      assertArrayContainingElements(result);
   }

   @Test
   public void toArraySupportsEmptyCollection() {
      final Long[] result = testSet.toArray(new Long[testSet.size()]);

      assertArrayEquals(new Long[]{}, result);
   }

   // Test case from usage bug.
   @Test
   public void chainCompactionShouldNotCauseElementsToBeMovedBeforeTheirHash() {
      final LongHashSet requiredFields = new LongHashSet(14);

      requiredFields.add(8L);
      requiredFields.add(9L);
      requiredFields.add(35L);
      requiredFields.add(49L);
      requiredFields.add(56L);

      assertTrue(requiredFields.remove(8L), "Failed to remove 8");
      assertTrue(requiredFields.remove(9L), "Failed to remove 9");
      assertTrue(requiredFields.contains(35L), "requiredFields does not contain " + 35);
      assertTrue(requiredFields.contains(49L), "requiredFields does not contain " + 49);
      assertTrue(requiredFields.contains(56L), "requiredFields does not contain " + 56);
   }

   @Test
   public void shouldResizeWhenItHitsCapacity() {
      for (long i = 0; i < 2 * INITIAL_CAPACITY; i++) {
         assertTrue(testSet.add(i));
      }

      for (long i = 0; i < 2 * INITIAL_CAPACITY; i++) {
         assertTrue(testSet.contains(i));
      }
   }

   @Test
   public void containsEmptySet() {
      final LongHashSet other = new LongHashSet(100);

      assertTrue(testSet.containsAll(other));
      assertTrue(testSet.containsAll((Collection<?>) other));
   }

   @Test
   public void containsSubset() {
      addTwoElements(testSet);

      final LongHashSet subset = new LongHashSet(100);

      subset.add(1L);

      assertTrue(testSet.containsAll(subset));
      assertTrue(testSet.containsAll((Collection<?>) subset));
   }

   @Test
   public void doesNotContainDisjointSet() {
      addTwoElements(testSet);

      final LongHashSet other = new LongHashSet(100);

      other.add(1L);
      other.add(1002L);

      assertFalse(testSet.containsAll(other));
      assertFalse(testSet.containsAll((Collection<?>) other));
   }

   @Test
   public void doesNotContainSuperset() {
      addTwoElements(testSet);

      final LongHashSet superset = new LongHashSet(100);

      addTwoElements(superset);
      superset.add(15L);

      assertFalse(testSet.containsAll(superset));
      assertFalse(testSet.containsAll((Collection<?>) superset));
   }

   @Test
   public void addingEmptySetDoesNothing() {
      addTwoElements(testSet);

      assertFalse(testSet.addAll(new LongHashSet(100)));
      assertFalse(testSet.addAll(new HashSet<>()));
      assertContainsElements(testSet);
   }

   @Test
   public void addingSubsetDoesNothing() {
      addTwoElements(testSet);

      final LongHashSet subset = new LongHashSet(100);

      subset.add(1L);

      final HashSet<Long> subSetCollection = new HashSet<>(subset);

      assertFalse(testSet.addAll(subset));
      assertFalse(testSet.addAll(subSetCollection));
      assertContainsElements(testSet);
   }

   @Test
   public void addingEqualSetDoesNothing() {
      addTwoElements(testSet);

      final LongHashSet equal = new LongHashSet(100);

      addTwoElements(equal);

      final HashSet<Long> equalCollection = new HashSet<>(equal);

      assertFalse(testSet.addAll(equal));
      assertFalse(testSet.addAll(equalCollection));
      assertContainsElements(testSet);
   }

   @Test
   public void containsValuesAddedFromDisjointSetPrimitive() {
      addTwoElements(testSet);

      final LongHashSet disjoint = new LongHashSet(100);

      disjoint.add(2L);
      disjoint.add(1002L);

      assertTrue(testSet.addAll(disjoint));
      assertTrue(testSet.contains(1L));
      assertTrue(testSet.contains(1001L));
      assertTrue(testSet.containsAll(disjoint));
   }

   @Test
   public void containsValuesAddedFromDisjointSet() {
      addTwoElements(testSet);

      final HashSet<Long> disjoint = new HashSet<>();

      disjoint.add(2L);
      disjoint.add(1002L);

      assertTrue(testSet.addAll(disjoint));
      assertTrue(testSet.contains(1L));
      assertTrue(testSet.contains(1001L));
      assertTrue(testSet.containsAll(disjoint));
   }

   @Test
   public void containsValuesAddedFromIntersectingSetPrimitive() {
      addTwoElements(testSet);

      final LongHashSet intersecting = new LongHashSet(100);

      intersecting.add(1L);
      intersecting.add(1002L);

      assertTrue(testSet.addAll(intersecting));
      assertTrue(testSet.contains(1L));
      assertTrue(testSet.contains(1001L));
      assertTrue(testSet.containsAll(intersecting));
   }

   @Test
   public void containsValuesAddedFromIntersectingSet() {
      addTwoElements(testSet);

      final HashSet<Long> intersecting = new HashSet<>();

      intersecting.add(1L);
      intersecting.add(1002L);

      assertTrue(testSet.addAll(intersecting));
      assertTrue(testSet.contains(1L));
      assertTrue(testSet.contains(1001L));
      assertTrue(testSet.containsAll(intersecting));
   }

   @Test
   public void removingEmptySetDoesNothing() {
      addTwoElements(testSet);

      assertFalse(testSet.removeAll(new LongHashSet(100)));
      assertFalse(testSet.removeAll(new HashSet<Long>()));
      assertContainsElements(testSet);
   }

   @Test
   public void removingDisjointSetDoesNothing() {
      addTwoElements(testSet);

      final LongHashSet disjoint = new LongHashSet(100);

      disjoint.add(2L);
      disjoint.add(1002L);

      assertFalse(testSet.removeAll(disjoint));
      assertFalse(testSet.removeAll(new HashSet<Long>()));
      assertContainsElements(testSet);
   }

   @Test
   public void doesNotContainRemovedIntersectingSetPrimitive() {
      addTwoElements(testSet);

      final LongHashSet intersecting = new LongHashSet(100);

      intersecting.add(1L);
      intersecting.add(1002L);

      assertTrue(testSet.removeAll(intersecting));
      assertTrue(testSet.contains(1001L));
      assertFalse(testSet.containsAll(intersecting));
   }

   @Test
   public void doesNotContainRemovedIntersectingSet() {
      addTwoElements(testSet);

      final HashSet<Long> intersecting = new HashSet<>();

      intersecting.add(1L);
      intersecting.add(1002L);

      assertTrue(testSet.removeAll(intersecting));
      assertTrue(testSet.contains(1001L));
      assertFalse(testSet.containsAll(intersecting));
   }

   @Test
   public void isEmptyAfterRemovingEqualSetPrimitive() {
      addTwoElements(testSet);

      final LongHashSet equal = new LongHashSet(100);

      addTwoElements(equal);

      assertTrue(testSet.removeAll(equal));
      assertTrue(testSet.isEmpty());
   }

   @Test
   public void isEmptyAfterRemovingEqualSet() {
      addTwoElements(testSet);

      final HashSet<Long> equal = new HashSet<>();

      addTwoElements(equal);

      assertTrue(testSet.removeAll(equal));
      assertTrue(testSet.isEmpty());
   }

   @Test
   public void removeElementsFromIterator() {
      addTwoElements(testSet);

      final LongHashSet.LongIterator iterator = testSet.iterator();
      while (iterator.hasNext()) {
         if (iterator.nextValue() == 1L) {
            iterator.remove();
         }
      }

      assertTrue(testSet.contains(1001L), "testSet does not contain 1001");
      assertEquals(1, testSet.size());
   }

   @Test
   public void shouldNotContainMissingValueInitially() {
      assertFalse(testSet.contains(LongHashSet.MISSING_VALUE));
   }

   @Test
   public void shouldAllowMissingValue() {
      assertTrue(testSet.add(LongHashSet.MISSING_VALUE));

      assertTrue(testSet.contains(LongHashSet.MISSING_VALUE));

      assertFalse(testSet.add(LongHashSet.MISSING_VALUE));
   }

   @Test
   public void shouldAllowRemovalOfMissingValue() {
      assertTrue(testSet.add(LongHashSet.MISSING_VALUE));

      assertTrue(testSet.remove(LongHashSet.MISSING_VALUE));

      assertFalse(testSet.contains(LongHashSet.MISSING_VALUE));

      assertFalse(testSet.remove(LongHashSet.MISSING_VALUE));
   }

   @Test
   public void sizeAccountsForMissingValue() {
      testSet.add(1L);
      testSet.add(LongHashSet.MISSING_VALUE);

      assertEquals(2, testSet.size());
   }

   @Test
   public void toArrayCopiesElementsIntoNewArrayIncludingMissingValue() {
      addTwoElements(testSet);

      testSet.add(LongHashSet.MISSING_VALUE);

      final Long[] result = testSet.toArray(new Long[testSet.size()]);

      assertTrue(Arrays.asList(result).contains(1L));
      assertTrue(Arrays.asList(result).contains(1001L));
      assertTrue(Arrays.asList(result).contains(LongHashSet.MISSING_VALUE));
   }

   @Test
   public void toObjectArrayCopiesElementsIntoNewArrayIncludingMissingValue() {
      addTwoElements(testSet);

      testSet.add(LongHashSet.MISSING_VALUE);

      final Object[] result = testSet.toArray();

      assertTrue(Arrays.asList(result).contains(1L));
      assertTrue(Arrays.asList(result).contains(1001L));
      assertTrue(Arrays.asList(result).contains(LongHashSet.MISSING_VALUE));
   }

   @Test
   public void equalsAccountsForMissingValue() {
      addTwoElements(testSet);
      testSet.add(LongHashSet.MISSING_VALUE);

      final LongHashSet other = new LongHashSet(100);
      addTwoElements(other);

      assertNotEquals(testSet, other);

      other.add(LongHashSet.MISSING_VALUE);
      assertEquals(testSet, other);

      testSet.remove(LongHashSet.MISSING_VALUE);

      assertNotEquals(testSet, other);
   }

   @Test
   public void consecutiveValuesShouldBeCorrectlyStored() {
      for (long i = 0; i < 10_000; i++) {
         testSet.add(i);
      }

      assertEquals(10_000, testSet.size());

      int distinctElements = 0;
      for (final long ignore : testSet) {
         distinctElements++;
      }

      assertEquals(10_000, distinctElements);
   }

   @Test
   public void hashCodeAccountsForMissingValue() {
      addTwoElements(testSet);
      testSet.add(LongHashSet.MISSING_VALUE);

      final LongHashSet other = new LongHashSet(100);
      addTwoElements(other);

      assertNotEquals(testSet.hashCode(), other.hashCode());

      other.add(LongHashSet.MISSING_VALUE);
      assertEquals(testSet.hashCode(), other.hashCode());

      testSet.remove(LongHashSet.MISSING_VALUE);

      assertNotEquals(testSet.hashCode(), other.hashCode());
   }

   @Test
   public void iteratorAccountsForMissingValue() {
      addTwoElements(testSet);
      testSet.add(LongHashSet.MISSING_VALUE);

      int missingValueCount = 0;
      final LongHashSet.LongIterator iterator = testSet.iterator();
      while (iterator.hasNext()) {
         if (iterator.nextValue() == LongHashSet.MISSING_VALUE) {
            missingValueCount++;
         }
      }

      assertEquals(1, missingValueCount);
   }

   @Test
   public void iteratorCanRemoveMissingValue() {
      addTwoElements(testSet);
      testSet.add(LongHashSet.MISSING_VALUE);

      final LongHashSet.LongIterator iterator = testSet.iterator();
      while (iterator.hasNext()) {
         if (iterator.nextValue() == LongHashSet.MISSING_VALUE) {
            iterator.remove();
         }
      }

      assertFalse(testSet.contains(LongHashSet.MISSING_VALUE));
   }

   @Test
   public void shouldGenerateStringRepresentation() {
      final long[] testEntries = {3L, 1L, -2L, 19L, 7L, 11L, 12L, 7L};

      for (final long testEntry : testEntries) {
         testSet.add(testEntry);
      }

      final String mapAsAString = "{1, 19, 11, 7, 3, 12, -2}";
      assertEquals(mapAsAString, testSet.toString());
   }

   @Test
   public void shouldRemoveMissingValueWhenCleared() {
      assertTrue(testSet.add(LongHashSet.MISSING_VALUE));

      testSet.clear();

      assertFalse(testSet.contains(LongHashSet.MISSING_VALUE));
   }

   @Test
   public void shouldHaveCompatibleEqualsAndHashcode() {
      final HashSet<Long> compatibleSet = new HashSet<>();
      final long seed = System.nanoTime();
      final Random r = new Random(seed);
      for (long i = 0; i < 1024; i++) {
         final long value = r.nextLong();
         compatibleSet.add(value);
         testSet.add(value);
      }

      if (r.nextBoolean()) {
         compatibleSet.add(LongHashSet.MISSING_VALUE);
         testSet.add(LongHashSet.MISSING_VALUE);
      }

      assertEquals(testSet, compatibleSet, "Fail with seed:" + seed);
      assertEquals(compatibleSet, testSet, "Fail with seed:" + seed);
      assertEquals(compatibleSet.hashCode(), testSet.hashCode(), "Fail with seed:" + seed);
   }

   private static void addTwoElements(final LongHashSet obj) {
      obj.add(1L);
      obj.add(1001L);
   }

   private static void addTwoElements(final HashSet<Long> obj) {
      obj.add(1L);
      obj.add(1001L);
   }

   private void assertIteratorHasElements() {
      final Iterator<Long> iter = testSet.iterator();

      final Set<Long> values = new HashSet<>();

      assertTrue(iter.hasNext());
      values.add(iter.next());
      assertTrue(iter.hasNext());
      values.add(iter.next());
      assertFalse(iter.hasNext());

      assertContainsElements(values);
   }

   private void assertIteratorHasElementsWithoutHasNext() {
      final Iterator<Long> iter = testSet.iterator();

      final Set<Long> values = new HashSet<>();

      values.add(iter.next());
      values.add(iter.next());

      assertContainsElements(values);
   }

   private static void assertArrayContainingElements(final Long[] result) {
      assertTrue(Arrays.asList(result).contains(1L));
      assertTrue(Arrays.asList(result).contains(1001L));
   }

   private static void assertContainsElements(final Set<Long> other) {
      assertTrue(other.contains(1L));
      assertTrue(other.contains(1001L));
   }

   private void exhaustIterator() {
      final Iterator iterator = testSet.iterator();
      iterator.next();
      iterator.next();
      iterator.next();
   }
}
