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
package org.apache.activemq.artemis.protocol.amqp.connect.bridge;

import javax.management.ObjectName;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeReceiverInfo.ReceiverRole;
import org.apache.activemq.artemis.protocol.amqp.connect.bridge.AMQPBridgeToSenderController.SenderRole;
import org.apache.activemq.artemis.utils.CompositeAddress;

/**
 * Support methods for working with the AMQP bridge management types
 */
public final class AMQPBridgeManagementSupport {

   // Resource names specific to bridge resources that exist on the local end of a broker connection.

   /**
    * Template used to denote bridge manager instances in the server management registry.
    */
   public static final String BRIDGE_MANAGER_RESOURCE_TEMPLATE = "brokerconnection.%s.bridge.%s";

   /**
    * Template used to denote bridge policy managers created on the source in the server management registry.
    */
   public static final String BRIDGE_POLICY_RESOURCE_TEMPLATE = BRIDGE_MANAGER_RESOURCE_TEMPLATE + ".policy.%s";

   /**
    * Template used to denote bridge receivers on the source in the server management registry. Since policy
    * names are unique on the local broker AMQP bridge configuration these names should not collide as each
    * policy will create only one receiver for a given address or queue.
    */
   public static final String BRIDGE_RECEIVER_RESOURCE_TEMPLATE = BRIDGE_POLICY_RESOURCE_TEMPLATE + ".receiver.%s";

   /**
    * Template used to denote bridge senders on the source in the server management registry. Since policy
    * names are unique on the local broker AMQP bridge configuration these names should not collide as each
    * policy will create only one sender for a given address or queue.
    */
   public static final String BRIDGE_SENDER_RESOURCE_TEMPLATE = BRIDGE_POLICY_RESOURCE_TEMPLATE + ".sender.%s";

   // MBean naming that will be registered under an name that links to the location of the bean
   // either source or target end of the broker connection.

   /**
    * The template used to create the object name suffix that is appending to the broker connection
    * object name when adding and removing AMQP bridge related control elements.
    */
   public static final String BRIDGE_NAME_TEMPLATE = "serviceCatagory=bridge,bridgeName=%s";

   /**
    * The template used to create the object name suffix that is appending to the broker connection
    * object name when adding and removing AMQP bridge policy control elements.
    */
   public static final String BRIDGE_POLICY_NAME_TEMPLATE = BRIDGE_NAME_TEMPLATE + ",policyType=%s,policyName=%s";

   /**
    * The template used to create the object name suffix that is appending to the broker connection
    * object name when adding and removing AMQP bridge queue receiver control elements.
    */
   public static final String BRIDGE_QUEUE_RECEIVER_NAME_TEMPLATE = BRIDGE_POLICY_NAME_TEMPLATE + ",linkType=receivers,fqqn=%s";

   /**
    * The template used to create the object name suffix that is appending to the broker connection
    * object name when adding and removing AMQP bridge address receiver control elements.
    */
   public static final String BRIDGE_ADDRESS_RECEIVER_NAME_TEMPLATE = BRIDGE_POLICY_NAME_TEMPLATE + ",linkType=receivers,address=%s";

   /**
    * The template used to create the object name suffix that is appending to the broker connection
    * object name when adding and removing AMQP bridge queue sender control elements.
    */
   public static final String BRIDGE_QUEUE_SENDER_NAME_TEMPLATE = BRIDGE_POLICY_NAME_TEMPLATE + ",linkType=senders,fqqn=%s";

   /**
    * The template used to create the object name suffix that is appending to the broker connection
    * object name when adding and removing AMQP bridge address sender control elements.
    */
   public static final String BRIDGE_ADDRESS_SENDER_NAME_TEMPLATE = BRIDGE_POLICY_NAME_TEMPLATE + ",linkType=senders,address=%s";

   /**
    * Register the given {@link AMQPBridgeManager} instance with the broker management services.
    *
    * @param bridge
    *    The bridge manager instance being registered with management.
    *
    * @throws Exception if an error occurs while registering the bridge with the management services.
    */
   public static void registerBridgeManager(AMQPBridgeManager bridge) throws Exception {
      final String bridgeName = bridge.getName();
      final String brokerConnectionName = bridge.getBrokerConnection().getName();
      final ActiveMQServer server = bridge.getServer();
      final ManagementService management = server.getManagementService();
      final AMQPBridgeManagerControlType control = new AMQPBridgeManagerControlType(server, bridge);


      management.registerInJMX(getBridgeManagerObjectName(management, brokerConnectionName, bridgeName), control);
      management.registerAMQPControl(getBridgeManagerResourceName(brokerConnectionName, bridgeName), control);
   }

   /**
    * Unregister the given {@link AMQPBridgeManager} instance with the broker management services.
    *
    * @param bridge
    *    The bridge manager instance being unregistered from management.
    *
    * @throws Exception if an error occurs while unregistering the bridge with the management services.
    */
   public static void unregisterBridgeManager(AMQPBridgeManager bridge) throws Exception {
      final String bridgeName = bridge.getName();
      final String brokerConnectionName = bridge.getBrokerConnection().getName();
      final ActiveMQServer server = bridge.getServer();
      final ManagementService management = server.getManagementService();

      management.unregisterFromJMX(getBridgeManagerObjectName(management, brokerConnectionName, bridgeName));
      management.unRegisterAMQPControl(getBridgeManagerResourceName(brokerConnectionName, bridgeName));
   }

   public static String getBridgeManagerResourceName(String brokerConnectionName, String bridgeName) {
      return String.format(BRIDGE_MANAGER_RESOURCE_TEMPLATE, brokerConnectionName, bridgeName);
   }

   public static ObjectName getBridgeManagerObjectName(ManagementService management, String brokerConnection, String bridgeName) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + BRIDGE_NAME_TEMPLATE,
            management.getObjectNameBuilder().getBrokerConnectionBaseObjectNameString(brokerConnection),
            ObjectName.quote(bridgeName)));
   }

   /**
    * Register an AMQP bridge policy manager with the server management services.
    *
    * @param manager
    *    The AMQP bridge policy manager instance that is being managed.
    *
    * @throws Exception if an error occurs while registering the manager with the management services.
    */
   public static void registerBridgePolicyManager(AMQPBridgePolicyManager manager) throws Exception {
      final AMQPBridgeManager bridgeManager = manager.getBridgeManager();
      final String brokerConnectionName = bridgeManager.getBrokerConnection().getName();
      final ActiveMQServer server = bridgeManager.getServer();
      final ManagementService management = server.getManagementService();
      final AMQPBridgePolicyManagerControlType control = new AMQPBridgePolicyManagerControlType(manager);
      final String bridgeName = bridgeManager.getName();
      final String policyName = manager.getPolicyName();

      management.registerInJMX(getBridgePolicyManagerObjectName(management, brokerConnectionName, bridgeName, manager.getPolicyType().toString(), policyName), control);
      management.registerAMQPControl(getBridgePolicyManagerResourceName(brokerConnectionName, bridgeName, policyName), control);
   }

   /**
    * Unregister an AMQP bridge policy manager with the server management services.
    *
    * @param manager
    *    The AMQP bridge policy manager instance that is being managed.
    *
    * @throws Exception if an error occurs while unregistering the manager with the management services.
    */
   public static void unregisterBridgePolicyManager(AMQPBridgePolicyManager manager) throws Exception {
      final AMQPBridgeManager bridgeManager = manager.getBridgeManager();
      final String brokerConnectionName = bridgeManager.getBrokerConnection().getName();
      final ActiveMQServer server = bridgeManager.getServer();
      final ManagementService management = server.getManagementService();
      final String bridgeName = bridgeManager.getName();
      final String policyName = manager.getPolicyName();

      management.unregisterFromJMX(getBridgePolicyManagerObjectName(management, brokerConnectionName, bridgeName, manager.getPolicyType().toString(), policyName));
      management.unRegisterAMQPControl(getBridgePolicyManagerResourceName(brokerConnectionName, bridgeName, policyName));
   }

   public static String getBridgePolicyManagerResourceName(String brokerConnectionName, String bridgeName, String policyName) {
      return String.format(BRIDGE_POLICY_RESOURCE_TEMPLATE, brokerConnectionName, bridgeName, policyName);
   }

   public static ObjectName getBridgePolicyManagerObjectName(ManagementService management, String brokerConnection, String bridgeName, String policyType, String policyName) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + BRIDGE_POLICY_NAME_TEMPLATE,
            management.getObjectNameBuilder().getBrokerConnectionBaseObjectNameString(brokerConnection),
            ObjectName.quote(bridgeName),
            ObjectName.quote(policyType),
            ObjectName.quote(policyName)));
   }

   /**
    * Registers the bridge receiver with the server management services on the source.
    *
    * @param receiver
    *    The AMQP bridge receiver instance that is being managed.
    *
    * @throws Exception if an error occurs while registering the receiver with the management services.
    */
   public static void registerBridgeReceiver(AMQPBridgeReceiver receiver) throws Exception {
      final AMQPBridgePolicyManager manager = receiver.getPolicyManager();
      final AMQPBridgeManager bridgeManager = manager.getBridgeManager();
      final String brokerConnectionName = bridgeManager.getBrokerConnection().getName();
      final ActiveMQServer server = bridgeManager.getServer();
      final ManagementService management = server.getManagementService();
      final AMQPBridgeReceiverControlType control = new AMQPBridgeReceiverControlType(receiver);
      final String bridgeName = bridgeManager.getName();
      final String policyName = manager.getPolicyName();

      if (receiver.getRole() == ReceiverRole.ADDRESS_RECEIVER) {
         management.registerInJMX(getBridgeAddressReceiverObjectName(management, brokerConnectionName, bridgeName, manager.getPolicyType().toString(), policyName, receiver.getReceiverInfo().getLocalAddress()), control);
         management.registerAMQPControl(getBridgeAddressReceiverResourceName(brokerConnectionName, bridgeName, policyName, receiver.getReceiverInfo().getLocalAddress()), control);
      } else {
         management.registerInJMX(getBridgeQueueReceiverObjectName(management, brokerConnectionName, bridgeName, manager.getPolicyType().toString(), policyName, receiver.getReceiverInfo().getLocalFqqn()), control);
         management.registerAMQPControl(getBridgeQueueReceiverResourceName(brokerConnectionName, bridgeName, policyName, receiver.getReceiverInfo().getLocalFqqn()), control);
      }
   }

   /**
    * Unregisters the bridge receiver with the server management services on the source.
    *
    * @param receiver
    *    The AMQP bridge receiver instance that is being managed.
    *
    * @throws Exception if an error occurs while registering the receiver with the management services.
    */
   public static void unregisterBridgeReceiver(AMQPBridgeReceiver receiver) throws Exception {
      final AMQPBridgePolicyManager manager = receiver.getPolicyManager();
      final AMQPBridgeManager bridgeManager = manager.getBridgeManager();
      final String brokerConnectionName = bridgeManager.getBrokerConnection().getName();
      final ActiveMQServer server = bridgeManager.getServer();
      final ManagementService management = server.getManagementService();
      final String bridgeName = bridgeManager.getName();
      final String policyName = manager.getPolicyName();

      if (receiver.getRole() == ReceiverRole.ADDRESS_RECEIVER) {
         management.unregisterFromJMX(getBridgeAddressReceiverObjectName(management, brokerConnectionName, bridgeName, manager.getPolicyType().toString(), policyName, receiver.getReceiverInfo().getLocalAddress()));
         management.unRegisterAMQPControl(getBridgeAddressReceiverResourceName(brokerConnectionName, bridgeName, policyName, receiver.getReceiverInfo().getLocalAddress()));
      } else {
         management.unregisterFromJMX(getBridgeQueueReceiverObjectName(management, brokerConnectionName, bridgeName, manager.getPolicyType().toString(), policyName, receiver.getReceiverInfo().getLocalFqqn()));
         management.unRegisterAMQPControl(getBridgeQueueReceiverResourceName(brokerConnectionName, bridgeName, policyName, receiver.getReceiverInfo().getLocalFqqn()));
      }
   }

   public static String getBridgeAddressReceiverResourceName(String brokerConnectionName, String bridgeName, String policyName, String address) {
      return String.format(BRIDGE_RECEIVER_RESOURCE_TEMPLATE, brokerConnectionName, bridgeName, policyName, address);
   }

   public static ObjectName getBridgeAddressReceiverObjectName(ManagementService management, String brokerConnection, String bridgeName, String policyType, String policyName, String address) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + BRIDGE_ADDRESS_RECEIVER_NAME_TEMPLATE,
            management.getObjectNameBuilder().getBrokerConnectionBaseObjectNameString(brokerConnection),
            ObjectName.quote(bridgeName),
            ObjectName.quote(policyType),
            ObjectName.quote(policyName),
            ObjectName.quote(address)));
   }

   public static String getBridgeQueueReceiverResourceName(String brokerConnectionName, String bridgeName, String policyName, String fqqn) {
      return String.format(BRIDGE_RECEIVER_RESOURCE_TEMPLATE, brokerConnectionName, bridgeName, policyName, fqqn);
   }

   public static ObjectName getBridgeQueueReceiverObjectName(ManagementService management, String brokerConnection, String bridgeName, String policyType, String policyName, String fqqn) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + BRIDGE_QUEUE_RECEIVER_NAME_TEMPLATE,
            management.getObjectNameBuilder().getBrokerConnectionBaseObjectNameString(brokerConnection),
            ObjectName.quote(bridgeName),
            ObjectName.quote(policyType),
            ObjectName.quote(policyName),
            ObjectName.quote(fqqn)));
   }

   /**
    * Registers the bridge sender with the server management services on the source.
    *
    * @param sender
    *    The AMQP bridge sender controller that manages the bridgeManager sender.
    *
    * @throws Exception if an error occurs while registering the producer with the management services.
    */
   public static void registerBridgeSender(AMQPBridgeToSenderController sender) throws Exception {
      final AMQPBridgePolicyManager manager = sender.getPolicyManager();
      final AMQPBridgeManager bridgeManager = manager.getBridgeManager();
      final String brokerConnectionName = bridgeManager.getBrokerConnection().getName();
      final ActiveMQServer server = bridgeManager.getServer();
      final ManagementService management = server.getManagementService();
      final AMQPBridgeSenderControlType control = new AMQPBridgeSenderControlType(sender);
      final String bridgeName = bridgeManager.getName();
      final String policyName = manager.getPolicyName();

      if (sender.getRole() == SenderRole.ADDRESS_SENDER) {
         final String address = control.getAddress();

         management.registerInJMX(getBridgeAddressSenderObjectName(management, brokerConnectionName, bridgeName, manager.getPolicyType().toString(), policyName, address), control);
         management.registerAMQPControl(getBridgeAddressSenderResourceName(brokerConnectionName, bridgeName, policyName, address), control);
      } else {
         final String fqqn = control.getFqqn();

         management.registerInJMX(getBridgeQueueSenderObjectName(management, brokerConnectionName, bridgeName, manager.getPolicyType().toString(), policyName, fqqn), control);
         management.registerAMQPControl(getBridgeQueueSenderResourceName(brokerConnectionName, bridgeName, policyName, fqqn), control);
      }
   }

   /**
    * Unregisters the bridge sender with the server management services on the source.
    *
    * @param sender
    *    The AMQP bridge sender controller that manages the bridge producer.
    *
    * @throws Exception if an error occurs while registering the sender with the management services.
    */
   public static void unregisterBridgeSender(AMQPBridgeToSenderController sender) throws Exception {
      final AMQPBridgePolicyManager manager = sender.getPolicyManager();
      final AMQPBridgeManager bridgeManager = manager.getBridgeManager();
      final String brokerConnectionName = bridgeManager.getBrokerConnection().getName();
      final ActiveMQServer server = bridgeManager.getServer();
      final ManagementService management = server.getManagementService();
      final String bridgeName = bridgeManager.getName();
      final String policyName = manager.getPolicyName();

      if (sender.getRole() == SenderRole.ADDRESS_SENDER) {
         final String address = sender.getServerConsumer().getQueueAddress().toString();

         management.unregisterFromJMX(getBridgeAddressSenderObjectName(management, brokerConnectionName, bridgeName, manager.getPolicyType().toString(), policyName, address));
         management.unRegisterAMQPControl(getBridgeAddressSenderResourceName(brokerConnectionName, bridgeName, policyName, address));
      } else {
         final String fqqn = CompositeAddress.toFullyQualified(sender.getServerConsumer().getQueueAddress().toString(), sender.getServerConsumer().getQueueName().toString());

         management.unregisterFromJMX(getBridgeQueueSenderObjectName(management, brokerConnectionName, bridgeName, manager.getPolicyType().toString(), policyName, fqqn));
         management.unRegisterAMQPControl(getBridgeQueueSenderResourceName(brokerConnectionName, bridgeName, policyName, fqqn));
      }
   }

   public static String getBridgeAddressSenderResourceName(String brokerConnectionName, String bridgeName, String policyName, String address) {
      return String.format(BRIDGE_SENDER_RESOURCE_TEMPLATE, brokerConnectionName, bridgeName, policyName, address);
   }

   public static ObjectName getBridgeAddressSenderObjectName(ManagementService management, String brokerConnection, String bridgeName, String policyType, String policyName, String address) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + BRIDGE_ADDRESS_SENDER_NAME_TEMPLATE,
            management.getObjectNameBuilder().getBrokerConnectionBaseObjectNameString(brokerConnection),
            ObjectName.quote(bridgeName),
            ObjectName.quote(policyType),
            ObjectName.quote(policyName),
            ObjectName.quote(address)));
   }

   public static String getBridgeQueueSenderResourceName(String brokerConnectionName, String bridgeName, String policyName, String fqqn) {
      return String.format(BRIDGE_SENDER_RESOURCE_TEMPLATE, brokerConnectionName, bridgeName, policyName, fqqn);
   }

   public static ObjectName getBridgeQueueSenderObjectName(ManagementService management, String brokerConnection, String bridgeName, String policyType, String policyName, String fqqn) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + BRIDGE_QUEUE_SENDER_NAME_TEMPLATE,
            management.getObjectNameBuilder().getBrokerConnectionBaseObjectNameString(brokerConnection),
            ObjectName.quote(bridgeName),
            ObjectName.quote(policyType),
            ObjectName.quote(policyName),
            ObjectName.quote(fqqn)));
   }
   private AMQPBridgeManagementSupport() {
      // Prevent creation.
   }
}
