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

import java.util.Collection;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.paging.impl.Page;
import org.apache.activemq.artemis.core.paging.impl.PagingStoreImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.jdbc.store.file.JDBCSequentialFile;
import org.apache.activemq.artemis.tests.db.common.Database;
import org.apache.activemq.artemis.tests.db.common.ParameterDBTestBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(ParameterizedTestExtension.class)
public class NetworkTimeoutCheckTest extends ParameterDBTestBase {

   private static final int TIMEOUT = 33_333;

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

   // check if the sync timeout is propated to the page file
   @TestTemplate
   public void testDBTimeoutPropagated() throws Exception {
      ActiveMQServer server = createServer(createDefaultConfig(0, true));
      server.start();


      server.addAddressInfo(new AddressInfo(getName()).addRoutingType(RoutingType.ANYCAST));
      Queue queue = server.createQueue(QueueConfiguration.of(getName()).setRoutingType(RoutingType.ANYCAST).setDurable(true));
      queue.getPagingStore().startPaging();
      PagingStoreImpl store = (PagingStoreImpl) queue.getPagingStore();
      Page page = store.getCurrentPage();
      JDBCSequentialFile file = (JDBCSequentialFile) page.getFile();
      assertEquals(TIMEOUT, file.getNetworkTimeoutMillis());

      server.stop();
   }

   @Override
   protected DatabaseStorageConfiguration createDefaultDatabaseStorageConfiguration() {
      DatabaseStorageConfiguration dbStorageConfiguration = super.createDefaultDatabaseStorageConfiguration();
      dbStorageConfiguration.setJdbcNetworkTimeout(TIMEOUT);
      return dbStorageConfiguration;
   }

}