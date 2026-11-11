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
package org.apache.activemq.artemis.uri.schemas.acceptor;

import java.net.URI;
import java.util.Set;

import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.uri.schema.connector.TCPTransportConfigurationSchema;

public class TCPAcceptorTransportConfigurationSchema extends TCPTransportConfigurationSchema {

   public TCPAcceptorTransportConfigurationSchema(Set<String> allowableProperties) {
      super(allowableProperties);
   }

   @Override
   public String getFactoryName(URI uri) {
      return NettyAcceptorFactory.class.getName();
   }
}
