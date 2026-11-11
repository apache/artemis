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
package org.apache.activemq.artemis.core.protocol.core.impl;

import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.core.message.impl.CoreMessagePersister;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.spi.core.protocol.AbstractProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.utils.uri.BeanSupport;

public class CoreProtocolManagerFactory extends AbstractProtocolManagerFactory<Interceptor> {

   private static String[] SUPPORTED_PROTOCOLS = {ActiveMQClient.DEFAULT_CORE_PROTOCOL};

   private static final String MODULE_NAME = "artemis-server";

   @Override
   public Persister<Message>[] getPersister() {
      return new Persister[]{CoreMessagePersister.getInstance()};
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ProtocolManager createProtocolManager(final ActiveMQServer server,
                                                Map<String, Object> parameters,
                                                final List<BaseInterceptor> incomingInterceptors,
                                                List<BaseInterceptor> outgoingInterceptors) throws Exception {
      BeanSupport.stripPasswords(parameters);
      return BeanSupport.setData(new CoreProtocolManager(this, server, filterInterceptors(incomingInterceptors), filterInterceptors(outgoingInterceptors)), parameters);
   }

   @Override
   public List<Interceptor> filterInterceptors(List<BaseInterceptor> interceptors) {
      // This is using this tool method
      // it wouldn't be possible to write a generic method without this class parameter
      // and I didn't want to bloat the cllaers for this
      return internalFilterInterceptors(Interceptor.class, interceptors);
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
