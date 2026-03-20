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
package org.apache.activemq.artemis.tests.integration.persistence;

import java.io.File;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LargeMessageJournalTest extends StorageManagerTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public LargeMessageJournalTest() {
      super(StoreConfiguration.StoreType.FILE);
   }

   @Test
   public void testCoreLargeMessage() throws Exception {
      journal.generateID();
      long id = journal.generateID();
      LargeServerMessage largeMessage = journal.createCoreLargeMessage();
      journal.onLargeMessageCreate(id, largeMessage);
      largeMessage.setDurable(true);

      byte[] bodyBytes = RandomUtil.randomBytes(100 * 1024);
      largeMessage.addBytes(bodyBytes);

      journal.storeMessage(largeMessage.getMessage());

      assertTrue(journal.getContext().waitCompletion(5000));

      File largeMessagesDir = new File(getLargeMessagesDir());
      String[] msgFiles = largeMessagesDir.list((dir, name) -> name.endsWith(".msg"));
      assertEquals(1, msgFiles.length, "Expected one large message file on disk");

      logger.debug("Large message stored with id={}", id);
   }
}
