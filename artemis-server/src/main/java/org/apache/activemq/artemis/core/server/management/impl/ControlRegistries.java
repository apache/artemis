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
package org.apache.activemq.artemis.core.server.management.impl;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.apache.activemq.artemis.api.core.management.AcceptorControl;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.BaseBroadcastGroupControl;
import org.apache.activemq.artemis.api.core.management.BridgeControl;
import org.apache.activemq.artemis.api.core.management.BrokerConnectionControl;
import org.apache.activemq.artemis.api.core.management.ClusterConnectionControl;
import org.apache.activemq.artemis.api.core.management.ConnectionRouterControl;
import org.apache.activemq.artemis.api.core.management.DivertControl;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.RemoteBrokerConnectionControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.management.impl.ActiveMQServerControlImpl;
import org.apache.activemq.artemis.core.server.management.HawtioSecurityControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ControlRegistries {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServerControl serverControl;

   public final Map<String, QueueControl> queueControls;

   public final Map<String, AddressControl> addressControls;

   public final Map<String, AcceptorControl> accepterControls;

   public final Map<String, BaseBroadcastGroupControl> broadcastGroupControls;

   public final Map<String, BrokerConnectionControl> brokerConnectionControls;

   public final Map<String, RemoteBrokerConnectionControl> remoteBrokerConnectionControls;

   public final Map<String, BridgeControl> bridgeControls;

   public final Map<String, ClusterConnectionControl> clusterConnectionControl;

   public final Map<String, ConnectionRouterControl> connectionRouterControls;

   public HawtioSecurityControl hawtioSecurityControl;

   public final Map<String, DivertControl> divertControls;

   public final Map<String, Object> untypedControls;

   public ControlRegistries() {
      queueControls = new ConcurrentHashMap<>();
      addressControls = new ConcurrentHashMap<>();
      accepterControls = new ConcurrentHashMap<>();
      broadcastGroupControls = new ConcurrentHashMap<>();
      brokerConnectionControls = new ConcurrentHashMap<>();
      remoteBrokerConnectionControls = new ConcurrentHashMap<>();
      bridgeControls = new ConcurrentHashMap<>();
      clusterConnectionControl = new ConcurrentHashMap<>();
      connectionRouterControls = new ConcurrentHashMap<>();
      untypedControls = new ConcurrentHashMap<>();
      divertControls = new ConcurrentHashMap<>();
   }

   public void registerQueueControl(final String name, final QueueControl queueControl) {
      Object replaced = queueControls.put(name, queueControl);
      logRegistration(name, replaced, queueControl);
   }

   public void unregisterQueueControl(final String name) {
      Object removed = queueControls.remove(name);
      logUnregistration(name, removed);
   }

   public int getQueueControlCount() {
      return queueControls.size();
   }

   public List<QueueControl> getQueueControls(Predicate<QueueControl> predicate) {
      if (predicate == null) {
         return List.copyOf(queueControls.values());
      }
      return queueControls.values().stream().filter(predicate).toList();
   }

   public QueueControl getQueueControl(String name) {
      return queueControls.get(name);
   }

   public List<String> getQueueControlNames() {
      return List.copyOf(queueControls.keySet());
   }

   public void registerAddressControl(final String name, final AddressControl addressControl) {
      Object replaced = addressControls.put(name, addressControl);
      logRegistration(name, replaced, addressControl);
   }

   public void unregisterAddressControl(final String name) {
      Object removed = addressControls.remove(name);
      logUnregistration(name, removed);
   }

   public int getAddressControlCount() {
      return addressControls.size();
   }

   public List<AddressControl> getAddressControls(Predicate<AddressControl> predicate) {
      if (predicate == null) {
         return List.copyOf(addressControls.values());
      }
      return addressControls.values().stream().filter(predicate).toList();
   }

   public AddressControl getAddressControl(String name) {
      return addressControls.get(name);
   }

   public List<String> getAddressControlNames() {
      return List.copyOf(addressControls.keySet());
   }

   public void registerAcceptor(String name, AcceptorControl acceptorControl) {
      Object replaced = accepterControls.put(name, acceptorControl);
      logRegistration(name, replaced, acceptorControl);
   }

   public void unregisterAcceptorControl(String name) {
      Object removed = accepterControls.remove(name);
      logUnregistration(name, removed);
   }

   public AcceptorControl getAcceptorControl(String name) {
      return accepterControls.get(name);
   }

   public List<AcceptorControl> getAcceptorControls() {
      return List.copyOf(accepterControls.values());
   }

   public List<String> getAcceptorControlNames() {
      return List.copyOf(accepterControls.keySet());
   }

   public void registerBroadcastGroupControl(String name, BaseBroadcastGroupControl control) {
      Object replaced = broadcastGroupControls.put(name, control);
      logRegistration(name, replaced, control);
   }

   public void unregisterBroadcastGroupControls(String name) {
      Object removed = broadcastGroupControls.remove(name);
      logUnregistration(name, removed);
   }

   public List<BaseBroadcastGroupControl> getBroadcastGroupControls() {
      return List.copyOf(broadcastGroupControls.values());
   }

   public List<String> getBroadcastGroupControlNames() {
      return List.copyOf(broadcastGroupControls.keySet());
   }

   public void registerBrokerConnectionControl(String name, BrokerConnectionControl control) {
      Object replaced = brokerConnectionControls.put(name, control);
      logRegistration(name, replaced, control);
   }

   public void unregisterBrokerConnectionControl(String name) {
      Object removed = brokerConnectionControls.remove(name);
      logUnregistration(name, removed);
   }

   public List<BrokerConnectionControl> getBrokerConnectionControls() {
      return List.copyOf(brokerConnectionControls.values());
   }

   public BrokerConnectionControl getBrokerConnectionControl(String name) {
      return brokerConnectionControls.get(name);
   }

   public List<String> getBrokerConnectionControlNames() {
      return List.copyOf(brokerConnectionControls.keySet());
   }

   public void registerRemoteBrokerConnectionControl(String name, RemoteBrokerConnectionControl control) {
      Object replaced = remoteBrokerConnectionControls.put(name, control);
      logRegistration(name, replaced, control);
   }

   public void unregisterRemoteBrokerConnectionControl(String name) {
      Object removed = remoteBrokerConnectionControls.remove(name);
      logUnregistration(name, removed);
   }

   public List<RemoteBrokerConnectionControl> getRemoteBrokerConnectionControls() {
      return List.copyOf(remoteBrokerConnectionControls.values());
   }

   public RemoteBrokerConnectionControl getRemoteBrokerConnectionControl(String name) {
      return remoteBrokerConnectionControls.get(name);
   }

   public List<String> getRemoteBrokerConnectionControlNames() {
      return List.copyOf(remoteBrokerConnectionControls.keySet());
   }

   public void registerBridgeControl(String name, BridgeControl control) {
      Object replaced = bridgeControls.put(name, control);
      logRegistration(name, replaced, control);
   }

   public void unregisterBridgeControl(String name) {
      Object removed = bridgeControls.remove(name);
      logUnregistration(name, removed);
   }

   public List<BridgeControl> getBridgeControls() {
      return List.copyOf(bridgeControls.values());
   }

   public List<String> getBridgeControlNames() {
      return List.copyOf(bridgeControls.keySet());
   }

   public int getBridgeControlCount() {
      return bridgeControls.size();
   }

   public void registerClusterConnectionControl(String name, ClusterConnectionControl control) {
      Object replaced = clusterConnectionControl.put(name, control);
      logRegistration(name, replaced, control);
   }

   public void unregisterClusterConnectionControl(String name) {
      Object removed = clusterConnectionControl.remove(name);
      logUnregistration(name, removed);
   }

   public List<ClusterConnectionControl> getClusterConnectionControls() {
      return List.copyOf(clusterConnectionControl.values());
   }

   public List<String> getClusterConnectionControlNames() {
      return List.copyOf(clusterConnectionControl.keySet());
   }

   public void registerConnectionRouterControl(String name, ConnectionRouterControl connectionRouterControl) {
      Object replaced = connectionRouterControls.put(name, connectionRouterControl);
      logRegistration(name, replaced, connectionRouterControl);
   }

   public void unregisterConnectionRouterControl(String name) {
      Object removed = connectionRouterControls.remove(name);
      logUnregistration(name, removed);
   }

   public List<ConnectionRouterControl> getConnectionRouterControls() {
      return List.copyOf(connectionRouterControls.values());
   }

   public ConnectionRouterControl getConnectionRouterControl(String name) {
      return connectionRouterControls.get(name);
   }

   public List<String> getConnectionRouterControlNames() {
      return List.copyOf(connectionRouterControls.keySet());
   }

   public void registerHawtioSecurityControl(HawtioSecurityControl hawtioSecurityControl) {
      Object original = this.hawtioSecurityControl;
      this.hawtioSecurityControl = hawtioSecurityControl;
      logRegistration(ResourceNames.MANAGEMENT_SECURITY, original, hawtioSecurityControl);
   }

   public void unregisterHawtioSecurityControl() {
      Object removed = hawtioSecurityControl;
      hawtioSecurityControl = null;
      logUnregistration(ResourceNames.MANAGEMENT_SECURITY, removed);
   }

   public HawtioSecurityControl getHawtioSecurity() {
      return hawtioSecurityControl;
   }

   public void registerUntypedControl(String name, Object control) {
      untypedControls.put(name, control);
   }

   public void unregisterUntypedControl(String name) {
      Object removed = untypedControls.remove(name);
      logUnregistration(name, removed);
   }

   public Object getUntypedControl(String name) {
      return untypedControls.get(name);
   }

   public List<String> getUntypedControlNames() {
      return List.copyOf(untypedControls.keySet());
   }

   public List<Object> getUntypedControls(Class<?> resourceType) {
      return untypedControls.values().stream().filter(resourceType::isInstance).toList();
   }

   public void registerServer(ActiveMQServerControlImpl serverControl) {
      Object original = this.serverControl;
      this.serverControl = serverControl;
      logRegistration(ResourceNames.BROKER, original, serverControl);
   }

   public void unregisterServer() {
      Object removed = serverControl;
      serverControl = null;
      logUnregistration(ResourceNames.BROKER, removed);
   }

   public ActiveMQServerControl getServerControl() {
      return serverControl;
   }

   public void registerDivertControl(String name, DivertControl divertControl) {
      Object replaced = divertControls.put(name, divertControl);
      logRegistration(name, replaced, divertControl);
   }

   public void unregisterDivertControl(String name) {
      Object removed = divertControls.remove(name);
      logUnregistration(name, removed);
   }

   public List<DivertControl> getDivertControls() {
      return List.copyOf(divertControls.values());
   }

   public List<String> getDivertControlNames() {
      return List.copyOf(divertControls.keySet());
   }

   public void clear() {
      serverControl = null;
      addressControls.clear();
      queueControls.clear();
      accepterControls.clear();
      broadcastGroupControls.clear();
      brokerConnectionControls.clear();
      remoteBrokerConnectionControls.clear();
      bridgeControls.clear();
      clusterConnectionControl.clear();
      connectionRouterControls.clear();
      hawtioSecurityControl = null;
      divertControls.clear();
      untypedControls.clear();
   }

   /**
    * Retrieves the management control object associated with the provided resource name. This method determines the
    * type of the resource based on its prefix and returns the corresponding control object if available. If possible,
    * use the strongly typed methods instead as performance will be better.
    *
    * @param name The full name of the resource for which a control object is to be retrieved. The name may include a
    *             prefix indicating the type of the resource (e.g., "queue.", "address.", "broker", etc.).
    * @return the management control object associated with the specified resource name, or {@code null} if no control
    * object is registered for the resource.
    */
   public Object getByName(String name) {
      String namePrefix = name;
      String unprefixedName = name;
      int idx = name.indexOf(".");
      if (idx > 0) {
         namePrefix = name.substring(0, idx + 1);
         unprefixedName = name.substring(idx + 1);
      }

      return switch (namePrefix) {
         case ResourceNames.BROKER -> serverControl;
         case ResourceNames.ADDRESS -> addressControls.get(unprefixedName);
         case ResourceNames.QUEUE -> queueControls.get(unprefixedName);
         case ResourceNames.ACCEPTOR -> accepterControls.get(unprefixedName);
         case ResourceNames.BROADCAST_GROUP -> broadcastGroupControls.get(unprefixedName);
         case ResourceNames.BROKER_CONNECTION -> brokerConnectionControls.get(unprefixedName);
         case ResourceNames.REMOTE_BROKER_CONNECTION -> remoteBrokerConnectionControls.get(unprefixedName);
         case ResourceNames.BRIDGE -> bridgeControls.get(unprefixedName);
         case ResourceNames.CORE_CLUSTER_CONNECTION -> clusterConnectionControl.get(unprefixedName);
         case ResourceNames.CONNECTION_ROUTER -> connectionRouterControls.get(unprefixedName);
         case ResourceNames.MANAGEMENT_SECURITY -> hawtioSecurityControl;
         case ResourceNames.DIVERT -> divertControls.get(unprefixedName);
         default -> untypedControls.get(name);
      };
   }

   private static void logUnregistration(String name, Object removed) {
      if (removed != null) {
         logger.debug("Unregistered from management: {} as {}", name, removed);
      } else {
         logger.debug("Attempted to unregister {} from management, but it was not registered.");
      }
   }

   private static void logRegistration(String name, Object replaced, Object managedResource) {
      String addendum = "";
      if (replaced != null) {
         addendum = ". Replaced: " + replaced;
      }
      logger.debug("Registered in management: {} as {}{}", name, managedResource, addendum);
   }
}
