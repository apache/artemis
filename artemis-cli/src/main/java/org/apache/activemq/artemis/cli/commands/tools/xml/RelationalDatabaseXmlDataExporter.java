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
package org.apache.activemq.artemis.cli.commands.tools.xml;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedDivertConfiguration;
import org.apache.activemq.artemis.core.persistence.impl.database.DatabaseStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.data.AddressData;
import org.apache.artemis.database.data.MessageDataReferenceMerged;
import org.apache.artemis.database.data.MessageDataReferenceMerged.QueueRef;
import org.apache.artemis.database.data.QueueData;
import org.apache.artemis.database.queries.AddressJDBCQuery;
import org.apache.artemis.database.queries.GenericDataJDBCQuery;
import org.apache.artemis.database.queries.MessagesAndReferencesDBQuery;
import org.apache.artemis.database.queries.QueueJDBCQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RelationalDatabaseXmlDataExporter {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final DatabaseStorageConfiguration databaseConfiguration;
   private final String undefinedPrefix;
   private final List<String> queues;
   private final int logInterval;
   private final PrintStream err;

   private XMLStreamWriter xmlWriter;
   private XMLMessageExporter exporter;
   private Throwable lastError;

   private final Map<Long, AddressData> addressBindings = new HashMap<>();
   private final Map<Long, QueueData> queueBindings = new HashMap<>();
   private final Map<String, PersistedDivertConfiguration> divertConfigurations = new HashMap<>();

   long messagesPrinted = 0L;
   long bindingsPrinted = 0L;

   public RelationalDatabaseXmlDataExporter(DatabaseStorageConfiguration databaseConfiguration,
                                            String undefinedPrefix,
                                            List<String> queues,
                                            int logInterval,
                                            PrintStream err) {
      this.databaseConfiguration = databaseConfiguration;
      this.undefinedPrefix = undefinedPrefix;
      this.queues = queues;
      this.logInterval = logInterval;
      this.err = err;
   }

   public void writeOutput(OutputStream out) throws Exception {
      XMLOutputFactory factory = XMLOutputFactory.newInstance();
      XMLStreamWriter rawXmlWriter = factory.createXMLStreamWriter(out, "UTF-8");
      PrettyPrintHandler handler = new PrettyPrintHandler(rawXmlWriter);
      xmlWriter = (XMLStreamWriter) Proxy.newProxyInstance(XMLStreamWriter.class.getClassLoader(), new Class[]{XMLStreamWriter.class}, handler);
      exporter = new XMLMessageExporter(xmlWriter);
      writeXMLData();
   }

   public Throwable getLastError() {
      return lastError;
   }

   private boolean includeQueue(String queueName) {
      return queues == null || queues.isEmpty() || queues.contains(queueName);
   }

   private void writeXMLData() throws Exception {
      long start = System.currentTimeMillis();

      DatabaseProvider databaseProvider = databaseConfiguration.getDatabaseProvider();

      try (Connection connection = databaseProvider.getConnection()) {
         loadBindings(databaseProvider, connection);
         warnUnknownQueues();
         printDataAsXML(databaseProvider, connection);
      }

      logger.debug("\n\nProcessing took: {}ms", (System.currentTimeMillis() - start));
      logger.debug("Output {} messages and {} bindings.", messagesPrinted, bindingsPrinted);
   }

   private void loadBindings(DatabaseProvider databaseProvider, Connection connection) throws Exception {
      AddressJDBCQuery addressQuery = new AddressJDBCQuery(databaseProvider, connection);
      addressQuery.query(data -> addressBindings.put(data.id, data));

      QueueJDBCQuery queueQuery = new QueueJDBCQuery(databaseProvider, connection);
      queueQuery.query(data -> queueBindings.put(data.id, data));

      GenericDataJDBCQuery configQuery = new GenericDataJDBCQuery(databaseProvider, connection, databaseProvider.getSqlProvider().getConfigData(), true);
      configQuery.query(data -> {
         if (data.recordType == JournalRecordIds.DIVERT_RECORD && data.dataSupplier != null) {
            Object decoded = DatabaseStorageManager.describeGenericData(data.id, data.recordType, data.dataSupplier.get());
            if (decoded instanceof PersistedDivertConfiguration divert) {
               divertConfigurations.put(divert.getName(), divert);
            }
         }
      });
   }

   private void warnUnknownQueues() {
      if (queues == null || queues.isEmpty()) {
         return;
      }
      Set<String> existing = queueBindings.values().stream()
         .map(q -> q.name)
         .collect(Collectors.toSet());
      for (String queue : queues) {
         if (!existing.contains(queue)) {
            err.println("Queue '" + queue + "' was not found; nothing will be exported for it.");
         }
      }
   }

   private void printDataAsXML(DatabaseProvider databaseProvider, Connection connection) {
      try {
         xmlWriter.writeStartElement(XmlDataConstants.DOCUMENT_PARENT);
         printBindingsAsXML();
         printAllMessagesAsXML(databaseProvider, connection);
         xmlWriter.writeEndElement(); // end DOCUMENT_PARENT
         xmlWriter.writeEndDocument();
         xmlWriter.flush();
         xmlWriter.close();
      } catch (Throwable e) {
         e.printStackTrace();
         this.lastError = e;
      }
   }

   private void printBindingsAsXML() throws XMLStreamException {
      Set<String> includedAddresses = (queues == null || queues.isEmpty()) ? null :
         queueBindings.values().stream()
            .filter(q -> includeQueue(q.name))
            .map(q -> {
               AddressData addr = addressBindings.get(q.addressId);
               return addr != null ? addr.address : null;
            })
            .collect(Collectors.toSet());

      xmlWriter.writeStartElement(XmlDataConstants.BINDINGS_PARENT);

      for (AddressData addr : addressBindings.values()) {
         if (includedAddresses != null && !includedAddresses.contains(addr.address)) {
            continue;
         }
         xmlWriter.writeEmptyElement(XmlDataConstants.ADDRESS_BINDINGS_CHILD);

         List<String> routingTypes = new ArrayList<>();
         if (addr.isMulticast) {
            routingTypes.add(RoutingType.MULTICAST.toString());
         }
         if (addr.isAnycast) {
            routingTypes.add(RoutingType.ANYCAST.toString());
         }

         xmlWriter.writeAttribute(XmlDataConstants.ADDRESS_BINDING_ROUTING_TYPE, String.join(",", routingTypes));
         xmlWriter.writeAttribute(XmlDataConstants.ADDRESS_BINDING_NAME, addr.address);
         xmlWriter.writeAttribute(XmlDataConstants.ADDRESS_BINDING_ID, Long.toString(addr.id));
         bindingsPrinted++;
      }

      for (QueueData q : queueBindings.values()) {
         if (!includeQueue(q.name)) {
            continue;
         }

         String addressName = q.name;
         AddressData addr = addressBindings.get(q.addressId);
         if (addr != null) {
            addressName = addr.address;
         }

         String routingType;
         if (q.isMulticast) {
            routingType = RoutingType.MULTICAST.toString();
         } else {
            routingType = RoutingType.ANYCAST.toString();
         }

         xmlWriter.writeEmptyElement(XmlDataConstants.QUEUE_BINDINGS_CHILD);
         xmlWriter.writeAttribute(XmlDataConstants.QUEUE_BINDING_ADDRESS, addressName);
         xmlWriter.writeAttribute(XmlDataConstants.QUEUE_BINDING_FILTER_STRING, q.filter == null ? "" : q.filter);
         xmlWriter.writeAttribute(XmlDataConstants.QUEUE_BINDING_NAME, q.name);
         xmlWriter.writeAttribute(XmlDataConstants.QUEUE_BINDING_ID, Long.toString(q.id));
         xmlWriter.writeAttribute(XmlDataConstants.QUEUE_BINDING_ROUTING_TYPE, routingType);
         bindingsPrinted++;
      }

      for (PersistedDivertConfiguration persistedDivert : divertConfigurations.values()) {
         DivertConfiguration dc = persistedDivert.getDivertConfiguration();
         xmlWriter.writeEmptyElement(XmlDataConstants.DIVERT_BINDINGS_CHILD);
         xmlWriter.writeAttribute(XmlDataConstants.DIVERT_BINDING_NAME, dc.getName());
         xmlWriter.writeAttribute(XmlDataConstants.DIVERT_BINDING_ROUTING_NAME, dc.getRoutingName() != null ? dc.getRoutingName() : "");
         xmlWriter.writeAttribute(XmlDataConstants.DIVERT_BINDING_ADDRESS, dc.getAddress() != null ? dc.getAddress() : "");
         xmlWriter.writeAttribute(XmlDataConstants.DIVERT_BINDING_FORWARDING_ADDRESS, dc.getForwardingAddress() != null ? dc.getForwardingAddress() : "");
         xmlWriter.writeAttribute(XmlDataConstants.DIVERT_BINDING_EXCLUSIVE, Boolean.toString(dc.isExclusive()));
         xmlWriter.writeAttribute(XmlDataConstants.DIVERT_BINDING_FILTER_STRING, dc.getFilterString() != null ? dc.getFilterString() : "");
         if (dc.getRoutingType() != null) {
            xmlWriter.writeAttribute(XmlDataConstants.DIVERT_BINDING_ROUTING_TYPE, dc.getRoutingType().toString());
         }
         if (dc.getTransformerConfiguration() != null && dc.getTransformerConfiguration().getClassName() != null) {
            xmlWriter.writeAttribute(XmlDataConstants.DIVERT_BINDING_TRANSFORMER_CLASS_NAME, dc.getTransformerConfiguration().getClassName());
         }
         bindingsPrinted++;
      }

      xmlWriter.writeEndElement(); // end BINDINGS_PARENT
   }

   private void printAllMessagesAsXML(DatabaseProvider databaseProvider, Connection connection) throws Exception {
      xmlWriter.writeStartElement(XmlDataConstants.MESSAGES_PARENT);

      if (logInterval > 0) {
         err.println("Processing database messages");
      }

      long msgs = 0;

      MessagesAndReferencesDBQuery msgQuery = new MessagesAndReferencesDBQuery(databaseProvider, connection);
      final long[] msgCount = {0};
      final Throwable[] queryError = {null};

      msgQuery.query(merged -> {
         try {
            List<String> queueNames = resolveQueueNames(merged);
            if (queueNames.isEmpty()) {
               return;
            }

            Message message = DatabaseStorageManager.decodeMessage(
               merged.messageID(), merged.isLarge(), merged.isCore(),
               merged.messageBufferSupplier(), merged.largeBodySupplier(), null);

            exporter.printSingleMessageAsXML(message.toCore(), queueNames, false);
            messagesPrinted++;
            msgCount[0]++;

            if (logInterval > 0 && msgCount[0] % logInterval == 0) {
               err.println("exported " + msgCount[0] + " messages from database");
            }
         } catch (Exception e) {
            queryError[0] = e;
            throw new RuntimeException(e);
         }
      });

      if (queryError[0] != null) {
         throw new Exception("Error exporting messages", queryError[0]);
      }

      xmlWriter.writeEndElement(); // end "messages"
   }

   private List<String> resolveQueueNames(MessageDataReferenceMerged merged) {
      List<String> queueNames = new ArrayList<>();
      for (QueueRef ref : merged.queues()) {
         long queueID = ref.queueID();
         QueueData queueData = queueBindings.get(queueID);
         String queueName;

         if (queueData == null) {
            if (queues != null && !queues.isEmpty()) {
               continue;
            }
            String name = undefinedPrefix + queueID;
            queueBindings.put(queueID, new QueueData(0, queueID, name, null, false, true, null, null));
            queueName = name;
            err.println("Queue ID " + queueID + " not defined. Exporting it as " + name);
         } else {
            queueName = queueData.name;
         }

         if (includeQueue(queueName)) {
            queueNames.add(queueName);
         }
      }
      return queueNames;
   }
}
