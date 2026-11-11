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
package org.apache.activemq.artemis.uri;

import java.net.URI;
import java.util.Map;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.uri.schema.connector.InVMTransportConfigurationSchema;
import org.apache.activemq.artemis.uri.schema.serverLocator.InVMServerLocatorSchema;
import org.apache.activemq.artemis.utils.uri.BeanSupport;
import org.apache.activemq.artemis.utils.uri.SchemaConstants;

public class InVMSchema extends AbstractCFSchema {

   @Override
   public String getSchemaName() {
      return SchemaConstants.VM;
   }

   @Override
   protected ActiveMQConnectionFactory internalNewObject(URI uri,
                                                         Map<String, String> query,
                                                         String name) throws Exception {
      JMSConnectionOptions options = newConectionOptions(uri, query);
      ActiveMQConnectionFactory factory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(options.getFactoryTypeEnum(), InVMTransportConfigurationSchema.createTransportConfiguration(uri, query, name, "org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory"));
      BeanSupport.setData(uri, factory, query);
      BeanSupport.setData(uri, factory.getServerLocator(), query);
      return factory;
   }

   @Override
   protected URI internalNewURI(ActiveMQConnectionFactory bean) throws Exception {
      return InVMServerLocatorSchema.getUri(bean.getStaticConnectors());
   }
}
