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
package org.apache.activemq.artemis.jdbc.store.sql;

import org.apache.activemq.artemis.tests.util.ArtemisTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class PropertySQLProviderTest extends ArtemisTestCase {

   @Test
   public void testGetProperty() {
      for (SQLProvider.DatabaseStoreType storeType : SQLProvider.DatabaseStoreType.values()) {
         PropertySQLProvider factory = (PropertySQLProvider) new PropertySQLProvider.Factory((PropertySQLProvider.Factory.SQLDialect) null).create("who-cares", storeType);
         factory.sql("create-file-table");
      }
   }

   @Test
   public void testGetMissingProperty() {
      for (SQLProvider.DatabaseStoreType storeType : SQLProvider.DatabaseStoreType.values()) {
         PropertySQLProvider factory = (PropertySQLProvider) new PropertySQLProvider.Factory((PropertySQLProvider.Factory.SQLDialect) null).create("who-cares", storeType);
         try {
            factory.sql("missing-property");
            fail();
         } catch (IllegalStateException e) {
            // expected
         }
      }
   }

   @Test
   public void testGetMissingPropertyNoCheck() {
      for (SQLProvider.DatabaseStoreType storeType : SQLProvider.DatabaseStoreType.values()) {
         PropertySQLProvider factory = (PropertySQLProvider) new PropertySQLProvider.Factory((PropertySQLProvider.Factory.SQLDialect) null).create("who-cares", storeType);
         assertNull(factory.sql("missing-property", false));
      }
   }
}
