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

import java.util.Map;
import org.apache.qpid.proton.amqp.Symbol;

/**
 * Constants class for values used in the AMQP Bridge implementation.
 */
public final class AMQPBridgeConstants {

   /**
    * Property value that can be applied to bridge configuration that controls the timeout value
    * for a link attach to complete before the attach attempt is considered to have failed. The value
    * is configured in seconds (default is 30 seconds).
    */
   public static final String LINK_ATTACH_TIMEOUT = "attach-timeout";

   /**
    * Default timeout value (in seconds) used to control when a link attach is considered to have
    * failed due to not responding to an attach request.
    */
   public static final int DEFAULT_LINK_ATTACH_TIMEOUT = 30;

   /**
    * Configuration property that defines the amount of credits to batch to an AMQP receiver link
    * and the top up limit when sending more credit once the credits are determined to be running
    * low. Overrides the credits value configured on the connector URI and can also override the
    * value assigned to a parent bridge configuration in an receiver from address or queue policy.
    */
   public static final String RECEIVER_CREDITS = "amqpCredits";

   /**
    * A low water mark for receiver credits that indicates more should be sent to top it up to the
    * original credit batch size. Overrides the low credits value configured on the connector URI and
    * can also override the value assigned to a parent bridge configuration in an receiver from address
    * or queue policy. If the receiver credits is set to zero this configuration value is ignored.
    */
   public static final String RECEIVER_CREDITS_LOW = "amqpLowCredits";

   /**
    * Configuration property that defines the amount of credits to batch to an AMQP receiver link
    * and the top up value when sending more credit once the broker has capacity available for
    * them. This value is set of the bridge connection configuration or can override the parent
    * bridge connection value if set on a bridge from queue configuration
    */
   public static final String PULL_RECEIVER_BATCH_SIZE = "amqpPullConsumerCredits";

   /**
    * Default credits granted to a receiver that is in pull mode.
    */
   public static final int DEFAULT_PULL_CREDIT_BATCH_SIZE = 10;

   /**
    * Configuration property used to override the value set on the connector to use when considering
    * if an incoming message is a large message,
    */
   public static final String LARGE_MESSAGE_THRESHOLD = "minLargeMessageSize";

   /**
    * Configuration property used to set the value to use when considering if bridged queue consumers
    * should filter using the filters defined on individual queue subscriptions. This can be used to prevent
    * multiple subscriptions on the same queue based on local demand with differing subscription filters
    * but does imply that message that don't match those filters would be bridged to the local broker. The
    * filters applied would be JMS filters which implies the remote needs to support JMS filters to apply
    * them to the created receiver instance.
    */
   public static final String IGNORE_QUEUE_CONSUMER_FILTERS = "ignoreQueueConsumerFilters";

   /**
    * Default value for the filtering applied to bridge Queue consumers that controls if
    * the filter specified by a consumer subscription is used or if the higher level Queue
    * filter only is applied when creating a bridge Queue consumer.
    */
   public static final boolean DEFAULT_IGNNORE_QUEUE_CONSUMER_FILTERS = false;

   /**
    * Configuration property used to set the value to use when considering if bridged queue consumers
    * should filter using the filters defined on individual queue definitions. The filters applied would
    * be JMS filters which implies the remote needs to support JMS filters to apply them to the created
    * receiver instance. This value would be checked only after checking the initiating queue subscription
    * for a filter and it either had none, or the ignore subscription option was set to <code>true</code>.
    */
   public static final String IGNORE_QUEUE_FILTERS = "ignoreQueueFilters";

   /**
    * Default value for the filtering applied to bridge Queue consumers that controls if
    * the filter specified by a Queue is applied when creating a bridge Queue consumer.
    */
   public static final boolean DEFAULT_IGNNORE_QUEUE_FILTERS = false;

   /**
    * Configuration property used to set whether the bridging receiver links should omit any priority
    * values in the link properties of receivers created to bridge addresses or queues from a remote peer.
    * This can be used to omit this value if the remote does not support it and reacts badly to its
    * presence in the link properties.
    */
   public static final String DISABLE_RECEIVER_PRIORITY = "disableReceiverPriority";

   /**
    * Default value for ignoring the addition of a link property indicating the priority to
    * assign to a receiver link either for address or queue policy managers.
    */
   public static final boolean DEFAULT_DISABLE_RECEIVER_PRIORITY = false;

   /**
    * Configuration property used to set whether the bridging receiver links should track local demand
    * on the bridged resource and simply always create a remote receiver regardless of local demand for
    * bridged messages. This can still be combined with pull receiver functionality but due to the possibility
    * that there are no active consumers of pulled messages the bridge receiver would only ever grant at most
    * one batch of credit until local backlog is removed by the addition of a local consumer or a purge of the
    * bridged resource.
    */
   public static final String DISABLE_RECEIVER_DEMAND_TRACKING = "disableReceiverDemandTracking";

   /**
    * Default value for ignoring local demand as the trigger for bridging an address or queue and
    * simple always creating a receiver if the address or queue that matches the policy exists.
    */
   public static final boolean DEFAULT_DISABLE_RECEIVER_DEMAND_TRACKING = false;

   /**
    * Configuration property that controls if a bridge from address receiver that has been configured
    * to use durable subscriptions will prefer to use a shared durable subscription if the remote peer
    * indicates in its connection capabilities that it supports them. Using a shared subscription can
    * avoid a rare edge case race on rapid attach and re-attach cycles due to demand coming and going
    * rapidly which leads to a stuck consumer. Enabling this on configurations that previously did not
    * have this option set to true could lead to orphaning a previous subscription so care should be
    * taken when changing the defaults for this option.
    */
   public static final String PREFER_SHARED_DURABLE_SUBSCRIPTIONS = "preferSharedDurableSubscriptions";

   /**
    * Default value for whether a bridge from address receiver that has been told to use durable subscriptions
    * should prefer to use a shared durable address subscriptions or a standard JMS style non-shared durable
    * subscription.
    */
   public static final boolean DEFAULT_PREFER_SHARED_DURABLE_SUBSCRIPTIONS = true;

   /**
    * Link Property added to the receiver properties when opening an AMQP bridge address or queue consumer
    * that indicates the consumer priority that should be used when creating the remote consumer. The
    * value assign to the properties {@link Map} is a signed integer value. This value is set on each bridge
    * receiver so long as the option to disable receiver priority is not set to <code>true</code>.
    */
   public static final Symbol BRIDGE_RECEIVER_PRIORITY = Symbol.getSymbol("priority");

   /**
    * Property added to the bridge policies which controls if senders are marked as presettled meaning
    * the messages are sent as settled before dispatch which can improve performance at the cost of
    * reliability.
    */
   public static final String PRESETTLE_SEND_MODE = "presettle";

   /**
    * Default value for link sender settle mode on send to and receiver from bridge policy created
    * links.
    */
   public static final boolean DEFAULT_SEND_SETTLED = false;

   /**
    * Configuration options that controls the maximum number of retires that are attempted when a bridge
    * link is rejected or closed due to a recoverable error such as not-found or remote forced close.
    * A negative value indicates there is no retry limit and a value of zero indicates retries are disabled.
    * The link recoveries stop after this value or for demand dependent links the recoveries stop if local
    * demand is removed.
    */
   public static final String MAX_LINK_RECOVERY_ATTEMPTS = "maxLinkRecoveryAttempts";

   /**
    * Default value for the number of link recovery attempts that will be made for a link that failed to
    * attach or was closed later due to some remote condition, negative values mean try forever.
    */
   public static final int DEFAULT_MAX_LINK_RECOVERY_ATTEMPTS = -1;

   /**
    * Configuration options that controls the initial delay (in milliseconds) before the first link recovery
    * attempt is made when a remote link is closed or rejected.
    */
   public static final String LINK_RECOVERY_INITIAL_DELAY = "linkRecoveryInitialDelay";

   /**
    * Default delay in that will be used on the first link recovery attempt.
    */
   public static final long DEFAULT_LINK_RECOVERY_INITIAL_DELAY = 1_000;

   /**
    * Configuration options that controls the delay (in milliseconds) that will be used as successive link
    * recovery attempts are made.
    */
   public static final String LINK_RECOVERY_DELAY = "linkRecoveryDelay";

   /**
    * Default delay in that will be used as successive link recovery attempts are made.
    */
   public static final long DEFAULT_LINK_RECOVERY_DELAY = 60_000;

   /**
    * Default value for the core message tunneling feature that indicates if core protocol messages
    * should be streamed as binary blobs as the payload of an custom AMQP message which avoids any
    * conversions of the messages to / from AMQP.
    */
   public static final boolean DEFAULT_CORE_MESSAGE_TUNNELING_ENABLED = true;

   /**
    * Default priority adjustment used for a bridge queue policy if no value was specific in the
    * broker configuration file.
    */
   public static final int DEFAULT_PRIORITY_ADJUSTMENT_VALUE = -1;

   /**
    * When a bridge receiver link is being closed due to removal of local demand this timeout
    * value enforces a maximum wait for drain and processing of in-flight messages before the link
    * is forcibly terminated with the assumption that the remote is no longer responding.
    */
   public static final String RECEIVER_QUIESCE_TIMEOUT = "receiverQuiesceTimeout";

   /**
    * Default timeout (milliseconds) applied to bridge receivers that are being closed due to removal
    * of local demand and need to drain link credit and process any in-flight deliveries before closure.
    * If the timeout elapses before the link has quiesced the link is forcibly closed.
    */
   public static final int DEFAULT_RECEIVER_QUIESCE_TIMEOUT = 60_000;

   /**
    * When a bridge address receiver link has been successfully drained after demand was removed
    * from the bridged resource, this value controls how long the link can remain in an attached but
    * idle state before it is closed.
    */
   public static final String ADDRESS_RECEIVER_IDLE_TIMEOUT = "addressReceiverIdleTimeout";

   /**
    * Default timeout (milliseconds) applied to bridge address receivers that have been stopped due to
    * lack of local demand. The close delay prevent a link from detaching in cases where demand drops and
    * returns in quick succession allowing for faster recovery. The idle timeout kicks in once the link has
    * completed its drain of outstanding credit.
    */
   public static final int DEFAULT_ADDRESS_RECEIVER_IDLE_TIMEOUT = 5_000;

   /**
    * When a bridge queue receiver link has been successfully drained after demand was removed
    * from the bridged resource, this value controls how long the link can remain in an attached but
    * idle state before it is closed.
    */
   public static final String QUEUE_RECEIVER_IDLE_TIMEOUT = "queueReceiverIdleTimeout";

   /**
    * Default timeout (milliseconds) applied to bridge queue receivers that have been stopped due to
    * lack of local demand. The close delay prevent a link from detaching in cases where demand drops and
    * returns in quick succession allowing for faster recovery. The idle timeout kicks in once the link has
    * completed its drain of outstanding credit.
    */
   public static final int DEFAULT_QUEUE_RECEIVER_IDLE_TIMEOUT = 60_000;

   /**
    * Default value for the auto delete address sender durable subscription binding.
    */
   public static final boolean DEFAULT_AUTO_DELETE_DURABLE_SUBSCRIPTION = false;

   /**
    * Encodes a boolean value that indicates if AMQP bridge senders should configure an auto delete option
    * for the durable subscription binding on the configured send to address. By default this option is
    * <code>false</code> and the senders durable subscription binding is not auto deleted. This value is
    * only checked if the bridge address sender is configured to use durable address subscriptions.
    */
   public static final String AUTO_DELETE_DURABLE_SUBSCRIPTION = "auto-delete-durable-subscription";

   /**
    * Default value for the auto delete address sender message count for durable subscription bindings.
    */
   public static final long DEFAULT_AUTO_DELETE_DURABLE_SUBSCRIPTION_MSG_COUNT = 0;

   /**
    * Encodes a signed long value that controls the delay before auto deletion if using durable address
    * subscriptions for bridge to address senders.
    */
   public static final String AUTO_DELETE_DURABLE_SUBSCRIPTION_MSG_COUNT = "auto-delete-durable-subscription-message-count";

   /**
    * Default value for the auto delete address sender message count for durable subscription bindings.
    */
   public static final long DEFAULT_AUTO_DELETE_DURABLE_SUBSCRIPTION_DELAY = 0;

   /**
    * Encodes a signed long value that controls the message count value that allows for address auto delete
    * if using durable address subscriptions for bridge to address senders.
    */
   public static final String AUTO_DELETE_DURABLE_SUBSCRIPTION_DELAY = "auto-delete-durable-subscription-delay";

   /**
    * Configuration property for how a bridge receiver should respond to delivery errors indicating that an address is
    * full and cannot accept messages at this time. By default we want to send Modified outcomes with the delivery failed
    * value set to true such that the remote will deliver the message again after incrementing the delivery count of the
    * message.
    */
   public static final String USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS = "amqpUseModifiedForTransientDeliveryErrors";

   /**
    * Default value for how a bridge receiver should respond to delivery errors indicating that an address is full
    * and cannot accept messages at this time. By default we want to send Modified outcomes with the delivery failed
    * value set to true such that the remote will deliver the message again after incrementing the delivery count of
    * the message. This is an opinionated choice and the value set on the connector URI is not referenced by bridges
    * as we want to maintain this behavior unless specifically set on bridge configuration explicitly.
    */
   public static final boolean DEFAULT_USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS = true;

   /**
    * Configuration property that defines the time in milliseconds that a receiver will wait before considering a pending
    * quiesce timeout to have failed and should close the link. This option can be used to override the value specified on
    * the connector URI to allow bridges to operate with a different default.
    */
   public static final String RECEIVER_LINK_QUIESCE_TIMEOUT = "amqpLinkQuiesceTimeout";

   /**
    * Configuration property that defines if a bridge receiver should drain the link credit when a transient delivery
    * error such as the address being full occurs. This option can be used to override the value specified on the
    * connector URI to allow bridges to operate with a different default.
    */
   public static final String RECEIVER_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS = "amqpDrainOnTransientDeliveryErrors";

}
