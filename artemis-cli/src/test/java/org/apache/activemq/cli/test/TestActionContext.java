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
package org.apache.activemq.cli.test;

import org.apache.activemq.artemis.cli.commands.ActionContext;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class TestActionContext extends ActionContext {

   public ByteArrayOutputStream stdout;
   public ByteArrayOutputStream stderr;
   private int bufferSize;

   public TestActionContext(int bufferSize) {
      this.bufferSize = bufferSize;
      this.stdout = new ByteArrayOutputStream(bufferSize);
      this.stderr = new ByteArrayOutputStream(bufferSize);
      this.in = System.in;
      this.out = new PrintStream(stdout);
      this.err = new PrintStream(stderr);
   }

   public TestActionContext() {
      this(4096);
   }

   public String getStdout() {
      return stdout.toString();
   }

   public byte[] getStdoutBytes() {
      return stdout.toByteArray();
   }

   public String getStderr() {
      return stderr.toString();
   }

   public byte[] getStdErrBytes() {
      return stderr.toByteArray();
   }
}
