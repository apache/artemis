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
import java.io.PrintStream;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.persistence.impl.database.DatabaseStorageManager;
import org.apache.activemq.artemis.utils.TableOut;
import org.apache.activemq.artemis.utils.XidCodecSupport;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.data.MessageDataReferenceMerged;
import org.apache.artemis.database.data.PrepareTXData;
import org.apache.artemis.database.queries.AddressJDBCQuery;
import org.apache.artemis.database.queries.GenericDataJDBCQuery;
import org.apache.artemis.database.queries.MessagesAndReferencesDBQuery;
import org.apache.artemis.database.queries.PreparedTXQuery;
import org.apache.artemis.database.queries.QueueJDBCQuery;

public class DescribeNewDatabase {

   public static void describeDatabase(DatabaseStorageConfiguration dbConfig, PrintStream out, boolean safe) throws Exception {
      DatabaseProvider databaseProvider = dbConfig.getDatabaseProvider();
      try (Connection connection = databaseProvider.getConnection()) {


         printSection(out, "A D D R E S S E S", () -> printAddresses(databaseProvider, connection, out));

         printSection(out, "Q U E U E S", () -> printQueues(databaseProvider, connection, out));

         printPreparedTransactions(databaseProvider, connection, out);

         printSection(out, "C O N F I G   D A T A", () -> printGenericData(databaseProvider, connection, out, true));

         printSection(out, "B R O K E R   D A T A", () -> printGenericData(databaseProvider, connection, out, false));

         printSection(out, "M E S S A G E S", () -> printMessages(databaseProvider, connection, out, safe));
      }
   }

   private static void printSection(PrintStream out, String title, ThrowableRunnable runnable) {
      printBanner(out, title);
      try {
         runnable.run();
      } catch (Exception e) {
         out.println("WARNING: Could not read table: " + e.getMessage());
         e.printStackTrace(out);
         out.println();

      }
   }

   @FunctionalInterface
   private interface ThrowableRunnable {
      void run() throws Exception;
   }

   private static void printAddresses(DatabaseProvider databaseProvider, Connection connection, PrintStream out) throws Exception {
      int[] columnSizes = {10, 50, 10, 10};
      TableOut tableOut = new TableOut("|", 2, columnSizes);
      tableOut.printTopSeparator(out);
      tableOut.print(out, new String[]{"ID", "Name", "Multicast", "Anycast"});
      tableOut.printSeparator(out);

      AtomicInteger count = new AtomicInteger();
      AddressJDBCQuery query = new AddressJDBCQuery(databaseProvider, connection);
      query.query(data -> {
         tableOut.print(out, new String[]{String.valueOf(data.id), data.address, data.isMulticast ? "Y" : "N", data.isAnycast ? "Y" : "N"});
         count.incrementAndGet();
      });
      tableOut.printBottomSeparator(out);
      out.println("Total addresses: " + count.get());
      out.println();
   }

   private static void printQueues(DatabaseProvider databaseProvider, Connection connection, PrintStream out) throws Exception {
      int[] columnSizes = {10, 10, 30, 15, 20, 90};
      TableOut tableOut = new TableOut("|", 2, columnSizes);
      tableOut.printTopSeparator(out);
      tableOut.print(out, new String[]{"ID", "Address", "Name", "Type", "Filter", "Config"});
      tableOut.printSeparator(out);

      AtomicInteger count = new AtomicInteger();
      QueueJDBCQuery query = new QueueJDBCQuery(databaseProvider, connection);
      query.query(data -> {
         String filter = data.filter != null ? data.filter : "";
         String config = data.queueConfigJson != null ? data.queueConfigJson : "";
         tableOut.print(out, new String[]{
            String.valueOf(data.id),
            String.valueOf(data.addressId),
            data.name,
            data.getQueueTypes(),
            filter,
            config
         });
         count.incrementAndGet();
      });
      tableOut.printBottomSeparator(out);
      out.println("Total queues: " + count.get());
      out.println();
   }

   private static void printMessages(DatabaseProvider databaseProvider, Connection connection, PrintStream out, boolean safe) throws Exception {
      int[] columnSizes = {10, 10, 15, 130};
      TableOut tableOut = new TableOut("|", 2, columnSizes);
      tableOut.printTopSeparator(out);
      @SuppressWarnings("unchecked")
      java.util.List<String>[] header = new java.util.List[columnSizes.length];
      header[0] = java.util.List.of("ID");
      header[1] = java.util.List.of("Memory", "Estimate");
      header[2] = java.util.List.of("Queues", "* = Paged");
      header[3] = java.util.List.of(safe ? "Size" : "Message");
      tableOut.print(out, header);
      tableOut.printSeparator(out);

      AtomicInteger count = new AtomicInteger();
      MessagesAndReferencesDBQuery query = new MessagesAndReferencesDBQuery(databaseProvider, connection, "");
      query.query(data -> {
         String idStr = data.tx() != null ? data.messageID() + "(tx=" + data.tx() + ")" : String.valueOf(data.messageID());
         String memEstStr = String.valueOf(data.memoryEstimate());
         String queuesStr = formatQueues(data);
         if (safe) {
            int size = data.messageBufferSupplier() != null ? data.messageBufferSupplier().get().readableBytes() : 0;
            tableOut.print(out, new String[]{idStr, memEstStr, queuesStr, size + " bytes"});
         } else {
            try {
               Message message = DatabaseStorageManager.decodeMessage(data.messageID(), data.isLarge(), data.isCore(), data.messageBufferSupplier(), data.messageBufferSupplier(), null);
               tableOut.print(out, new String[]{idStr, memEstStr, queuesStr, String.valueOf(message)});
            } catch (Exception e) {
               tableOut.print(out, new String[]{idStr, memEstStr, queuesStr, "ERROR decoding: " + e.getMessage()});
            }
         }
         count.incrementAndGet();
      });
      tableOut.printBottomSeparator(out);
      out.println("Total messages: " + count.get());
      out.println();
   }

   private static String formatQueues(MessageDataReferenceMerged data) {
      if (data.queues().isEmpty()) {
         return "";
      }
      StringBuilder sb = new StringBuilder();
      for (MessageDataReferenceMerged.QueueRef ref : data.queues()) {
         if (sb.length() > 0) {
            sb.append(", ");
         }
         sb.append(ref.toString());
      }
      return sb.toString();
   }

   private static void printPreparedTransactions(DatabaseProvider databaseProvider, Connection connection, PrintStream out) throws Exception {
      List<PrepareTXData> preparedList = new ArrayList<>();
      try {
         PreparedTXQuery txQuery = new PreparedTXQuery(databaseProvider, connection);
         txQuery.query(preparedList::add);
      } catch (Exception e) {
         out.println("WARNING: Could not read prepared transactions: " + e.getMessage());
         return;
      }

      if (preparedList.isEmpty()) {
         return;
      }

      printBanner(out, "P R E P A R E D   T R A N S A C T I O N S");
      int[] columnSizes = {15, 150};
      TableOut tableOut = new TableOut("|", 2, columnSizes);
      tableOut.printTopSeparator(out);
      tableOut.print(out, new String[]{"TX ID", "XID"});
      tableOut.printSeparator(out);

      for (PrepareTXData data : preparedList) {
         Xid xid = XidCodecSupport.decodeXid(data.xid);
         tableOut.print(out, new String[]{String.valueOf(data.getTx().getId()), xid.toString()});
      }
      tableOut.printBottomSeparator(out);
      out.println("Total prepared transactions: " + preparedList.size());
      out.println();
   }

   private static void printGenericData(DatabaseProvider databaseProvider, Connection connection, PrintStream out, boolean bindings) throws Exception {
      String tableName = bindings ? databaseProvider.getSqlProvider().getConfigData() : databaseProvider.getSqlProvider().getBrokerData();

      int[] columnSizes = {10, 26, 130};
      TableOut tableOut = new TableOut("|", 2, columnSizes);
      tableOut.printTopSeparator(out);
      tableOut.print(out, new String[]{"ID", "Record Type", "Decoded"});
      tableOut.printSeparator(out);

      AtomicInteger count = new AtomicInteger();
      GenericDataJDBCQuery query = new GenericDataJDBCQuery(databaseProvider, connection, tableName, bindings, "");
      query.query(data -> {
         Object decoded = DatabaseStorageManager.describeGenericData(data.id, data.recordType, data.dataSupplier != null ? data.dataSupplier.get() : null);

         String idStr = data.getTx() != null ? data.id + "(tx=" + data.getTx().getId() + ")" : String.valueOf(data.id);
         String decodedStr = decoded != null ? decoded.toString() : "UNKNOWN(type=" + data.recordType + ")";
         String typeStr = data.recordType + " (" + JournalRecordIds.recordTypeName(data.recordType) + ")";
         tableOut.print(out, new String[]{idStr, typeStr, decodedStr});
         count.incrementAndGet();
      });
      tableOut.printBottomSeparator(out);
      out.println("Total generic data records: " + count.get());
      out.println();
   }

   private static void printBanner(PrintStream out, String title) {
      out.println();
      out.println("********************************************");
      out.println(title);
      out.println("********************************************");
   }
}
