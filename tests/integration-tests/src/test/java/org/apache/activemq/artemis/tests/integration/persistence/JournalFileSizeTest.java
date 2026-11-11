/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.io.aio.AIOSequentialFileFactory;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.actors.OrderedExecutorFactory;
import org.apache.activemq.artemis.utils.critical.EmptyCriticalAnalyzer;
import org.junit.jupiter.api.Test;

public class JournalFileSizeTest extends ActiveMQTestBase {

   private static int align;

   static {
      try {
         AIOSequentialFileFactory factory = new AIOSequentialFileFactory(new File("./target/"), 100);
         align = factory.getAlignment();
      } catch (Exception e) {
         align = 512;
      }
   }

   @Override
   protected ConfigurationImpl createBasicConfig(final int serverID) {
      ConfigurationImpl configuration = new ConfigurationImpl().setJournalDirectory(getJournalDir(serverID, false)).setBindingsDirectory(getBindingsDir(serverID, false)).setPagingDirectory(getPageDir(serverID, false)).setLargeMessagesDirectory(getLargeMessagesDir(serverID, false));

      return configuration;
   }

   @Test
   public void testIncorrectFileSizeLower() throws Exception {
      ConfigurationImpl config = createBasicConfig();
      int origFileSize = config.getJournalFileSize();
      config.setJournalFileSize(origFileSize + (align / 2 - 1));
      JournalStorageManager manager = new JournalStorageManager(config, EmptyCriticalAnalyzer.getInstance(), new OrderedExecutorFactory(null), new OrderedExecutorFactory(null));
      int fileSize = manager.getMessageJournal().getFileSize();
      assertEquals(origFileSize, fileSize);
   }

   @Test
   public void testIncorrectFileSizeHigher() throws Exception {
      ConfigurationImpl config = createBasicConfig();
      int origFileSize = config.getJournalFileSize();
      config.setJournalFileSize(origFileSize + (align / 2 + 1));
      JournalStorageManager manager = new JournalStorageManager(config, EmptyCriticalAnalyzer.getInstance(), new OrderedExecutorFactory(null), new OrderedExecutorFactory(null));
      int fileSize = manager.getMessageJournal().getFileSize();
      assertEquals(origFileSize + align, fileSize);
   }

   @Test
   public void testIncorrectFileSizeHalf() throws Exception {
      ConfigurationImpl config = createBasicConfig();
      int origFileSize = config.getJournalFileSize();
      config.setJournalFileSize(origFileSize + (align / 2));
      JournalStorageManager manager = new JournalStorageManager(config, EmptyCriticalAnalyzer.getInstance(), new OrderedExecutorFactory(null), new OrderedExecutorFactory(null));
      int fileSize = manager.getMessageJournal().getFileSize();
      assertEquals(origFileSize + align, fileSize);
   }
}
