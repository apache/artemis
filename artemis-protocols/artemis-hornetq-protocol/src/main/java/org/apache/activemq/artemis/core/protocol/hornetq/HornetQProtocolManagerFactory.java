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
package org.apache.activemq.artemis.core.protocol.hornetq;

import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.protocol.core.impl.CoreProtocolManagerFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManagerFactory;
import org.apache.activemq.artemis.utils.uri.BeanSupport;
import org.osgi.service.component.annotations.Component;

@Component(service = ProtocolManagerFactory.class)
public class HornetQProtocolManagerFactory extends CoreProtocolManagerFactory {

   public static final String HORNETQ_PROTOCOL_NAME = "HORNETQ";

   private static final String MODULE_NAME = "artemis-hornetq-protocol";

   private static String[] SUPPORTED_PROTOCOLS = {HORNETQ_PROTOCOL_NAME};

   @Override
   public ProtocolManager createProtocolManager(final ActiveMQServer server,
                                                final Map<String, Object> parameters,
                                                final List<BaseInterceptor> incomingInterceptors,
                                                List<BaseInterceptor> outgoingInterceptors) throws Exception {

      List<Interceptor> hqIncoming = filterInterceptors(incomingInterceptors);
      List<Interceptor> hqOutgoing = filterInterceptors(outgoingInterceptors);

      hqIncoming.add(new HQPropertiesConversionInterceptor(true));
      hqIncoming.add(new HQFilterConversionInterceptor());
      hqOutgoing.add(new HQPropertiesConversionInterceptor(false));

      BeanSupport.stripPasswords(parameters);
      return BeanSupport.setData(new HornetQProtocolManager(this, server, hqIncoming, hqOutgoing), parameters);
   }

   @Override
   public String[] getProtocols() {
      return SUPPORTED_PROTOCOLS;
   }

   @Override
   public String getModuleName() {
      return MODULE_NAME;
   }
}
