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
package org.apache.activemq.artemis.core.server.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.apache.activemq.artemis.api.core.SimpleString;

/**
 * BucketMessageGroups, stores values against a bucket, where the bucket used is based on the provided key objects
 * hash.
 * <p>
 * As such where keys compute to the same bucket they will act on that stored value, not the unique specific key.
 * <p>
 * The number of buckets is provided at construction.
 */
public class BucketMessageGroups<C> implements MessageGroups<C> {

   /*
    * This _AMQ_GROUP_BUCKET_INT_KEY uses the post-fixed value after this key, as an int, it is used for a few cases:
    * 1) For the admin screen we need to show a group key so we have to map back from int to something, as it expects SimpleString.
    * 2) Admin users still need to interact with a specific bucket/group e.g. they may need to reset a bucket.
    * 3) Choice of key is we want to avoid risk of clashing with users groups keys.
    * 4) Actually makes testing a little easier as we know how the parsed int will hash.
    */
   private static SimpleString _AMQ_GROUP_BUCKET_INT_KEY = SimpleString.of("_AMQ_GROUP_BUCKET_INT_KEY_");

   private final int bucketCount;
   private C[] buckets;
   private int size = 0;

   public BucketMessageGroups(int bucketCount) {
      if (bucketCount < 1) {
         throw new IllegalArgumentException("Bucket count must be greater than 0");
      }
      this.bucketCount = bucketCount;
   }

   private int getBucket(SimpleString key) {
      Object bucketKey = key;
      if (key.startsWith(_AMQ_GROUP_BUCKET_INT_KEY)) {
         bucketKey = retrieveBucketIntFromKey(key);
      }
      return getHashBucket(bucketKey, bucketCount);
   }

   private static int getHashBucket(final Object key, final int bucketCount) {
      return (key.hashCode() & Integer.MAX_VALUE) % bucketCount;
   }

   private static Object retrieveBucketIntFromKey(SimpleString key) {
      SimpleString bucket = key.subSeq(_AMQ_GROUP_BUCKET_INT_KEY.length(), key.length());
      try {
         return Integer.parseInt(bucket.toString());
      } catch (NumberFormatException nfe) {
         return key;
      }
   }

   @Override
   public void put(SimpleString key, C consumer) {
      if (buckets == null) {
         buckets = newBucketArray(bucketCount);
      }
      if (buckets[getBucket(key)] == null) {
         size++;
      }
      buckets[getBucket(key)] = consumer;
   }

   @SuppressWarnings({ "unchecked", "SuspiciousArrayCast" })
   private static <C> C[] newBucketArray(int capacity) {
      return (C[]) new Object[capacity];
   }

   @Override
   public C get(SimpleString key) {
      if (buckets == null) {
         return null;
      }
      return buckets[getBucket(key)];
   }

   @Override
   public C remove(SimpleString key) {
      if (buckets == null) {
         return null;
      }
      return remove(getBucket(key));
   }

   private C remove(int bucket) {
      C existing = buckets[bucket];
      if (existing != null) {
         size--;
         buckets[bucket] = null;
      }
      return existing;
   }

   @Override
   public boolean removeIf(Predicate<? super C> filter) {
      if (buckets != null && size > 0) {
         boolean removed = false;
         for (int bucket = 0; bucket < buckets.length; bucket++) {
            if (filter.test(buckets[bucket])) {
               remove(bucket);
               removed = true;
            }
         }
         return removed;
      } else {
         return false;
      }
   }

   @Override
   public void removeAll() {
      if (buckets != null && size > 0) {
         Arrays.fill(buckets, null);
      }
      size = 0;
   }

   @Override
   public int size() {
      return size;
   }

   @Override
   public Map<SimpleString, C> toMap() {
      if (buckets != null && size > 0) {
         Map<SimpleString, C> map = new HashMap<>(size);
         for (int bucket = 0; bucket < buckets.length; bucket++) {
            C value = buckets[bucket];
            if (value != null) {
               map.put(toGroupBucketIntKey(bucket), value);
            }
         }
         return map;
      } else {
         return Collections.emptyMap();
      }
   }

   static SimpleString toGroupBucketIntKey(int i) {
      return _AMQ_GROUP_BUCKET_INT_KEY.concat(Integer.toString(i));
   }

}
