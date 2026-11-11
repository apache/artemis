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
package org.apache.activemq.artemis.uri.schema.connector;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.utils.uri.SchemaConstants;

public class InVMTransportConfigurationSchema extends AbstractTransportConfigurationSchema {

   /*
    * This is the same as org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.CONNECTIONS_ALLOWED,
    * but this Maven module can't see that class.
    */
   public static final String CONNECTIONS_ALLOWED = "connectionsAllowed";
   public static final String SECURITY_DOMAIN = "securityDomain";

   @Override
   public String getSchemaName() {
      return SchemaConstants.VM;
   }

   @Override
   protected List<TransportConfiguration> internalNewObject(URI uri,
                                                            Map<String, String> query,
                                                            String name) throws Exception {
      List<TransportConfiguration> configurations = new ArrayList<>();
      configurations.add(createTransportConfiguration(uri, query, name, getFactoryName()));
      return configurations;
   }

   @Override
   protected URI internalNewURI(List<TransportConfiguration> bean) throws Exception {
      return null;
   }

   protected String getFactoryName() {
      return "org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory";
   }

   public static TransportConfiguration createTransportConfiguration(URI uri,
                                                                     Map<String, String> query,
                                                                     String name,
                                                                     String factoryName) {
      Map<String, Object> inVmTransportConfig = new HashMap<>();
      inVmTransportConfig.put("serverId", uri.getHost());
      if (query.containsKey(CONNECTIONS_ALLOWED)) {
         inVmTransportConfig.put(CONNECTIONS_ALLOWED, query.get(CONNECTIONS_ALLOWED));
      } else if (query.containsKey(SECURITY_DOMAIN)) {
         inVmTransportConfig.put(SECURITY_DOMAIN, query.get(SECURITY_DOMAIN));
      }
      return new TransportConfiguration(factoryName, inVmTransportConfig, name);
   }
}
