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
package org.apache.activemq.artemis.tests.db.dbstorage.stress;

import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.db.common.Database;
import org.apache.activemq.artemis.tests.db.dbstorage.statements.AbstractStatementTest;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.artemis.database.DatabaseProvider;
import org.apache.artemis.database.queries.MessagesPendingDeliverQueryForUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * This test helps validates the query used to fetch messages during depaging.
 * If using postgresql for example you can't use prefetch very large or enable auto commit as you would need the whole table in the memory
 */
@DisabledIf("isNoDatabaseSelected")
@ExtendWith(ParameterizedTestExtension.class)
public class ServerStressTest extends AbstractStatementTest {

   private static final String QUEUE_NAME = "ServerStressTest";

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @BeforeEach
   @Override
   public void setupTest() throws Exception {
      super.setupTest();
      configuration.addAddressConfiguration(new CoreAddressConfiguration().setName("DLQ").addRoutingType(RoutingType.ANYCAST).addQueueConfiguration(QueueConfiguration.of("DLQ").setRoutingType(RoutingType.ANYCAST)));
      configuration.addAddressConfiguration(new CoreAddressConfiguration().setName("ExpiryQueue").addRoutingType(RoutingType.ANYCAST).addQueueConfiguration(QueueConfiguration.of("ExpiryQueue").setRoutingType(RoutingType.ANYCAST)));
   }

   @Override
   protected boolean isDropDatabase() {
      // we try to reuse the database if running the test repetivie
      return false;
   }

   public static void main(String[] args) throws Exception {

      String databaseName = args[0];
      boolean pageJoinFetch = args.length > 1 && Boolean.parseBoolean(args[1]);

      System.out.println("Running with pageJoinFetch=" + pageJoinFetch);

      Database db = Database.valueOf(databaseName.toUpperCase());
      ClassLoader dbClassLoader = db.getDBClassLoader();
      if (dbClassLoader != null) {
         Thread.currentThread().setContextClassLoader(dbClassLoader);
         db.registerDriver();
      }

      System.out.println("Driver::" + db.getDriverClass());

      org.apache.commons.dbcp2.BasicDataSource dataSource = new org.apache.commons.dbcp2.BasicDataSource();
      dataSource.setDriverClassName(db.getDriverClass());
      //dataSource.addConnectionProperty("oracle.jdbc.defaultLobPrefetchSize", "1048576");
      dataSource.setUrl(db.getJdbcURI());

      DatabaseProvider databaseProvider = new DatabaseProvider(dataSource, null, null);
      String messagesTable = databaseProvider.getSqlProvider().getMessages();
      String referencesTable = databaseProvider.getSqlProvider().getRefs();
      String deliverSQL = databaseProvider.getSqlProvider().deliverPendingMessages(referencesTable);
      System.out.println(deliverSQL);
      try (Connection jdbcConnection = databaseProvider.getConnection()) {
         jdbcConnection.setAutoCommit(false);

         long queueID = getQueueID(jdbcConnection);
         int read = 0;
         long timeStart;
         ResultSet rset = null;

         PreparedStatement preparedStatement = jdbcConnection.prepareStatement("SELECT DR.MESSAGE_ID, DM.MEMORY_ESTIMATE FROM DB_REFERENCES DR, DB_MESSAGES DM WHERE DR.QUEUE_ID=? AND DR.PAGED='Y' AND DR.MESSAGE_ID = DM.MESSAGE_ID ORDER BY DR.MESSAGE_ID");

         for (int repeat = 0; repeat < 10; repeat++) {
            preparedStatement.setLong(1, queueID);
            preparedStatement.setFetchSize(10);
            timeStart = System.currentTimeMillis();
            rset = preparedStatement.executeQuery();
            read = 0;
            ArrayList<String> idsFound = new ArrayList<>();
            while (rset.next()) {
               idsFound.add(rset.getString(1));
               if (++read > 10_000) {
                  break;
               }
            }
            rset.close();
            System.out.println("ID query finished in " + (System.currentTimeMillis() - timeStart) + "ms, returned " + read + " elements");

            if (!idsFound.isEmpty()) {
               Statement recordSelect = jdbcConnection.createStatement();

               timeStart = System.currentTimeMillis();
               ResultSet blobRecords = recordSelect.executeQuery(databaseProvider.getSqlProvider().selectMessagesBlob("DB_MESSAGES", idsFound));
               int blobCount = 0;
               while (blobRecords.next()) {
                  blobCount++;
               }
               blobRecords.close();
               recordSelect.close();
               System.out.println("BLOB query finished in " + (System.currentTimeMillis() - timeStart) + "ms, returned " + blobCount + " elements");
            }
         }

         MessagesPendingDeliverQueryForUpdate pendingDeliverQueryForUpdate = new MessagesPendingDeliverQueryForUpdate(databaseProvider, jdbcConnection, pageJoinFetch);
         pendingDeliverQueryForUpdate.prepare();

         timeStart = System.currentTimeMillis();
         rset = pendingDeliverQueryForUpdate.execute(queueID);
         ArrayList<String> ids = new ArrayList<>();
         read = 0;
         while (rset.next()) {
            if (read++ > 10_000) {
               break;
            }
            ids.add(rset.getString(1));
         }
         System.out.println("Finished The query in " + ((System.currentTimeMillis() - timeStart)) + ", returned " + read + " elements");

         timeStart = System.currentTimeMillis();

         try (Statement statement = jdbcConnection.createStatement(); ResultSet blobRecords = statement.executeQuery(databaseProvider.getSqlProvider().selectMessagesBlob("DB_MESSAGES", ids))) {
            read++;
            while (blobRecords.next()) {
               read++;
            }

            System.out.println("Finished The blobs query in " + ((System.currentTimeMillis() - timeStart)) + ", returned " + read + " elements");


         }
      }
   }

   @TestTemplate
   public void testLargeQuery() throws Exception {
      assumeTrue(database != Database.HSQL, "HSQL uses an in-VM database, stress test not applicable");
      int threads = 10; // I played with 300 when I was debugging this test
      int messagesPerThread = 30_000; // I played with 300_000 when I was debugging this test

      int totalExpectedMessages = threads * messagesPerThread;

      boolean needsRecreate = true;

      try (Connection connection = storageConfiguration.getDatabaseProvider().getConnection()) {
         int countDBM = selectCount(connection, "db_messages");
         int countDBR = selectCount(connection, "db_references");

         if (countDBR == totalExpectedMessages && countDBM == totalExpectedMessages) {
            logger.info("no need to drop the database and recreate it. Test is being executed in loop and just needs to run the query portion.");
            needsRecreate = false;
         }
      } catch (Exception e) {
         logger.warn(e.getMessage());
      }

      if (needsRecreate) {

         dropDatabase();

         ActiveMQServer server = createServer(true, configuration);
         server.getConfiguration().getAddressSettings().clear();
         server.getConfiguration().addAddressSetting("#", new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeMessages(0));
         server.getConfiguration().addAddressConfiguration(new CoreAddressConfiguration().setName(QUEUE_NAME).addRoutingType(RoutingType.ANYCAST).addQueueConfiguration(QueueConfiguration.of(QUEUE_NAME).setAddress(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST)));
         server.start();

         Wait.assertNotNull(() -> server.locateQueue(QUEUE_NAME) != null);
         Queue queue1 = server.locateQueue(QUEUE_NAME);
         assertNotNull(queue1);
         queue1.getPagingStore().startPaging();

         ConnectionFactory factory = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616");
         ExecutorService service = Executors.newFixedThreadPool(threads);
         CountDownLatch done = new CountDownLatch(threads);
         AtomicInteger errors = new AtomicInteger(0);
         runAfter(service::shutdownNow);
         AtomicInteger sentMessages = new AtomicInteger(0);
         try (javax.jms.Connection connection = factory.createConnection()) {
            for (int tI = 0; tI < threads; tI++) {
               service.execute(() -> {
                  try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
                     javax.jms.Queue queue = session.createQueue(QUEUE_NAME);
                     MessageProducer producer = session.createProducer(queue);
                     for (int sendI = 0; sendI < messagesPerThread; sendI++) {
                        sentMessages.incrementAndGet();
                        producer.send(session.createTextMessage("single message"));
                        if (sendI % 1000 == 0) {
                           session.commit();
                           int sent = sentMessages.get();
                           logger.info("messages sent {} / {} ({} %)", sent, totalExpectedMessages, sent * 100L / totalExpectedMessages);
                        }
                     }
                     session.commit();
                  } catch (Exception e) {
                     logger.warn(e.getMessage(), e);
                     errors.incrementAndGet();
                  } finally {
                     done.countDown();
                  }
               });
            }

            assertTrue(done.await(1, TimeUnit.HOURS));
            assertEquals(0, errors.get());
            server.stop();
         }
      }

      String classPath = SpawnedVMSupport.getClassPath();
      String dbLibPath = SpawnedVMSupport.getClassPath(new java.io.File(getServerLocation(database.getName()), "lib"));
      if (!dbLibPath.isEmpty()) {
         classPath = classPath + java.io.File.pathSeparator + dbLibPath;
      }

      for (boolean pageJoinFetch : new boolean[]{true, false}) {
         logger.info("Running spawned query process with pageJoinFetch={}", pageJoinFetch);

         Process process = SpawnedVMSupport.spawnVM(classPath, ServerStressTest.getTestClassName(), new String[]{"-Xms512m", "-Xmx8G"}, true, database.getName(), String.valueOf(pageJoinFetch));
         assertTrue(process.waitFor(5, TimeUnit.MINUTES), "Spawned query process timed out (pageJoinFetch=" + pageJoinFetch + ")");
         assertEquals(0, process.exitValue(), "Spawned query process failed (pageJoinFetch=" + pageJoinFetch + ")");
      }
   }

   private static long getQueueID(Connection connection) throws SQLException {
      long queueID;
      try (PreparedStatement statement = connection.prepareStatement("SELECT QUEUE_ID FROM DB_QUEUE WHERE QUEUE_NAME = ?")) {
         statement.setString(1, QUEUE_NAME);
         try (ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next(), "Queue record not found in DB_QUEUE for " + QUEUE_NAME);
            queueID = resultSet.getLong(1);
         }
      }
      return queueID;
   }

}