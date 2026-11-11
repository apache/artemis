/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.cli.commands.tools.journal;

import java.text.DecimalFormat;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.tools.OptionalLocking;
import org.apache.activemq.artemis.cli.commands.util.SyncCalculation;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.server.JournalType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "perf-journal", description = "Calculate the journal-buffer-timeout to use with the current data folder.")
public class PerfJournal extends OptionalLocking {


   @Option(names = "--block-size", description = "The block size for each write. Default 4096.")
   public int size = 4 * 1024;

   @Option(names = "--writes", description = "The number of writes to be performed. Default: 250.")
   public int writes = 250;

   @Option(names = "--tries", description = "The number of tries for the test. Default: 5.")
   public int tries = 5;

   @Option(names = "--no-sync", description = "Disable syncs.")
   public boolean nosyncs = false;

   @Option(names = "--sync", description = "Enable syncs.")
   public boolean syncs = false;

   @Option(names = "--journal-type", description = "Journal type to be used: Default: read from broker.xml.")
   public String journalType = null;

   @Option(names = "--sync-writes", description = "Perform each write synchronously, e.g. if there was a single producer.")
   public boolean syncWrites = false;

   @Option(names = "--file", description = "The file name to be used. Default: test.tmp.")
   public String fileName = "test.tmp";

   @Option(names = "--max-aio", description = "libaio.maxAIO to be used. Default: read from broker.xml.")
   public int maxAIO = 0;


   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      FileConfiguration fileConfiguration = getFileConfiguration();

      if (nosyncs) {
         fileConfiguration.setJournalDatasync(false);
      } else if (syncs) {
         fileConfiguration.setJournalDatasync(true);
      }


      if (journalType != null) {
         fileConfiguration.setJournalType(JournalType.getType(journalType));
      }

      context.out.println("");
      context.out.println("Auto tuning journal ...");

      context.out.println("Performing " + tries + " tests writing " + writes + " blocks of " + size + " on each test, sync=" + fileConfiguration.isJournalDatasync() + " with journalType = " + fileConfiguration.getJournalType());

      fileConfiguration.getJournalLocation().mkdirs();

      if (maxAIO <= 0) {
         maxAIO = fileConfiguration.getJournalMaxIO_AIO();
      }

      long time = SyncCalculation.syncTest(fileConfiguration.getJournalLocation(), size, writes, tries, verbose, fileConfiguration.isJournalDatasync(), syncWrites, fileName, maxAIO, fileConfiguration.getJournalType(), context);

      long nanosecondsWait = SyncCalculation.toNanos(time, writes, verbose, context);
      double writesPerMillisecond = (double) writes / (double) time;

      String writesPerMillisecondStr = new DecimalFormat("###.##").format(writesPerMillisecond);

      context.out.println("Your system can execute " + writesPerMillisecondStr + " writes per millisecond");
      context.out.println("Your journal-buffer-timeout should be:" + nanosecondsWait);
      context.out.println("You should use this following configuration:");
      context.out.println();
      context.out.println("<journal-buffer-timeout>" + nanosecondsWait + "</journal-buffer-timeout>");

      return null;
   }
}
