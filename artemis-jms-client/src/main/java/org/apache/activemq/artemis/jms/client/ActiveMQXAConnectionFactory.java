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
import javax.jms.XATopicConnectionFactory;

import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;

/**
 * A class that represents a XAConnectionFactory.
 * <p>
 * We consider the XAConnectionFactory to be the most complete possible option. It can be casted to any other connection
 * factory since it is fully functional
 */
public class ActiveMQXAConnectionFactory extends ActiveMQConnectionFactory implements XATopicConnectionFactory, XAQueueConnectionFactory {

   private static final long serialVersionUID = 743611571839154115L;

   public ActiveMQXAConnectionFactory() {
      super();
   }

   public ActiveMQXAConnectionFactory(String uri) {
      super(uri);
   }

   public ActiveMQXAConnectionFactory(String url, String user, String password) {
      super(url, user, password);
   }

   public ActiveMQXAConnectionFactory(ServerLocator serverLocator) {
      super(serverLocator);
   }

   public ActiveMQXAConnectionFactory(final boolean ha, final DiscoveryGroupConfiguration groupConfiguration) {
      super(ha, groupConfiguration);
   }

   public ActiveMQXAConnectionFactory(final boolean ha, final TransportConfiguration... initialConnectors) {
      super(ha, initialConnectors);
   }

   @Override
   public int getFactoryType() {
      return JMSFactoryType.XA_CF.intValue();
   }

}
