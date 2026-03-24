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
package org.apache.activemq.artemis.jms.persistence.config;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.jms.server.config.ConnectionFactoryConfiguration;
import org.apache.activemq.artemis.jms.server.config.impl.ConnectionFactoryConfigurationImpl;

public class PersistedConnectionFactory implements EncodingSupport {


   private long id;

   private ConnectionFactoryConfiguration config;

   public PersistedConnectionFactory() {
      super();
   }

   /**
    * @param config
    */
   public PersistedConnectionFactory(final ConnectionFactoryConfiguration config) {
      super();
      this.config = config;
   }



   /**
    * @return the id
    */
   public long getId() {
      return id;
   }

   public void setId(final long id) {
      this.id = id;
   }

   public String getName() {
      return config.getName();
   }

   /**
    * @return the config
    */
   public ConnectionFactoryConfiguration getConfig() {
      return config;
   }

   @Override
   public void decode(final ActiveMQBuffer buffer) {
      config = new ConnectionFactoryConfigurationImpl();
      config.decode(buffer);
   }

   @Override
   public void encode(final ActiveMQBuffer buffer) {
      config.encode(buffer);
   }

   @Override
   public int getEncodeSize() {
      return config.getEncodeSize();
   }

}
