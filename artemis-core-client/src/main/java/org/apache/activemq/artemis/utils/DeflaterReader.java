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
package org.apache.activemq.artemis.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;

/**
 * A DeflaterReader
 * <p>
 * The reader takes an inputstream and compress it.
 * <p>
 * Not for concurrent use.
 */
public class DeflaterReader extends InputStream {

   private final Deflater deflater = new Deflater();
   private boolean isFinished = false;
   private boolean compressDone = false;

   private InputStream input;

   private final AtomicLong bytesRead;

   public DeflaterReader(final InputStream inData, final AtomicLong bytesRead) {
      input = inData;
      this.bytesRead = bytesRead;
   }

   @Override
   public int read() throws IOException {
      byte[] buffer = new byte[1];
      int n = read(buffer, 0, 1);
      if (n == 1) {
         return buffer[0] & 0xFF;
      }
      if (n == -1 || n == 0) {
         return -1;
      }
      throw new IOException("Error reading data, invalid n: " + n);
   }

   /**
    * Try to fill the buffer with compressed bytes. Except the last effective read, this method always returns with a
    * full buffer of compressed data.
    *
    * @param buffer the buffer to fill compressed bytes
    * @return the number of bytes really filled, -1 indicates end
    */
   @Override
   public int read(final byte[] buffer, int offset, int len) throws IOException {
      if (compressDone) {
         return -1;
      }

      //buffer for reading input stream
      byte[] readBuffer = new byte[2 * len];

      int n;
      int read = 0;

      while (len > 0) {
         n = deflater.deflate(buffer, offset, len);
         if (n == 0) {
            if (isFinished) {
               deflater.end();
               compressDone = true;
               break;
            } else if (deflater.needsInput()) {
               // read some data from inputstream
               int m = input.read(readBuffer);

               if (m == -1) {
                  deflater.finish();
                  isFinished = true;
               } else {
                  if (bytesRead != null) {
                     bytesRead.addAndGet(m);
                  }
                  deflater.setInput(readBuffer, 0, m);
               }
            } else {
               deflater.finish();
               isFinished = true;
            }
         } else {
            read += n;
            offset += n;
            len -= n;
         }
      }
      return read;
   }

   @Override
   public void close() throws IOException {
      super.close();
      input.close();
   }

   public long getTotalSize() {
      return bytesRead.get();
   }

   public void setLevel(int level) {
      deflater.setLevel(level);
   }

}
