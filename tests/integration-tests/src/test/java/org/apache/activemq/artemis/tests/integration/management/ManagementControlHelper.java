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
package org.apache.activemq.artemis.tests.integration.management;

import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.AcceptorControl;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.BridgeControl;
import org.apache.activemq.artemis.api.core.management.BroadcastGroupControl;
import org.apache.activemq.artemis.api.core.management.BrokerBalancerControl;
import org.apache.activemq.artemis.api.core.management.ClusterConnectionControl;
import org.apache.activemq.artemis.api.core.management.DivertControl;
import org.apache.activemq.artemis.api.core.management.JGroupsChannelBroadcastGroupControl;
import org.apache.activemq.artemis.api.core.management.JGroupsFileBroadcastGroupControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.RoutingType;

public class ManagementControlHelper {



   public static AcceptorControl createAcceptorControl(final String name,
                                                       final MBeanServer mbeanServer) throws Exception {
      return (AcceptorControl) ManagementControlHelper.createProxy(ObjectNameBuilder.DEFAULT.getAcceptorObjectName(name), AcceptorControl.class, mbeanServer);
   }

   public static BroadcastGroupControl createBroadcastGroupControl(final String name,
                                                                   final MBeanServer mbeanServer) throws Exception {
      return (BroadcastGroupControl) ManagementControlHelper.createProxy(ObjectNameBuilder.DEFAULT.getBroadcastGroupObjectName(name), BroadcastGroupControl.class, mbeanServer);
   }

   public static JGroupsFileBroadcastGroupControl createJgroupsFileBroadcastGroupControl(final String name,
                                                                   final MBeanServer mbeanServer) throws Exception {
      return (JGroupsFileBroadcastGroupControl) ManagementControlHelper.createProxy(ObjectNameBuilder.DEFAULT.getBroadcastGroupObjectName(name), JGroupsFileBroadcastGroupControl.class, mbeanServer);
   }

   public static JGroupsChannelBroadcastGroupControl createJgroupsChannelBroadcastGroupControl(final String name,
                                                                                            final MBeanServer mbeanServer) throws Exception {
      return (JGroupsChannelBroadcastGroupControl) ManagementControlHelper.createProxy(ObjectNameBuilder.DEFAULT.getBroadcastGroupObjectName(name), JGroupsChannelBroadcastGroupControl.class, mbeanServer);
   }

   public static BridgeControl createBridgeControl(final String name, final MBeanServer mbeanServer) throws Exception {
      return (BridgeControl) ManagementControlHelper.createProxy(ObjectNameBuilder.DEFAULT.getBridgeObjectName(name), BridgeControl.class, mbeanServer);
   }

   public static DivertControl createDivertControl(final String name, String address, final MBeanServer mbeanServer) throws Exception {
      return (DivertControl) ManagementControlHelper.createProxy(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address), DivertControl.class, mbeanServer);
   }

   public static ClusterConnectionControl createClusterConnectionControl(final String name,
                                                                         final MBeanServer mbeanServer) throws Exception {
      return (ClusterConnectionControl) ManagementControlHelper.createProxy(ObjectNameBuilder.DEFAULT.getClusterConnectionObjectName(name), ClusterConnectionControl.class, mbeanServer);
   }

   public static ActiveMQServerControl createActiveMQServerControl(final MBeanServer mbeanServer) throws Exception {
      return (ActiveMQServerControl) ManagementControlHelper.createProxy(ObjectNameBuilder.DEFAULT.getActiveMQServerObjectName(), ActiveMQServerControl.class, mbeanServer);
   }

   public static QueueControl createQueueControl(final SimpleString address,
                                                 final SimpleString name,
                                                 final MBeanServer mbeanServer) throws Exception {
      return (QueueControl) ManagementControlHelper.createProxy(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, ActiveMQDefaultConfiguration.getDefaultRoutingType()), QueueControl.class, mbeanServer);
   }

   public static QueueControl createQueueControl(final SimpleString address,
                                                 final SimpleString name,
                                                 final RoutingType routingType,
                                                 final MBeanServer mbeanServer) throws Exception {
      return (QueueControl) ManagementControlHelper.createProxy(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, routingType), QueueControl.class, mbeanServer);
   }

   public static AddressControl createAddressControl(final SimpleString address,
                                                     final MBeanServer mbeanServer) throws Exception {
      return (AddressControl) ManagementControlHelper.createProxy(ObjectNameBuilder.DEFAULT.getAddressObjectName(address), AddressControl.class, mbeanServer);
   }


   public static BrokerBalancerControl createBrokerBalancerControl(final String name,
                                                                   final MBeanServer mbeanServer) throws Exception {
      return (BrokerBalancerControl) ManagementControlHelper.createProxy(ObjectNameBuilder.DEFAULT.getBrokerBalancerObjectName(name), BrokerBalancerControl.class, mbeanServer);
   }



   public static Object createProxy(final ObjectName objectName,
                                     final Class mbeanInterface,
                                     final MBeanServer mbeanServer) {
      return MBeanServerInvocationHandler.newProxyInstance(mbeanServer, objectName, mbeanInterface, false);
   }


}
