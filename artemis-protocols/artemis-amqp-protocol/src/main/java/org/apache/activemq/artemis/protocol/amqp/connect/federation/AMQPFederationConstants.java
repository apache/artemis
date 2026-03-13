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

import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.qpid.proton.amqp.Symbol;

/**
 * Constants class for values used in the AMQP Federation implementation.
 */
public final class AMQPFederationConstants {

   /**
    * Property added on the AMQP federation control link that carries a version associated with the
    * side of the link the attach frame that carries it came from. A control link will provide two
    * versions to the AMQP federation instance, the local side and the remote side once the link has
    * fully opened.
    */
   public static final Symbol FEDERATION_VERSION = Symbol.getSymbol("federation_version");

   /**
    * Default AMQP federation version used when the version is not set on the control link as that
    * would indicate all versions prior to the addition of versions being added on the control link.
    */
   public static final int FEDERATION_V1 = 1;

   /**
    * Version 2 marker for AMQP federation control links. Version 2.0 bump primarily adjusts the address federation
    * link names to avoid potential link stealing when demand is quickly added and removed and as a result the AMQP
    * source address value uses an FQQN that provides a stable address and queue binding name for the remote federation
    * subscription which is recoverable on reconnects or on broker restart.
    * <p>
    * Also in this version AMQP federation queue consumers that are included in federation demand tracking are forwarded
    * to the remote with their original priority values to avoid a case of infinite decreasing priority loops that can
    * occur in some configurations.
    */
   public static final int FEDERATION_V2 = 2;

   /**
    * Address used by a remote broker instance to validate that an incoming federation connection has access rights to
    * perform federation operations. The user that connects to the AMQP federation endpoint and attempts to create the
    * control link must have write access to this address and any address prefixed by this value.
    * <p>
    * When securing a federation user account the user must have read and write permissions to addresses under this
    * prefix using the broker defined delimiter, this include the ability to create non-durable resources.
    *
    * <pre>
    *    $ACTIVEMQ_ARTEMIS_FEDERATION.#;
    * </pre>
    */
   public static final String FEDERATION_BASE_VALIDATION_ADDRESS = "$ACTIVEMQ_ARTEMIS_FEDERATION";

   /**
    * The prefix value added when creating a federation control link beyond the initial portion of the validation
    * address prefix. Links for command and control of federation operations follow the form:
    *
    * <pre>
    *    $ACTIVEMQ_ARTEMIS_FEDERATION.control.&lt;unique-id&gt;
    * </pre>
    */
   public static final String FEDERATION_CONTROL_LINK_PREFIX = "control";

   /**
    * The prefix value added when creating a federation events links beyond the initial portion of the validation
    * address prefix. Links for federation events follow the form:
    *
    * <pre>
    *    $ACTIVEMQ_ARTEMIS_FEDERATION.events.&lt;unique-id&gt;
    * </pre>
    */
   public static final String FEDERATION_EVENTS_LINK_PREFIX = "events";

   /**
    * A desired capability added to the federation control link that must be offered in return for a federation
    * connection to be successfully established.
    */
   public static final Symbol FEDERATION_CONTROL_LINK = Symbol.getSymbol("AMQ_FEDERATION_CONTROL_LINK");

   /**
    * A desired capability added to the federation events links that must be offered in return for a federation event
    * link to be successfully established.
    */
   public static final Symbol FEDERATION_EVENT_LINK = Symbol.getSymbol("AMQ_FEDERATION_EVENT_LINK");

   /**
    * Property name used to embed a nested map of properties meant to be applied if the federation resources created on
    * the remote end of the control link if configured to do so. These properties essentially carry local configuration
    * to the remote side that would otherwise use broker defaults and not match behaviors of resources created on the
    * local side of the connection.
    */
   public static final Symbol FEDERATION_CONFIGURATION = Symbol.getSymbol("federation-configuration");

   /**
    * Property value that can be applied to federation configuration that controls the timeout value for a link attach
    * to complete before the attach attempt is considered to have failed. The value is configured in seconds (default is
    * 30 seconds).
    */
   public static final String LINK_ATTACH_TIMEOUT = "attach-timeout";

   /**
    * Configuration property that defines the amount of credits to batch to an AMQP receiver link and the top up limit
    * when sending more credit once the credits are determined to be running low. this can be sent to the peer so that
    * dual federation configurations share the same configuration on both sides of the connection.
    */
   public static final String RECEIVER_CREDITS = "amqpCredits";

   /**
    * A low water mark for receiver credits that indicates more should be sent to top it up to the original credit batch
    * size. this can be sent to the peer so that dual federation configurations share the same configuration on both
    * sides of the connection.
    */
   public static final String RECEIVER_CREDITS_LOW = "amqpLowCredits";

   /**
    * Configuration property that defines the amount of credits to batch to an AMQP receiver link and the top up value
    * when sending more credit once the broker has capacity available for them. this can be sent to the peer so that
    * dual federation configurations share the same configuration on both sides of the connection.
    */
   public static final String PULL_RECEIVER_BATCH_SIZE = "amqpPullConsumerCredits";

   /**
    * Configuration property used to convey the local side value to use when considering if a message is a large
    * message, this can be sent to the peer so that dual federation configurations share the same configuration on both
    * sides of the connection.
    */
   public static final String LARGE_MESSAGE_THRESHOLD = "minLargeMessageSize";

   /**
    * Configuration property used to convey the local side value to use when considering if federation address consumers
    * should filter using the filters defined on individual bindings, this can be sent to the peer so that dual federation
    * configurations share the same configuration on both sides of the connection. This value can be used to reduce the
    * amount of messages that are federated for an address if the local consumers use filters but must be enabled with care
    * as it can easily lead to duplicate messages being dispatched from the remote as each unique address binding filter
    * creates a remote consumer with that matching filter. If any filters overlap in the messages they allow to pass or
    * there are bindings without a filter then the remote will be sending the same message to multiple federation consumers
    * in order to provide each group of bindings with their matching messages.
    */
   public static final String IGNORE_ADDRESS_BINDING_FILTERS = "ignoreAddressBindingFilters";

   /**
    * Configuration property used to convey the local side value to use when considering if federation queue consumers
    * should filter using the filters defined on individual queue subscriptions, this can be sent to the peer so that
    * dual federation configurations share the same configuration on both sides of the connection. This can be used to
    * prevent multiple subscriptions on the same queue based on local demand with differing subscription filters but
    * does imply that message that don't match those filters would be federated to the local broker.
    */
   public static final String IGNORE_QUEUE_CONSUMER_FILTERS = "ignoreQueueConsumerFilters";

   /**
    * Configuration property used to convey the local side value to use when considering if federation queue consumers
    * should apply a consumer priority offset based on the subscription priority or should use a singular priority
    * offset based on policy configuration. This can be sent to the peer so that dual federation configurations share
    * the same configuration on both sides of the connection. This can be used to prevent multiple subscriptions on the
    * same queue based on local demand with differing consumer priorities but does imply that care needs to be taken to
    * ensure remote consumers would normally have a higher priority value than the configured default priority offset.
    */
   public static final String IGNORE_QUEUE_CONSUMER_PRIORITIES = "ignoreQueueConsumerPriorities";

   /**
    * Configuration property for how a federation receiver should respond to delivery errors indicating that an address is
    * full and cannot accept messages at this time. By default we want to send Modified outcomes with the delivery failed
    * value set to true such that the remote will deliver the message again after incrementing the delivery count of the
    * message.
    */
   public static final String USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS = "amqpUseModifiedForTransientDeliveryErrors";

   /**
    * Configuration property that defines the time in milliseconds that a receiver will wait before considering a pending
    * quiesce timeout to have failed and should close the link. This configuration can be sent to the remote peer so that
    * dual federation configurations share the same configuration on both sides of the connection. This option can be used
    * to override the value specified on the connector URI to allow federation to operate with a different default.
    */
   public static final String RECEIVER_LINK_QUIESCE_TIMEOUT = "amqpLinkQuiesceTimeout";

   /**
    * Configuration property that defines if a federation receiver should drain the link credit when a transient delivery
    * error such as the address being full occurs. This configuration can be sent to the remote peer so that dual federation
    * configurations share the same configuration on both sides of the connection. This option can be used to override the
    * value specified on the connector URI to allow federation to operate with a different default.
    */
   public static final String RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS = "amqpDrainOnTransientDeliveryErrors";

   /**
    * A desired capability added to the federation queue receiver link that must be offered in return for a federation
    * queue receiver to be successfully opened.  On the remote the presence of this capability indicates that the
    * matching queue should be present on the remote and its absence constitutes a failure that should result in the
    * attach request being failed.
    */
   public static final Symbol FEDERATION_QUEUE_RECEIVER = Symbol.getSymbol("AMQ_FEDERATION_QUEUE_RECEIVER");

   /**
    * A desired capability added to the federation address receiver link that must be offered in return for a federation
    * address receiver to be successfully opened.
    */
   public static final Symbol FEDERATION_ADDRESS_RECEIVER = Symbol.getSymbol("AMQ_FEDERATION_ADDRESS_RECEIVER");

   /**
    * Property added to the receiver properties when opening an AMQP federation address or queue consumer that indicates
    * the consumer priority that should be used when creating the remote consumer. The value assign to the properties
    * {@link Map} is a signed integer value.
    */
   public static final Symbol FEDERATION_RECEIVER_PRIORITY = Symbol.getSymbol("priority");

   /**
    * Commands sent across the control link will each carry an operation type to indicate the desired action the remote
    * should take upon receipt of the command. The type of command infers the payload of the structure of the message
    * payload.
    */
   public static final Symbol OPERATION_TYPE = Symbol.getSymbol("x-opt-amq-federation-op-type");

   /**
    * Indicates that the message carries a federation queue match policy that should be added to the remote for reverse
    * federation of matching queue from the remote peer.
    */
   public static final String ADD_QUEUE_POLICY = "ADD_QUEUE_POLICY";

   /**
    * Indicates that the message carries a federation address match policy that should be added to the remote for
    * reverse federation of matching queue from the remote peer.
    */
   public static final String ADD_ADDRESS_POLICY = "ADD_ADDRESS_POLICY";

   /**
    * Both Queue and Address policies carry a unique name that will always be encoded.
    */
   public static final String POLICY_NAME = "policy-name";

   /**
    * Queue policy includes are encoded as a {@link List} of flattened key / value pairs when configured.
    */
   public static final String QUEUE_INCLUDES = "queue-includes";

   /**
    * Queue policy excludes are encoded as a {@link List} of flattened key / value pairs when configured.
    */
   public static final String QUEUE_EXCLUDES = "queue-excludes";

   /**
    * Encodes a boolean value that indicates if the include federation option should be enabled.
    */
   public static final String QUEUE_INCLUDE_FEDERATED = "include-federated";

   /**
    * Encodes a signed integer value that adjusts the priority of the any created queue receivers.
    */
   public static final String QUEUE_PRIORITY_ADJUSTMENT = "priority-adjustment";

   /**
    * Address policy includes are encoded as a {@link List} of string entries when configured.
    */
   public static final String ADDRESS_INCLUDES = "address-includes";

   /**
    * Address policy excludes are encoded as a {@link List} of string entries when configured.
    */
   public static final String ADDRESS_EXCLUDES = "address-excludes";

   /**
    * Encodes a boolean value that indicates if queue auto delete option should be enabled.
    */
   public static final String ADDRESS_AUTO_DELETE = "auto-delete";

   /**
    * Encodes a signed long value that controls the delay before auto deletion if auto delete is enabled.
    */
   public static final String ADDRESS_AUTO_DELETE_DELAY = "auto-delete-delay";

   /**
    * Encodes a signed long value that controls the message count value that allows for address auto delete.
    */
   public static final String ADDRESS_AUTO_DELETE_MSG_COUNT = "auto-delete-msg-count";

   /**
    * Encodes a signed integer value that controls the maximum number of hops allowed for federated messages.
    */
   public static final String ADDRESS_MAX_HOPS = "max-hops";

   /**
    * Encodes boolean value that controls if the address federation should include divert bindings.
    */
   public static final String ADDRESS_ENABLE_DIVERT_BINDINGS = "enable-divert-bindings";

   /**
    * Encodes a boolean value that controls if the address federation should check the matcher value in the
    * policy and if its a wildcard, create a consumer that indicates its source is the wildcard address of
    * the matcher and place the normal address binding under that wildcard but add a filter to prevent any
    * messages other than those of its target address from being routed to it.
    */
   public static final String ADDRESS_ALLOW_WILDCARD_GROUPINGS = "allow-wildcard-groupings";

   /**
    * Encodes a {@link Map} of String keys and values that are carried along in the federation policy (address or
    * queue). These values can be used to add extended configuration to the policy object such as overriding settings
    * from the connection URI.
    */
   public static final String POLICY_PROPERTIES_MAP = "policy-properties-map";

   /**
    * Encodes a string value carrying the name of the {@link Transformer} class to use.
    */
   public static final String TRANSFORMER_CLASS_NAME = "transformer-class-name";

   /**
    * Encodes a {@link Map} of String keys and values that are applied to the transformer configuration for the policy.
    */
   public static final String TRANSFORMER_PROPERTIES_MAP = "transformer-properties-map";

   /**
    * Events sent across the events link will each carry an event type to indicate the event type which controls how the
    * remote reacts to the given event. The type of event infers the payload of the structure of the message payload.
    */
   public static final Symbol EVENT_TYPE = Symbol.getSymbol("x-opt-amq-federation-ev-type");

   /**
    * Indicates that the message carries an address and queue name that was previously requested but did not exist, or
    * that was federated but the remote consumer was closed due to removal of the queue on the target peer.
    */
   public static final String REQUESTED_QUEUE_ADDED = "REQUESTED_QUEUE_ADDED_EVENT";

   /**
    * Indicates that the message carries an address name that was previously requested but did not exist, or that was
    * federated but the remote consumer was closed due to removal of the address on the target peer.
    */
   public static final String REQUESTED_ADDRESS_ADDED = "REQUESTED_ADDRESS_ADDED_EVENT";

   /**
    * Carries the name of a Queue that was either not present when a federation consumer was initiated and subsequently
    * rejected, or was removed and has been recreated.
    */
   public static final String REQUESTED_QUEUE_NAME = "REQUESTED_QUEUE_NAME";

   /**
    * Carries the name of an Address that was either not present when a federation consumer was initiated and
    * subsequently rejected, or was removed and has been recreated.
    */
   public static final String REQUESTED_ADDRESS_NAME = "REQUESTED_ADDRESS_NAME";

   /**
    * When a federation receiver link is being drained due to removal of local demand this timeout value enforces a
    * maximum wait for drain and processing of in-flight messages before the link is forcibly terminated with the
    * assumption that the remote is no longer responding.
    */
   public static final String RECEIVER_QUIESCE_TIMEOUT = "receiverQuiesceTimeout";

   /**
    * When a federation address receiver link has been successfully drained after demand was removed from the federated
    * resource, this value controls how long the link can remain in an attached but idle state before it is closed.
    */
   public static final String ADDRESS_RECEIVER_IDLE_TIMEOUT = "addressReceiverIdleTimeout";

   /**
    * When a federation queue receiver link has been successfully drained after demand was removed from the federated
    * resource, this value controls how long the link can remain in an attached but idle state before it is closed.
    */
   public static final String QUEUE_RECEIVER_IDLE_TIMEOUT = "queueReceiverIdleTimeout";

   /**
    * Property name used to carry the name of the federation that triggered creation of the remote connection. This
    * value is intended to be added to AMQP control link properties to provide the remote peer with the name of the
    * federation that triggered the creation of the control link and allow for lookup of metrics or other data
    * associated with a remote federation targets.
    */
   public static final Symbol FEDERATION_NAME = Symbol.valueOf("federationName");

   /**
    * Property name used to carry the name of the federation policy that triggered creation of the remote resource. This
    * value is intended to be added to AMQP link properties to provide the remote peer with the name of the federation
    * policy that triggered the creation of the link and allow for lookup of metrics or other data associated with a
    * remote policy as links or connections are torn down and re-estabilished.
    */
   public static final Symbol FEDERATION_POLICY_NAME = Symbol.valueOf("federationPolicyName");

}
