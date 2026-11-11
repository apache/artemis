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
package org.apache.activemq.artemis.jms.persistence;

import java.util.List;

import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.jms.persistence.config.PersistedBindings;
import org.apache.activemq.artemis.jms.persistence.config.PersistedConnectionFactory;
import org.apache.activemq.artemis.jms.persistence.config.PersistedDestination;
import org.apache.activemq.artemis.jms.persistence.config.PersistedType;

public interface JMSStorageManager extends ActiveMQComponent {

   void load() throws Exception;

   void storeDestination(PersistedDestination destination) throws Exception;

   void deleteDestination(PersistedType type, String name) throws Exception;

   List<PersistedDestination> recoverDestinations();

   void deleteConnectionFactory(String connectionFactory) throws Exception;

   void storeConnectionFactory(PersistedConnectionFactory connectionFactory) throws Exception;

   List<PersistedConnectionFactory> recoverConnectionFactories();

   void addBindings(PersistedType type, String name, String... address) throws Exception;

   List<PersistedBindings> recoverPersistedBindings() throws Exception;

   void deleteBindings(PersistedType type, String name, String address) throws Exception;

   void deleteBindings(PersistedType type, String name) throws Exception;
}
