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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.persistence.config.PersistedConnector;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(ParameterizedTestExtension.class)
public class ConnectorStorageTest extends StorageManagerTestBase {

   @Parameters(name = "storeType={0}")
   public static Collection<Object[]> data() {
      Object[][] params = new Object[][]{{StoreConfiguration.StoreType.FILE}, {StoreConfiguration.StoreType.DATABASE}};
      return Arrays.asList(params);
   }

   public ConnectorStorageTest(StoreConfiguration.StoreType storeType) {
      super(storeType);
   }

   @TestTemplate
   public void testStoreConnector() throws Exception {
      final String NAME = RandomUtil.randomUUIDString();
      final String URL = RandomUtil.randomUUIDString();

      PersistedConnector connector = new PersistedConnector(NAME, URL);

      journal.storeConnector(connector);

      rebootStorage();

      List<PersistedConnector> connectors = journal.recoverConnectors();

      assertEquals(1, connectors.size());

      PersistedConnector persistedConnector = connectors.get(0);
      assertEquals(NAME, persistedConnector.getName());
      assertEquals(URL, persistedConnector.getUrl());
   }
}
