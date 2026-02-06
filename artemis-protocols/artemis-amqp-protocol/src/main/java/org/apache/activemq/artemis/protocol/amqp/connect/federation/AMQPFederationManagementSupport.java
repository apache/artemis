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
package org.apache.activemq.artemis.protocol.amqp.connect.federation;

import javax.management.ObjectName;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationSenderController.Role;
import org.apache.activemq.artemis.protocol.amqp.federation.FederationConsumerInfo;
import org.apache.activemq.artemis.utils.CompositeAddress;

/**
 * Support methods for working with the AMQP Federation management types
 */
public abstract class AMQPFederationManagementSupport {

   // Resource names specific to federation resources that exist on the local end of a broker connection.

   /**
    * Template used to denote federation source instances in the server management registry.
    */
   public static final String FEDERATION_SOURCE_RESOURCE_TEMPLATE = "brokerconnection.%s.federation.%s";

   /**
    * Template used to denote federation policy managers created on the source in the server management registry.
    */
   public static final String FEDERATION_SOURCE_POLICY_RESOURCE_TEMPLATE = FEDERATION_SOURCE_RESOURCE_TEMPLATE + ".policy.%s";

   /**
    * Template used to denote federation consumers on the source in the server management registry. Since policy names
    * are unique on the local broker AMQP federation configuration these names should not collide as each policy will
    * create only one consumer for a given address or queue.
    */
   public static final String FEDERATION_SOURCE_CONSUMER_RESOURCE_TEMPLATE = FEDERATION_SOURCE_POLICY_RESOURCE_TEMPLATE + ".consumer.%s";

   /**
    * Template used to denote federation producers on the source in the server management registry. Since policy names
    * are unique on the local broker AMQP federation configuration these names should not collide as each policy will
    * create only one producer for a given address or queue.
    */
   public static final String FEDERATION_SOURCE_PRODUCER_RESOURCE_TEMPLATE = FEDERATION_SOURCE_POLICY_RESOURCE_TEMPLATE + ".producer.%s";

   // Resource names specific to federation resources that exist on the remote end of a broker connection.

   /**
    * Template used to denote federation source instances in the server management registry.
    */
   public static final String FEDERATION_TARGET_RESOURCE_TEMPLATE = "node.%s.brokerconnection.%s.federation.%s";

   /**
    * Template used to denote federation policy managers created on the source in the server management registry.
    */
   public static final String FEDERATION_TARGET_POLICY_RESOURCE_TEMPLATE = FEDERATION_TARGET_RESOURCE_TEMPLATE + ".policy.%s";

   /**
    * Template used to denote federation consumers on the source in the server management registry. Since policy names
    * are unique on the local broker AMQP federation configuration these names should not collide as each policy will
    * create only one consumer for a given address or queue.
    */
   public static final String FEDERATION_TARGET_CONSUMER_RESOURCE_TEMPLATE = FEDERATION_TARGET_POLICY_RESOURCE_TEMPLATE + ".consumer.%s";

   /**
    * Template used to denote federation producers on the source in the server management registry. Since policy names
    * are unique on the local broker AMQP federation configuration these names should not collide as each policy will
    * create only one producer for a given address or queue.
    */
   public static final String FEDERATION_TARGET_PRODUCER_RESOURCE_TEMPLATE = FEDERATION_TARGET_POLICY_RESOURCE_TEMPLATE + ".producer.%s";

   // MBean naming that will be registered under an name that links to the location of the bean
   // either source or target end of the broker connection.

   /**
    * The template used to create the object name suffix that is appending to the broker connection object name when
    * adding and removing AMQP federation policy control elements.
    */
   public static final String FEDERATION_NAME_TEMPLATE = "serviceCatagory=federations,federationName=%s";

   /**
    * The template used to create the object name suffix that is appending to the broker connection object name when
    * adding and removing AMQP federation policy control elements.
    */
   public static final String FEDERATION_POLICY_NAME_TEMPLATE = FEDERATION_NAME_TEMPLATE + ",policyType=%s,policyName=%s";

   /**
    * The template used to create the object name suffix that is appending to the broker connection object name when
    * adding and removing AMQP federation queue consumer control elements.
    */
   public static final String FEDERATION_QUEUE_CONSUMER_NAME_TEMPLATE = FEDERATION_POLICY_NAME_TEMPLATE + ",linkType=consumers,fqqn=%s";

   /**
    * The template used to create the object name suffix that is appending to the broker connection object name when
    * adding and removing AMQP federation address consumer control elements.
    */
   public static final String FEDERATION_ADDRESS_CONSUMER_NAME_TEMPLATE = FEDERATION_POLICY_NAME_TEMPLATE + ",linkType=consumers,address=%s";

   /**
    * The template used to create the object name suffix that is appending to the broker connection object name when
    * adding and removing AMQP federation queue producer control elements.
    */
   public static final String FEDERATION_QUEUE_PRODUCER_NAME_TEMPLATE = FEDERATION_POLICY_NAME_TEMPLATE + ",linkType=producers,fqqn=%s";

   /**
    * The template used to create the object name suffix that is appending to the broker connection object name when
    * adding and removing AMQP federation address producer control elements.
    */
   public static final String FEDERATION_ADDRESS_PRODUCER_NAME_TEMPLATE = FEDERATION_POLICY_NAME_TEMPLATE + ",linkType=producers,address=%s";

   // APIS for registration of federation instance with management

   /**
    * Register the given {@link AMQPFederationSource} instance with the broker management services.
    *
    * @param federation The federation source instance being registered with management.
    * @throws Exception if an error occurs while registering the federation with the management services.
    */
   public static void registerFederationSource(AMQPFederationSource federation) throws Exception {
      final String federationName = federation.getName();
      final String brokerConnectionName = federation.getBrokerConnection().getName();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final AMQPFederationSourceControlType control = new AMQPFederationSourceControlType(server, federation);

      management.registerUntypedControl(getFederationSourceResourceName(brokerConnectionName, federationName),
                                        control,
                                        getFederationSourceObjectName(management, brokerConnectionName, federationName));
   }

   /**
    * Unregister the given {@link AMQPFederationSource} instance with the broker management services.
    *
    * @param federation The federation source instance being unregistered from management.
    * @throws Exception if an error occurs while unregistering the federation with the management services.
    */
   public static void unregisterFederationSource(AMQPFederationSource federation) throws Exception {
      final String federationName = federation.getName();
      final String brokerConnectionName = federation.getBrokerConnection().getName();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();

      management.unregisterUntypedControl(getFederationSourceResourceName(brokerConnectionName, federationName),
                                          getFederationSourceObjectName(management, brokerConnectionName, federationName));
   }

   public static String getFederationSourceResourceName(String brokerConnectionName, String federationName) {
      return String.format(FEDERATION_SOURCE_RESOURCE_TEMPLATE, brokerConnectionName, federationName);
   }

   public static ObjectName getFederationSourceObjectName(ManagementService management, String brokerConnection, String federationName) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + FEDERATION_NAME_TEMPLATE,
            management.getObjectNameBuilder().getBrokerConnectionBaseObjectNameString(brokerConnection),
            ObjectName.quote(federationName)));
   }

   /**
    * Register the given {@link AMQPFederationTarget} instance with the broker management services.
    *
    * @param federation The federation target instance being registered with management.
    * @throws Exception if an error occurs while registering the federation with the management services.
    */
   public static void registerFederationTarget(String remoteNodeId, String brokerConnectionName, AMQPFederationTarget federation) throws Exception {
      final String federationName = federation.getName();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final AMQPFederationTargetControlType control = new AMQPFederationTargetControlType(server, federation);

      management.registerUntypedControl(getFederationTargetResourceName(remoteNodeId, brokerConnectionName, federationName),
                                        control,
                                        getFederationTargetObjectName(management, remoteNodeId, brokerConnectionName, federationName));
   }

   /**
    * Unregister the given {@link AMQPFederationTarget} instance with the broker management services.
    *
    * @param federation The federation target instance being unregistered from management.
    * @throws Exception if an error occurs while unregistering the federation with the management services.
    */
   public static void unregisterFederationTarget(String remoteNodeId, String brokerConnectionName, AMQPFederationTarget federation) throws Exception {
      final String federationName = federation.getName();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();

      management.unregisterUntypedControl(getFederationTargetResourceName(remoteNodeId, brokerConnectionName, federationName),
                                          getFederationTargetObjectName(management, remoteNodeId, brokerConnectionName, federationName));
   }

   public static String getFederationTargetResourceName(String remoteNodeId, String brokerConnectionName, String federationName) {
      return String.format(FEDERATION_TARGET_RESOURCE_TEMPLATE, remoteNodeId, brokerConnectionName, federationName);
   }

   public static ObjectName getFederationTargetObjectName(ManagementService management, String remoteNodeId, String brokerConnection, String federationName) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + FEDERATION_NAME_TEMPLATE,
            management.getObjectNameBuilder().getRemoteBrokerConnectionBaseObjectNameString(remoteNodeId, brokerConnection),
            ObjectName.quote(federationName)));
   }

   // APIs for federation address and queue policy management

   /**
    * Register a local federation policy manager with the server management services for a federation source.
    *
    * @param brokerConnectionName The name of the broker connection that owns the manager being registered.
    * @param manager              The AMQP federation policy manager instance that is being managed.
    * @throws Exception if an error occurs while registering the manager with the management services.
    */
   public static void registerLocalPolicyOnSource(String brokerConnectionName, AMQPFederationLocalPolicyManager manager) throws Exception {
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final AMQPFederationLocalPolicyControlType control = new AMQPFederationLocalPolicyControlType(manager);
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      management.registerUntypedControl(getFederationSourcePolicyResourceName(brokerConnectionName, federationName, policyName),
                                        control,
                                        getFederationSourcePolicyObjectName(management, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName));
   }

   /**
    * Unregister a local federation policy manager with the server management services for a federation source.
    *
    * @param brokerConnectionName The name of the broker connection that owns the manager being unregistered.
    * @param manager              The AMQP federation policy manager instance that is being managed.
    * @throws Exception if an error occurs while unregistering the manager with the management services.
    */
   public static void unregisterLocalPolicyOnSource(String brokerConnectionName, AMQPFederationLocalPolicyManager manager) throws Exception {
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      management.unregisterUntypedControl(getFederationSourcePolicyResourceName(brokerConnectionName, federationName, policyName),
                                          getFederationSourcePolicyObjectName(management, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName));
   }

   /**
    * Register a remote federation policy manager with the server management services for a federation source.
    *
    * @param brokerConnectionName The name of the broker connection that owns the manager being registered.
    * @param manager              The AMQP federation policy manager instance that is being managed.
    * @throws Exception if an error occurs while registering the manager with the management services.
    */
   public static void registerRemotePolicyOnSource(String brokerConnectionName, AMQPFederationRemotePolicyManager manager) throws Exception {
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final AMQPFederationRemotePolicyControl control = new AMQPFederationRemotePolicyControlType(server, manager);
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      management.registerUntypedControl(getFederationSourcePolicyResourceName(brokerConnectionName, federationName, policyName),
                                        control,
                                        getFederationSourcePolicyObjectName(management, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName));
   }

   /**
    * Unregister a remote federation policy manager with the server management services for a federation source.
    *
    * @param brokerConnectionName The name of the broker connection that owns the manager being unregistered.
    * @param manager              The AMQP federation policy manager instance that is being managed.
    * @throws Exception if an error occurs while unregistering the manager with the management services.
    */
   public static void unregisterRemotePolicyOnSource(String brokerConnectionName, AMQPFederationRemotePolicyManager manager) throws Exception {
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      management.unregisterUntypedControl(getFederationSourcePolicyResourceName(brokerConnectionName, federationName, policyName),
                                          getFederationSourcePolicyObjectName(management, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName));
   }

   public static String getFederationSourcePolicyResourceName(String brokerConnectionName, String federationName, String policyName) {
      return String.format(FEDERATION_SOURCE_POLICY_RESOURCE_TEMPLATE, brokerConnectionName, federationName, policyName);
   }

   public static ObjectName getFederationSourcePolicyObjectName(ManagementService management, String brokerConnection, String federationName, String policyType, String policyName) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + FEDERATION_POLICY_NAME_TEMPLATE,
            management.getObjectNameBuilder().getBrokerConnectionBaseObjectNameString(brokerConnection),
            ObjectName.quote(federationName),
            ObjectName.quote(policyType),
            ObjectName.quote(policyName)));
   }

   /**
    * Register a local federation policy manager with the server management services for a federation target.
    *
    * @param remoteNodeId         The remote broker node ID that is the source of the federation operations.
    * @param brokerConnectionName The name of the remote broker connection that owns the manager being registered.
    * @param manager              The AMQP federation policy manager instance that is being managed.
    * @throws Exception if an error occurs while registering the manager with the management services.
    */
   public static void registerLocalPolicyOnTarget(String remoteNodeId, String brokerConnectionName, AMQPFederationLocalPolicyManager manager) throws Exception {
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final AMQPFederationLocalPolicyControlType control = new AMQPFederationLocalPolicyControlType(manager);
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      management.registerUntypedControl(getFederationTargetPolicyResourceName(remoteNodeId, brokerConnectionName, federationName, policyName),
                                        control,
                                        getFederationTargetPolicyObjectName(management, remoteNodeId, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName));
   }

   /**
    * Unregister a local federation policy manager with the server management services for a federation target.
    *
    * @param remoteNodeId         The remote broker node ID that is the source of the federation operations.
    * @param brokerConnectionName The name of the remote broker connection that owns the manager being unregistered.
    * @param manager              The AMQP federation policy manager instance that is being managed.
    * @throws Exception if an error occurs while unregistering the manager with the management services.
    */
   public static void unregisterLocalPolicyOnTarget(String remoteNodeId, String brokerConnectionName, AMQPFederationLocalPolicyManager manager) throws Exception {
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      management.unregisterUntypedControl(getFederationTargetPolicyResourceName(remoteNodeId, brokerConnectionName, federationName, policyName),
                                          getFederationTargetPolicyObjectName(management, remoteNodeId, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName));
   }

   /**
    * Register a remote federation policy manager with the server management services for a federation target.
    *
    * @param remoteNodeId         The remote broker node ID that is the source of the federation operations.
    * @param brokerConnectionName The name of the remote broker connection that owns the manager being registered.
    * @param manager              The AMQP federation policy manager instance that is being managed.
    * @throws Exception if an error occurs while registering the manager with the management services.
    */
   public static void registerRemotePolicyOnTarget(String remoteNodeId, String brokerConnectionName, AMQPFederationRemotePolicyManager manager) throws Exception {
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final AMQPFederationRemotePolicyControl control = new AMQPFederationRemotePolicyControlType(server, manager);
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      management.registerUntypedControl(getFederationTargetPolicyResourceName(remoteNodeId, brokerConnectionName, federationName, policyName),
                                        control,
                                        getFederationTargetPolicyObjectName(management, remoteNodeId, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName));
   }

   /**
    * Unregister a remote federation policy manager with the server management services for a federation target.
    *
    * @param remoteNodeId         The remote broker node ID that is the source of the federation operations.
    * @param brokerConnectionName The name of the remote broker connection that owns the manager being unregistered.
    * @param manager              The AMQP federation policy manager instance that is being managed.
    * @throws Exception if an error occurs while unregistering the manager with the management services.
    */
   public static void unregisterRemotePolicyOnTarget(String remoteNodeId, String brokerConnectionName, AMQPFederationRemotePolicyManager manager) throws Exception {
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      management.unregisterUntypedControl(getFederationTargetPolicyResourceName(remoteNodeId, brokerConnectionName, federationName, policyName),
                                          getFederationTargetPolicyObjectName(management, remoteNodeId, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName));
   }

   public static String getFederationTargetPolicyResourceName(String remoteNodeId, String brokerConnectionName, String federationName, String policyName) {
      return String.format(FEDERATION_TARGET_POLICY_RESOURCE_TEMPLATE, remoteNodeId, brokerConnectionName, federationName, policyName);
   }

   public static ObjectName getFederationTargetPolicyObjectName(ManagementService management, String remoteNodeId, String brokerConnection, String federationName, String policyType, String policyName) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + FEDERATION_POLICY_NAME_TEMPLATE,
            management.getObjectNameBuilder().getRemoteBrokerConnectionBaseObjectNameString(remoteNodeId, brokerConnection),
            ObjectName.quote(federationName),
            ObjectName.quote(policyType),
            ObjectName.quote(policyName)));
   }

   // APIs for federation consumer and producer management

   /**
    * Registers the federation consumer with the server management services on the source.
    *
    * @param brokerConnectionName The name of the broker connection that owns the consumer being registered.
    * @param consumer             The AMQP federation consumer instance that is being managed.
    * @throws Exception if an error occurs while registering the consumer with the management services.
    */
   public static void registerFederationSourceConsumer(String brokerConnectionName, AMQPFederationConsumer consumer) throws Exception {
      final AMQPFederationPolicyManager manager = consumer.getPolicyManager();
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final AMQPFederationConsumerControlType control = new AMQPFederationConsumerControlType(consumer);
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      if (consumer.getRole() == FederationConsumerInfo.Role.ADDRESS_CONSUMER) {
         management.registerUntypedControl(getFederationSourceAddressConsumerResourceName(brokerConnectionName, federationName, policyName, consumer.getConsumerInfo().getAddress()),
                                           control,
                                           getFederationSourceAddressConsumerObjectName(management, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, consumer.getConsumerInfo().getAddress()));
      } else {
         management.registerUntypedControl(getFederationSourceQueueConsumerResourceName(brokerConnectionName, federationName, policyName, consumer.getConsumerInfo().getFqqn()),
                                           control,
                                           getFederationSourceQueueConsumerObjectName(management, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, consumer.getConsumerInfo().getFqqn()));
      }
   }

   /**
    * Unregisters the federation consumer with the server management services on the source.
    *
    * @param brokerConnectionName The name of the broker connection that owns the consumer being registered.
    * @param consumer             The AMQP federation consumer instance that is being managed.
    * @throws Exception if an error occurs while registering the consumer with the management services.
    */
   public static void unregisterFederationSourceConsumer(String brokerConnectionName, AMQPFederationConsumer consumer) throws Exception {
      final AMQPFederationPolicyManager manager = consumer.getPolicyManager();
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      if (consumer.getRole() == FederationConsumerInfo.Role.ADDRESS_CONSUMER) {
         management.unregisterUntypedControl(getFederationSourceAddressConsumerResourceName(brokerConnectionName, federationName, policyName, consumer.getConsumerInfo().getAddress()),
                                             getFederationSourceAddressConsumerObjectName(management, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, consumer.getConsumerInfo().getAddress()));
      } else {
         management.unregisterUntypedControl(getFederationSourceQueueConsumerResourceName(brokerConnectionName, federationName, policyName, consumer.getConsumerInfo().getFqqn()),
                                             getFederationSourceQueueConsumerObjectName(management, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, consumer.getConsumerInfo().getFqqn()));
      }
   }

   /**
    * Registers the federation producer with the server management services on the source.
    *
    * @param brokerConnectionName The name of the broker connection that owns the producer being registered.
    * @param sender               The AMQP federation sender controller that manages the federation producer.
    * @throws Exception if an error occurs while registering the producer with the management services.
    */
   public static void registerFederationSourceProducer(String brokerConnectionName, AMQPFederationSenderController sender) throws Exception {
      final AMQPFederationPolicyManager manager = sender.getPolicyManager();
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final AMQPFederationProducerControlType control = new AMQPFederationProducerControlType(sender);
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      if (sender.getRole() == Role.ADDRESS_PRODUCER) {
         final String address = control.getAddress();
         management.registerUntypedControl(getFederationSourceAddressProducerResourceName(brokerConnectionName, federationName, policyName, address),
                                           control,
                                           getFederationSourceAddressProducerObjectName(management, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, address));
      } else {
         final String fqqn = control.getFqqn();
         management.registerUntypedControl(getFederationSourceQueueProducerResourceName(brokerConnectionName, federationName, policyName, fqqn),
                                           control,
                                           getFederationSourceQueueProducerObjectName(management, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, fqqn));
      }
   }

   /**
    * Unregisters the federation producer with the server management services on the source.
    *
    * @param brokerConnectionName The name of the broker connection that owns the producer being registered.
    * @param sender               The AMQP federation sender controller that manages the federation producer.
    * @throws Exception if an error occurs while registering the producer with the management services.
    */
   public static void unregisterFederationSourceProducer(String brokerConnectionName, AMQPFederationSenderController sender) throws Exception {
      final AMQPFederationPolicyManager manager = sender.getPolicyManager();
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      if (sender.getRole() == Role.ADDRESS_PRODUCER) {
         final String address = sender.getServerConsumer().getQueueAddress().toString();
         management.unregisterUntypedControl(getFederationSourceAddressProducerResourceName(brokerConnectionName, federationName, policyName, address),
                                             getFederationSourceAddressProducerObjectName(management, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, address));
      } else {
         final String fqqn = CompositeAddress.toFullyQualified(sender.getServerConsumer().getQueueAddress().toString(), sender.getServerConsumer().getQueueName().toString());
         management.unregisterUntypedControl(getFederationSourceQueueProducerResourceName(brokerConnectionName, federationName, policyName, fqqn),
                                             getFederationSourceQueueProducerObjectName(management, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, fqqn));
      }
   }

   public static String getFederationSourceAddressConsumerResourceName(String brokerConnectionName, String federationName, String policyName, String address) {
      return String.format(FEDERATION_SOURCE_CONSUMER_RESOURCE_TEMPLATE, brokerConnectionName, federationName, policyName, address);
   }

   public static String getFederationSourceQueueConsumerResourceName(String brokerConnectionName, String federationName, String policyName, String fqqn) {
      return String.format(FEDERATION_SOURCE_CONSUMER_RESOURCE_TEMPLATE, brokerConnectionName, federationName, policyName, fqqn);
   }

   public static String getFederationSourceAddressProducerResourceName(String brokerConnectionName, String federationName, String policyName, String address) {
      return String.format(FEDERATION_SOURCE_PRODUCER_RESOURCE_TEMPLATE, brokerConnectionName, federationName, policyName, address);
   }

   public static String getFederationSourceQueueProducerResourceName(String brokerConnectionName, String federationName, String policyName, String fqqn) {
      return String.format(FEDERATION_SOURCE_PRODUCER_RESOURCE_TEMPLATE, brokerConnectionName, federationName, policyName, fqqn);
   }

   public static ObjectName getFederationSourceAddressConsumerObjectName(ManagementService management, String brokerConnection, String federationName, String policyType, String policyName, String address) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + FEDERATION_ADDRESS_CONSUMER_NAME_TEMPLATE,
            management.getObjectNameBuilder().getBrokerConnectionBaseObjectNameString(brokerConnection),
            ObjectName.quote(federationName),
            ObjectName.quote(policyType),
            ObjectName.quote(policyName),
            ObjectName.quote(address)));
   }

   public static ObjectName getFederationSourceAddressProducerObjectName(ManagementService management, String brokerConnection, String federationName, String policyType, String policyName, String address) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + FEDERATION_ADDRESS_PRODUCER_NAME_TEMPLATE,
            management.getObjectNameBuilder().getBrokerConnectionBaseObjectNameString(brokerConnection),
            ObjectName.quote(federationName),
            ObjectName.quote(policyType),
            ObjectName.quote(policyName),
            ObjectName.quote(address)));
   }

   public static ObjectName getFederationSourceQueueConsumerObjectName(ManagementService management, String brokerConnection, String federationName, String policyType, String policyName, String fqqn) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + FEDERATION_QUEUE_CONSUMER_NAME_TEMPLATE,
            management.getObjectNameBuilder().getBrokerConnectionBaseObjectNameString(brokerConnection),
            ObjectName.quote(federationName),
            ObjectName.quote(policyType),
            ObjectName.quote(policyName),
            ObjectName.quote(fqqn)));
   }

   public static ObjectName getFederationSourceQueueProducerObjectName(ManagementService management, String brokerConnection, String federationName, String policyType, String policyName, String fqqn) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + FEDERATION_QUEUE_PRODUCER_NAME_TEMPLATE,
            management.getObjectNameBuilder().getBrokerConnectionBaseObjectNameString(brokerConnection),
            ObjectName.quote(federationName),
            ObjectName.quote(policyType),
            ObjectName.quote(policyName),
            ObjectName.quote(fqqn)));
   }

   /**
    * Registers the federation consumer with the server management services on the target.
    *
    * @param brokerConnectionName The name of the remote broker connection that owns the consumer being registered.
    * @param consumer             The AMQP federation consumer instance that is being managed.
    * @throws Exception if an error occurs while registering the consumer with the management services.
    */
   public static void registerFederationTargetConsumer(String remoteNodeId, String brokerConnectionName, AMQPFederationConsumer consumer) throws Exception {
      final AMQPFederationPolicyManager manager = consumer.getPolicyManager();
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final AMQPFederationConsumerControlType control = new AMQPFederationConsumerControlType(consumer);
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      if (consumer.getRole() == FederationConsumerInfo.Role.ADDRESS_CONSUMER) {
         management.registerUntypedControl(getFederationTargetAddressConsumerResourceName(remoteNodeId, brokerConnectionName, federationName, policyName, consumer.getConsumerInfo().getAddress()),
                                           control,
                                           getFederationTargetAddressConsumerObjectName(management, remoteNodeId, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, consumer.getConsumerInfo().getAddress()));
      } else {
         management.registerUntypedControl(getFederationTargetQueueConsumerResourceName(remoteNodeId, brokerConnectionName, federationName, policyName, consumer.getConsumerInfo().getFqqn()),
                                           control,
                                           getFederationTargetQueueConsumerObjectName(management, remoteNodeId, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, consumer.getConsumerInfo().getFqqn()));
      }
   }

   /**
    * Unregisters the federation consumer with the server management services on the target.
    *
    * @param brokerConnectionName The name of the remote broker connection that owns the consumer being registered.
    * @param consumer             The AMQP federation consumer instance that is being managed.
    * @throws Exception if an error occurs while registering the consumer with the management services.
    */
   public static void unregisterFederationTargetConsumer(String remoteNodeId, String brokerConnectionName, AMQPFederationConsumer consumer) throws Exception {
      final AMQPFederationPolicyManager manager = consumer.getPolicyManager();
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      if (consumer.getRole() == FederationConsumerInfo.Role.ADDRESS_CONSUMER) {
         management.unregisterUntypedControl(getFederationTargetAddressConsumerResourceName(remoteNodeId, brokerConnectionName, federationName, policyName, consumer.getConsumerInfo().getAddress()),
                                             getFederationTargetAddressConsumerObjectName(management, remoteNodeId, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, consumer.getConsumerInfo().getAddress()));
      } else {
         management.unregisterUntypedControl(getFederationTargetQueueConsumerResourceName(remoteNodeId, brokerConnectionName, federationName, policyName, consumer.getConsumerInfo().getFqqn()),
                                             getFederationTargetQueueConsumerObjectName(management, remoteNodeId, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, consumer.getConsumerInfo().getFqqn()));
      }
   }

   /**
    * Registers the federation producer with the server management services on the target.
    *
    * @param brokerConnectionName The name of the remote broker connection that owns the producer being registered.
    * @param sender               The AMQP federation sender controller that manages the federation producer.
    * @throws Exception if an error occurs while registering the producer with the management services.
    */
   public static void registerFederationTargetProducer(String remoteNodeId, String brokerConnectionName, AMQPFederationSenderController sender) throws Exception {
      final AMQPFederationPolicyManager manager = sender.getPolicyManager();
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final AMQPFederationProducerControlType control = new AMQPFederationProducerControlType(sender);
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      if (sender.getRole() == Role.ADDRESS_PRODUCER) {
         final String address = control.getAddress();
         management.registerUntypedControl(getFederationTargetAddressProducerResourceName(remoteNodeId, brokerConnectionName, federationName, policyName, address),
                                           control,
                                           getFederationTargetAddressProducerObjectName(management, remoteNodeId, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, address));
      } else {
         final String fqqn = control.getFqqn();
         management.registerUntypedControl(getFederationTargetQueueProducerResourceName(remoteNodeId, brokerConnectionName, federationName, policyName, fqqn),
                                           control,
                                           getFederationTargetQueueProducerObjectName(management, remoteNodeId, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, fqqn));
      }
   }

   /**
    * Unregisters the federation producer with the server management services on the target.
    *
    * @param brokerConnectionName The name of the remote broker connection that owns the producer being registered.
    * @param sender               The AMQP federation sender controller that manages the federation producer.
    * @throws Exception if an error occurs while registering the producer with the management services.
    */
   public static void unregisterFederationTargetProducer(String remoteNodeId, String brokerConnectionName, AMQPFederationSenderController sender) throws Exception {
      final AMQPFederationPolicyManager manager = sender.getPolicyManager();
      final AMQPFederation federation = manager.getFederation();
      final ActiveMQServer server = federation.getServer();
      final ManagementService management = server.getManagementService();
      final String federationName = federation.getName();
      final String policyName = manager.getPolicyName();

      if (sender.getRole() == Role.ADDRESS_PRODUCER) {
         final String address = sender.getServerConsumer().getQueueAddress().toString();
         management.unregisterUntypedControl(getFederationTargetAddressProducerResourceName(remoteNodeId, brokerConnectionName, federationName, policyName, address),
                                             getFederationTargetAddressProducerObjectName(management, remoteNodeId, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, address));
      } else {
         final String fqqn = CompositeAddress.toFullyQualified(sender.getServerConsumer().getQueueAddress().toString(), sender.getServerConsumer().getQueueName().toString());
         management.unregisterUntypedControl(getFederationTargetQueueProducerResourceName(remoteNodeId, brokerConnectionName, federationName, policyName, fqqn),
                                             getFederationTargetQueueProducerObjectName(management, remoteNodeId, brokerConnectionName, federationName, manager.getPolicyType().toString(), policyName, fqqn));
      }
   }

   public static String getFederationTargetAddressConsumerResourceName(String remoteNodeId, String brokerConnectionName, String federationName, String policyName, String address) {
      return String.format(FEDERATION_SOURCE_CONSUMER_RESOURCE_TEMPLATE, brokerConnectionName, federationName, policyName, address);
   }

   public static String getFederationTargetQueueConsumerResourceName(String remoteNodeId, String brokerConnectionName, String federationName, String policyName, String fqqn) {
      return String.format(FEDERATION_SOURCE_CONSUMER_RESOURCE_TEMPLATE, brokerConnectionName, federationName, policyName, fqqn);
   }

   public static String getFederationTargetAddressProducerResourceName(String remoteNodeId, String brokerConnectionName, String federationName, String policyName, String address) {
      return String.format(FEDERATION_SOURCE_PRODUCER_RESOURCE_TEMPLATE, brokerConnectionName, federationName, policyName, address);
   }

   public static String getFederationTargetQueueProducerResourceName(String remoteNodeId, String brokerConnectionName, String federationName, String policyName, String fqqn) {
      return String.format(FEDERATION_SOURCE_PRODUCER_RESOURCE_TEMPLATE, brokerConnectionName, federationName, policyName, fqqn);
   }

   public static ObjectName getFederationTargetAddressConsumerObjectName(ManagementService management, String remoteNodeId, String brokerConnection, String federationName, String policyType, String policyName, String address) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + FEDERATION_ADDRESS_CONSUMER_NAME_TEMPLATE,
            management.getObjectNameBuilder().getRemoteBrokerConnectionBaseObjectNameString(remoteNodeId, brokerConnection),
            ObjectName.quote(federationName),
            ObjectName.quote(policyType),
            ObjectName.quote(policyName),
            ObjectName.quote(address)));
   }

   public static ObjectName getFederationTargetAddressProducerObjectName(ManagementService management, String remoteNodeId, String brokerConnection, String federationName, String policyType, String policyName, String address) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + FEDERATION_ADDRESS_PRODUCER_NAME_TEMPLATE,
            management.getObjectNameBuilder().getRemoteBrokerConnectionBaseObjectNameString(remoteNodeId, brokerConnection),
            ObjectName.quote(federationName),
            ObjectName.quote(policyType),
            ObjectName.quote(policyName),
            ObjectName.quote(address)));
   }

   public static ObjectName getFederationTargetQueueConsumerObjectName(ManagementService management, String remoteNodeId, String brokerConnection, String federationName, String policyType, String policyName, String fqqn) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + FEDERATION_QUEUE_CONSUMER_NAME_TEMPLATE,
            management.getObjectNameBuilder().getRemoteBrokerConnectionBaseObjectNameString(remoteNodeId, brokerConnection),
            ObjectName.quote(federationName),
            ObjectName.quote(policyType),
            ObjectName.quote(policyName),
            ObjectName.quote(fqqn)));
   }

   public static ObjectName getFederationTargetQueueProducerObjectName(ManagementService management, String remoteNodeId, String brokerConnection, String federationName, String policyType, String policyName, String fqqn) throws Exception {
      return ObjectName.getInstance(
         String.format("%s," + FEDERATION_QUEUE_PRODUCER_NAME_TEMPLATE,
            management.getObjectNameBuilder().getRemoteBrokerConnectionBaseObjectNameString(remoteNodeId, brokerConnection),
            ObjectName.quote(federationName),
            ObjectName.quote(policyType),
            ObjectName.quote(policyName),
            ObjectName.quote(fqqn)));
   }
}
