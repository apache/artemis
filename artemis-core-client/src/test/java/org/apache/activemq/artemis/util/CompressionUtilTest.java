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
package org.apache.activemq.artemis.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;

import org.apache.activemq.artemis.utils.DeflaterReader;
import org.apache.activemq.artemis.utils.InflaterReader;
import org.apache.activemq.artemis.utils.InflaterWriter;
import org.junit.jupiter.api.Test;

public class CompressionUtilTest {

   @Test
   public void testDeflaterReader() throws Exception {
      String inputString = "blahblahblah??blahblahblahblahblah??blablahblah??blablahblah??bla";
      byte[] input = inputString.getBytes(StandardCharsets.UTF_8);

      ByteArrayInputStream inputStream = new ByteArrayInputStream(input);

      AtomicLong counter = new AtomicLong(0);
      List<Integer> zipHolder = new ArrayList<>();

      try (DeflaterReader reader = new DeflaterReader(inputStream, counter)) {
         int b = reader.read();

         while (b != -1) {
            zipHolder.add(b);
            b = reader.read();
         }
      }

      assertEquals(input.length, counter.get());

      byte[] allCompressed = new byte[zipHolder.size()];
      for (int i = 0; i < allCompressed.length; i++) {
         allCompressed[i] = (byte) zipHolder.get(i).intValue();
      }

      byte[] output = new byte[30];
      Deflater compressor = new Deflater();
      compressor.setInput(input);
      compressor.finish();
      int compressedDataLength = compressor.deflate(output);

      compareByteArray(allCompressed, output, compressedDataLength);
   }

   @Test
   public void testDeflaterReader2() throws Exception {
      String inputString = "blahblahblah??blahblahblahblahblah??blablahblah??blablahblah??bla";
      byte[] input = inputString.getBytes(StandardCharsets.UTF_8);

      ByteArrayInputStream inputStream = new ByteArrayInputStream(input);
      AtomicLong counter = new AtomicLong(0);

      byte[] buffer = new byte[7];
      List<Integer> zipHolder = new ArrayList<>();

      try (DeflaterReader reader = new DeflaterReader(inputStream, counter)) {
         int n = reader.read(buffer);
         while (n != -1) {
            for (int i = 0; i < n; i++) {
               zipHolder.add((int) buffer[i]);
            }
            n = reader.read(buffer);
         }
      }

      assertEquals(input.length, counter.get());

      byte[] allCompressed = new byte[zipHolder.size()];
      for (int i = 0; i < allCompressed.length; i++) {
         allCompressed[i] = (byte) zipHolder.get(i).intValue();
      }

      byte[] output = new byte[30];
      Deflater compressor = new Deflater();
      compressor.setInput(input);
      compressor.finish();
      int compressedDataLength = compressor.deflate(output);

      compareByteArray(allCompressed, output, compressedDataLength);
   }

   @Test
   public void testInflaterReader() throws Exception {
      String inputString = "blahblahblah??blahblahblahblahblah??blablahblah??blablahblah??bla";
      byte[] input = inputString.getBytes(StandardCharsets.UTF_8);
      byte[] output = new byte[30];
      Deflater compressor = new Deflater();
      compressor.setInput(input);
      compressor.finish();
      int compressedDataLength = compressor.deflate(output);

      byte[] zipBytes = new byte[compressedDataLength];

      System.arraycopy(output, 0, zipBytes, 0, compressedDataLength);
      ByteArrayInputStream byteInput = new ByteArrayInputStream(zipBytes);

      List<Integer> holder = new ArrayList<>();
      try (InflaterReader inflater = new InflaterReader(byteInput)) {
         int read = inflater.read();

         while (read != -1) {
            holder.add(read);
            read = inflater.read();
         }
      }

      byte[] result = new byte[holder.size()];

      for (int i = 0; i < result.length; i++) {
         result[i] = holder.get(i).byteValue();
      }

      String txt = new String(result);

      assertEquals(inputString, txt);
   }

   @Test
   public void testInflaterWriter() throws Exception {
      String inputString = "blahblahblah??blahblahblahblahblah??blablahblah??blablahblah??bla";
      byte[] input = inputString.getBytes(StandardCharsets.UTF_8);
      byte[] output = new byte[30];
      Deflater compressor = new Deflater();
      compressor.setInput(input);
      compressor.finish();
      int compressedDataLength = compressor.deflate(output);

      byte[] zipBytes = new byte[compressedDataLength];

      System.arraycopy(output, 0, zipBytes, 0, compressedDataLength);
      ByteArrayInputStream byteInput = new ByteArrayInputStream(zipBytes);

      ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
      byte[] zipBuffer = new byte[12];

      try (InflaterWriter writer = new InflaterWriter(byteOutput)) {
         int n = byteInput.read(zipBuffer);
         while (n > 0) {
            writer.write(zipBuffer, 0, n);
            n = byteInput.read(zipBuffer);
         }
      }

      byte[] outcome = byteOutput.toByteArray();
      String outStr = new String(outcome);

      assertEquals(inputString, outStr);
   }

   private void compareByteArray(byte[] first, byte[] second, int length) {
      for (int i = 0; i < length; i++) {
         assertEquals(first[i], second[i]);
      }
   }
}
