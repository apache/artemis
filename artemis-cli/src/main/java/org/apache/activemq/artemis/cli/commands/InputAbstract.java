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

import org.apache.activemq.artemis.cli.commands.util.input.InputReader;
import org.apache.activemq.artemis.cli.commands.util.input.JLineInputReader;
import org.apache.activemq.artemis.cli.commands.util.input.SystemInputReader;
import picocli.CommandLine.Option;

public class InputAbstract extends ActionAbstract {


   InputReader lineReader;


   private static boolean inputEnabled = false;

   /**
    * Test cases validating or using the CLI usually don't deal with inputs, so they are generally disabled, however
    * the main method from the CLI will enable it back.
    */
   public static void enableInput() {
      inputEnabled = true;
   }

   public static void disableInput() {
      inputEnabled = false;
   }

   @Option(names = "--silent", description = "Disable all the inputs, and make a best guess for any required input.")
   private boolean silentInput = false;

   public boolean isSilentInput() {
      return silentInput || !inputEnabled;
   }

   public void setSilentInput(boolean isSilent) {
      this.silentInput = isSilent;
   }

   protected boolean inputBoolean(String propertyName, String prompt, boolean silentDefault) {
      if (isSilentInput()) {
         return silentDefault;
      }

      Boolean booleanValue = null;
      do {
         String value = input(propertyName, prompt + ", valid values are Y, N, True, False", Boolean.toString(silentDefault));

         booleanValue = switch (value.toUpperCase().trim()) {
            case "TRUE", "Y" -> Boolean.TRUE;
            case "FALSE", "N" -> Boolean.FALSE;
            default -> booleanValue;
         };
      }
      while (booleanValue == null);

      return booleanValue.booleanValue();
   }

   public int inputInteger(String propertyName, String prompt, String silentDefault) {

      Integer value = null;
      do {
         String input = input(propertyName, prompt, silentDefault);
         if (input == null || input.trim().isEmpty()) {
            input = "0";
         }

         try {
            value = Integer.parseInt(input);
         } catch (NumberFormatException e) {
            e.printStackTrace();
            value = null;
         }
      }
      while(value == null);

      return value.intValue();
   }

   protected String input(String propertyName, String prompt, String silentDefault) {
      return input(propertyName, prompt, silentDefault, false);
   }

   protected String input(String propertyName, String prompt, String silentDefault, boolean acceptNull) {
      if (isSilentInput()) {
         return silentDefault;
      }

      String inputStr = lineReader.inputLoop(propertyName, prompt, s -> s != null && !s.isEmpty());

      return inputStr.trim();
   }

   protected String inputPassword(String propertyName, String prompt, String silentDefault) {
      if (isSilentInput()) {
         return silentDefault;
      }

      String inputStr = "";
      boolean valid = false;
      getActionContext().out.println();
      do {
         getActionContext().out.println(propertyName + ": is mandatory with this configuration:");
         inputStr = lineReader.readPassword(prompt);

         // could be null if the user input something weird like Ctrl-d
         if (inputStr == null) {
            getActionContext().out.println("Invalid Entry!");
            continue;
         }

         if (inputStr.trim().isEmpty()) {
            getActionContext().out.println("Invalid Entry!");
         } else {
            valid = true;
         }
      }
      while (!valid);

      return inputStr.trim();
   }

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      if (context.lineReader != null) {
         this.lineReader = new JLineInputReader(context.lineReader);
      } else {
         this.lineReader = new SystemInputReader(context.in, context.out);
      }

      return null;
   }

   public void setLineReader(InputReader lineReader) {
      this.lineReader = lineReader;
   }
}
