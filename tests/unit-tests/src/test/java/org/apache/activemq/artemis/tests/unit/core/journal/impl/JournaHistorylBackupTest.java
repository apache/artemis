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

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.nio.NIOSequentialFileFactory;
import org.apache.activemq.artemis.core.journal.impl.JournalFile;
import org.apache.activemq.artemis.core.journal.impl.JournalFilesRepository;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.fakes.FakeSequentialFileFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JournaHistorylBackupTest extends ActiveMQTestBase {


   @Test
   public void testDoubleReplacement() throws Throwable {

      File history = new File(getTestDirfile(), "history");
      history.mkdirs();


      File journalFolder = new File(getTestDirfile(), "journal");
      journalFolder.mkdirs();
      NIOSequentialFileFactory nioSequentialFileFactory = new NIOSequentialFileFactory(journalFolder, 1);
      JournalImpl journal = new JournalImpl(10 * 1024, 10, 10, 0, 100, nioSequentialFileFactory, "test", "journal", 1);
      journal.setHistoryFolder(history, -1, -1);
      journal.start();
      journal.loadInternalOnly();

      SequentialFile file = nioSequentialFileFactory.createSequentialFile("test-4.journal");
      file.open();
      JournalFile journalFile = journal.readFileHeader(file);
      file.close();
      journalFile.getFile().renameTo(journalFile.getFile().getFileName() + ".bkp");

      journal.stop();


      Calendar oldCalendar = new GregorianCalendar();
      oldCalendar.setTimeInMillis(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
      String toBeReplacedFileName = journal.getHistoryFileName(journalFile.getFileID(), oldCalendar);


      File historyFile = new File(history, toBeReplacedFileName);
      FileOutputStream outputStream = new FileOutputStream(historyFile);
      outputStream.write(0);
      outputStream.close();

      nioSequentialFileFactory = new NIOSequentialFileFactory(journalFolder, 1);
      journal = new JournalImpl(10 * 1024, 10, 10, 0, 100, nioSequentialFileFactory, "test", "journal", 1);
      journal.setHistoryFolder(history, -1, -1);
      journal.start();
      journal.loadInternalOnly();

      File[] fileList = history.listFiles((a, name) -> name.endsWith(".journal"));

      assertEquals(1, fileList.length);
   }

   @Test
   public void verifyFileName() throws Throwable {
      GregorianCalendar clebertsBirthday = new GregorianCalendar(1972, 0, 19, 4, 5, 7);


      JournalImpl journal = new JournalImpl(10 * 1024, 10, 10, 0, 100, new FakeSequentialFileFactory(), "cleberts", "birthday", 1);
      String fileNameGenerated = journal.getHistoryFileName(1, clebertsBirthday);

      // I was actually born at 4:30 :) but I need all numbers lower than 2 digits on the test
      assertEquals("cleberts-19720119040507-1.birthday", fileNameGenerated);
      assertEquals("19720119040507", journal.getDatePortion(fileNameGenerated));

      long d = journal.getDatePortionMillis(fileNameGenerated);

      GregorianCalendar compareCalendar = new GregorianCalendar();
      compareCalendar.setTimeInMillis(d);

      assertEquals(1972, compareCalendar.get(Calendar.YEAR));
      assertEquals(0, compareCalendar.get(Calendar.MONTH));
      assertEquals(19, compareCalendar.get(Calendar.DAY_OF_MONTH));
      assertEquals(4, compareCalendar.get(Calendar.HOUR_OF_DAY));
      assertEquals(5, compareCalendar.get(Calendar.MINUTE));
      assertEquals(7, compareCalendar.get(Calendar.SECOND));

      assertFalse(d < clebertsBirthday.getTimeInMillis());

      compareCalendar.set(Calendar.YEAR, 1971);

      assertTrue(compareCalendar.getTimeInMillis() < clebertsBirthday.getTimeInMillis());

   }

   @Test
   public void removeBKPExtension() throws Throwable {
      JournalImpl journal = new JournalImpl(10 * 1024, 10, 10, 0, 100, new FakeSequentialFileFactory(), "jrn", "data", 1);

      String withoutBkp = "jrn-1.data";
      String withBKP = withoutBkp + ".bkp";
      // I was actually born at 4:30 :) but I need all numbers lower than 2 digits on the test
      assertEquals(withoutBkp, journal.removeBackupExtension(withBKP));
      assertEquals(withoutBkp, journal.removeBackupExtension(withoutBkp)); // it should be possible to do it
   }

   @Test
   public void testFileID() throws Throwable {
      JournalImpl journal = new JournalImpl(10 * 1024, 10, 10, 0, 100, new FakeSequentialFileFactory(), "jrn", "data", 1);
      GregorianCalendar calendar = new GregorianCalendar();
      calendar.setTimeInMillis(System.currentTimeMillis());
      String fileName = journal.getHistoryFileName(3, calendar);
      long id = JournalFilesRepository.getFileNameID("jrn", fileName);
      assertEquals(3, id);
   }

   @Test
   public void testRemoveOldFiles() throws Exception {
      GregorianCalendar todayCalendar = new GregorianCalendar();
      todayCalendar.setTimeInMillis(System.currentTimeMillis());

      File tempFolder = new File(getTestDirfile(), "history");
      tempFolder.mkdirs();

      JournalImpl journal = new JournalImpl(10 * 1024, 10, 10, 0, 100, new FakeSequentialFileFactory(), "jrn", "data", 1);
      journal.setHistoryFolder(tempFolder, -1, TimeUnit.HOURS.toMillis(24));

      Calendar dayOlderCalendar = new GregorianCalendar();
      dayOlderCalendar.setTimeInMillis(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(25));


      for (int i = 0; i < 100; i++) {
         String fileName = journal.getHistoryFileName(i, dayOlderCalendar);
         File file = new File(tempFolder, fileName);
         FileOutputStream outputStream = new FileOutputStream(file);
         outputStream.write(0);
         outputStream.close();
      }

      for (int i = 0; i < 100; i++) {
         String fileName = journal.getHistoryFileName(i, todayCalendar);
         File file = new File(tempFolder, fileName);
         FileOutputStream outputStream = new FileOutputStream(file);
         outputStream.write(0);
         outputStream.close();
      }

      journal.processBackupCleanup();

      FilenameFilter fnf = (file, name) -> name.endsWith(".data");

      File[] files = tempFolder.listFiles(fnf);

      assertEquals(100, files.length);

      Set<String> hashSet = new HashSet<>();
      for (File file : files) {
         hashSet.add(file.getName());
      }

      for (int i = 0; i < 100; i++) {
         assertTrue(hashSet.contains(journal.getHistoryFileName(i, todayCalendar)));
      }

   }


   @Test
   public void testKeepOldFiles() throws Exception {
      GregorianCalendar todayCalendar = new GregorianCalendar();
      todayCalendar.setTimeInMillis(System.currentTimeMillis());

      File tempFolder = new File(getTestDirfile(), "history");
      tempFolder.mkdirs();

      JournalImpl journal = new JournalImpl(10 * 1024, 10, 10, 0, 100, new FakeSequentialFileFactory(), "jrn", "data", 1);
      journal.setHistoryFolder(tempFolder, -1, TimeUnit.HOURS.toMillis(24));

      Calendar oldCalendar = new GregorianCalendar();
      oldCalendar.setTimeInMillis(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));


      for (int i = 0; i < 100; i++) {
         String fileName = journal.getHistoryFileName(i, oldCalendar);
         File file = new File(tempFolder, fileName);
         FileOutputStream outputStream = new FileOutputStream(file);
         outputStream.write(0);
         outputStream.close();
      }

      for (int i = 0; i < 100; i++) {
         String fileName = journal.getHistoryFileName(i, todayCalendar);
         File file = new File(tempFolder, fileName);
         FileOutputStream outputStream = new FileOutputStream(file);
         outputStream.write(0);
         outputStream.close();
      }

      journal.processBackupCleanup();

      FilenameFilter fnf = (file, name) -> name.endsWith(".data");

      File[] files = tempFolder.listFiles(fnf);

      assertEquals(200, files.length);

      Set<String> hashSet = new HashSet<>();
      for (File file : files) {
         hashSet.add(file.getName());
      }

      for (int i = 0; i < 100; i++) {
         assertTrue(hashSet.contains(journal.getHistoryFileName(i, todayCalendar)));
      }

      for (int i = 0; i < 100; i++) {
         assertTrue(hashSet.contains(journal.getHistoryFileName(i, oldCalendar)));
      }

   }


   @Test
   public void testMaxFiles() throws Exception {
      GregorianCalendar todayCalendar = new GregorianCalendar();
      todayCalendar.setTimeInMillis(System.currentTimeMillis());

      File tempFolder = new File(getTestDirfile(), "history");
      tempFolder.mkdirs();

      JournalImpl journal = new JournalImpl(10 * 1024, 10, 10, 0, 100, new FakeSequentialFileFactory(), "jrn", "data", 1);
      journal.setHistoryFolder(tempFolder, 10 * journal.getFileSize(), TimeUnit.HOURS.toMillis(24));

      Calendar oldCalendar = new GregorianCalendar();
      oldCalendar.setTimeInMillis(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));


      for (int i = 0; i < 100; i++) {
         String fileName = journal.getHistoryFileName(i, oldCalendar);
         File file = new File(tempFolder, fileName);
         FileOutputStream outputStream = new FileOutputStream(file);
         outputStream.write(0);
         outputStream.close();
      }

      for (int i = 0; i < 100; i++) {
         String fileName = journal.getHistoryFileName(i, todayCalendar);
         File file = new File(tempFolder, fileName);
         FileOutputStream outputStream = new FileOutputStream(file);
         outputStream.write(0);
         outputStream.close();
      }

      journal.processBackupCleanup();

      FilenameFilter fnf = (file, name) -> name.endsWith(".data");

      File[] files = tempFolder.listFiles(fnf);

      assertEquals(10, files.length);

      Set<String> hashSet = new HashSet<>();
      for (File file : files) {
         hashSet.add(file.getName());
      }


      for (int i = 90; i < 100; i++) {
         assertTrue(hashSet.contains(journal.getHistoryFileName(i, todayCalendar)));
      }

   }

}