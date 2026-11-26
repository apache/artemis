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

import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.util.List;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.tools.DBOption;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.server.JournalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "exp", description = "Export all message-data using an XML that could be interpreted by any system.")
public final class XmlDataExporter extends DBOption {

   @Option(names = "log-interval", description = "How often to print progress in the console. Set to <= 0 to disable it.")
   private int logInterval = 10_000;

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private Throwable lastError;

   @Option(names = "undefined-prefix", description = "In case a queue does not exist, this will define the prefix to be used on the message export. Default: 'UndefinedQueue_'")
   private String undefinedPrefix = "UndefinedQueue_";

   @Option(names = "--queue", description = "Only export the specified queue(s). Repeatable. When omitted, all queues are exported.")
   private List<String> queues;

   public String getUndefinedPrefix() {
      return undefinedPrefix;
   }

   public XmlDataExporter setUndefinedPrefix(String undefinedPrefix) {
      this.undefinedPrefix = undefinedPrefix;
      return this;
   }

   public List<String> getQueues() {
      return queues;
   }

   public XmlDataExporter setQueues(List<String> queues) {
      this.queues = queues;
      return this;
   }

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      try {
         config = getParameterConfiguration();
         process(context.out);
         done();
      } catch (Exception e) {
         treatError(e, "data", "exp");
      }
      return null;
   }

   /**
    * Use setConfiguration and process(out) instead.
    */
   @Deprecated
   public void process(OutputStream out,
                       String bindingsDir,
                       String journalDir,
                       String pagingDir,
                       String largeMessagesDir) throws Exception {
      config = new ConfigurationImpl().setBindingsDirectory(bindingsDir).setJournalDirectory(journalDir).setPagingDirectory(pagingDir).setLargeMessagesDirectory(largeMessagesDir).setJournalType(JournalType.NIO);
      initializeJournal(config);
      writeOutput(out);
      cleanup();
   }

   public void process(OutputStream out) throws Exception {
      if (config.isUsingDatabaseStorage()) {
         RelationalDatabaseXmlDataExporter dbExporter = new RelationalDatabaseXmlDataExporter(
            (DatabaseStorageConfiguration) config.getStoreConfiguration(),
            undefinedPrefix, queues, logInterval, getActionContext().err);
         dbExporter.writeOutput(out);
         lastError = dbExporter.getLastError();
      } else {
         initializeJournal(config);
         writeOutput(out);
         cleanup();
      }
   }

   protected void writeOutput(OutputStream out) throws Exception {
      logger.info("StorageManager:: {}", storageManager.getClass().getName());
      JournalBasedXmlDataExporter journalExporter = new JournalBasedXmlDataExporter(storageManager, pagingmanager, config, undefinedPrefix, queues, logInterval, getActionContext().err);
      journalExporter.writeOutput(out);
      lastError = journalExporter.getLastError();
   }

   public Throwable getLastError() {
      return lastError;
   }
}
