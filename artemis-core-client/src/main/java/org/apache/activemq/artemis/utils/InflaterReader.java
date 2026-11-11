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
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * InflaterReader
 * <p>
 * It takes a compressed input stream and decompressed it as it is being read. Not for concurrent use.
 */
public class InflaterReader extends InputStream {

   private Inflater inflater = new Inflater();

   private InputStream input;

   private byte[] readBuffer;
   private int pointer;
   private int length;

   public InflaterReader(InputStream input) {
      this(input, 1024);
   }

   public InflaterReader(InputStream input, int bufferSize) {
      this.input = input;
      this.readBuffer = new byte[bufferSize];
      this.pointer = -1;
   }

   @Override
   public int read() throws IOException {
      if (pointer == -1) {
         try {
            length = doRead(readBuffer, 0, readBuffer.length);
            if (length == 0) {
               return -1;
            }
            pointer = 0;
         } catch (DataFormatException e) {
            throw new IOException(e.getMessage(), e);
         }
      }

      int value = readBuffer[pointer] & 0xFF;
      pointer++;
      if (pointer == length) {
         pointer = -1;
      }

      return value;
   }

   /*
    * Feed inflater more bytes in order to get some decompressed output.
    * returns number of bytes actually read
    */
   private int doRead(byte[] buf, int offset, int len) throws DataFormatException, IOException {
      int read = 0;
      int n;
      byte[] inputBuffer = new byte[len];

      while (len > 0) {
         n = inflater.inflate(buf, offset, len);
         if (n == 0) {
            if (inflater.finished()) {
               break;
            } else if (inflater.needsInput()) {
               //feeding
               int m = input.read(inputBuffer);

               if (m == -1) {
                  //it shouldn't be here, throw exception
                  throw new DataFormatException("Input is over while inflater still expecting data");
               } else {
                  //feed the data in
                  inflater.setInput(inputBuffer, 0, m);
                  n = inflater.inflate(buf, offset, len);
                  if (n > 0) {
                     read += n;
                     offset += n;
                     len -= n;
                  }
               }
            } else {
               //it shouldn't be here, throw
               throw new DataFormatException("Inflater is neither finished nor needing input.");
            }
         } else {
            read += n;
            offset += n;
            len -= n;
         }
      }
      return read;
   }

}
