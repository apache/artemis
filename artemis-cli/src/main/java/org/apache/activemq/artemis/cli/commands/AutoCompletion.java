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

import java.io.File;

import org.apache.activemq.artemis.cli.Artemis;
import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;

@Command(name = "auto-complete", description = "Generates the auto complete script file to be used in bash or zsh. This feature is deprecated in favor of ./artemis completion", hidden = true)
public class AutoCompletion implements Runnable {

   public AutoCompletion() {
   }

   @Option(names = "--start-script", description = "the script used to start artemis. (default ./artemis)", defaultValue = "./artemis")
   String startScript;

   @Parameters (description = "The generated auto-complete script", defaultValue = "auto-complete-artemis.sh")
   File autoCompleteFile;


   @Override
   public void run() {
      try {
         CommandLine artemisCommand = Artemis.buildCommand(true, true, true);
         AutoComplete.bash(startScript, autoCompleteFile, null, artemisCommand);
         System.out.println("Type the following commands before you can use auto-complete:");
         System.out.println("*******************************************************************************************************************************");
         System.out.println("source " + autoCompleteFile.getAbsolutePath());
         System.out.println("*******************************************************************************************************************************");

      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   // I'm letting the possibility of calling AutoCompletion directly bypassing the artemis CLI.
   public static void main(String[] args) {
      CommandLine commandLine = new CommandLine(new AutoCompletion());
      commandLine.execute(args);
   }
}
