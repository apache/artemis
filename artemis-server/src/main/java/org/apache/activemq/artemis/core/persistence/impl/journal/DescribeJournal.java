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
package org.apache.activemq.artemis.core.persistence.impl.journal;

import javax.transaction.xa.Xid;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.journal.Journal;
import org.apache.activemq.artemis.core.journal.PreparedTransactionInfo;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.journal.impl.JournalReaderCallback;
import org.apache.activemq.artemis.core.paging.cursor.impl.PageSubscriptionCounterImpl;
import org.apache.activemq.artemis.core.paging.impl.PageTransactionInfoImpl;
import org.apache.activemq.artemis.core.persistence.config.PersistedAddressSetting;
import org.apache.activemq.artemis.core.persistence.config.PersistedAddressSettingJSON;
import org.apache.activemq.artemis.core.persistence.config.PersistedBridgeConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedDivertConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedRole;
import org.apache.activemq.artemis.core.persistence.config.PersistedSecuritySetting;
import org.apache.activemq.artemis.core.persistence.config.PersistedUser;
import org.apache.activemq.artemis.core.persistence.impl.journal.BatchingIDGenerator.IDCounterEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.AckRetry;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.CursorAckRecordEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.DeliveryCountUpdateEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.DuplicateIDEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.HeuristicCompletionEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.LargeMessagePersister;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PageCountPendingImpl;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PageCountRecord;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PageCountRecordInc;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PageUpdateTXEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PendingLargeMessageEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PersistentAddressBindingEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PersistentQueueBindingEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.RefEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.ScheduledDeliveryEncoding;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.spi.core.protocol.MessagePersister;
import org.apache.activemq.artemis.utils.Base64;
import org.apache.activemq.artemis.utils.XMLUtil;
import org.apache.activemq.artemis.utils.XidCodecSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import java.util.Objects;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ACKNOWLEDGE_CURSOR;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ACKNOWLEDGE_REF;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ADDRESS_BINDING_RECORD;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ADDRESS_SETTING_RECORD;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ADDRESS_SETTING_RECORD_JSON;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ADDRESS_STATUS_RECORD;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ADD_LARGE_MESSAGE;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ADD_LARGE_MESSAGE_PENDING;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ADD_MESSAGE;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ADD_MESSAGE_PROTOCOL;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ADD_REF;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.DIVERT_RECORD;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.DUPLICATE_ID;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.HEURISTIC_COMPLETION;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ID_COUNTER_RECORD;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ACK_RETRY;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.PAGE_CURSOR_COMPLETE;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.PAGE_CURSOR_COUNTER_INC;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.PAGE_CURSOR_COUNTER_VALUE;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.PAGE_CURSOR_PENDING_COUNTER;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.PAGE_TRANSACTION;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.QUEUE_BINDING_RECORD;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.QUEUE_STATUS_RECORD;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.ROLE_RECORD;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.SECURITY_SETTING_RECORD;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.SET_SCHEDULED_DELIVERY_TIME;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.UPDATE_DELIVERY_COUNT;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.USER_RECORD;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds.BRIDGE_RECORD;

/**
 * Outputs a String description of the Journals contents.
 * <p>
 * Meant to be used in debugging.
 */
public final class DescribeJournal {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final List<RecordInfo> records;
   private final List<PreparedTransactionInfo> preparedTransactions;
   private List<PersistentQueueBindingEncoding> bindingEncodings;

   private static Configuration getConfiguration() {
      Configuration configuration;
      String instanceFolder = System.getProperty("artemis.instance");
      if (instanceFolder != null) {
         configuration = new FileConfiguration();
         File configFile = new File(instanceFolder + "/etc/broker.xml");

         try {
            URL url = configFile.toURI().toURL();
            Element e = XMLUtil.urlToElement(url);

            String root = ((FileConfiguration) configuration).getRootElement();
            NodeList children = e.getElementsByTagName(root);
            if (root != null && children.getLength() > 0) {
               Node item = children.item(0);
               XMLUtil.validate(item, ((FileConfiguration) configuration).getSchema());
               ((FileConfiguration) configuration).parse((Element) item, url);
            }
         } catch (Exception e) {
            logger.error("failed to load broker.xml", e);
         }
      } else {
         configuration = new ConfigurationImpl();
      }

      return configuration;
   }

   public DescribeJournal(List<RecordInfo> records, List<PreparedTransactionInfo> preparedTransactions) {
      this.records = records;
      this.preparedTransactions = preparedTransactions;
   }

   public List<PersistentQueueBindingEncoding> getBindingEncodings() {
      return bindingEncodings;
   }

   public DescribeJournal setBindingEncodings(List<PersistentQueueBindingEncoding> bindingEncodings) {
      this.bindingEncodings = bindingEncodings;
      return this;
   }

   public List<RecordInfo> getRecords() {
      return records;
   }

   public List<PreparedTransactionInfo> getPreparedTransactions() {
      return preparedTransactions;
   }

   public static void describeBindingsJournal(final File bindingsDir) throws Exception {
      describeBindingsJournal(bindingsDir, System.out, false, true, true);
   }


   public static void describeBindingsJournal(final File bindingsDir, PrintStream out, boolean safe, boolean printRecords, boolean printSurviving) throws Exception {
      describeBindingsJournal(bindingsDir, out, safe, printRecords, printSurviving, false);
   }

   public static DescribeJournal describeBindingsJournal(final File bindingsDir, PrintStream out, boolean safe, boolean printRecords, boolean printSurviving, boolean reclaimed) throws Exception {

      SequentialFileFactory bindingsFF = new NIOSequentialFileFactory(bindingsDir, null, 1);

      JournalImpl bindings = new JournalImpl(1024 * 1024, 2, 2, -1, 0, bindingsFF, "activemq-bindings", "bindings", 1);
      return describeJournal(bindingsFF, bindings, bindingsDir, out, safe, printRecords, printSurviving, reclaimed);
   }

   public static DescribeJournal describeMessagesJournal(final File messagesDir) throws Exception {
      return describeMessagesJournal(messagesDir, System.out, false, true, true, false);
   }

   public static DescribeJournal describeMessagesJournal(final File messagesDir, PrintStream out, boolean safe, boolean printRecords, boolean printSurviving, boolean reclaimed) throws Exception {
      Configuration configuration = getConfiguration();
      SequentialFileFactory messagesFF = new NIOSequentialFileFactory(messagesDir, null, 1);

      // Will use only default values. The load function should adapt to anything different
      JournalImpl messagesJournal = new JournalImpl(configuration.getJournalFileSize(), configuration.getJournalMinFiles(), configuration.getJournalPoolFiles(), 0, 0, messagesFF, "activemq-data", "amq", 1);

      return describeJournal(messagesFF, messagesJournal, messagesDir, out, safe, printRecords, printSurviving, reclaimed);
   }

   private static final PrintStream nullPrintStream = new PrintStream(new OutputStream() {
      @Override
      public void write(int b) throws IOException {

      }
   });

   private static DescribeJournal describeJournal(SequentialFileFactory fileFactory,
                                                  JournalImpl journal,
                                                  final File path,
                                                  PrintStream out,
                                                  boolean safe,
                                                  boolean printRecords,
                                                  boolean printSurving,
                                                  boolean reclaimed) throws Exception {
      List<JournalFile> files = journal.orderFiles();

      final Map<Long, PageSubscriptionCounterImpl> counters = new HashMap<>();

      PrintStream recordsPrintStream = printRecords ? out : nullPrintStream;
      PrintStream survivingPrintStrea = printSurving ? out : nullPrintStream;

      recordsPrintStream.println("Journal path: " + path);

      for (JournalFile file : files) {
         recordsPrintStream.println("#" + file + " (size=" + file.getFile().size() + ")");

         JournalImpl.readJournalFile(fileFactory, file, new JournalReaderCallback() {
            @Override
            public void onReadEventRecord(RecordInfo recordInfo) throws Exception {
               recordsPrintStream.println("operation@Event;" + describeRecord(recordInfo, safe));
            }

            @Override
            public void onReadUpdateRecordTX(final long transactionID, final RecordInfo recordInfo) throws Exception {
               recordsPrintStream.println("operation@UpdateTX;txID=" + transactionID + "," + describeRecord(recordInfo, safe));
               checkRecordCounter(recordInfo);
            }

            @Override
            public void onReadUpdateRecord(final RecordInfo recordInfo) throws Exception {
               recordsPrintStream.println("operation@Update;" + describeRecord(recordInfo, safe));
               checkRecordCounter(recordInfo);
            }

            @Override
            public void onReadRollbackRecord(final long transactionID) throws Exception {
               recordsPrintStream.println("operation@Rollback;txID=" + transactionID);
            }

            @Override
            public void onReadPrepareRecord(final long transactionID,
                                            final byte[] extraData,
                                            final int numberOfRecords) throws Exception {
               recordsPrintStream.println("operation@Prepare,txID=" + transactionID + ",numberOfRecords=" + numberOfRecords +
                              ",extraData=" + encode(extraData) + ", xid=" + toXid(extraData));
            }

            @Override
            public void onReadDeleteRecordTX(final long transactionID, final RecordInfo recordInfo) throws Exception {
               recordsPrintStream.println("operation@DeleteRecordTX;txID=" + transactionID + "," + describeRecord(recordInfo, safe));
            }

            @Override
            public void onReadDeleteRecord(final long recordID) throws Exception {
               recordsPrintStream.println("operation@DeleteRecord;recordID=" + recordID);
            }

            @Override
            public void onReadCommitRecord(final long transactionID, final int numberOfRecords) throws Exception {
               recordsPrintStream.println("operation@Commit;txID=" + transactionID + ",numberOfRecords=" + numberOfRecords);
            }

            @Override
            public void onReadAddRecordTX(final long transactionID, final RecordInfo recordInfo) throws Exception {
               recordsPrintStream.println("operation@AddRecordTX;txID=" + transactionID + "," + describeRecord(recordInfo, safe));
            }

            @Override
            public void onReadAddRecord(final RecordInfo recordInfo) throws Exception {
               recordsPrintStream.println("operation@AddRecord;" + describeRecord(recordInfo, safe));
            }

            @Override
            public void markAsDataFile(final JournalFile file1) {
            }

            public void checkRecordCounter(RecordInfo info) {
               if (info.getUserRecordType() == JournalRecordIds.PAGE_CURSOR_COUNTER_VALUE) {
                  PageCountRecord encoding = (PageCountRecord) newObjectEncoding(info);
                  long queueIDForCounter = encoding.getQueueID();

                  PageSubscriptionCounterImpl subsCounter = lookupCounter(counters, queueIDForCounter);

                  if (subsCounter.getValue() != 0 && subsCounter.getValue() != encoding.getValue()) {
                     recordsPrintStream.println("####### Counter replace wrongly on queue " + queueIDForCounter + " oldValue=" + subsCounter.getValue() + " newValue=" + encoding.getValue());
                  }

                  subsCounter.loadValue(info.id, encoding.getValue(), encoding.getPersistentSize());
                  subsCounter.processReload();
                  recordsPrintStream.print("#Counter queue " + queueIDForCounter + " value=" + subsCounter.getValue() + " persistentSize=" + subsCounter.getPersistentSize() + ", result=" + subsCounter.getValue());
                  if (subsCounter.getValue() < 0) {
                     recordsPrintStream.println(" #NegativeCounter!!!!");
                  } else {
                     recordsPrintStream.println();
                  }
                  recordsPrintStream.println();
               } else if (info.getUserRecordType() == JournalRecordIds.PAGE_CURSOR_COUNTER_INC) {
                  PageCountRecordInc encoding = (PageCountRecordInc) newObjectEncoding(info);
                  long queueIDForCounter = encoding.getQueueID();

                  PageSubscriptionCounterImpl subsCounter = lookupCounter(counters, queueIDForCounter);

                  subsCounter.loadInc(info.id, encoding.getValue(), encoding.getPersistentSize());
                  subsCounter.processReload();
                  recordsPrintStream.print("#Counter queue " + queueIDForCounter + " value=" + subsCounter.getValue() + " persistentSize=" + subsCounter.getPersistentSize() + " increased by " + encoding.getValue());
                  if (subsCounter.getValue() < 0) {
                     recordsPrintStream.println(" #NegativeCounter!!!!");
                  } else {
                     recordsPrintStream.println();
                  }
                  recordsPrintStream.println();
               }
            }
         }, null, reclaimed, null);
      }

      recordsPrintStream.println();

      if (counters.size() != 0) {
         recordsPrintStream.println("#Counters during initial load:");
         printCounters(recordsPrintStream, counters);
      }

      return printSurvivingRecords(journal, survivingPrintStrea, safe);
   }

   public static DescribeJournal printSurvivingRecords(Journal journal,
                                                       PrintStream out,
                                                       boolean safe) throws Exception {

      final Map<Long, PageSubscriptionCounterImpl> counters = new HashMap<>();
      out.println("### Surviving Records Summary ###");

      List<RecordInfo> records = new LinkedList<>();
      List<PreparedTransactionInfo> preparedTransactions = new LinkedList<>();
      List<PersistentQueueBindingEncoding> bindings = null;

      journal.start();

      final StringBuilder bufferFailingTransactions = new StringBuilder();

      final class Count {

         int value;

         Count(int v) {
            value = v;
         }

         @Override
         public String toString() {
            return Integer.toString(value);
         }

         @Override
         public boolean equals(Object obj) {
            if (this == obj) {
               return true;
            }
            if (!(obj instanceof Count other)) {
               return false;
            }

            return value == other.value;
         }

         @Override
         public int hashCode() {
            return Integer.hashCode(value);
         }
      }
      long messageCount = 0;
      long largeMessageCount = 0;
      Map<Long, Count> messageRefCounts = new HashMap<>();
      long preparedMessageCount = 0;
      long preparedLargeMessageCount = 0;
      Map<Long, Count> preparedMessageRefCount = new HashMap<>();
      journal.load(records, preparedTransactions, (transactionID, records1, recordsToDelete) -> {
         bufferFailingTransactions.append("Transaction " + transactionID + " failed with these records:\n");
         for (RecordInfo info : records1) {
            bufferFailingTransactions.append("- " + describeRecord(info, safe) + "\n");
         }

         for (RecordInfo info : recordsToDelete) {
            bufferFailingTransactions.append("- " + describeRecord(info, safe) + " <marked to delete>\n");
         }

      }, false);

      for (RecordInfo info : records) {
         PageSubscriptionCounterImpl subsCounter = null;
         long queueIDForCounter = 0;

         Object o = newObjectEncoding(info);
         final byte userRecordType = info.getUserRecordType();
         if (userRecordType == ADD_MESSAGE || userRecordType == ADD_MESSAGE_PROTOCOL) {
            messageCount++;
         } else if (userRecordType == ADD_LARGE_MESSAGE) {
            largeMessageCount++;
         } else if (userRecordType == JournalRecordIds.ADD_REF) {
            ReferenceDescribe ref = (ReferenceDescribe) o;
            Count count = messageRefCounts.get(ref.refEncoding.queueID);
            if (count == null) {
               count = new Count(0);
               messageRefCounts.put(ref.refEncoding.queueID, count);
            }
            count.value++;
         } else if (userRecordType == JournalRecordIds.ACKNOWLEDGE_REF) {
            AckDescribe ref = (AckDescribe) o;
            Count count = messageRefCounts.get(ref.refEncoding.queueID);
            if (count == null) {
               messageRefCounts.put(ref.refEncoding.queueID, new Count(0));
            } else {
               count.value--;
            }
         } else if (userRecordType == JournalRecordIds.PAGE_CURSOR_COUNTER_VALUE) {
            PageCountRecord encoding = (PageCountRecord) o;
            queueIDForCounter = encoding.getQueueID();

            subsCounter = lookupCounter(counters, queueIDForCounter);

            subsCounter.loadValue(info.id, encoding.getValue(), encoding.getPersistentSize());
            subsCounter.processReload();
         } else if (userRecordType == JournalRecordIds.PAGE_CURSOR_COUNTER_INC) {
            PageCountRecordInc encoding = (PageCountRecordInc) o;
            queueIDForCounter = encoding.getQueueID();

            subsCounter = lookupCounter(counters, queueIDForCounter);

            subsCounter.loadInc(info.id, encoding.getValue(), encoding.getPersistentSize());
            subsCounter.processReload();
         } else if (userRecordType == QUEUE_BINDING_RECORD) {
            PersistentQueueBindingEncoding bindingEncoding = (PersistentQueueBindingEncoding) DescribeJournal.newObjectEncoding(info, null);
            if (bindings == null) {
               bindings = new LinkedList<>();
            }
            bindings.add(bindingEncoding);

         }

         out.println(describeRecord(info, o, safe));

         if (subsCounter != null) {
            out.println("##SubsCounter for queue=" + queueIDForCounter + ", value=" + subsCounter.getValue());
            out.println();
         }
      }

      if (!counters.isEmpty()) {
         out.println("### Page Counters");
         printCounters(out, counters);
      }

      out.println();
      out.println("### Prepared TX ###");

      for (PreparedTransactionInfo tx : preparedTransactions) {
         out.println(tx.getId());
         for (RecordInfo info : tx.getRecords()) {
            Object o = newObjectEncoding(info);
            out.println("- " + describeRecord(info, o, safe));
            final byte userRecordType = info.getUserRecordType();
            if (userRecordType == ADD_MESSAGE || userRecordType == ADD_MESSAGE_PROTOCOL) {
               preparedMessageCount++;
            } else if (userRecordType == ADD_LARGE_MESSAGE) {
               preparedLargeMessageCount++;
            } else if (userRecordType == ADD_REF) {
               ReferenceDescribe ref = (ReferenceDescribe) o;
               Count count = preparedMessageRefCount.get(ref.refEncoding.queueID);
               if (count == null) {
                  count = new Count(0);
                  preparedMessageRefCount.put(ref.refEncoding.queueID, count);
               }
               count.value++;
            }
         }

         for (RecordInfo info : tx.getRecordsToDelete()) {
            out.println("- " + describeRecord(info, safe) + " <marked to delete>");
         }
      }

      String missingTX = bufferFailingTransactions.toString();

      if (!missingTX.isEmpty()) {
         out.println();
         out.println("### Failed Transactions (Missing commit/prepare/rollback record) ###");
      }

      out.println(bufferFailingTransactions.toString());

      out.println("### Message Counts ###");
      out.println("message count=" + messageCount);
      out.println("large message count=" + largeMessageCount);
      out.println("message reference count");
      messageRefCounts.forEach((queueId, count) -> {
         out.println("queue id " + queueId + ",count=" + count);
      });
      out.println("prepared message count=" + preparedMessageCount);
      out.println("prepared large message count=" + preparedLargeMessageCount);
      out.println("prepared message reference count");
      preparedMessageRefCount.forEach((queueId, count) -> {
         out.println("queue id " + queueId + ",count=" + count);
      });

      journal.stop();

      return new DescribeJournal(records, preparedTransactions).setBindingEncodings(bindings);
   }

   protected static void printCounters(final PrintStream out, final Map<Long, PageSubscriptionCounterImpl> counters) {
      for (Map.Entry<Long, PageSubscriptionCounterImpl> entry : counters.entrySet()) {
         out.println("Queue " + entry.getKey() + " value=" + entry.getValue().getValue());
      }
   }

   protected static PageSubscriptionCounterImpl lookupCounter(Map<Long, PageSubscriptionCounterImpl> counters,
                                                              long queueIDForCounter) {
      PageSubscriptionCounterImpl subsCounter;
      subsCounter = counters.get(queueIDForCounter);
      if (subsCounter == null) {
         subsCounter = new PageSubscriptionCounterImpl(null, -1);
         counters.put(queueIDForCounter, subsCounter);
      }
      return subsCounter;
   }

   private static boolean isSafe(Object obj) {
      // these classes will have user's data and not considered safe
      return !(obj instanceof PersistentAddressBindingEncoding ||
              obj instanceof MessageDescribe ||
              obj instanceof PersistentQueueBindingEncoding);
   }

   private static String toString(Object obj, boolean safe) {
      if (obj == null) {
         return "** null **";
      }
      if (safe && !isSafe(obj)) {
         if (obj instanceof MessageDescribe messageDescribe) {
            try {
               return messageDescribe.getMsg().getClass().getSimpleName() + "(safe data, size=" + messageDescribe.getMsg().getPersistentSize() + ")";
            } catch (Throwable e) {
               e.printStackTrace();
               return messageDescribe.getMsg().getClass().getSimpleName() + "(safe data)";
            }
         } else {
            return obj.getClass().getSimpleName() + "(safe data)";
         }
      } else {
         return obj.toString();
      }
   }

   private static String describeRecord(RecordInfo info, boolean safe) {
      return "recordID=" + info.id + ";userRecordType=" + info.userRecordType + ";isUpdate=" + info.isUpdate + ";compactCount=" + info.compactCount + ";" + toString(newObjectEncoding(info), safe);
   }

   private static String describeRecord(RecordInfo info, Object o, boolean safe) {
      return "recordID=" + info.id + ";userRecordType=" + info.userRecordType + ";isUpdate=" + info.isUpdate + ";compactCount=" + info.compactCount + ";" + toString(o, safe);
   }

   private static String encode(final byte[] data) {
      return Base64.encodeBytes(data, true);
   }

   private static Xid toXid(final byte[] data) {
      try {
         return XidCodecSupport.decodeXid(ActiveMQBuffers.wrappedBuffer(data));
      } catch (Exception e) {
         return null;
      }
   }

   public static Object newObjectEncoding(RecordInfo info) {
      return newObjectEncoding(info, null);
   }

   public static Object newObjectEncoding(RecordInfo info, JournalStorageManager storageManager) {
      ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(info.data);
      long id = info.id;
      int rec = info.getUserRecordType();

      switch (rec) {
         case DIVERT_RECORD:
            return AbstractJournalStorageManager.newPersistedConfigurationEncoding(PersistedDivertConfiguration.class, id, buffer);

         case BRIDGE_RECORD:
            return AbstractJournalStorageManager.newPersistedConfigurationEncoding(PersistedBridgeConfiguration.class, id, buffer);

         case ADD_LARGE_MESSAGE_PENDING: {
            PendingLargeMessageEncoding lmEncoding = new PendingLargeMessageEncoding();
            lmEncoding.decode(buffer);

            return lmEncoding;
         }
         case ADD_LARGE_MESSAGE: {

            LargeServerMessage largeMessage = new LargeServerMessageImpl(storageManager);

            LargeMessagePersister.getInstance().decode(buffer, largeMessage, null);

            return new MessageDescribe(largeMessage.toMessage());
         }
         case ADD_MESSAGE: {
            return "ADD-MESSAGE is not supported any longer, use export/import";
         }
         case ADD_MESSAGE_PROTOCOL: {
            Message message = MessagePersister.getInstance().decode(buffer, null, null, storageManager);
            return new MessageDescribe(message);
         }
         case ADD_REF: {
            final RefEncoding encoding = new RefEncoding();
            encoding.decode(buffer);
            return new ReferenceDescribe(encoding);
         }

         case ACKNOWLEDGE_REF: {
            final RefEncoding encoding = new RefEncoding();
            encoding.decode(buffer);
            return new AckDescribe(encoding);
         }

         case UPDATE_DELIVERY_COUNT: {
            DeliveryCountUpdateEncoding updateDeliveryCount = new DeliveryCountUpdateEncoding();
            updateDeliveryCount.decode(buffer);
            return updateDeliveryCount;
         }

         case PAGE_TRANSACTION: {
            if (info.isUpdate) {
               PageUpdateTXEncoding pageUpdate = new PageUpdateTXEncoding();

               pageUpdate.decode(buffer);

               return pageUpdate;
            } else {
               PageTransactionInfoImpl pageTransactionInfo = new PageTransactionInfoImpl();

               pageTransactionInfo.decode(buffer);

               pageTransactionInfo.setRecordID(info.id);

               return pageTransactionInfo;
            }
         }

         case SET_SCHEDULED_DELIVERY_TIME: {
            ScheduledDeliveryEncoding encoding = new ScheduledDeliveryEncoding();

            encoding.decode(buffer);

            return encoding;
         }
         case DUPLICATE_ID: {
            DuplicateIDEncoding encoding = new DuplicateIDEncoding();

            encoding.decode(buffer);

            return encoding;
         }
         case HEURISTIC_COMPLETION: {
            HeuristicCompletionEncoding encoding = new HeuristicCompletionEncoding();

            encoding.decode(buffer);

            return encoding;
         }
         case ACKNOWLEDGE_CURSOR: {
            CursorAckRecordEncoding encoding = new CursorAckRecordEncoding();

            encoding.decode(buffer);

            return encoding;
         }
         case PAGE_CURSOR_COUNTER_VALUE: {
            PageCountRecord encoding = new PageCountRecord();

            encoding.decode(buffer);

            return encoding;
         }

         case PAGE_CURSOR_COMPLETE: {
            CursorAckRecordEncoding encoding = new PageCompleteCursorAckRecordEncoding();

            encoding.decode(buffer);

            return encoding;
         }

         case PAGE_CURSOR_COUNTER_INC: {
            PageCountRecordInc encoding = new PageCountRecordInc();

            encoding.decode(buffer);

            return encoding;
         }

         case PAGE_CURSOR_PENDING_COUNTER: {
            PageCountPendingImpl encoding = new PageCountPendingImpl();
            encoding.decode(buffer);
            encoding.setID(info.id);
            return encoding;
         }

         case QUEUE_STATUS_RECORD:
            return AbstractJournalStorageManager.newQueueStatusEncoding(id, buffer);

         case QUEUE_BINDING_RECORD:
            return AbstractJournalStorageManager.newQueueBindingEncoding(id, buffer);

         case ID_COUNTER_RECORD:
            EncodingSupport idReturn = new IDCounterEncoding();
            idReturn.decode(buffer);

            return idReturn;

         case JournalRecordIds.GROUP_RECORD:
            return AbstractJournalStorageManager.newGroupEncoding(id, buffer);

         case ADDRESS_SETTING_RECORD:
            return AbstractJournalStorageManager.newPersistedConfigurationEncoding(PersistedAddressSetting.class, id, buffer);

         case ADDRESS_SETTING_RECORD_JSON:
            return AbstractJournalStorageManager.newPersistedConfigurationEncoding(PersistedAddressSettingJSON.class, id, buffer);

         case SECURITY_SETTING_RECORD:
            return AbstractJournalStorageManager.newPersistedConfigurationEncoding(PersistedSecuritySetting.class, id, buffer);

         case ADDRESS_BINDING_RECORD:
            return AbstractJournalStorageManager.newAddressBindingEncoding(id, buffer);

         case ADDRESS_STATUS_RECORD:
            return AbstractJournalStorageManager.newAddressStatusEncoding(id, buffer);

         case USER_RECORD:
            return AbstractJournalStorageManager.newPersistedConfigurationEncoding(PersistedUser.class, id, buffer);

         case ROLE_RECORD:
            return AbstractJournalStorageManager.newPersistedConfigurationEncoding(PersistedRole.class, id, buffer);

         case ACK_RETRY:
            return AckRetry.getPersister().decode(buffer, null, null);

         default:
            return null;
      }
   }

   private static final class PageCompleteCursorAckRecordEncoding extends CursorAckRecordEncoding {

      @Override
      public String toString() {
         return "PGComplete [queueID=" + queueID + ", position=" + position + "]";
      }
   }

   public static final class MessageDescribe {

      public MessageDescribe(Message msg) {
         this.msg = msg;
      }

      Message msg;

      @Override
      public String toString() {
         StringBuilder sb = new StringBuilder();
         sb.append(msg.isLargeMessage() ? "LargeMessage(" : "Message(");
         sb.append("messageID=" + msg.getMessageID());
         if (msg.getUserID() != null) {
            sb.append(";userMessageID=" + msg.getUserID().toString());
         }

         sb.append(";msg=" + msg.toString());

         return sb.toString();
      }

      public Message getMsg() {
         return msg;
      }

   }

   public static final class ReferenceDescribe {

      public RefEncoding refEncoding;

      public ReferenceDescribe(RefEncoding refEncoding) {
         this.refEncoding = refEncoding;
      }

      @Override
      public String toString() {
         return "AddRef;" + refEncoding;
      }

      @Override
      public int hashCode() {
         return Objects.hashCode(refEncoding);
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         }
         if (obj == null) {
            return false;
         }
         if (!(obj instanceof ReferenceDescribe other)) {
            return false;
         }

         return Objects.equals(refEncoding, other.refEncoding);
      }
   }

}
