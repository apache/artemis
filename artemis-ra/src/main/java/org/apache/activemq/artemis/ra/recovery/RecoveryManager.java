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
package org.apache.activemq.artemis.ra.recovery;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.ra.ActiveMQRALogger;
import org.apache.activemq.artemis.service.extensions.xa.recovery.ActiveMQRegistry;
import org.apache.activemq.artemis.service.extensions.xa.recovery.ActiveMQRegistryImpl;
import org.apache.activemq.artemis.service.extensions.xa.recovery.XARecoveryConfig;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;

public final class RecoveryManager {

   private ActiveMQRegistry registry;

   private static final String RESOURCE_RECOVERY_CLASS_NAMES = "org.jboss.as.messaging.jms.AS7RecoveryRegistry;" + "org.jboss.as.integration.activemq.recovery.AS5RecoveryRegistry";

   private final Set<XARecoveryConfig> resources = new ConcurrentHashSet<>();

   public void start(final boolean useAutoRecovery) {
      if (useAutoRecovery) {
         locateRecoveryRegistry();
      } else {
         registry = null;
      }
   }

   public XARecoveryConfig register(ActiveMQConnectionFactory factory,
                                    String userName,
                                    String password,
                                    Map<String, String> properties) {
      ActiveMQRALogger.LOGGER.debug("registering recovery for factory : " + factory);

      XARecoveryConfig config = XARecoveryConfig.newConfig(factory, userName, password, properties);
      resources.add(config);
      if (registry != null) {
         registry.register(config);
      }
      return config;
   }

   public void unRegister(XARecoveryConfig resourceRecovery) {
      if (registry != null) {
         registry.unRegister(resourceRecovery);
      }
   }

   public void stop() {
      if (registry != null) {
         for (XARecoveryConfig recovery : resources) {
            registry.unRegister(recovery);
         }
         registry.stop();
      }

      resources.clear();
   }

   private void locateRecoveryRegistry() {
      String[] locatorClasses = RESOURCE_RECOVERY_CLASS_NAMES.split(";");

      for (String locatorClasse : locatorClasses) {
         try {
            ServiceLoader<ActiveMQRegistry> sl = ServiceLoader.load(ActiveMQRegistry.class, RecoveryManager.class.getClassLoader());
            if (sl.iterator().hasNext()) {
               registry = sl.iterator().next();
            } else {
               registry = ActiveMQRegistryImpl.getInstance();
            }
         } catch (Throwable e) {
            ActiveMQRALogger.LOGGER.debug("unable to load  recovery registry " + locatorClasse, e);
         }
         if (registry != null) {
            break;
         }
      }

      if (registry != null) {
         ActiveMQRALogger.LOGGER.debug("Recovery Registry located = " + registry);
      }
   }

   public Set<XARecoveryConfig> getResources() {
      return resources;
   }
}
