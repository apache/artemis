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

package org.apache.activemq.artemis.jdbc.store.file;

import java.nio.ByteBuffer;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.io.IOCallback;

public class ScheduledWrite {
   ActiveMQBuffer amqBuffer;
   ByteBuffer ioBuffer;
   final IOCallback callback;
   final boolean append;

   public ScheduledWrite(ActiveMQBuffer amqBuffer, IOCallback callback, boolean append) {
      this.amqBuffer = amqBuffer;
      this.ioBuffer = null;
      this.callback = callback;
      this.append = append;
   }

   public ScheduledWrite(ByteBuffer ioBuffer, IOCallback callback, boolean append) {
      this.ioBuffer = ioBuffer;
      this.amqBuffer = null;
      this.callback = callback;
      this.append = append;
   }

   // for a scheduled Callback without a write
   public ScheduledWrite(IOCallback callback) {
      this.ioBuffer = null;
      this.amqBuffer = null;
      this.callback = callback;
      this.append = false;
   }

   public int readable() {
      if (ioBuffer != null) {
         return ioBuffer.remaining();
      } else if (amqBuffer != null) {
         return amqBuffer.readableBytes();
      } else {
         return 0;
      }
   }

   public int readAt(byte[] dst, int offset) {
      if (ioBuffer != null) {
         int size = ioBuffer.remaining();
         ioBuffer.get(dst, offset, size);
         return size;
      } else if (amqBuffer != null) {
         int size = amqBuffer.readableBytes();
         amqBuffer.getBytes(0, dst, offset, size);
         return size;
      } else {
         return 0;
      }
   }

   /**
    * Remove references letting buffer to be ready for GC
    */
   public void releaseBuffer() {
      amqBuffer = null;
      ioBuffer = null;
   }
}
