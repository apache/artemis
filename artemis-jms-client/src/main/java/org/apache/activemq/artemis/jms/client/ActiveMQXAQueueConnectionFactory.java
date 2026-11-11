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

import javax.jms.XAQueueConnectionFactory;

import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;

/**
 * {@inheritDoc}
 */
public class ActiveMQXAQueueConnectionFactory extends ActiveMQConnectionFactory implements XAQueueConnectionFactory {

   private static final long serialVersionUID = 8612457847251087454L;

   public ActiveMQXAQueueConnectionFactory() {
      super();
   }

   public ActiveMQXAQueueConnectionFactory(String uri) {
      super(uri);
   }

   public ActiveMQXAQueueConnectionFactory(String url, String user, String password) {
      super(url, user, password);
   }

   public ActiveMQXAQueueConnectionFactory(ServerLocator serverLocator) {
      super(serverLocator);
   }

   public ActiveMQXAQueueConnectionFactory(final boolean ha, final DiscoveryGroupConfiguration groupConfiguration) {
      super(ha, groupConfiguration);
   }

   public ActiveMQXAQueueConnectionFactory(final boolean ha, final TransportConfiguration... initialConnectors) {
      super(ha, initialConnectors);
   }

   @Override
   public int getFactoryType() {
      return JMSFactoryType.QUEUE_XA_CF.intValue();
   }

}
