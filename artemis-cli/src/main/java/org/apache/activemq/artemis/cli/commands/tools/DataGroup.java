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

import org.apache.activemq.artemis.cli.commands.HelpAction;
import org.apache.activemq.artemis.cli.commands.tools.journal.CompactJournal;
import org.apache.activemq.artemis.cli.commands.tools.journal.DecodeJournal;
import org.apache.activemq.artemis.cli.commands.tools.journal.EncodeJournal;
import org.apache.activemq.artemis.cli.commands.tools.xml.XmlDataExporter;
import org.apache.activemq.artemis.cli.commands.tools.xml.XmlDataImporter;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "data", description = "use 'help data' for sub commands list", subcommands = {RecoverMessages.class, PrintData.class, XmlDataExporter.class, XmlDataImporter.class, DecodeJournal.class, EncodeJournal.class, CompactJournal.class})
public class DataGroup implements Runnable {

   CommandLine commandLine;

   public DataGroup(CommandLine commandLine) {
      this.commandLine = commandLine;
   }

   @Override
   public void run() {
      HelpAction.help(commandLine, "data");
   }

}
