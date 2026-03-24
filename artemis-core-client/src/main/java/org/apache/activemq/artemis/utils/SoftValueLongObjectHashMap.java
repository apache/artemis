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
package org.apache.activemq.artemis.utils;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import org.jboss.logging.Logger;

public class SoftValueLongObjectHashMap<V extends SoftValueLongObjectHashMap.ValueCache> implements LongObjectMap<V> {

   private static final Logger logger = Logger.getLogger(SoftValueLongObjectHashMap.class);

   // The soft references that are already good.
   // too bad there's no way to override the queue method on ReferenceQueue, so I wouldn't need this
   private final ReferenceQueue<V> refQueue = new ReferenceQueue<>();

   private final LongObjectMap<AggregatedSoftReference<V>> mapDelegate = new LongObjectHashMap<>();

   private long usedCounter = 0;

   private int maxElements;



   public interface ValueCache {

      boolean isLive();
   }



   public SoftValueLongObjectHashMap(final int maxElements) {
      this.maxElements = maxElements;
   }

   public void setMaxElements(final int maxElements) {
      this.maxElements = maxElements;
      checkCacheSize();
   }

   public int getMaxEelements() {
      return this.maxElements;
   }

   /**
    * @see java.util.Map#size()
    */
   @Override
   public int size() {
      processQueue();
      return mapDelegate.size();
   }

   /**
    * @see java.util.Map#isEmpty()
    */
   @Override
   public boolean isEmpty() {
      processQueue();
      return mapDelegate.isEmpty();
   }

   /**
    * @param key
    * @see java.util.Map#containsKey(java.lang.Object)
    */
   @Override
   public boolean containsKey(final Object key) {
      return containsKey(objectToKey(key));
   }

   /**
    * @param value
    * @see java.util.Map#containsValue(java.lang.Object)
    */
   @Override
   public boolean containsValue(final Object value) {
      processQueue();
      for (AggregatedSoftReference<V> valueIter : mapDelegate.values()) {
         V valueElement = valueIter.get();
         if (valueElement != null && value.equals(valueElement)) {
            return true;
         }

      }
      return false;
   }

   private static long objectToKey(Object key) {
      return ((Long) key).longValue();
   }

   /**
    * @param key
    * @see java.util.Map#get(java.lang.Object)
    */
   @Override
   public V get(final Object key) {
      return get(objectToKey(key));
   }

   @Override
   public V put(Long key, V value) {
      return put(objectToKey(key), value);
   }

   @Override
   public V get(long key) {
      processQueue();
      AggregatedSoftReference<V> value = mapDelegate.get(key);
      if (value != null) {
         usedCounter++;
         value.used(usedCounter);
         return value.get();
      } else {
         return null;
      }
   }

   /**
    * @param key
    * @param value
    * @see java.util.Map#put(java.lang.Object, java.lang.Object)
    */
   @Override
   public V put(final long key, final V value) {
      processQueue();
      AggregatedSoftReference<V> newRef = createReference(key, value);
      AggregatedSoftReference<V> oldRef = mapDelegate.put(key, newRef);
      checkCacheSize();
      usedCounter++;
      newRef.used(usedCounter);
      if (oldRef != null) {
         return oldRef.get();
      } else {
         return null;
      }
   }

   @Override
   public V remove(long key) {
      processQueue();
      AggregatedSoftReference<V> ref = mapDelegate.remove(key);
      if (ref != null) {
         return ref.get();
      } else {
         return null;
      }
   }

   private void checkCacheSize() {
      if (maxElements > 0 && mapDelegate.size() > maxElements) {
         TreeSet<AggregatedSoftReference> usedReferences = new TreeSet<>(new ComparatorAgregated());

         for (AggregatedSoftReference<V> ref : mapDelegate.values()) {
            V v = ref.get();

            if (v != null && !v.isLive()) {
               usedReferences.add(ref);
            }
         }

         for (AggregatedSoftReference ref : usedReferences) {
            if (ref.used > 0) {
               Object removed = mapDelegate.remove(ref.key);

               if (logger.isTraceEnabled()) {
                  logger.trace("Removing " + removed + " with id = " + ref.key + " from SoftValueLongObjectHashMap");
               }

               if (mapDelegate.size() <= maxElements) {
                  break;
               }
            }
         }
      }
   }

   class ComparatorAgregated implements Comparator<AggregatedSoftReference> {

      @Override
      public int compare(AggregatedSoftReference o1, AggregatedSoftReference o2) {
         long k = o1.used - o2.used;

         if (k > 0) {
            return 1;
         } else if (k < 0) {
            return -1;
         }

         k = o1.hashCode() - o2.hashCode();

         if (k > 0) {
            return 1;
         } else if (k < 0) {
            return -1;
         } else {
            return 0;
         }
      }
   }

   /**
    * @param key
    * @see java.util.Map#remove(java.lang.Object)
    */
   @Override
   public V remove(final Object key) {
      return remove(objectToKey(key));
   }

   /**
    * @param m
    * @see java.util.Map#putAll(java.util.Map)
    */
   @Override
   public void putAll(final Map<? extends Long, ? extends V> m) {
      processQueue();
      if (m instanceof LongObjectMap) {
         final LongObjectMap<? extends V> primitiveMap = (LongObjectMap<? extends V>) m;
         for (PrimitiveEntry<? extends V> entry : primitiveMap.entries()) {
            put(entry.key(), entry.value());
         }
      } else {
         for (Map.Entry<? extends Long, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
         }
      }
   }

   /**
    * @see java.util.Map#clear()
    */
   @Override
   public void clear() {
      mapDelegate.clear();
   }

   /**
    * @see java.util.Map#keySet()
    */
   @Override
   public Set<Long> keySet() {
      processQueue();
      return mapDelegate.keySet();
   }

   /**
    * @see java.util.Map#values()
    */
   @Override
   public Collection<V> values() {
      processQueue();
      ArrayList<V> list = new ArrayList<>();

      for (AggregatedSoftReference<V> refs : mapDelegate.values()) {
         V value = refs.get();
         if (value != null) {
            list.add(value);
         }
      }

      return list;
   }

   @Override
   public Set<Entry<Long, V>> entrySet() {
      return null;
   }

   @Override
   public Iterable<PrimitiveEntry<V>> entries() {
      processQueue();
      final int size = mapDelegate.size();
      final List<PrimitiveEntry<V>> entries = new ArrayList<>(size);
      for (PrimitiveEntry<AggregatedSoftReference<V>> pair : mapDelegate.entries()) {
         V value = pair.value().get();
         if (value != null) {
            entries.add(new EntryElement<>(pair.key(), value));
         }
      }
      return entries;
   }

   @Override
   public boolean containsKey(long key) {
      processQueue();
      return mapDelegate.containsKey(key);
   }

   /**
    * @param o
    * @see java.util.Map#equals(java.lang.Object)
    */
   @Override
   public boolean equals(final Object o) {
      processQueue();
      return mapDelegate.equals(o);
   }

   /**
    * @see java.util.Map#hashCode()
    */
   @Override
   public int hashCode() {
      return mapDelegate.hashCode();
   }

   @SuppressWarnings("unchecked")
   private void processQueue() {
      AggregatedSoftReference ref;
      while ((ref = (AggregatedSoftReference) this.refQueue.poll()) != null) {
         logger.tracef("Removing reference through processQueue:: %s", ref.get());
         mapDelegate.remove(ref.key);
      }
   }

   private AggregatedSoftReference createReference(final long key, final V value) {
      return new AggregatedSoftReference(key, value, refQueue);
   }

   static final class AggregatedSoftReference<V> extends SoftReference<V> {

      final long key;

      long used = 0;

      public long getUsed() {
         return used;
      }

      public void used(long value) {
         this.used = value;
      }

      AggregatedSoftReference(final long key, final V referent, ReferenceQueue<V> refQueue) {
         super(referent, refQueue);
         this.key = key;
      }

      @Override
      public String toString() {
         return "AggregatedSoftReference [key=" + key + ", used=" + used + "]";
      }
   }

   static final class EntryElement<V> implements LongObjectMap.PrimitiveEntry<V> {

      final long key;

      final V value;

      EntryElement(final long key, final V value) {
         this.key = key;
         this.value = value;
      }

      @Override
      public long key() {
         return key;
      }

      @Override
      public V value() {
         return value;
      }

      @Override
      @Deprecated
      public void setValue(V value) {
         throw new UnsupportedOperationException();
      }
   }

}
