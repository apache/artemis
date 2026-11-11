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
package org.apache.activemq.artemis.jms.client;

import javax.jms.QueueConnectionFactory;

import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;

/**
 * {@inheritDoc}
 */
public class ActiveMQQueueConnectionFactory extends ActiveMQConnectionFactory implements QueueConnectionFactory {

   private static final long serialVersionUID = 5312455021322463546L;

   public ActiveMQQueueConnectionFactory() {
      super();
   }

   public ActiveMQQueueConnectionFactory(String url) {
      super(url);
   }

   public ActiveMQQueueConnectionFactory(ServerLocator serverLocator) {
      super(serverLocator);
   }

   public ActiveMQQueueConnectionFactory(boolean ha, final DiscoveryGroupConfiguration groupConfiguration) {
      super(ha, groupConfiguration);
   }

   public ActiveMQQueueConnectionFactory(String url, String user, String password) {
      super(url, user, password);
   }

   public ActiveMQQueueConnectionFactory(boolean ha, TransportConfiguration... initialConnectors) {
      super(ha, initialConnectors);
   }

   @Override
   public int getFactoryType() {
      return JMSFactoryType.QUEUE_CF.intValue();
   }
}
