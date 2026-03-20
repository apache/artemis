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
package org.apache.activemq.artemis.tests.db.dbstorage.integration;

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.impl.database.DatabaseStorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.tests.db.dbstorage.statements.AbstractStatementTest;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.queries.MessagesJDBCQuery;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIf("isNoDatabaseSelected")
@ExtendWith(ParameterizedTestExtension.class)
public class StorageManagerMessageTest extends AbstractStatementTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static void pretendToStore(LargeServerMessage largeMessage,
                                      long id,
                                      DatabaseStorageManager databaseStorageManager) {
      ActiveMQBuffer persistentBuffer = DatabaseStorageManager.encodeMessage(largeMessage.getMessage());
      ActiveMQBuffer fakeBuffer = ActiveMQBuffers.wrappedBuffer(new byte[1]);
      Message fakeDecoded = DatabaseStorageManager.decodeMessage(id, true, true, () -> persistentBuffer, () -> fakeBuffer, databaseStorageManager);
      assertEquals(fakeDecoded.getAddress(), largeMessage.getMessage().getAddress());
   }

   @TestTemplate
   public void testCoreLargeMessage() throws Exception {
      testCoreLargeMessage(false);
   }

   @TestTemplate
   public void testCoreLargeMessageTX() throws Exception {
      testCoreLargeMessage(true);
   }

   public void testCoreLargeMessage(boolean useTX) throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorService, null);
      databaseStorageManager.start();
      try {
         DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();

         Connection connection = databaseProvider.getConnection();
         runAfter(connection::close);

         OperationContext context = databaseStorageManager.getContext();
         runAfter(OperationContextImpl::clearContext);

         databaseStorageManager.generateID(); // skip the zero

         long id = databaseStorageManager.generateID();

         LargeServerMessage largeMessage = databaseStorageManager.createCoreLargeMessage();
         databaseStorageManager.onLargeMessageCreate(id, largeMessage);
         largeMessage.setDurable(true);
         largeMessage.getMessage().setAddress("Test");
         byte[] bodyBytes = RandomUtil.randomBytes(100 * 1024);
         largeMessage.addBytes(bodyBytes);

         long memoryEstimate = largeMessage.getMessage().getMemoryEstimate();
         logger.info("Memory Estimate {}", memoryEstimate);

         // pretend once to store, and validate things
         pretendToStore(largeMessage, id, databaseStorageManager);

         if (useTX) {
            TransactionImpl transaction = new TransactionImpl(databaseStorageManager);
            databaseStorageManager.storeMessageTransactional(transaction, largeMessage.getMessage());
            databaseStorageManager.storeReferenceTransactional(transaction, 1, largeMessage.getMessageID(), false);
            transaction.setContainsPersistent();
            databaseStorageManager.commit(transaction);
         } else {
            // now do the real thing
            databaseStorageManager.storeMessage(largeMessage.getMessage());
            databaseStorageManager.storeReference(1, largeMessage.getMessageID(), false, true);
         }

         assertTrue(context.waitCompletion(5000));
         Wait.assertEquals(1, () -> selectNumber(connection, "SELECT COUNT(*) FROM DB_MESSAGES WHERE LARGE_BODY IS NOT NULL"), 5000, 100);

         AtomicReference<Message> refMessage = new AtomicReference<>();
         MessagesJDBCQuery query = new MessagesJDBCQuery(databaseProvider, connection);
         logger.info("Querying messages");
         query.query(data -> {
            Message decoded = DatabaseStorageManager.decodeMessage(data.messageID, data.isLarge, data.isCore, data.messageBufferSupplier, data.largeBodySupplier, null);
            assertNull(refMessage.get());
            refMessage.set(decoded);
         });

         assertEquals(refMessage.get().getAddress(), largeMessage.getMessage().getAddress());

         logger.debug("Large message stored with id={}", id);
      } finally {
         databaseStorageManager.stop();
      }
   }

   @TestTemplate
   public void testCoreRegularMessage() throws Exception {
      DatabaseStorageManager databaseStorageManager = new DatabaseStorageManager(configuration, criticalAnalyzer, executorFactory, executorFactory, scheduledExecutorService, executorService, null);
      databaseStorageManager.start();
      try {
         DatabaseProvider databaseProvider = storageConfiguration.getDatabaseProvider();

         Connection connection = databaseProvider.getConnection();
         runAfter(connection::close);

         OperationContext context = databaseStorageManager.getContext();
         runAfter(OperationContextImpl::clearContext);

         databaseStorageManager.generateID(); // skip the zero

         long id = databaseStorageManager.generateID();

         CoreMessage storedMessage = new CoreMessage().initBuffer(100 * 1024).setDurable(true);
         storedMessage.setMessageID(id);
         storedMessage.setAddress("Test");
         byte[] bodyBytes = RandomUtil.randomBytes(1024);
         storedMessage.getBodyBuffer().writeBytes(bodyBytes);

         long memoryEstimate = storedMessage.getMemoryEstimate();
         logger.info("Memory Estimate {}", memoryEstimate);

         databaseStorageManager.storeMessage(storedMessage);
         databaseStorageManager.storeReference(1, storedMessage.getMessageID(), false, true);

         assertTrue(context.waitCompletion(5000));

         assertEquals(1, selectCount(connection, databaseProvider.getSqlProvider().getMessages()));

         AtomicReference<Message> refMessage = new AtomicReference<>();
         MessagesJDBCQuery query = new MessagesJDBCQuery(databaseProvider, connection);
         logger.info("Querying messages");
         query.query(data -> {
            Message decoded = DatabaseStorageManager.decodeMessage(data.messageID, data.isLarge, data.isCore, data.messageBufferSupplier, data.largeBodySupplier, null);
            assertNull(refMessage.get());
            refMessage.set(decoded);
         });

         assertEquals(refMessage.get().getAddress(), storedMessage.getAddress());

         logger.debug("Regular message stored with id={}", id);
      } finally {
         databaseStorageManager.stop();
      }
   }
}
