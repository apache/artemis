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
package org.apache.activemq.artemis.core.config.storage;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;

public class FileStorageConfiguration implements StoreConfiguration {

   private String messageTableName = ActiveMQDefaultConfiguration.getDefaultMessageTableName();

   private String bindingsTableName = ActiveMQDefaultConfiguration.getDefaultBindingsTableName();

   private String jdbcConnectionUrl = ActiveMQDefaultConfiguration.getDefaultDatabaseUrl();

   @Override
   public StoreType getStoreType() {
      return StoreType.FILE;
   }

   public String getMessageTableName() {
      return messageTableName;
   }

   public void setMessageTableName(String messageTableName) {
      this.messageTableName = messageTableName;
   }

   public String getBindingsTableName() {
      return bindingsTableName;
   }

   public void setBindingsTableName(String bindingsTableName) {
      this.bindingsTableName = bindingsTableName;
   }

   public void setJdbcConnectionUrl(String jdbcConnectionUrl) {
      this.jdbcConnectionUrl = jdbcConnectionUrl;
   }

   public String getJdbcConnectionUrl() {
      return jdbcConnectionUrl;
   }
}
