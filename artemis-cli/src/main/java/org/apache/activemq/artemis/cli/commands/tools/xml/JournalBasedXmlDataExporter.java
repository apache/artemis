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
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.TransactionFailureCallback;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PagePosition;
import org.apache.activemq.artemis.core.paging.cursor.impl.PagePositionImpl;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.paging.impl.PageTransactionInfoImpl;
import org.apache.activemq.artemis.core.persistence.impl.journal.AckDescribe;
import org.apache.activemq.artemis.core.persistence.impl.journal.DescribeJournal;
import org.apache.activemq.artemis.core.persistence.impl.journal.DescribeJournal.MessageDescribe;
import org.apache.activemq.artemis.core.persistence.impl.journal.DescribeJournal.ReferenceDescribe;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.CursorAckRecordEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PageUpdateTXEncoding;
import org.apache.activemq.artemis.core.persistence.config.PersistedDivertConfiguration;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PersistentAddressBindingEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PersistentQueueBindingEncoding;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JournalBasedXmlDataExporter {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final JournalStorageManager storageManager;
   private final PagingManager pagingManager;
   private final Configuration config;
   private final String undefinedPrefix;
   private final List<String> queues;
   private final int logInterval;
   private final PrintStream err;

   private XMLStreamWriter xmlWriter;
   private XMLMessageExporter exporter;
   private Throwable lastError;

   private final Map<Long, Map<Long, ReferenceDescribe>> messageRefs = new HashMap<>();
   private final Map<Long, Message> messages = new TreeMap<>();
   private final Map<Long, Set<PagePosition>> cursorRecords = new HashMap<>();
   private final Set<Long> pgTXs = new HashSet<>();
   private final Map<Long, PersistentQueueBindingEncoding> queueBindings = new HashMap<>();
   private final Map<Long, PersistentAddressBindingEncoding> addressBindings = new HashMap<>();
   private final Map<String, PersistedDivertConfiguration> divertConfigurations = new HashMap<>();

   long messagesPrinted = 0L;
   long bindingsPrinted = 0L;

   public JournalBasedXmlDataExporter(JournalStorageManager storageManager,
                                      PagingManager pagingManager,
                                      Configuration config,
                                      String undefinedPrefix,
                                      List<String> queues,
                                      int logInterval,
                                      PrintStream err) {
      this.storageManager = storageManager;
      this.pagingManager = pagingManager;
      this.config = config;
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
      getBindings();
      warnUnknownQueues();
      processMessageJournal();
      printDataAsXML();
      logger.debug("\n\nProcessing took: {}ms", (System.currentTimeMillis() - start));
      logger.debug("Output {} messages and {} bindings.", messagesPrinted, bindingsPrinted);
   }

   private void processMessageJournal() throws Exception {
      List<RecordInfo> acks = new ArrayList<>();

      List<RecordInfo> records = new LinkedList<>();

      List<PreparedTransactionInfo> preparedTransactions = new LinkedList<>();

      Journal messageJournal = storageManager.getMessageJournal();

      logger.debug("Reading journal from {}", config.getJournalDirectory());

      messageJournal.start();

      TransactionFailureCallback transactionFailureCallback = (transactionID, records1, recordsToDelete) -> {
         StringBuilder message = new StringBuilder();
         message.append("Encountered failed journal transaction: ").append(transactionID);
         for (int i = 0; i < records1.size(); i++) {
            if (i == 0) {
               message.append("; Records: ");
            }
            message.append(records1.get(i));
            if (i != (records1.size() - 1)) {
               message.append(", ");
            }
         }

         for (int i = 0; i < recordsToDelete.size(); i++) {
            if (i == 0) {
               message.append("; RecordsToDelete: ");
            }
            message.append(recordsToDelete.get(i));
            if (i != (recordsToDelete.size() - 1)) {
               message.append(", ");
            }
         }

         logger.debug(message.toString());
      };

      messageJournal.load(records, preparedTransactions, transactionFailureCallback, false);

      preparedTransactions = null;

      for (RecordInfo info : records) {
         byte[] data = info.data;

         ActiveMQBuffer buff = ActiveMQBuffers.wrappedBuffer(data);

         Object o = DescribeJournal.newObjectEncoding(info, storageManager);
         if (info.getUserRecordType() == JournalRecordIds.ADD_MESSAGE) {
            messages.put(info.id, ((MessageDescribe) o).getMsg().toCore());
         } else if (info.getUserRecordType() == JournalRecordIds.ADD_MESSAGE_PROTOCOL) {
            messages.put(info.id, ((MessageDescribe) o).getMsg().toCore());
         } else if (info.getUserRecordType() == JournalRecordIds.ADD_LARGE_MESSAGE) {
            messages.put(info.id, ((MessageDescribe) o).getMsg());
         } else if (info.getUserRecordType() == JournalRecordIds.ADD_REF) {
            ReferenceDescribe ref = (ReferenceDescribe) o;
            Map<Long, ReferenceDescribe> map = messageRefs.get(info.id);
            if (map == null) {
               Map<Long, ReferenceDescribe> newMap = new HashMap<>();
               newMap.put(ref.refEncoding.queueID, ref);
               messageRefs.put(info.id, newMap);
            } else {
               map.put(ref.refEncoding.queueID, ref);
            }
         } else if (info.getUserRecordType() == JournalRecordIds.ACKNOWLEDGE_REF) {
            acks.add(info);
         } else if (info.userRecordType == JournalRecordIds.ACKNOWLEDGE_CURSOR) {
            CursorAckRecordEncoding encoding = new CursorAckRecordEncoding();
            encoding.decode(buff);

            Set<PagePosition> set = cursorRecords.get(encoding.queueID);

            if (set == null) {
               set = new HashSet<>();
               cursorRecords.put(encoding.queueID, set);
            }

            set.add(encoding.position);
         } else if (info.userRecordType == JournalRecordIds.PAGE_TRANSACTION) {
            if (info.isUpdate) {
               PageUpdateTXEncoding pageUpdate = new PageUpdateTXEncoding();

               pageUpdate.decode(buff);
               pgTXs.add(pageUpdate.pageTX);
            } else {
               PageTransactionInfoImpl pageTransactionInfo = new PageTransactionInfoImpl();

               pageTransactionInfo.decode(buff);

               pageTransactionInfo.setRecordID(info.id);
               pgTXs.add(pageTransactionInfo.getTransactionID());
            }
         }
      }

      messageJournal.stop();

      removeAcked(acks);
   }

   private void removeAcked(List<RecordInfo> acks) {
      for (RecordInfo info : acks) {
         AckDescribe ack = (AckDescribe) DescribeJournal.newObjectEncoding(info, null);
         Map<Long, ReferenceDescribe> referenceDescribeHashMap = messageRefs.get(info.id);
         if (referenceDescribeHashMap != null) {
            referenceDescribeHashMap.remove(ack.refEncoding.queueID);
            if (referenceDescribeHashMap.isEmpty()) {
               messages.remove(info.id);
               messageRefs.remove(info.id);
            }
         }
      }
   }

   private void getBindings() throws Exception {
      List<RecordInfo> records = new LinkedList<>();

      Journal bindingsJournal = storageManager.getBindingsJournal();

      bindingsJournal.start();

      logger.debug("Reading bindings journal from {}", config.getBindingsDirectory());

      bindingsJournal.load(records, null, null);

      for (RecordInfo info : records) {
         if (info.getUserRecordType() == JournalRecordIds.QUEUE_BINDING_RECORD) {
            PersistentQueueBindingEncoding bindingEncoding = (PersistentQueueBindingEncoding) DescribeJournal.newObjectEncoding(info, null);
            queueBindings.put(bindingEncoding.getQueueConfiguration().getId(), bindingEncoding);
         } else if (info.getUserRecordType() == JournalRecordIds.ADDRESS_BINDING_RECORD) {
            PersistentAddressBindingEncoding bindingEncoding = (PersistentAddressBindingEncoding) DescribeJournal.newObjectEncoding(info, null);
            addressBindings.put(bindingEncoding.getId(), bindingEncoding);
         } else if (info.getUserRecordType() == JournalRecordIds.DIVERT_RECORD) {
            PersistedDivertConfiguration divertConfiguration = (PersistedDivertConfiguration) DescribeJournal.newObjectEncoding(info, null);
            divertConfigurations.put(divertConfiguration.getName(), divertConfiguration);
         }
      }

      bindingsJournal.stop();
   }

   private void warnUnknownQueues() {
      if (queues == null || queues.isEmpty()) {
         return;
      }
      Set<String> existing = queueBindings.values().stream()
         .map(binding -> binding.getQueueConfiguration().getName().toString())
         .collect(Collectors.toSet());
      for (String queue : queues) {
         if (!existing.contains(queue)) {
            err.println("Queue '" + queue + "' was not found; nothing will be exported for it.");
         }
      }
   }

   private void printDataAsXML() {
      try {

         xmlWriter.writeStartElement(XmlDataConstants.DOCUMENT_PARENT);
         printBindingsAsXML();
         printAllMessagesAsXML();
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
            .map(PersistentQueueBindingEncoding::getQueueConfiguration)
            .filter(queueConfig -> includeQueue(queueConfig.getName().toString()))
            .map(queueConfig -> queueConfig.getAddress().toString())
            .collect(Collectors.toSet());

      xmlWriter.writeStartElement(XmlDataConstants.BINDINGS_PARENT);
      for (Map.Entry<Long, PersistentAddressBindingEncoding> addressBindingEncodingEntry : addressBindings.entrySet()) {
         PersistentAddressBindingEncoding bindingEncoding = addressBindings.get(addressBindingEncodingEntry.getKey());
         if (includedAddresses != null && !includedAddresses.contains(bindingEncoding.getName().toString())) {
            continue;
         }
         xmlWriter.writeEmptyElement(XmlDataConstants.ADDRESS_BINDINGS_CHILD);
         String routingTypes = bindingEncoding.getRoutingTypes().stream().
            map(Enum::toString).collect(Collectors.joining(","));
         xmlWriter.writeAttribute(XmlDataConstants.ADDRESS_BINDING_ROUTING_TYPE, routingTypes);
         xmlWriter.writeAttribute(XmlDataConstants.ADDRESS_BINDING_NAME, bindingEncoding.getName().toString());
         xmlWriter.writeAttribute(XmlDataConstants.ADDRESS_BINDING_ID, Long.toString(bindingEncoding.getId()));
         bindingsPrinted++;
      }
      for (Map.Entry<Long, PersistentQueueBindingEncoding> queueBindingEncodingEntry : queueBindings.entrySet()) {
         QueueConfiguration queueConfig = queueBindings.get(queueBindingEncodingEntry.getKey()).getQueueConfiguration();
         if (!includeQueue(queueConfig.getName().toString())) {
            continue;
         }
         xmlWriter.writeEmptyElement(XmlDataConstants.QUEUE_BINDINGS_CHILD);
         xmlWriter.writeAttribute(XmlDataConstants.QUEUE_BINDING_ADDRESS, queueConfig.getAddress().toString());
         xmlWriter.writeAttribute(XmlDataConstants.QUEUE_BINDING_FILTER_STRING, queueConfig.getFilterString() == null ? "" : queueConfig.getFilterString().toString());
         xmlWriter.writeAttribute(XmlDataConstants.QUEUE_BINDING_NAME, queueConfig.getName().toString());
         xmlWriter.writeAttribute(XmlDataConstants.QUEUE_BINDING_ID, Long.toString(queueConfig.getId()));
         xmlWriter.writeAttribute(XmlDataConstants.QUEUE_BINDING_ROUTING_TYPE, queueConfig.getRoutingType().toString());
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

   private void printAllMessagesAsXML() throws Exception {
      xmlWriter.writeStartElement(XmlDataConstants.MESSAGES_PARENT);

      if (logInterval > 0) {
         err.println("Processing journal messages");
      }

      long msgs = 0;
      for (Map.Entry<Long, Message> messageMapEntry : messages.entrySet()) {
         List<String> queueNames = extractQueueNames(messageRefs.get(messageMapEntry.getKey()));
         if (queueNames.isEmpty()) {
            continue;
         }
         printSingleMessageAsXML(messageMapEntry.getValue().toCore(), queueNames);
         msgs++;
         if (logInterval > 0) {
            if (msgs % logInterval == 0) {
               err.println("exported " + msgs + " messages from journal");
            }
         }
      }

      printPagedMessagesAsXML();

      xmlWriter.writeEndElement(); // end "messages"
   }

   private void printSingleMessageAsXML(ICoreMessage message, List<String> queues) throws Exception {
      exporter.printSingleMessageAsXML(message, queues, false);
      messagesPrinted++;
   }

   private void printPagedMessagesAsXML() {
      try {

         pagingManager.start();

         long msgs = 0;
         SimpleString[] stores = pagingManager.getStoreNames();

         for (SimpleString store : stores) {
            PagingStore pageStore = pagingManager.getPageStore(store);

            if (pageStore != null) {
               File folder = pageStore.getFolder();
               logger.debug("Reading page store {} folder = {}", store, folder);

               long pageId = pageStore.getFirstPage();
               for (long i = 0; i < pageStore.getNumberOfPages(); i++) {
                  logger.debug("Reading page {}", pageId);
                  Page page = pageStore.newPageObject(pageId);
                  page.open(false);
                  org.apache.activemq.artemis.utils.collections.LinkedList<PagedMessage> messages = page.read(storageManager);
                  page.close(false, false);

                  int messageId = 0;

                  try (LinkedListIterator<PagedMessage> iter = messages.iterator()) {

                     while (iter.hasNext()) {
                        msgs++;
                        if (logInterval > 0 && msgs % logInterval == 0) {
                           err.println("Exported " + msgs + " messages from paging");
                        }
                        PagedMessage message = iter.next();
                        message.initMessage(storageManager);
                        long[] queueIDs = message.getQueueIDs();
                        List<String> queueNames = new ArrayList<>();
                        for (long queueID : queueIDs) {
                           PagePosition posCheck = new PagePositionImpl(pageId, messageId);

                           boolean acked = false;

                           Set<PagePosition> positions = cursorRecords.get(queueID);
                           if (positions != null) {
                              acked = positions.contains(posCheck);
                           }

                           if (!acked) {
                              PersistentQueueBindingEncoding queueBinding = queueBindings.get(queueID);
                              if (queueBinding != null) {
                                 SimpleString queueName = queueBinding.getQueueConfiguration().getName();
                                 if (includeQueue(queueName.toString())) {
                                    queueNames.add(queueName.toString());
                                 }
                              }
                           }
                        }

                        if (!queueNames.isEmpty() && (message.getTransactionID() == -1 || pgTXs.contains(message.getTransactionID()))) {
                           printSingleMessageAsXML(message.getMessage().toCore(), queueNames);
                        }

                        messageId++;
                     }

                     pageId++;
                  }
               }
            } else {
               logger.debug("Page store was null");
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   private List<String> extractQueueNames(Map<Long, DescribeJournal.ReferenceDescribe> refMap) {
      List<String> queueList = new ArrayList<>();
      for (DescribeJournal.ReferenceDescribe ref : refMap.values()) {
         String queueName;

         long id = ref.refEncoding.queueID;
         PersistentQueueBindingEncoding persistentQueueBindingEncoding = queueBindings.get(id);
         if (persistentQueueBindingEncoding == null) {
            if (queues != null && !queues.isEmpty()) {
               continue;
            }
            String name = undefinedPrefix + id;
            queueBindings.put(id, new PersistentQueueBindingEncoding(QueueConfiguration.of(name).setAddress(name)));
            queueName = String.valueOf(name);
            err.println("Queue ID " + id + " not defined. Exporting it as " + name);
         } else {
            queueName = String.valueOf(persistentQueueBindingEncoding.getQueueConfiguration().getName());
         }

         if (includeQueue(queueName)) {
            queueList.add(queueName);
         }
      }
      return queueList;
   }
}
