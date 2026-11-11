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
package org.apache.activemq.artemis.cli.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "help", description = "use 'help <command>' for more information")
public class HelpAction implements Runnable {

   CommandLine commandLine;

   @CommandLine.Parameters
   String[] args;

   public static void help(CommandLine commandLine, String... args) {
      if (args != null) {
         CommandLine theLIn = commandLine;
         for (String i : args) {
            Object subCommand = theLIn.getSubcommands().get(i);
            if (subCommand == null) {
               commandLine.usage(System.out);
            } else if (subCommand instanceof CommandLine subCommandLine) {
               theLIn = subCommandLine;
            } else {
               commandLine.usage(System.out);
            }
         }
         theLIn.usage(System.out);
      } else {
         commandLine.usage(System.out);
      }
   }

   public CommandLine getCommandLine() {
      return commandLine;
   }

   public HelpAction setCommandLine(CommandLine commandLine) {
      this.commandLine = commandLine;
      return this;
   }

   @Override
   public void run() {
      help(commandLine, args);
   }

}
