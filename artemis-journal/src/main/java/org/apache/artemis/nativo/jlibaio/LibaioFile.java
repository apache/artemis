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
package org.apache.artemis.nativo.jlibaio;

import java.io.Closeable;
import java.io.IOException;

public class LibaioFile<Callback extends SubmitInfo> implements Closeable {

   private final String errorMsg = "This is not supported for JDK < 24.";

   LibaioFile(int fd, LibaioContext ctx) {
   }

   public int getBlockSize() throws IOException {
      throw new UnsupportedOperationException(errorMsg);
   }

   public void fill(int alignment, long size) throws IOException {
      throw new UnsupportedOperationException(errorMsg);
   }

   @Override
   public void close() throws IOException {
   }
}
