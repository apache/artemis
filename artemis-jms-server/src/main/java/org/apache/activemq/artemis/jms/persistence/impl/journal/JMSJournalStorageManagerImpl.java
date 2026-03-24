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
package org.apache.activemq.artemis.jms.persistence.impl.journal;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.replication.ReplicatedJournal;
import org.apache.activemq.artemis.core.replication.ReplicationManager;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.jms.persistence.JMSStorageManager;
import org.apache.activemq.artemis.jms.persistence.config.PersistedBindings;
import org.apache.activemq.artemis.jms.persistence.config.PersistedConnectionFactory;
import org.apache.activemq.artemis.jms.persistence.config.PersistedDestination;
import org.apache.activemq.artemis.jms.persistence.config.PersistedType;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.IDGenerator;

public final class JMSJournalStorageManagerImpl implements JMSStorageManager {



   public static final byte CF_RECORD = 1;

   public static final byte DESTINATION_RECORD = 2;

   public static final byte BINDING_RECORD = 3;


   private final IDGenerator idGenerator;

   private final boolean createDir;

   private final Journal jmsJournal;

   private volatile boolean started;

   private final Map<String, PersistedConnectionFactory> mapFactories = new ConcurrentHashMap<>();

   private final Map<Pair<PersistedType, String>, PersistedDestination> destinations = new ConcurrentHashMap<>();

   private final Map<Pair<PersistedType, String>, PersistedBindings> mapBindings = new ConcurrentHashMap<>();

   private final Configuration config;


   public JMSJournalStorageManagerImpl(ExecutorFactory ioExecutors,
                                       final IDGenerator idGenerator,
                                       final Configuration config,
                                       final ReplicationManager replicator) {
      final EnumSet<JournalType> supportedJournalTypes = EnumSet.allOf(JournalType.class);
      if (!supportedJournalTypes.contains(config.getJournalType())) {
         throw new IllegalArgumentException("Only " + supportedJournalTypes + " are supported Journal types");
      }

      this.config = config;

      createDir = config.isCreateBindingsDir();

      SequentialFileFactory bindingsJMS = new NIOSequentialFileFactory(config.getBindingsLocation(), 1);

      Journal localJMS = new JournalImpl(ioExecutors, 1024 * 1024, 2, config.getJournalPoolFiles(), config.getJournalCompactMinFiles(), config.getJournalCompactPercentage(), bindingsJMS, "activemq-jms", "jms", 1, 0);

      if (replicator != null) {
         jmsJournal = new ReplicatedJournal((byte) 2, localJMS, replicator);
      } else {
         jmsJournal = localJMS;
      }

      this.idGenerator = idGenerator;
   }

   @Override
   public List<PersistedConnectionFactory> recoverConnectionFactories() {
      List<PersistedConnectionFactory> cfs = new ArrayList<>(mapFactories.values());
      return cfs;
   }

   @Override
   public void storeConnectionFactory(final PersistedConnectionFactory connectionFactory) throws Exception {
      deleteConnectionFactory(connectionFactory.getName());
      long id = idGenerator.generateID();
      connectionFactory.setId(id);
      jmsJournal.appendAddRecord(id, CF_RECORD, connectionFactory, true);
      mapFactories.put(connectionFactory.getName(), connectionFactory);
   }

   @Override
   public void deleteConnectionFactory(final String cfName) throws Exception {
      PersistedConnectionFactory oldCF = mapFactories.remove(cfName);
      if (oldCF != null) {
         jmsJournal.appendDeleteRecord(oldCF.getId(), false);
      }
   }

   @Override
   public List<PersistedDestination> recoverDestinations() {
      List<PersistedDestination> destinations = new ArrayList<>(this.destinations.values());
      return destinations;
   }

   @Override
   public void storeDestination(final PersistedDestination destination) throws Exception {
      deleteDestination(destination.getType(), destination.getName());
      long id = idGenerator.generateID();
      destination.setId(id);
      jmsJournal.appendAddRecord(id, DESTINATION_RECORD, destination, true);
      destinations.put(new Pair<>(destination.getType(), destination.getName()), destination);
   }

   @Override
   public List<PersistedBindings> recoverPersistedBindings() throws Exception {
      ArrayList<PersistedBindings> list = new ArrayList<>(mapBindings.values());
      return list;
   }

   @Override
   public void addBindings(PersistedType type, String name, String... address) throws Exception {
      Pair<PersistedType, String> key = new Pair<>(type, name);

      long tx = idGenerator.generateID();

      PersistedBindings currentBindings = mapBindings.get(key);
      if (currentBindings != null) {
         jmsJournal.appendDeleteRecordTransactional(tx, currentBindings.getId());
      } else {
         currentBindings = new PersistedBindings(type, name);
      }

      mapBindings.put(key, currentBindings);

      for (String adItem : address) {
         currentBindings.addBinding(adItem);
      }

      long newId = idGenerator.generateID();

      currentBindings.setId(newId);

      jmsJournal.appendAddRecordTransactional(tx, newId, BINDING_RECORD, currentBindings);

      jmsJournal.appendCommitRecord(tx, true);
   }

   @Override
   public void deleteBindings(PersistedType type, String name, String address) throws Exception {
      Pair<PersistedType, String> key = new Pair<>(type, name);

      long tx = idGenerator.generateID();

      PersistedBindings currentBindings = mapBindings.get(key);
      if (currentBindings == null) {
         return;
      } else {
         jmsJournal.appendDeleteRecordTransactional(tx, currentBindings.getId());
      }

      currentBindings.deleteBinding(address);

      if (currentBindings.getBindings().size() == 0) {
         mapBindings.remove(key);
      } else {
         long newId = idGenerator.generateID();
         currentBindings.setId(newId);
         jmsJournal.appendAddRecordTransactional(tx, newId, BINDING_RECORD, currentBindings);
      }

      jmsJournal.appendCommitRecord(tx, true);
   }

   @Override
   public void deleteBindings(PersistedType type, String name) throws Exception {
      Pair<PersistedType, String> key = new Pair<>(type, name);

      PersistedBindings currentBindings = mapBindings.remove(key);

      if (currentBindings != null) {
         jmsJournal.appendDeleteRecord(currentBindings.getId(), true);
      }
   }

   @Override
   public void deleteDestination(final PersistedType type, final String name) throws Exception {
      PersistedDestination destination = destinations.remove(new Pair<>(type, name));
      if (destination != null) {
         jmsJournal.appendDeleteRecord(destination.getId(), false);
      }
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   @Override
   public void start() throws Exception {
      checkAndCreateDir(config.getBindingsLocation(), createDir);

      jmsJournal.start();

      started = true;
   }

   @Override
   public void stop() throws Exception {
      this.started = false;
      jmsJournal.stop();
   }

   @Override
   public void load() throws Exception {
      mapFactories.clear();

      List<RecordInfo> data = new ArrayList<>();

      ArrayList<PreparedTransactionInfo> list = new ArrayList<>();

      jmsJournal.load(data, list, null);

      for (RecordInfo record : data) {
         long id = record.id;

         ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(record.data);

         byte rec = record.getUserRecordType();

         if (rec == CF_RECORD) {
            PersistedConnectionFactory cf = new PersistedConnectionFactory();
            cf.decode(buffer);
            cf.setId(id);
            mapFactories.put(cf.getName(), cf);
         } else if (rec == DESTINATION_RECORD) {
            PersistedDestination destination = new PersistedDestination();
            destination.decode(buffer);
            destination.setId(id);
            destinations.put(new Pair<>(destination.getType(), destination.getName()), destination);
         } else if (rec == BINDING_RECORD) {
            PersistedBindings bindings = new PersistedBindings();
            bindings.decode(buffer);
            bindings.setId(id);
            Pair<PersistedType, String> key = new Pair<>(bindings.getType(), bindings.getName());
            mapBindings.put(key, bindings);
         } else {
            throw new IllegalStateException("Invalid record type " + rec);
         }

      }

   }




   private void checkAndCreateDir(final File dir, final boolean create) {

      if (!dir.exists()) {
         if (create) {
            if (!dir.mkdirs()) {
               throw new IllegalStateException("Failed to create directory " + dir);
            }
         } else {
            throw new IllegalArgumentException("Directory " + dir + " does not exist and will not create it");
         }
      }
   }


}
