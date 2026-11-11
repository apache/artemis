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
package org.apache.activemq.artemis.tests.unit.core.journal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class SequentialFileFactoryTestBase extends ActiveMQTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      factory = createFactory(getTestDir());

      factory.start();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      factory.stop();

      factory = null;

      assertEquals(0, LibaioContext.getTotalMaxIO());

      super.tearDown();
   }

   protected abstract SequentialFileFactory createFactory(String folder);

   protected SequentialFileFactory factory;

   @Test
   public void listFilesOnNonExistentFolder() throws Exception {
      SequentialFileFactory fileFactory = createFactory("./target/dontexist");
      List<String> list = fileFactory.listFiles("tmp");
      assertNotNull(list);
      assertEquals(0, list.size());
   }

   @Test
   public void testCreateAndListFiles() throws Exception {
      List<String> expectedFiles = new ArrayList<>();

      final int numFiles = 10;

      for (int i = 0; i < numFiles; i++) {
         String fileName = UUID.randomUUID().toString() + ".amq";

         expectedFiles.add(fileName);

         SequentialFile sf = factory.createSequentialFile(fileName);

         sf.open();

         assertEquals(fileName, sf.getFileName());

         sf.close();
      }

      // Create a couple with a different extension - they shouldn't be picked
      // up

      SequentialFile sf1 = factory.createSequentialFile("different.file");
      sf1.open();

      SequentialFile sf2 = factory.createSequentialFile("different.cheese");
      sf2.open();

      List<String> fileNames = factory.listFiles("amq");

      assertEquals(expectedFiles.size(), fileNames.size());

      for (String fileName : expectedFiles) {
         assertTrue(fileNames.contains(fileName));
      }

      fileNames = factory.listFiles("file");

      assertEquals(1, fileNames.size());

      assertTrue(fileNames.contains("different.file"));

      fileNames = factory.listFiles("cheese");

      assertEquals(1, fileNames.size());

      assertTrue(fileNames.contains("different.cheese"));

      sf1.close();
      sf2.close();
   }

   @Test
   public void testFill() throws Exception {
      SequentialFile sf = factory.createSequentialFile("fill.amq");

      sf.open();

      try {

         checkFill(factory, sf, 2048);

         checkFill(factory, sf, 512);

         checkFill(factory, sf, 512 * 4);
      } finally {
         sf.close();
      }
   }

   @Test
   public void testDelete() throws Exception {
      SequentialFile sf = factory.createSequentialFile("delete-me.amq");

      sf.open();

      SequentialFile sf2 = factory.createSequentialFile("delete-me2.amq");

      sf2.open();

      List<String> fileNames = factory.listFiles("amq");

      assertEquals(2, fileNames.size());

      assertTrue(fileNames.contains("delete-me.amq"));

      assertTrue(fileNames.contains("delete-me2.amq"));

      sf.delete();

      fileNames = factory.listFiles("amq");

      assertEquals(1, fileNames.size());

      assertTrue(fileNames.contains("delete-me2.amq"));

      sf2.close();

   }

   @Test
   public void testRename() throws Exception {
      SequentialFile sf = factory.createSequentialFile("test1.amq");

      sf.open();

      List<String> fileNames = factory.listFiles("amq");

      assertEquals(1, fileNames.size());

      assertTrue(fileNames.contains("test1.amq"));

      sf.renameTo("test1.cmp");

      fileNames = factory.listFiles("cmp");

      assertEquals(1, fileNames.size());

      assertTrue(fileNames.contains("test1.cmp"));

      sf.delete();

      fileNames = factory.listFiles("amq");

      assertEquals(0, fileNames.size());

      fileNames = factory.listFiles("cmp");

      assertEquals(0, fileNames.size());

   }

   @Test
   public void testWriteandRead() throws Exception {
      SequentialFile sf = factory.createSequentialFile("write.amq");

      sf.open();

      String s1 = "aardvark";
      byte[] bytes1 = s1.getBytes(StandardCharsets.UTF_8);
      ActiveMQBuffer bb1 = wrapBuffer(bytes1);

      String s2 = "hippopotamus";
      byte[] bytes2 = s2.getBytes(StandardCharsets.UTF_8);
      ActiveMQBuffer bb2 = wrapBuffer(bytes2);

      String s3 = "echidna";
      byte[] bytes3 = s3.getBytes(StandardCharsets.UTF_8);
      ActiveMQBuffer bb3 = wrapBuffer(bytes3);

      long initialPos = sf.position();
      sf.write(bb1, true);
      long bytesWritten = sf.position() - initialPos;

      assertEquals(calculateRecordSize(bytes1.length, factory.getAlignment()), bytesWritten);

      initialPos = sf.position();
      sf.write(bb2, true);
      bytesWritten = sf.position() - initialPos;

      assertEquals(calculateRecordSize(bytes2.length, factory.getAlignment()), bytesWritten);

      initialPos = sf.position();
      sf.write(bb3, true);
      bytesWritten = sf.position() - initialPos;

      assertEquals(calculateRecordSize(bytes3.length, factory.getAlignment()), bytesWritten);

      sf.position(0);

      ByteBuffer rb1 = factory.newBuffer(bytes1.length);
      ByteBuffer rb2 = factory.newBuffer(bytes2.length);
      ByteBuffer rb3 = factory.newBuffer(bytes3.length);

      int bytesRead = sf.read(rb1);
      assertEquals(calculateRecordSize(bytes1.length, factory.getAlignment()), bytesRead);

      for (int i = 0; i < bytes1.length; i++) {
         assertEquals(bytes1[i], rb1.get(i));
      }

      bytesRead = sf.read(rb2);
      assertEquals(calculateRecordSize(bytes2.length, factory.getAlignment()), bytesRead);
      for (int i = 0; i < bytes2.length; i++) {
         assertEquals(bytes2[i], rb2.get(i));
      }

      bytesRead = sf.read(rb3);
      assertEquals(calculateRecordSize(bytes3.length, factory.getAlignment()), bytesRead);
      for (int i = 0; i < bytes3.length; i++) {
         assertEquals(bytes3[i], rb3.get(i));
      }

      sf.close();

   }

   @Test
   public void testPosition() throws Exception {
      SequentialFile sf = factory.createSequentialFile("position.amq");

      sf.open();

      try {

         sf.fill(3 * 512);

         String s1 = "orange";
         byte[] bytes1 = s1.getBytes(StandardCharsets.UTF_8);

         byte[] bytes2 = s1.getBytes(StandardCharsets.UTF_8);

         String s3 = "lemon";
         byte[] bytes3 = s3.getBytes(StandardCharsets.UTF_8);

         long initialPos = sf.position();
         sf.write(wrapBuffer(bytes1), true);
         long bytesWritten = sf.position() - initialPos;

         assertEquals(calculateRecordSize(bytes1.length, factory.getAlignment()), bytesWritten);

         initialPos = sf.position();
         sf.write(wrapBuffer(bytes2), true);
         bytesWritten = sf.position() - initialPos;

         assertEquals(calculateRecordSize(bytes2.length, factory.getAlignment()), bytesWritten);

         initialPos = sf.position();
         sf.write(wrapBuffer(bytes3), true);
         bytesWritten = sf.position() - initialPos;

         assertEquals(calculateRecordSize(bytes3.length, factory.getAlignment()), bytesWritten);

         byte[] rbytes1 = new byte[bytes1.length];

         byte[] rbytes2 = new byte[bytes2.length];

         byte[] rbytes3 = new byte[bytes3.length];

         ByteBuffer rb1 = factory.newBuffer(rbytes1.length);
         ByteBuffer rb2 = factory.newBuffer(rbytes2.length);
         ByteBuffer rb3 = factory.newBuffer(rbytes3.length);

         sf.position(calculateRecordSize(bytes1.length, factory.getAlignment()) + calculateRecordSize(bytes2.length, factory.getAlignment()));

         int bytesRead = sf.read(rb3);
         assertEquals(rb3.limit(), bytesRead);
         rb3.rewind();
         rb3.get(rbytes3);
         ActiveMQTestBase.assertEqualsByteArrays(bytes3, rbytes3);

         sf.position(rb1.limit());

         bytesRead = sf.read(rb2);
         assertEquals(rb2.limit(), bytesRead);
         rb2.get(rbytes2);
         ActiveMQTestBase.assertEqualsByteArrays(bytes2, rbytes2);

         sf.position(0);

         bytesRead = sf.read(rb1);
         assertEquals(rb1.limit(), bytesRead);
         rb1.get(rbytes1);

         ActiveMQTestBase.assertEqualsByteArrays(bytes1, rbytes1);

      } finally {
         try {
            sf.close();
         } catch (Exception ignored) {
         }
      }
   }

   @Test
   public void testOpenClose() throws Exception {
      SequentialFile sf = factory.createSequentialFile("openclose.amq");

      sf.open();

      sf.fill(512);

      String s1 = "cheesecake";
      byte[] bytes1 = s1.getBytes(StandardCharsets.UTF_8);

      long initialPos = sf.position();
      sf.write(wrapBuffer(bytes1), true);
      long bytesWritten = sf.position() - initialPos;

      assertEquals(calculateRecordSize(bytes1.length, factory.getAlignment()), bytesWritten);

      sf.close();

      try {

         sf.write(wrapBuffer(bytes1), true);

         fail("Should throw exception");
      } catch (Exception e) {
      }

      sf.open();

      sf.write(wrapBuffer(bytes1), true);

      sf.close();
   }

   private ActiveMQBuffer wrapBuffer(final byte[] bytes) {
      return ActiveMQBuffers.wrappedBuffer(bytes);
   }

   protected void checkFill(final SequentialFileFactory factory, final SequentialFile file, final int size) throws Exception {
      file.fill(size);

      file.close();

      file.open();

      file.position(0);

      ByteBuffer bb = factory.newBuffer(size);

      int bytesRead = file.read(bb);

      assertEquals(calculateRecordSize(size, factory.getAlignment()), bytesRead);

      for (int i = 0; i < size; i++) {
         // log.debug(" i is {}", i);
         assertEquals(0, bb.get(i));
      }

   }

}
