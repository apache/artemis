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
package org.apache.activemq.artemis.uri.schema.serverLocator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.uri.schema.connector.InVMTransportConfigurationSchema;
import org.apache.activemq.artemis.utils.uri.BeanSupport;
import org.apache.activemq.artemis.utils.uri.SchemaConstants;

public class InVMServerLocatorSchema extends AbstractServerLocatorSchema {

   @Override
   public String getSchemaName() {
      return SchemaConstants.VM;
   }

   @Override
   protected ServerLocator internalNewObject(URI uri, Map<String, String> query, String name) throws Exception {
      TransportConfiguration tc = InVMTransportConfigurationSchema.createTransportConfiguration(uri, query, name, "org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory");
      ServerLocator factory = ActiveMQClient.createServerLocatorWithoutHA(tc);
      BeanSupport.stripPasswords(query);
      return BeanSupport.setData(uri, factory, query);
   }

   @Override
   protected URI internalNewURI(ServerLocator bean) throws Exception {
      return getUri(bean.getStaticTransportConfigurations());
   }

   public static URI getUri(TransportConfiguration[] configurations) throws URISyntaxException {
      String host = "0";
      if (configurations != null && configurations.length > 0) {
         TransportConfiguration configuration = configurations[0];
         Map<String, Object> params = configuration.getParams();
         host = params.get("serverId") == null ? host : params.get("serverId").toString();
      }
      return new URI(SchemaConstants.VM, null, host, -1, null, null, null);
   }
}
