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
package org.apache.activemq.artemis.cli.commands.tools;

import java.io.File;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.cli.Artemis;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.journal.RecordInfo;
import org.apache.activemq.artemis.core.message.impl.CoreMessagePersister;
import org.apache.activemq.artemis.core.paging.PagedMessage;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.PagingStoreFactory;
import org.apache.activemq.artemis.core.paging.cursor.PagePosition;
import org.apache.activemq.artemis.core.paging.cursor.impl.PagePositionImpl;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.paging.impl.PageTransactionInfoImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingManagerImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreFactoryNIO;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.DescribeJournal;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.CursorAckRecordEncoding;
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.PageUpdateTXEncoding;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.server.impl.FileLockNodeManager;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.spi.core.protocol.MessagePersister;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.TableOut;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.apache.activemq.artemis.utils.collections.LinkedList;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "print", description = "Print data records information. WARNING: don't use while a production server is running.")
public class PrintData extends DBOption {

   @Option(names = "--safe", description = "Print your data structure without showing your data.")
   private boolean safe = false;

   @Option(names = "--reclaimed", description = "Try to print as many records as possible from reclaimed files.")
   private boolean reclaimed = false;

   @Option(names = "--max-pages", description = "Maximum number of pages to read. Default: unlimited (-1).")
   private int maxPages = -1;

   @Option(names = "--skip-bindings", description = "Do not print data from the bindings journal.")
   private boolean skipBindings = false;

   @Option(names = "--skip-journal", description = "Do not print data from the messages journal.")
   private boolean skipJournal = false;

   @Option(names = "--ascii", description = "Use plain-text (ASCII) output format instead of Unicode box-drawing characters (useful if parsing output with scripts).")
   private boolean ascii = false;

   private static final String BINDINGS_BANNER = "B I N D I N G S  J O U R N A L";
   private static final String MESSAGES_BANNER = "M E S S A G E S   J O U R N A L";
   static {
      MessagePersister.registerPersister(CoreMessagePersister.getInstance());
   }

   public boolean isAscii() {
      return ascii;
   }

   public PrintData setAscii(boolean ascii) {
      this.ascii = ascii;
      return this;
   }

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      Configuration configuration = getParameterConfiguration();

      try {
         if (configuration.isJDBC()) {
            printDataJDBC(configuration, context.out);
         } else {
            printData(new File(getBinding()), new File(getJournal()), new File(getPaging()), context.out, safe, reclaimed, skipBindings, skipJournal, maxPages, ascii);
         }
      } catch (Exception e) {
         treatError(e, "data", "print");
      } finally {
         done();
      }
      return null;
   }


   public void printDataJDBC(Configuration configuration, PrintStream out) throws Exception {
      initializeJournal(configuration);

      Artemis.printBanner(out);

      printBanner(out, BINDINGS_BANNER);

      DescribeJournal bindings = DescribeJournal.printSurvivingRecords(storageManager.getBindingsJournal(), out, safe, ascii);

      printBanner(out, MESSAGES_BANNER);

      DescribeJournal describeJournal = DescribeJournal.printSurvivingRecords(storageManager.getMessageJournal(), out, safe, ascii);

      printPages(describeJournal, storageManager, pagingmanager, out, safe, maxPages, bindings, ascii);

      cleanup();

   }
   public static void printData(File bindingsDirectory, File messagesDirectory, File pagingDirectory) throws Exception {
      printData(bindingsDirectory, messagesDirectory, pagingDirectory, false);
   }

   public static void printData(File bindingsDirectory, File messagesDirectory, File pagingDirectory, boolean secret) throws Exception {
      printData(bindingsDirectory, messagesDirectory, pagingDirectory, System.out, secret, false, false, false, -1);
   }

   public static void printData(File bindingsDirectory, File messagesDirectory, File pagingDirectory, PrintStream out, boolean secret) throws Exception {
      printData(bindingsDirectory, messagesDirectory, pagingDirectory, out, secret, false, false, false, -1);
   }

   public static void printData(File bindingsDirectory, File messagesDirectory, File pagingDirectory, PrintStream out, boolean safe, boolean reclaimed) throws Exception {
      printData(bindingsDirectory, messagesDirectory, pagingDirectory, out, safe, reclaimed, false, false, -1);

   }

   public static void printData(File bindingsDirectory, File messagesDirectory, File pagingDirectory, PrintStream out, boolean safe, boolean reclaimed, boolean skipBindings, boolean skipJournal, int maxPages) throws Exception {
      printData(bindingsDirectory, messagesDirectory, pagingDirectory, out, safe, reclaimed, skipBindings, skipJournal, maxPages, false);
   }

   public static void printData(File bindingsDirectory, File messagesDirectory, File pagingDirectory, PrintStream out, boolean safe, boolean reclaimed, boolean skipBindings, boolean skipJournal, int maxPages, boolean ascii) throws Exception {
      // printing the banner and version
      Artemis.printBanner(out);

      File serverLockFile = new File(messagesDirectory, "server.lock");

      if (serverLockFile.isFile()) {
         try {
            FileLockNodeManager fileLock = new FileLockNodeManager(messagesDirectory, false);
            fileLock.start();
            printBanner(out, "Server's ID=" + fileLock.getNodeId().toString());
            fileLock.stop();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      printBanner(out, BINDINGS_BANNER);
      DescribeJournal bindingsDescribe;
      if (skipBindings) {
         out.println(".... skipping");
         out.println();
         bindingsDescribe = null;
      } else {
         bindingsDescribe = printBindings(bindingsDirectory, out, safe, true, true, reclaimed, ascii);
      }

      printBanner(out, MESSAGES_BANNER);
      if (skipJournal) {
         out.println(".... skipping");
         out.println();
      }
      DescribeJournal describeJournal = null;
      describeJournal = printMessages(messagesDirectory, out, safe, !skipJournal, !skipJournal, reclaimed, ascii);

      if (describeJournal == null) {
         return;
      }

      try {
         printBanner(out, "P A G I N G");
         if (maxPages == 0) {
            out.println(".... skipping");
            out.println();
         } else {
            printPages(pagingDirectory, describeJournal, out, safe, maxPages, bindingsDescribe, ascii);
         }
      } catch (Exception e) {
         e.printStackTrace();
         return;
      }

   }

   public static DescribeJournal printMessages(File messagesDirectory, PrintStream out, boolean safe, boolean printRecords, boolean printSurviving, boolean reclaimed) {
      return printMessages(messagesDirectory, out, safe, printRecords, printSurviving, reclaimed, false);
   }

   public static DescribeJournal printMessages(File messagesDirectory, PrintStream out, boolean safe, boolean printRecords, boolean printSurviving, boolean reclaimed, boolean ascii) {
      DescribeJournal describeJournal;
      try {
         describeJournal = DescribeJournal.describeMessagesJournal(messagesDirectory, out, safe, printRecords, printSurviving, reclaimed, ascii);
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
      return describeJournal;
   }

   public static DescribeJournal printBindings(File bindingsDirectory, PrintStream out, boolean safe, boolean printRecords, boolean printSurviving, boolean reclaimed) {
      return printBindings(bindingsDirectory, out, safe, printRecords, printSurviving, reclaimed, false);
   }

   public static DescribeJournal printBindings(File bindingsDirectory, PrintStream out, boolean safe, boolean printRecords, boolean printSurviving, boolean reclaimed, boolean ascii) {
      try {
         return DescribeJournal.describeBindingsJournal(bindingsDirectory, out, safe, printRecords, printSurviving, reclaimed, ascii);
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   protected static void printBanner(PrintStream out, String x2) {
      out.println();
      out.println("********************************************");
      out.println(x2);
      out.println("********************************************");
   }

   private static void printPages(File pageDirectory, DescribeJournal describeJournal, PrintStream out, boolean safe, int maxPages, DescribeJournal bindingsDescribe, boolean ascii) {
      ActiveMQThreadFactory daemonFactory = new ActiveMQThreadFactory("cli", true, PrintData.class.getClassLoader());
      final ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(1, daemonFactory);
      final ExecutorService executor = Executors.newFixedThreadPool(10, daemonFactory);
      ExecutorFactory execfactory = () -> ArtemisExecutor.delegate(executor);
      try {

         final StorageManager sm = new NullStorageManager();
         PagingStoreFactory pageStoreFactory = new PagingStoreFactoryNIO(sm, pageDirectory, 1000L, scheduled, execfactory, false, null);
         HierarchicalRepository<AddressSettings> addressSettingsRepository = new HierarchicalObjectRepository<>();
         addressSettingsRepository.setDefault(new AddressSettings());
         PagingManager manager = new PagingManagerImpl(pageStoreFactory, addressSettingsRepository);

         printPages(describeJournal, sm, manager, out, safe, maxPages, bindingsDescribe, ascii);
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         executor.shutdownNow();
         scheduled.shutdownNow();
      }
   }

   private static void printPages(DescribeJournal describeJournal,
                                  StorageManager sm,
                                  PagingManager manager,
                                  PrintStream out,
                                  boolean safe, int maxPages,
                                  DescribeJournal bindingsDescribe,
                                  boolean ascii) throws Exception {
      PageCursorsInfo cursorACKs = calculateCursorsInfo(describeJournal.getRecords());

      Set<Long> existingQueues = new HashSet<>();
      if (bindingsDescribe != null && bindingsDescribe.getBindingEncodings() != null) {
         bindingsDescribe.getBindingEncodings().forEach(e -> existingQueues.add(e.getQueueConfiguration().getId()));
      }

      Set<Long> pgTXs = cursorACKs.getPgTXs();

      manager.start();

      SimpleString[] stores = manager.getStoreNames();

      int[] pageColumnSizes = {10, 10, 12, safe ? 10 : 100, 10};
      TableOut pageTable = ascii ? null : new TableOut("|", 2, pageColumnSizes);

      for (SimpleString store : stores) {
         PagingStore pgStore = manager.getPageStore(store);
         File folder = null;

         if (pgStore != null) {
            folder = pgStore.getFolder();
            out.println("####################################################################################################");
            out.println("Exploring store " + store + " folder = " + folder);
            long pgid = pgStore.getFirstPage();

            out.println("Number of pages ::" + pgStore.getNumberOfPages() + ", Current writing page ::" + pgStore.getCurrentWritingPage());

            for (int pg = 0; pg < pgStore.getNumberOfPages(); pg++) {
               if (maxPages >= 0 && pg > maxPages) {
                  out.println("******* Giving up at Page " + pgid + ", System has a total of " + pgStore.getNumberOfPages() + " pages");
                  break;
               }
               Page page = pgStore.newPageObject(pgid);
               while (!page.getFile().exists() && pgid < pgStore.getCurrentWritingPage()) {
                  pgid++;
                  page = pgStore.newPageObject(pgid);
               }
               if (ascii) {
                  out.println("*******   Page " + pgid);
               }
               page.open(false);
               LinkedList<PagedMessage> msgs = page.read(sm);
               page.close(false, false);

               int msgID = 0;

               if (pageTable != null) {
                  pageTable.printTopSeparator(out);
                  pageTable.print(out, new String[]{"Page", "Msg", "TX", safe ? "Size" : "Message", "Queues"});
                  pageTable.printSeparator(out);
               }
               try (LinkedListIterator<PagedMessage> iter = msgs.iterator()) {
                  while (iter.hasNext()) {
                     PagedMessage msg = iter.next();
                     msg.initMessage(sm);

                     long[] q = msg.getQueueIDs();
                     int ackCount = 0;

                     StringBuilder queuesStr = new StringBuilder();
                     for (int i = 0; i < q.length; i++) {
                        queuesStr.append(q[i]);

                        PagePosition posCheck = new PagePositionImpl(pgid, msgID);
                        boolean acked = false;

                        Set<PagePosition> positions = cursorACKs.getCursorRecords().get(q[i]);
                        if (positions != null) {
                           acked = positions.contains(posCheck);
                        }

                        if (acked) {
                           queuesStr.append(" (ACK)");
                        }

                        if (cursorACKs.getCompletePages(q[i]).contains(pgid)) {
                           acked = true;
                           queuesStr.append(" (PG-COMPLETE)");
                        }

                        if (!existingQueues.contains(q[i])) {
                           queuesStr.append(" (N/A)");
                           acked = true;
                        }

                        if (acked) {
                           ackCount++;
                        } else {
                           queuesStr.append(" (OK)");
                        }

                        if (i + 1 < q.length) {
                           queuesStr.append(", ");
                        }
                     }

                     if (msg.getTransactionID() >= 0 && !pgTXs.contains(msg.getTransactionID())) {
                        queuesStr.append(", **PG_TX_NOT_FOUND**");
                     }

                     if (ascii) {
                        if (safe) {
                           try {
                              out.print("pg=" + pgid + ", msg=" + msgID + ",pgTX=" + msg.getTransactionID() + ", msg=" + msg.getMessage().getClass().getSimpleName() + "(safe data, size=" + msg.getMessage().getPersistentSize() + ")");
                           } catch (Exception e) {
                              out.print("pg=" + pgid + ", msg=" + msgID + ",pgTX=" + msg.getTransactionID() + ", msg=" + msg.getMessage().getClass().getSimpleName() + "(safe data)");
                           }
                        } else {
                           out.print("pg=" + pgid + ", msg=" + msgID + ",pgTX=" + msg.getTransactionID() + ",userMessageID=" + Objects.requireNonNullElse(msg.getMessage().getUserID(), "") + ", msg=" + msg.getMessage());
                        }
                        out.print(",Queues = " + queuesStr);
                        out.println();

                        if (ackCount != q.length) {
                           out.println("^^^ Previous record has " + ackCount + " acked queues and " + q.length + " queues routed");
                           out.println();
                        }
                     } else {
                        String txStr = msg.getTransactionID() >= 0 ? String.valueOf(msg.getTransactionID()) : "";
                        String msgStr;
                        if (safe) {
                           try {
                              msgStr = msg.getMessage().getClass().getSimpleName() + "(size=" + msg.getMessage().getPersistentSize() + ")";
                           } catch (Exception e) {
                              msgStr = msg.getMessage().getClass().getSimpleName() + "(safe data)";
                           }
                        } else {
                           msgStr = String.valueOf(msg.getMessage());
                        }
                        pageTable.print(out, new String[]{String.valueOf(pgid), String.valueOf(msgID), txStr, msgStr, queuesStr.toString()});
                     }

                     msgID++;

                  }

                  pgid++;
               }

               if (pageTable != null) {
                  pageTable.printBottomSeparator(out);
               }

            }
         }
      }
   }

   /**
    * Calculate the acks on the page system
    */
   private static PageCursorsInfo calculateCursorsInfo(List<RecordInfo> records) throws Exception {

      PageCursorsInfo cursorInfo = new PageCursorsInfo();

      for (RecordInfo record : records) {
         byte[] data = record.data;

         ActiveMQBuffer buff = ActiveMQBuffers.wrappedBuffer(data);

         if (record.userRecordType == JournalRecordIds.ACKNOWLEDGE_CURSOR) {
            CursorAckRecordEncoding encoding = new CursorAckRecordEncoding();
            encoding.decode(buff);

            Set<PagePosition> set = cursorInfo.getCursorRecords().get(encoding.queueID);

            if (set == null) {
               set = new HashSet<>();
               cursorInfo.getCursorRecords().put(encoding.queueID, set);
            }

            set.add(encoding.position);
         } else if (record.userRecordType == JournalRecordIds.PAGE_CURSOR_COMPLETE) {
            CursorAckRecordEncoding encoding = new CursorAckRecordEncoding();
            encoding.decode(buff);

            Long queueID = encoding.queueID;
            Long pageNR = encoding.position.getPageNr();

            if (!cursorInfo.getCompletePages(queueID).add(pageNR)) {
               System.err.println("Page " + pageNR + " has been already set as complete on queue " + queueID);
            }
         } else if (record.userRecordType == JournalRecordIds.PAGE_TRANSACTION) {
            if (record.isUpdate) {
               PageUpdateTXEncoding pageUpdate = new PageUpdateTXEncoding();

               pageUpdate.decode(buff);
               cursorInfo.getPgTXs().add(pageUpdate.pageTX);
            } else {
               PageTransactionInfoImpl pageTransactionInfo = new PageTransactionInfoImpl();

               pageTransactionInfo.decode(buff);

               pageTransactionInfo.setRecordID(record.id);
               cursorInfo.getPgTXs().add(pageTransactionInfo.getTransactionID());
            }
         }
      }

      return cursorInfo;
   }

   private static class PageCursorsInfo {

      private final Map<Long, Set<PagePosition>> cursorRecords = new HashMap<>();

      private final Set<Long> pgTXs = new HashSet<>();

      private final Map<Long, Set<Long>> completePages = new HashMap<>();

      private PageCursorsInfo() {
      }

      Set<Long> getPgTXs() {
         return pgTXs;
      }

      Map<Long, Set<PagePosition>> getCursorRecords() {
         return cursorRecords;
      }

      Set<Long> getCompletePages(Long queueID) {
         Set<Long> completePagesSet = completePages.get(queueID);

         if (completePagesSet == null) {
            completePagesSet = new HashSet<>();
            completePages.put(queueID, completePagesSet);
         }

         return completePagesSet;
      }

   }

}
