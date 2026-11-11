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
package org.apache.activemq.artemis.api.core.management;

import javax.management.ObjectName;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.RoutingType;

/**
 * Helper class to build ObjectNames for Apache Artemis resources.
 */
public final class ObjectNameBuilder {


   /**
    * Default JMX domain for Apache Artemis resources.
    */
   public static final ObjectNameBuilder DEFAULT = new ObjectNameBuilder(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), "localhost", true);


   private final String domain;

   private String brokerName;

   private final boolean jmxUseBrokerName;


   public static ObjectNameBuilder create(final String domain) {
      if (domain == null) {
         return new ObjectNameBuilder(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), null, false);
      } else {
         return new ObjectNameBuilder(domain, null, false);
      }
   }

   public static ObjectNameBuilder create(final String domain, String brokerName) {
      if (domain == null) {
         return new ObjectNameBuilder(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), brokerName, true);
      } else {
         return new ObjectNameBuilder(domain, brokerName, true);
      }
   }

   public static ObjectNameBuilder create(final String domain, String brokerName, boolean jmxUseBrokerName) {
      if (domain == null) {
         return new ObjectNameBuilder(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), brokerName, jmxUseBrokerName);
      } else {
         return new ObjectNameBuilder(domain, brokerName, jmxUseBrokerName);
      }
   }



   private ObjectNameBuilder(final String domain, final String brokerName, boolean jmxUseBrokerName) {
      this.domain = domain;
      this.brokerName = brokerName;
      this.jmxUseBrokerName = jmxUseBrokerName;
   }

   /**
    * {@return the ObjectName used by the single {@link ActiveMQServerControl}}
    */
   public ObjectName getActiveMQServerObjectName() throws Exception {
      return ObjectName.getInstance(getActiveMQServerName());
   }

   /**
    * {@return the ObjectName used by AddressControl}
    *
    * @see AddressControl
    */
   public ObjectName getAddressObjectName(final SimpleString address) throws Exception {
      return ObjectName.getInstance(String.format("%s,component=addresses,address=%s", getActiveMQServerName(), ObjectName.quote(address.toString())));
   }

   /**
    * {@return the ObjectName used by QueueControl}
    *
    * @see QueueControl
    */
   public ObjectName getQueueObjectName(final SimpleString address, final SimpleString name, RoutingType routingType) throws Exception {
      return ObjectName.getInstance(String.format("%s,component=addresses,address=%s,subcomponent=queues,routing-type=%s,queue=%s", getActiveMQServerName(), ObjectName.quote(address.toString()), ObjectName.quote(routingType.toString().toLowerCase()), ObjectName.quote(name.toString())));
   }


   /**
    * {@return the ObjectName used by DivertControl}
    *
    * @see DivertControl
    */
   public ObjectName getDivertObjectName(final String name, String address) throws Exception {
      return ObjectName.getInstance(String.format("%s,component=addresses,address=%s,subcomponent=diverts,divert=%s", getActiveMQServerName(), ObjectName.quote(address), ObjectName.quote(name)));
   }

   /**
    * {@return the ObjectName used by AcceptorControl}
    *
    * @see AcceptorControl
    */
   public ObjectName getAcceptorObjectName(final String name) throws Exception {
      return createObjectName("acceptor", name);
   }

   /**
    * {@return the ObjectName used by BroadcastGroupControl}
    *
    * @see BroadcastGroupControl
    */
   public ObjectName getBroadcastGroupObjectName(final String name) throws Exception {
      return createObjectName("broadcast-group", name);
   }

   /**
    * {@return the ObjectName used by broker connection management objects for outgoing connections}
    *
    * @see BrokerConnectionControl
    */
   public ObjectName getBrokerConnectionObjectName(String name) throws Exception {
      return ObjectName.getInstance(getBrokerConnectionBaseObjectNameString(name));
   }

   /**
    * Returns the base ObjectName string used by for outgoing broker connections and possibly broker connection services
    * that are registered by broker connection specific features. This value is pre-quoted and ready for use in an
    * ObjectName.getIstnace call but is intended for use by broker connection components to create names specific to a
    * broker connection feature that registers its own object for management.
    *
    * @param name The broker connection name
    * @return the base object name string that is used for broker connection Object names
    * @see BrokerConnectionControl
    */
   public String getBrokerConnectionBaseObjectNameString(String name) throws Exception {
      return getActiveMQServerName() + ",component=broker-connections,name=" + ObjectName.quote(name);
   }

   /**
    * {@return the ObjectName used by remote broker connection management objects for incoming connections}
    *
    * @see RemoteBrokerConnectionControl
    */
   public ObjectName getRemoteBrokerConnectionObjectName(String nodeId, String name) throws Exception {
      return ObjectName.getInstance(getRemoteBrokerConnectionBaseObjectNameString(nodeId, name));
   }

   /**
    * Returns the base ObjectName string used by for incoming broker connections and possibly broker connection services
    * that are registered by broker connection specific features. This value is pre-quoted and ready for use in an
    * ObjectName.getIstnace call but is intended for use by broker connection components to create names specific to a
    * broker connection feature that registers its own object for management.
    *
    * @param nodeId The node ID of the remote broker that initiated the broker connection.
    * @param name   The broker connection name configured on the initiating broker.
    * @return the base object name string that is used for broker connection Object names
    */
   public String getRemoteBrokerConnectionBaseObjectNameString(String nodeId, String name) throws Exception {
      return getActiveMQServerName() +
             ",component=remote-broker-connections" +
             ",nodeId=" + ObjectName.quote(nodeId) +
             ",name=" + ObjectName.quote(name);
   }

   /**
    * {@return the ObjectName used by BridgeControl}
    *
    * @see BridgeControl
    */
   public ObjectName getBridgeObjectName(final String name) throws Exception {
      return createObjectName("bridge", name);
   }

   /**
    * {@return the ObjectName used by ClusterConnectionControl}
    *
    * @see ClusterConnectionControl
    */
   public ObjectName getClusterConnectionObjectName(final String name) throws Exception {
      return createObjectName("cluster-connection", name);
   }

   /**
    * {@return the ObjectName used by ConnectionRouterControl}
    *
    * @see ConnectionRouterControl
    */
   public ObjectName getConnectionRouterObjectName(final String name) throws Exception {
      return createObjectName("connection-router", name);
   }

   private ObjectName createObjectName(final String type, final String name) throws Exception {
      return ObjectName.getInstance(String.format("%s,component=%ss,name=%s", getActiveMQServerName(), type, ObjectName.quote(name)));
   }

   private String getActiveMQServerName() {
      return String.format("%s:broker=%s", domain, (jmxUseBrokerName && brokerName != null) ? ObjectName.quote(brokerName) : "artemis");
   }

   @Deprecated()
   public ObjectName getManagementContextObjectName() throws Exception {
      return getSecurityObjectName();
   }

   public ObjectName getSecurityObjectName() throws Exception {
      return ObjectName.getInstance("hawtio:type=security,area=jmx,name=ArtemisJMXSecurity");
   }
}
