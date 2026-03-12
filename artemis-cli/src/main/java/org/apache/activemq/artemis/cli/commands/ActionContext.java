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

import java.io.InputStream;
import java.io.PrintStream;

import org.jline.reader.LineReader;

public class ActionContext {

   public ActionContext(InputStream in, PrintStream out, PrintStream err) {
      this.in = in;
      this.out = out;
      this.err = err;
   }

   public ActionContext() {
      this(System.in, System.out, System.err);
   }

   public InputStream in;
   public PrintStream out;
   public PrintStream err;
   public LineReader lineReader;

   private static ThreadLocal<ActionContext> contextThreadLocal = ThreadLocal.withInitial(() -> new ActionContext());

   public static void setSystem(ActionContext context) {
      contextThreadLocal.set(context);
   }

   public static ActionContext system() {
      return contextThreadLocal.get();
   }

}
