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
package org.apache.activemq.artemis.osgi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManagerFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * Tracks the available ProtocolManagerFactory services as well as the required protocols. When a new service appears
 * the factory is added to the server. When all needed protocols are present the server is started. When required a
 * service disappears the server is stopped.
 */
@SuppressWarnings("rawtypes")
public class ProtocolTracker implements ServiceTrackerCustomizer<ProtocolManagerFactory<Interceptor>, ProtocolManagerFactory<Interceptor>> {

   private String name;
   private BundleContext context;
   private Map<String, Boolean> protocols;
   private ServerTrackerCallBack callback;

   public ProtocolTracker(String name,
                          BundleContext context,
                          String[] requiredProtocols,
                          ServerTrackerCallBack callback) {
      this.name = name;
      this.context = context;
      this.callback = callback;
      this.protocols = new HashMap<>();
      for (String requiredProtocol : requiredProtocols) {
         this.protocols.put(requiredProtocol, false);
      }
      ActiveMQOsgiLogger.LOGGER.brokerConfigFound(name, Arrays.asList(requiredProtocols).toString());

      //CORE is always registered as a protocol in RemoteServiceImpl
      this.protocols.put(ActiveMQClient.DEFAULT_CORE_PROTOCOL, true);

      //if no protocols are specified we need to start artemis
      List<String> missing = getMissing();
      if (missing.isEmpty()) {
         try {
            callback.start();
         } catch (Exception e) {
            ActiveMQOsgiLogger.LOGGER.errorStartingBroker(name, e);
         }
      }
   }

   @Override
   public ProtocolManagerFactory addingService(ServiceReference<ProtocolManagerFactory<Interceptor>> reference) {
      ProtocolManagerFactory<Interceptor> pmf = context.getService(reference);
      callback.addFactory(pmf);
      for (String protocol : pmf.getProtocols()) {
         protocolAdded(protocol);
      }

      return pmf;
   }

   @Override
   public void modifiedService(ServiceReference<ProtocolManagerFactory<Interceptor>> reference,
                               ProtocolManagerFactory<Interceptor> pmf) {
      // Not supported
   }

   @Override
   public void removedService(ServiceReference<ProtocolManagerFactory<Interceptor>> reference,
                              ProtocolManagerFactory<Interceptor> pmf) {
      for (String protocol : pmf.getProtocols()) {
         protocolRemoved(protocol);
      }
      callback.removeFactory(pmf);
   }

   private void protocolAdded(String protocol) {
      Boolean present = this.protocols.get(protocol);
      if (present != null && !present) {
         this.protocols.put(protocol, true);
         List<String> missing = getMissing();
         ActiveMQOsgiLogger.LOGGER.protocolWasAddedForBroker(protocol, name, (missing.isEmpty() ? "Starting broker." : "Still waiting for " + missing));
         if (missing.isEmpty()) {
            try {
               callback.start();
            } catch (Exception e) {
               ActiveMQOsgiLogger.LOGGER.errorStartingBroker(name, e);
            }
         }
      }
   }

   private void protocolRemoved(String protocol) {
      Boolean present = this.protocols.get(protocol);
      if (present != null && present) {
         List<String> missing = getMissing();
         ActiveMQOsgiLogger.LOGGER.protocolWasRemovedForBroker(protocol, name, (missing.isEmpty() ? "Stopping broker. " : ""));
         if (missing.isEmpty()) {
            try {
               callback.stop();
            } catch (Exception e) {
               ActiveMQOsgiLogger.LOGGER.errorStoppingBroker(name, e);
            }
         }
         this.protocols.put(protocol, false);
      }
   }

   private List<String> getMissing() {
      List<String> missing = new ArrayList<>();
      for (String protocol : protocols.keySet()) {
         Boolean present = protocols.get(protocol);
         if (!present) {
            missing.add(protocol);
         }
      }
      return missing;
   }

}
