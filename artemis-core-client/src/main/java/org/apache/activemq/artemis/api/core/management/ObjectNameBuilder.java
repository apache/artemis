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
 * Helper class to build ObjectNames for ActiveMQ Artemis resources.
 */
public final class ObjectNameBuilder {


   /**
    * Default JMX domain for ActiveMQ Artemis resources.
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
    * Returns the ObjectName used by the single {@link ActiveMQServerControl}.
    */
   public ObjectName getActiveMQServerObjectName() throws Exception {
      return ObjectName.getInstance(getActiveMQServerName());
   }

   /**
    * Returns the ObjectName used by AddressControl.
    *
    * @see AddressControl
    */
   public ObjectName getAddressObjectName(final SimpleString address) throws Exception {
      return ObjectName.getInstance(String.format("%s,component=addresses,address=%s", getActiveMQServerName(), ObjectName.quote(address.toString())));
   }

   /**
    * Returns the ObjectName used by QueueControl.
    *
    * @see QueueControl
    */
   public ObjectName getQueueObjectName(final SimpleString address, final SimpleString name, RoutingType routingType) throws Exception {
      return ObjectName.getInstance(String.format("%s,component=addresses,address=%s,subcomponent=queues,routing-type=%s,queue=%s", getActiveMQServerName(), ObjectName.quote(address.toString()), ObjectName.quote(routingType.toString().toLowerCase()), ObjectName.quote(name.toString())));
   }


   /**
    * Returns the ObjectName used by DivertControl.
    *
    * @see DivertControl
    */
   public ObjectName getDivertObjectName(final String name, String address) throws Exception {
      return ObjectName.getInstance(String.format("%s,component=addresses,address=%s,subcomponent=diverts,divert=%s", getActiveMQServerName(), ObjectName.quote(address.toString()), ObjectName.quote(name.toString())));
   }

   /**
    * Returns the ObjectName used by AcceptorControl.
    *
    * @see AcceptorControl
    */
   public ObjectName getAcceptorObjectName(final String name) throws Exception {
      return createObjectName("acceptor", name);
   }

   /**
    * Returns the ObjectName used by BroadcastGroupControl.
    *
    * @see BroadcastGroupControl
    */
   public ObjectName getBroadcastGroupObjectName(final String name) throws Exception {
      return createObjectName("broadcast-group", name);
   }

   /**
    * Returns the ObjectName used by BridgeControl.
    *
    * @see BridgeControl
    */
   public ObjectName getBridgeObjectName(final String name) throws Exception {
      return createObjectName("bridge", name);
   }

   /**
    * Returns the ObjectName used by ClusterConnectionControl.
    *
    * @see ClusterConnectionControl
    */
   public ObjectName getClusterConnectionObjectName(final String name) throws Exception {
      return createObjectName("cluster-connection", name);
   }

   /**
    * Returns the ObjectName used by BrokerBalancerControl.
    *
    * @see BrokerBalancerControl
    */
   public ObjectName getBrokerBalancerObjectName(final String name) throws Exception {
      return createObjectName("broker-balancer", name);
   }

   private ObjectName createObjectName(final String type, final String name) throws Exception {
      return ObjectName.getInstance(String.format("%s,component=%ss,name=%s", getActiveMQServerName(), type, ObjectName.quote(name)));
   }

   private String getActiveMQServerName() {
      return String.format("%s:broker=%s", domain, (jmxUseBrokerName && brokerName != null) ? ObjectName.quote(brokerName) : "artemis");
   }

   public ObjectName getManagementContextObjectName() throws Exception {
      return ObjectName.getInstance(String.format("hawtio:type=security,area=jmx,name=ArtemisJMXSecurity"));
   }
}
