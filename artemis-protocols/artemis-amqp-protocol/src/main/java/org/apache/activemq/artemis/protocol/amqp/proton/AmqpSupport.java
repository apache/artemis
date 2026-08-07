/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.protocol.amqp.proton;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.DestinationUtil;
import org.apache.qpid.proton.amqp.DescribedType;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.engine.Link;

/**
 * Set of useful methods and definitions used in the AMQP protocol handling
 */
public class AmqpSupport {

   // Key used to add a Runnable initializer to a link that the broker has opened
   // which will be called when the remote responds to the broker outgoing Attach
   // with its own Attach response.
   public static final Object AMQP_LINK_INITIALIZER_KEY = Runnable.class;

   // Default thresholds/values used for granting credit to producers
   public static final int AMQP_CREDITS_DEFAULT = 1000;
   public static final int AMQP_LOW_CREDITS_DEFAULT = 300;

   // Defaults for controlling the interpretation of AMQP dispositions
   public static final boolean AMQP_TREAT_REJECT_AS_UNMODIFIED_DELIVERY_FAILURE = false;

   // Defaults for controlling the behaviour of AMQP dispositions
   public static final boolean AMQP_USE_MODIFIED_FOR_TRANSIENT_DELIVERY_ERRORS = false;

   // Defaults for controlling the behaviour of AMQP credit draining on resource exhaustion
   public static final boolean AMQP_DRAIN_ON_TRANSIENT_DELIVERY_ERRORS = true;

   // Default timeout (in milliseconds) before closing a link as failed if a quiesce is initiated from this peer
   public static final int AMQP_LINK_QUIESCE_TIMEOUT = 600_000; // ten minutes

   // Identification values used to locating JMS selector types.
   public static final Symbol JMS_SELECTOR_KEY = Symbol.valueOf("jms-selector");
   public static final UnsignedLong JMS_SELECTOR_CODE = UnsignedLong.valueOf(0x0000468C00000004L);
   public static final Symbol JMS_SELECTOR_NAME = Symbol.valueOf("apache.org:selector-filter:string");
   public static final Object[] JMS_SELECTOR_FILTER_IDS = new Object[]{JMS_SELECTOR_CODE, JMS_SELECTOR_NAME};
   public static final UnsignedLong NO_LOCAL_CODE = UnsignedLong.valueOf(0x0000468C00000003L);
   public static final Symbol NO_LOCAL_NAME = Symbol.valueOf("apache.org:no-local-filter:list");
   public static final Object[] NO_LOCAL_FILTER_IDS = new Object[]{NO_LOCAL_CODE, NO_LOCAL_NAME};

   // Capabilities used to identify destination type in some requests.
   public static final Symbol TEMP_QUEUE_CAPABILITY = Symbol.valueOf("temporary-queue");
   public static final Symbol TEMP_TOPIC_CAPABILITY = Symbol.valueOf("temporary-topic");
   public static final Symbol QUEUE_CAPABILITY = Symbol.valueOf("queue");
   public static final Symbol TOPIC_CAPABILITY = Symbol.valueOf("topic");

   // Symbols used to announce connection information to remote peer.
   public static final Symbol INVALID_FIELD = Symbol.valueOf("invalid-field");
   public static final Symbol CONTAINER_ID = Symbol.valueOf("container-id");

   // Symbols used to announce connection information to remote peer.
   public static final Symbol ANONYMOUS_RELAY = Symbol.valueOf("ANONYMOUS-RELAY");
   public static final Symbol DELAYED_DELIVERY = Symbol.valueOf("DELAYED_DELIVERY");
   public static final Symbol QUEUE_PREFIX = Symbol.valueOf("queue-prefix");
   public static final Symbol TOPIC_PREFIX = Symbol.valueOf("topic-prefix");
   public static final Symbol SHARED = Symbol.valueOf("shared");
   public static final Symbol GLOBAL = Symbol.valueOf("global");
   public static final Symbol CONNECTION_OPEN_FAILED = Symbol.valueOf("amqp:connection-establishment-failed");
   public static final Symbol PRODUCT = Symbol.valueOf("product");
   public static final Symbol VERSION = Symbol.valueOf("version");
   public static final Symbol PLATFORM = Symbol.valueOf("platform");
   public static final Symbol RESOURCE_DELETED = Symbol.valueOf("amqp:resource-deleted");
   public static final Symbol CONNECTION_FORCED = Symbol.valueOf("amqp:connection:forced");
   public static final Symbol DETACH_FORCED = Symbol.valueOf("amqp:link:detach-forced");
   public static final Symbol NOT_FOUND = Symbol.valueOf("amqp:not-found");
   public static final Symbol SHARED_SUBS = Symbol.valueOf("SHARED-SUBS");
   public static final Symbol NETWORK_HOST = Symbol.valueOf("network-host");
   public static final Symbol PORT = Symbol.valueOf("port");
   static final Symbol SCHEME = Symbol.valueOf("scheme");
   static final Symbol HOSTNAME = Symbol.valueOf("hostname");

   static final Symbol FAILOVER_SERVER_LIST = Symbol.valueOf("failover-server-list");

   public static final int MAX_FRAME_SIZE_DEFAULT = 128 * 1024;
   public static final int INITIAL_REMOTE_MAX_FRAME_SIZE_DEFAULT = 4 * 1024;

   /**
    * Default for the number of allowed transfers for a given incoming delivery. This does govern
    * the maximum message size to an extent when combined with the maximum frame size.
    */
   public static final int DEFAULT_MAX_TRANSFERS_PER_DELIVERY = 65535;

   /**
    * The default maximum depth the decoder will allow before triggering a decoding error when
    * the depth is increased during decode of complex types that can nest objects.
    */
   public static final int DEFAULT_MAX_DECODE_DEPTH = 32;

   /**
    * Default for the allowed number of elements that can be decoded when an array encoding is one of
    * the zero width type (e.g. UINT0). We allow only zero sized zero width array encodings by default
    * as the this is not generally expected in normal encodings.
    */
   public static final int DEFAULT_ZERO_WIDTH_ARRAY_ELEMENT_LIMIT = 0;

   // Symbols used in configuration of newly opened links.
   public static final Symbol COPY = Symbol.getSymbol("copy");

   // Lifetime policy symbols
   public static final Symbol LIFETIME_POLICY = Symbol.valueOf("lifetime-policy");

   public static final Symbol SOLE_CONNECTION_CAPABILITY = Symbol.valueOf("sole-connection-for-container");

   /**
    * A capability added to the sender or receiver links that indicate that the link either wants support for or offers
    * support for tunneling Core messages as custom formatted AMQP messages.
    */
   public static final Symbol CORE_MESSAGE_TUNNELING_SUPPORT = Symbol.getSymbol("AMQ_CORE_MESSAGE_TUNNELING");

   /**
    * Property value that can be applied to federation and bridge configurations that controls if the various links
    * will request that the opposing peers link tunnel core messages inside an AMQP message as a binary blob to be
    * unwrapped on the other side. The sending peer would still need to support this feature for message tunneling to
    * occur.
    */
   public static final String TUNNEL_CORE_MESSAGES = "tunnel-core-messages";

   /**
    * A priority value added to a remote receiver link attach that indicates the desired priority for the receiver
    * created for the link.
    */
   public static final Symbol RECEIVER_PRIORITY = Symbol.getSymbol("priority");

   /**
    * Check the set of remote properties sent on link attach for any values that are treated as indicating priority and
    * return the match if any. If no priority is indicated in the link properties this method returns null.
    *
    * @param remoteProperties The {@link Map} of remote properties sent on the remote attach.
    * @return a {@link Number} indicating the desired link priority or null if none
    */
   public static Number getReceiverPriority(Map<Symbol, Object> remoteProperties) {
      return getReceiverPriority(remoteProperties, null);
   }

   /**
    * Check the set of remote properties sent on link attach for any values that are treated as indicating priority and
    * return the match if any. If no priority is indicated in the link properties this method returns null.
    *
    * @param remoteProperties The {@link Map} of remote properties sent on the remote attach.
    * @param defaultPriority  The default value that should be returned if no remote priority indicated.
    * @return a {@link Number} indicating the desired link priority or null if none
    */
   public static Number getReceiverPriority(Map<Symbol, Object> remoteProperties, Number defaultPriority) {
      final Number remotePriority = remoteProperties != null ? (Number) remoteProperties.get(RECEIVER_PRIORITY) : null;
      return remotePriority != null ? remotePriority : defaultPriority;
   }

   /**
    * Search for a given Symbol in a given array of Symbol object.
    *
    * @param symbols the set of Symbols to search.
    * @param key     the value to try and find in the Symbol array.
    * @return {@code true} if the key is found in the given Symbol array
    */
   public static boolean contains(Symbol[] symbols, Symbol key) {
      if (symbols == null || symbols.length == 0) {
         return false;
      }

      for (Symbol symbol : symbols) {
         if (symbol.equals(key)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Search for a particular filter using a set of known identification values in the Map of filters.
    *
    * @param filters   The filters map that should be searched.
    * @param filterIds The aliases for the target filter to be located.
    * @return the filter if found in the mapping or null if not found
    */
   public static Map.Entry<Symbol, DescribedType> findFilter(Map<Symbol, Object> filters, Object[] filterIds) {

      if (filterIds == null || filterIds.length == 0) {
         StringBuilder ids = new StringBuilder();
         if (filterIds != null) {
            for (Object filterId : filterIds) {
               ids.append(filterId).append(" ");
            }
         }
         throw new IllegalArgumentException("Invalid Filter Ids array passed: " + ids);
      }

      if (filters == null || filters.isEmpty()) {
         return null;
      }

      for (Map.Entry<Symbol, Object> filter : filters.entrySet()) {
         if (filter.getValue() instanceof DescribedType describedType) {
            Object descriptor = describedType.getDescriptor();

            for (Object filterId : filterIds) {
               if (descriptor.equals(filterId)) {
                  return new AbstractMap.SimpleImmutableEntry<>(filter.getKey(), describedType);
               }
            }
         }
      }

      return null;
   }

   private static final Symbol[] EMPTY_CAPABILITIES = new Symbol[0];

   /**
    * Verifies that the desired capabilities that were sent to the remote were indeed offered in return. If the remote
    * has not offered a capability that was desired then the initiating resource should determine if the offered set is
    * still acceptable or it should close the link and report the reason.
    * <p>
    * The remote could have offered more capabilities than the requested desired capabilities, this method does not
    * validate that or consider that a failure.
    *
    * @param link The link in question (Sender or Receiver).
    * @return {@code true} if the remote offered all of the capabilities that were desired
    */
   public static boolean verifyOfferedCapabilities(final Link link) {
      return verifyCapabilities(link.getRemoteOfferedCapabilities(), link.getDesiredCapabilities());
   }

   /**
    * Verifies that the given set of desired capabilities (which should be the full set of desired capabilities
    * configured on the link or a subset of those values) are indeed offered in return. If the remote has not offered a
    * capability that was desired then the initiating resource should determine if the offered set is still acceptable
    * or it should close the link and report the reason.
    * <p>
    * The remote could have offered more capabilities than the requested desired capabilities, this method does not
    * validate that or consider that a failure.
    *
    * @param link         The link in question (Sender or Receiver).
    * @param capabilities The capabilities that are required being checked for.
    * @return {@code true} if the remote offered all of the capabilities that were desired
    */
   public static boolean verifyOfferedCapabilities(final Link link, final Symbol... capabilities) {
      return verifyCapabilities(link.getRemoteOfferedCapabilities(), capabilities);
   }

   /**
    * Verifies that the given desired capability is present in the remote link details.
    * <p>
    * The remote could have desired more capabilities than the one given, this method does not validate that or consider
    * that a failure.
    *
    * @param link              The link in question (Sender or Receiver).
    * @param desiredCapability The non-null capability that is being checked as being desired.
    * @return {@code true} if the remote desired all of the capabilities that were given
    */
   public static boolean verifyDesiredCapability(final Link link, final Symbol desiredCapability) {
      return verifyCapabilities(link.getRemoteDesiredCapabilities(), desiredCapability);
   }

   /**
    * Verifies that the desired capability is present in the Source capabilities.
    *
    * @param source     The Source instance whose capabilities are being searched.
    * @param capability The non-null capability that is being checked as being desired.
    * @return {@code true} if the remote desired all of the capabilities that were given
    */
   public static boolean verifySourceCapability(final Source source, final Symbol capability) {
      return verifyCapabilities(source.getCapabilities(), capability);
   }

   /**
    * Verifies that the desired capability is present in the Source capabilities.
    *
    * @param target     The Target instance whose capabilities are being searched.
    * @param capability The non-null capability that is being checked as being desired.
    * @return {@code true} if the remote desired all of the capabilities that were given
    */
   public static boolean verifyTargetCapability(final Target target, final Symbol capability) {
      return verifyCapabilities(target.getCapabilities(), capability);
   }

   /**
    * Verifies that the given set of capabilities contains each of the desired capabilities.
    * <p>
    * The remote could have offered more capabilities than the requested desired capabilities, this method does not
    * validate that or consider that a failure.
    *
    * @param offered The capabilities that were offered from the remote or were set by the local side
    * @param desired The desired capabilities to search for in the offered set.
    * @return {@code true} if the desired capabilities were found in the offered set
    */
   public static boolean verifyCapabilities(final Symbol[] offered, final Symbol... desired) {
      for (Symbol desiredCapability : Objects.requireNonNullElse(desired, EMPTY_CAPABILITIES)) {
         boolean foundCurrent = false;

         for (Symbol offeredCapability : Objects.requireNonNullElse(offered, EMPTY_CAPABILITIES)) {
            if (desiredCapability.equals(offeredCapability)) {
               foundCurrent = true;
               break;
            }
         }

         if (!foundCurrent) {
            return false;
         }
      }

      return true;
   }

   /**
    * Given the input values construct a Queue name for use in messaging handlers.
    *
    * @param useCoreSubscriptionNaming Should the name match core client subscription naming.
    * @param clientId                  The client ID of the remote peer.
    * @param senderId                  The ID assigned to the sender asking for a generated Queue name.
    * @param shared                    Is this Queue used for shared subscriptions
    * @param global                    Should the shared subscription Queue indicate globally shared.
    * @param isVolatile                Is the Queue meant to be volatile or not.
    * @return a queue name based on the provided inputs
    */
   public static SimpleString createQueueName(boolean useCoreSubscriptionNaming,
                                              String clientId,
                                              String senderId,
                                              boolean shared,
                                              boolean global,
                                              boolean isVolatile) {

      Objects.requireNonNull(senderId, "The sender Id cannot be null");

      if (useCoreSubscriptionNaming) {
         final boolean durable = !isVolatile;
         final String subscriptionName = senderId.contains("|") ? senderId.split("\\|")[0] : senderId;
         final String clientID = clientId == null || clientId.isEmpty() || global ? null : clientId;
         return DestinationUtil.createQueueNameForSubscription(durable, clientID, subscriptionName);
      } else {
         String queue = clientId == null || clientId.isEmpty() || global ? senderId : clientId + "." + senderId;

         if (shared) {
            if (queue.contains("|")) {
               queue = queue.split("\\|")[0];
            }
            if (isVolatile) {
               queue += ":shared-volatile";
            }
            if (global) {
               queue += ":global";
            }
         }

         return SimpleString.of(queue);
      }
   }
}
