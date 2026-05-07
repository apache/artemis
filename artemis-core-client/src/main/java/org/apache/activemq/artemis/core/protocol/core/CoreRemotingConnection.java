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
package org.apache.activemq.artemis.core.protocol.core;

import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.security.ActiveMQPrincipal;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * Extension of RemotingConnection for the Apache Artemis core protocol
 */
public interface CoreRemotingConnection extends RemotingConnection {

   /**
    * The client protocol used  on the communication. This will determine if the client has support for certain packet
    * types
    */
   int getChannelVersion();

   default boolean isVersionBeforeAddressChange() {
      int version = getChannelVersion();
      return  (version > 0 && version < PacketImpl.ADDRESSING_CHANGE_VERSION);
   }

   default boolean isVersionBeforeAsyncResponseChange() {
      int version = getChannelVersion();
      return  (version > 0 && version < PacketImpl.ASYNC_RESPONSE_CHANGE_VERSION);
   }

   default boolean isVersionSupportConsumerPriority() {
      int version = getChannelVersion();
      return  version >= PacketImpl.CONSUMER_PRIORITY_CHANGE_VERSION;
   }

   default boolean isVersionNewFQQN() {
      int version = getChannelVersion();
      return  version >= PacketImpl.ARTEMIS_2_7_0_VERSION;
   }

   default boolean isVersionSupportClientID() {
      int version = getChannelVersion();
      return  version >= PacketImpl.ARTEMIS_2_18_0_VERSION;
   }

   default boolean isVersionSupportRouting() {
      int version = getChannelVersion();
      return  version >= PacketImpl.ARTEMIS_2_18_0_VERSION;
   }

   default boolean isVersionSupportCommitV2() {
      int version = getChannelVersion();
      return  version >= PacketImpl.ARTEMIS_2_21_0_VERSION;
   }

   default boolean isVersionUsingLongOnPageReplication() {
      int version = getChannelVersion();
      return  version >= PacketImpl.ARTEMIS_2_24_0_VERSION;
   }

   default boolean isBeforeTwoEighteen() {
      int version = getChannelVersion();
      return  version < PacketImpl.ARTEMIS_2_18_0_VERSION;
   }

   default boolean isBeforeProducerMetricsChanged() {
      int version = getChannelVersion();
      return version < PacketImpl.ARTEMIS_2_28_0_VERSION;
   }

   default boolean isBeforeAMQPMapCodecChanged() {
      int version = getChannelVersion();
      return version < PacketImpl.ARTEMIS_2_54_0_VERSION;
   }

   /**
    * Sets the client protocol used on the communication. This will determine if the client has support for certain
    * packet types
    */
   void setChannelVersion(int clientVersion);

   /**
    * Returns the channel with the channel id specified.
    * <p>
    * If it does not exist create it with the confirmation window size.
    *
    * @param channelID      the channel id
    * @param confWindowSize the confirmation window size
    * @return the channel
    */
   Channel getChannel(long channelID, int confWindowSize);

   /**
    * add the channel with the specified channel id
    *
    * @param channelID the channel id
    * @param channel   the channel
    */
   void putChannel(long channelID, Channel channel);

   /**
    * remove the channel with the specified channel id
    *
    * @param channelID the channel id
    * @return true if removed
    */
   boolean removeChannel(long channelID);

   /**
    * generate a unique (within this connection) channel id
    *
    * @return the id
    */
   long generateChannelID();

   /**
    * Resets the id generator used to generate id's.
    *
    * @param id the first id to set it to
    */
   void syncIDGeneratorSequence(long id);

   /**
    * {@return the next id to be chosen}
    */
   long getIDGeneratorSequence();

   /**
    * {@return the current timeout for blocking calls)}
    */
   long getBlockingCallTimeout();

   /**
    * {@return the current timeout for blocking calls}
    */
   long getBlockingCallFailoverTimeout();

   /**
    * {@return the transfer lock used when transferring connections}
    */
   Object getTransferLock();

   /**
    * {@return the default security principal}
    */
   ActiveMQPrincipal getDefaultActiveMQPrincipal();

   /**
    * {@see org.apache.activemq.artemis.spi.core.remoting.Connection#blockUntilWritable(long,
    * java.util.concurrent.TimeUnit)}
    */
   boolean blockUntilWritable(long timeout);
}
