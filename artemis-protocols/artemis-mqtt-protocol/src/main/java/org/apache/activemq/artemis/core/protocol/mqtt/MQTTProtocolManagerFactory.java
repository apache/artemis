/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.protocol.mqtt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.spi.core.protocol.AbstractProtocolManagerFactory;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManagerFactory;
import org.apache.activemq.artemis.utils.uri.BeanSupport;
import org.osgi.service.component.annotations.Component;

@Component(service = ProtocolManagerFactory.class)
public class MQTTProtocolManagerFactory extends AbstractProtocolManagerFactory<MQTTInterceptor> {

   public static final String MQTT_PROTOCOL_NAME = "MQTT";

   private static final String MODULE_NAME = "artemis-mqtt-protocol";

   private static final String[] SUPPORTED_PROTOCOLS = {MQTT_PROTOCOL_NAME};

   @Override
   public ProtocolManager createProtocolManager(ActiveMQServer server,
                                                final Map<String, Object> parameters,
                                                List<BaseInterceptor> incomingInterceptors,
                                                List<BaseInterceptor> outgoingInterceptors) throws Exception {

      BeanSupport.stripPasswords(parameters);
      return BeanSupport.setData(new MQTTProtocolManager(server, incomingInterceptors, outgoingInterceptors), parameters);
   }

   @Override
   public List<MQTTInterceptor> filterInterceptors(List<BaseInterceptor> interceptors) {
      return internalFilterInterceptors(MQTTInterceptor.class, interceptors);
   }

   @Override
   public String[] getProtocols() {
      return SUPPORTED_PROTOCOLS;
   }

   @Override
   public String getModuleName() {
      return MODULE_NAME;
   }

   @Override
   public void loadProtocolServices(ActiveMQServer server, List<ActiveMQComponent> services) throws Exception {
      server.registerRecordsLoader(MQTTStateManager.getInstance(server)::reload);
      services.add(new MQTTPeriodicTasks(server, server.getScheduledPool()));
   }

   public class MQTTPeriodicTasks extends ActiveMQScheduledComponent {
      final ActiveMQServer server;
      public MQTTPeriodicTasks(ActiveMQServer server, ScheduledExecutorService scheduledExecutorService) {
         super(scheduledExecutorService, null, server.getConfiguration().getMqttSessionScanInterval(), TimeUnit.MILLISECONDS, false);
         this.server = server;
      }
      @Override
      public void run() {
         try {
            MQTTStateManager.getInstance(server).scanSessions();
         } catch (Exception e) {
            MQTTLogger.LOGGER.unableToScanSessions(e);
         }
      }
   }
}
