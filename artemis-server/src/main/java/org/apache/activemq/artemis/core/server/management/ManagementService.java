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
package org.apache.activemq.artemis.core.server.management;

import javax.management.ObjectName;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Predicate;

import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.management.AcceptorControl;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.BaseBroadcastGroupControl;
import org.apache.activemq.artemis.api.core.management.BridgeControl;
import org.apache.activemq.artemis.api.core.management.BrokerConnectionControl;
import org.apache.activemq.artemis.api.core.management.ClusterConnectionControl;
import org.apache.activemq.artemis.api.core.management.ConnectionRouterControl;
import org.apache.activemq.artemis.api.core.management.DivertControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.RemoteBrokerConnectionControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.management.impl.ActiveMQServerControlImpl;
import org.apache.activemq.artemis.core.messagecounter.MessageCounterManager;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.remoting.server.RemotingService;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.security.SecurityAuth;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.BrokerConnection;
import org.apache.activemq.artemis.core.server.Divert;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.core.server.RemoteBrokerConnection;
import org.apache.activemq.artemis.core.server.cluster.Bridge;
import org.apache.activemq.artemis.core.server.cluster.BroadcastGroup;
import org.apache.activemq.artemis.core.server.cluster.ClusterConnection;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.routing.ConnectionRouter;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;

public interface ManagementService extends NotificationService, ActiveMQComponent {

   MessageCounterManager getMessageCounterManager();

   SimpleString getManagementAddress();

   SimpleString getManagementNotificationAddress();

   ObjectNameBuilder getObjectNameBuilder();

   void setStorageManager(StorageManager storageManager);

   /**
    * @deprecated use one of the other register methods instead, they will register the control in JMX assuming it is
    * enabled:
    * <ul>
    * <li>{@link #registerServer}
    * <li>{@link #registerAddress}
    * <li>{@link #registerQueue}
    * <li>{@link #registerAcceptor}
    * <li>{@link #registerDivert}
    * <li>{@link #registerBroadcastGroup}
    * <li>{@link #registerBridge}
    * <li>{@link #registerCluster}
    * <li>{@link #registerConnectionRouter}
    * <li>{@link #registerBrokerConnection}
    * <li>{@link #registerRemoteBrokerConnection}
    * <li>{@link #registerHawtioSecurity}
    * <li>{@link #registerUntypedControl}
    * </ul>
    */
   @Deprecated(forRemoval = true)
   void registerInJMX(ObjectName objectName, Object managedResource) throws Exception;

   /**
    * @deprecated use one of the other unregister methods instead:
    * <ul>
    * <li>{@link #unregisterServer}
    * <li>{@link #unregisterAddress}
    * <li>{@link #unregisterQueue}
    * <li>{@link #unregisterAcceptor}
    * <li>{@link #unregisterDivert}
    * <li>{@link #unregisterBroadcastGroup}
    * <li>{@link #unregisterBridge}
    * <li>{@link #unregisterCluster}
    * <li>{@link #unregisterConnectionRouter}
    * <li>{@link #unregisterBrokerConnection}
    * <li>{@link #unregisterRemoteBrokerConnection}
    * <li>{@link #unregisterHawtioSecurity}
    * <li>{@link #unregisterUntypedControl}
    * </ul>
    */
   @Deprecated(forRemoval = true)
   void unregisterFromJMX(ObjectName objectName) throws Exception;

   ActiveMQServerControlImpl registerServer(PostOffice postOffice,
                                            SecurityStore securityStore,
                                            StorageManager storageManager,
                                            Configuration configuration,
                                            HierarchicalRepository<AddressSettings> addressSettingsRepository,
                                            HierarchicalRepository<Set<Role>> securityRepository,
                                            ResourceManager resourceManager,
                                            RemotingService remotingService,
                                            ActiveMQServer messagingServer,
                                            QueueFactory queueFactory,
                                            ScheduledExecutorService scheduledThreadPool,
                                            PagingManager pagingManager,
                                            boolean backup) throws Exception;

   void unregisterServer() throws Exception;

   ActiveMQServerControl getServerControl();

   void registerAddress(AddressInfo addressInfo) throws Exception;

   void unregisterAddress(SimpleString address) throws Exception;

   int getAddressControlCount();

   List<AddressControl> getAddressControls();

   List<AddressControl> getAddressControls(Predicate<AddressControl> predicate);

   AddressControl getAddressControl(String name);

   void registerQueue(Queue queue, SimpleString address, StorageManager storageManager) throws Exception;

   void unregisterQueue(SimpleString name, SimpleString address, RoutingType routingType) throws Exception;

   int getQueueControlCount();

   List<QueueControl> getQueueControls();

   List<QueueControl> getQueueControls(Predicate<QueueControl> predicate);

   QueueControl getQueueControl(String name);

   List<String> getQueueControlNames();

   void registerAcceptor(Acceptor acceptor, TransportConfiguration configuration) throws Exception;

   void unregisterAcceptor(String acceptorName) throws Exception;

   void unregisterAcceptors();

   List<AcceptorControl> getAcceptorControls();

   AcceptorControl getAcceptorControl(String name);

   List<String> getAddressControlNames();

   void registerDivert(Divert divert) throws Exception;

   void unregisterDivert(SimpleString name, SimpleString address) throws Exception;

   List<DivertControl> getDivertControls();

   List<String> getDivertControlNames();

   void registerBroadcastGroup(BroadcastGroup broadcastGroup, BroadcastGroupConfiguration configuration) throws Exception;

   void unregisterBroadcastGroup(String name) throws Exception;

   List<BaseBroadcastGroupControl> getBroadcastGroupControls();

   void registerBridge(Bridge bridge) throws Exception;

   void unregisterBridge(String name) throws Exception;

   List<BridgeControl> getBridgeControls();

   List<String> getBridgeControlNames();

   int getBridgeControlCount();

   void registerCluster(ClusterConnection cluster, ClusterConnectionConfiguration configuration) throws Exception;

   void unregisterCluster(String name) throws Exception;

   List<ClusterConnectionControl> getClusterConnectionControls();

   List<String> getClusterConnectionControlNames();

   void registerConnectionRouter(ConnectionRouter router) throws Exception;

   void unregisterConnectionRouter(String name) throws Exception;

   List<ConnectionRouterControl> getConnectionRouterControls();

   ConnectionRouterControl getConnectionRouterControl(String name);

   void registerBrokerConnection(BrokerConnection brokerConnection) throws Exception;

   void unregisterBrokerConnection(String name) throws Exception;

   List<BrokerConnectionControl> getBrokerConnectionControls();

   BrokerConnectionControl getBrokerConnectionControl(String name);

   RemoteBrokerConnectionControl getRemoteBrokerConnectionControl(String name);

   void registerRemoteBrokerConnection(RemoteBrokerConnection brokerConnection) throws Exception;

   void unregisterRemoteBrokerConnection(String nodeId, String name) throws Exception;

   List<RemoteBrokerConnectionControl> getRemoteBrokerConnectionControls();

   void registerHawtioSecurity(GuardInvocationHandler guardInvocationHandler) throws Exception;

   void unregisterHawtioSecurity() throws Exception;

   HawtioSecurityControl getHawtioSecurity();

   /**
    * Registers an untyped control with the specified name, control object, and associated ObjectName.
    * <p>
    * Use this for management controls not explicitly supported by other typed methods.
    *
    * @param name       the unique name used to identify the untyped control; must not be null or empty
    * @param control    the untyped control object to be registered; must not be null
    * @param objectName the MBean object name to be associated with the control; must not be null
    * @throws Exception if an error occurs during registration
    */
   void registerUntypedControl(String name, Object control, ObjectName objectName) throws Exception;

   /**
    * Unregisters an untyped control associated with the specified name and ObjectName.
    *
    * @param name       the name of the control to be unregistered
    * @param objectName the ObjectName associated with the control to be unregistered
    * @throws Exception if an error occurs during the unregistration process
    */
   void unregisterUntypedControl(String name, ObjectName objectName) throws Exception;

   Object getUntypedControl(String name);

   List<Object> getUntypedControls(Class<?> resourceType);

   /**
    * Retrieves a resource identified by the given name.
    *
    * @param resourceName the name of the resource to retrieve; must not be null or empty; should be prefixed with a
    *                     value from {@link ResourceNames} unless using an "untyped" control
    * @return the resource object associated with the specified name or null if the resource is not found
    * @deprecated use one of the strongly typed "get" methods or {@link #getUntypedControl(String)} instead if no type
    * is desired
    */
   @Deprecated(forRemoval = true)
   Object getResource(String resourceName);

   @Deprecated(forRemoval = true)
   Object[] getResources(Class<?> resourceType);

   ICoreMessage handleMessage(SecurityAuth auth, Message message) throws Exception;

   /**
    * Retrieves the value of a specified attribute from a management control. The inputs to and output from this method
    * are intentionally vague to support management messages and other use cases where types are not known ahead of
    * time.
    * <p>
    * If possible, use the strongly typed methods instead as performance will be better.
    *
    * @param resourceName the name of the resource from which the attribute is to be retrieved; should be prefixed with
    *                     a value from {@link ResourceNames} unless using an "untyped" control
    * @param attribute    the name of the attribute whose value is to be retrieved
    * @param auth         the security authorization context used to validate permissions for retrieving the attribute
    * @return the value of the specified attribute
    * @throws IllegalStateException if the resource cannot be found, the getter method does not exist, if there are
    *                               security issues, or if any error occurs during method invocation
    */
   Object getAttribute(String resourceName, String attribute, SecurityAuth auth);

   /**
    * Invokes a specified operation on a managed control identified by its name. The inputs to and output from this
    * method are intentionally vague to support management messages and other use cases where types are not known ahead
    * of time.
    * <p>
    * If possible, use the strongly typed methods instead as performance will be better.
    *
    * @param resourceName the name of the resource on which the operation will be invoked; should be prefixed with a
    *                     value from {@link ResourceNames} unless using an "untyped" control
    * @param operation    the name of the operation to invoke
    * @param params       an array of parameters to pass to the operation being invoked
    * @param auth         the security authentication object used to validate access permissions
    * @return the result of the invoked operation
    * @throws Exception if the resource is not found, the operation is invalid, or an error occurs during the invocation
    *                   process
    */
   Object invokeOperation(String resourceName, String operation, Object[] params, SecurityAuth auth) throws Exception;
}
