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

package org.apache.activemq.artemis.cli.commands.util.input;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;

public class SystemInputReader extends InputReader {
   public SystemInputReader() {
      this(System.in, System.out);
   }

   public SystemInputReader(InputStream inputStream, PrintStream promptStream) {
      this.scanner = new Scanner(inputStream);
      this.promptStream = promptStream;
   }

   private final Scanner scanner;
   private final PrintStream promptStream;


   @Override
   public String readLine(String prompt) {
      promptStream.println(prompt);
      return scanner.nextLine();
   }

   @Override
   public String readPassword(String prompt) {
      promptStream.println(prompt);
      char[] typedPassword = System.console().readPassword();
      if (typedPassword == null) {
         return null;
      } else {
         return new String(typedPassword);
      }
   }

   @Override
   protected void printLine(String line) {
      promptStream.println(line);
   }
}
