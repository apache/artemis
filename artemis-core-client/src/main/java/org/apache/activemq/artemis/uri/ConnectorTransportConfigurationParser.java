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

import java.util.List;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.uri.schema.connector.InVMTransportConfigurationSchema;
import org.apache.activemq.artemis.uri.schema.connector.TCPTransportConfigurationSchema;
import org.apache.activemq.artemis.utils.uri.URIFactory;

public class ConnectorTransportConfigurationParser extends URIFactory<List<TransportConfiguration>, String> {

   public ConnectorTransportConfigurationParser() {
      this(true);
   }

   public ConnectorTransportConfigurationParser(boolean invm) {
      registerSchema(new TCPTransportConfigurationSchema(TransportConstants.ALLOWABLE_CONNECTOR_KEYS));
      if (invm) {
         registerSchema(new InVMTransportConfigurationSchema());
      }
   }
}
