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

import org.apache.activemq.artemis.cli.Terminal;
import org.jline.reader.LineReader;

public class JLineInputReader extends InputReader {
   private final LineReader reader;

   public JLineInputReader(LineReader reader) {
      this.reader = reader;
   }

   @Override
   public String readLine(String prompt) {
      return reader.readLine(Terminal.INPUT_COLOR_UNICODE + prompt + ":" + Terminal.CLEAR_UNICODE);
   }

   @Override
   public String readPassword(String prompt) {
      return reader.readLine(Terminal.INPUT_COLOR_UNICODE + prompt + ":" + Terminal.CLEAR_UNICODE, '*');
   }

   @Override
   protected void printLine(String line) {
      System.out.println(line);
   }
}
