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
package org.apache.activemq.artemis.tests.db.paging;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;
import java.sql.ResultSet;
import java.util.Collection;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.db.common.Database;
import org.apache.activemq.artemis.tests.db.common.ParameterDBTestBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ParameterizedTestExtension.class)
public class PageSizeTest extends ParameterDBTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Parameters(name = "db={0}")
   public static Collection<Object[]> parameters() {
      return convertParameters(Database.selectedList());
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      dropDatabase();
   }

   @Override
   protected final String getJDBCClassName() {
      return database.getDriverClass();
   }

   @TestTemplate
   public void testMaxSizePage() throws Exception {
      ActiveMQServer server = createServer(createDefaultConfig(0, true));
      server.getConfiguration().getAddressSettings().clear();
      server.getConfiguration().getAddressSettings().put("#", new AddressSettings().setMaxSizeMessages(1));
      DatabaseStorageConfiguration dbstoreConfig = (DatabaseStorageConfiguration) server.getConfiguration().getStoreConfiguration();
      dbstoreConfig.setMaxPageSizeBytes(30 * 1024);
      server.start();
      String addressName = "Q" + RandomUtil.randomUUIDString();
      server.addAddressInfo(new AddressInfo(addressName).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(QueueConfiguration.of(addressName).setRoutingType(RoutingType.ANYCAST).setDurable(true));

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(addressName));
         for (int i = 0; i < 100; i++) {
            BytesMessage message = session.createBytesMessage();
            message.writeBytes(new byte[1024]);
            producer.send(message);
         }
         session.commit();

         Queue queue = server.locateQueue(addressName);
         Wait.assertTrue(queue.getPagingStore()::isPaging, 1000, 100);
         long size = getMaxSizeBytesStored(queue);
         // organically all the pages should have less than 30K
         assertTrue(size <= dbstoreConfig.getMaxPageSizeBytes(), "size is " + size);

         // will send one very large message, but bellow the streaming size. We should have one record > page_size
         BytesMessage message = session.createBytesMessage();
         message.writeBytes(new byte[50 * 1024]);
         producer.send(message);
         session.commit();

         // Since we sent a large message (I mean, not streaming large, but larger than page-size,
         // a page with more than 30K should been created to allow the "large" message to be stored.
         Wait.assertTrue(() -> getMaxSizeBytesStored(queue) >= 50 * 1024);
      }
   }


   protected long getMaxSizeBytesStored(Queue queue) throws Exception {
      String tableName = queue.getPagingStore().getFolderName();

      try (java.sql.Connection sqlConn = database.getConnection()) {
         String sql = switch (database) {
            case MSSQL -> "SELECT MAX(LEN(DATA)) FROM " + tableName;
            case HSQL -> "SELECT MAX(OCTET_LENGTH(DATA)) FROM " + tableName;
            case ORACLE, DB2, MYSQL -> "SELECT MAX(LENGTH(DATA)) FROM " + tableName;
            case POSTGRES -> "SELECT MAX(OCTET_LENGTH(lo_get(DATA))) FROM  " + tableName;
            default -> null;
         };
         logger.debug("query: {}", sql);
         if (sql != null) {
            ResultSet resultSet = null;
            resultSet = sqlConn.createStatement().executeQuery(sql);
            assertTrue(resultSet.next());
            return resultSet.getLong(1);
         } else {
            return -1;
         }
      }
   }
}