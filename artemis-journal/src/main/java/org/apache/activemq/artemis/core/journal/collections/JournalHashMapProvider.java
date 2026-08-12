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

package org.apache.activemq.artemis.core.journal.collections;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.journal.IOCompletion;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.persistence.Persister;

public class JournalHashMapProvider<I, K, V, C> {

   final MapStorageManager journal;
   final Persister<JournalHashMap.MapRecord<I, K, V>> persister;
   final Map<I, JournalHashMap<I, K, V, C>> journalMaps = new HashMap<>();
   final LongSupplier idSupplier;
   final byte recordType;
   final IOCriticalErrorListener ioExceptionListener;
   final Supplier<IOCompletion> ioCompletionSupplier;
   final Function<I, C> contextProvider;

   public JournalHashMapProvider(LongSupplier idSupplier, MapStorageManager journal, AbstractHashMapPersister<I, K, V> persister, byte recordType, Supplier<IOCompletion> ioCompletionSupplier, Function<I, C> contextProvider, IOCriticalErrorListener ioExceptionListener) {
      this.idSupplier = idSupplier;
      this.persister = persister;
      this.journal = journal;
      this.recordType = recordType;
      this.ioExceptionListener = ioExceptionListener;
      this.contextProvider = contextProvider;
      this.ioCompletionSupplier = ioCompletionSupplier;
   }

   public List<JournalHashMap<I, K, V, C>> getMaps() {
      return new ArrayList<>(journalMaps.values());
   }

   public void clear() {
      journalMaps.clear();
   }

   public void reload(RecordInfo recordInfo) {
      JournalHashMap.MapRecord<I, K, V> mapRecord = persister.decode(recordInfo.wrapData(), null, null);
      getMap(mapRecord.collectionID, null).reload(mapRecord);
   }

   public Iterator<JournalHashMap<I, K, V, C>> iterMaps() {
      return journalMaps.values().iterator();
   }

   public synchronized JournalHashMap<I, K, V, C> getMap(I collectionID, C context) {
      JournalHashMap<I, K, V, C> journalHashMap = journalMaps.get(collectionID);
      if (journalHashMap == null) {
         journalHashMap = new JournalHashMap<>(collectionID, journal, idSupplier, persister, recordType, ioCompletionSupplier, contextProvider, ioExceptionListener).setContext(context);
         journalMaps.put(collectionID, journalHashMap);
      }
      return journalHashMap;
   }

   public JournalHashMap<I, K, V, C> getMap(I collectionID) {
      return getMap(collectionID, null);
   }

   public boolean containsMap(I collectionID) {
      return journalMaps.containsKey(collectionID);
   }
}
