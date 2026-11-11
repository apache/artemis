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

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;

/**
 * A Packet represents a packet of data transmitted over a connection.
 */
public interface Packet {

   int INITIAL_PACKET_SIZE = 1500;

   /**
    * Sets the channel id that should be used once the packet has been successfully decoded it is sent to the correct
    * channel.
    *
    * @param channelID the id of the channel to handle the packet
    */
   void setChannelID(long channelID);

   /**
    * This will return the expected packet size for the encoding
    */
   default int expectedEncodeSize() {
      return INITIAL_PACKET_SIZE;
   }

   boolean isRequiresResponse();

   boolean isResponseAsync();

   long getCorrelationID();

   void setCorrelationID(long correlationID);

   /**
    * {@return the channel id of the channel that should handle this packet}
    */
   long getChannelID();

   /**
    * {@return true if this packet is being sent in response to a previously received packet}
    */
   boolean isResponse();

   /**
    * {@return the type of the packet; needed when decoding the packet}
    */
   byte getType();

   /**
    * Encodes the packet and returns a {@link ActiveMQBuffer} containing the data
    *
    * @param connection the connection
    * @return the buffer to encode to
    */
   ActiveMQBuffer encode(CoreRemotingConnection connection);

   /**
    * decodes the buffer into this packet
    *
    * @param buffer the buffer to decode from
    */
   void decode(ActiveMQBuffer buffer);

   /**
    * {@return The size of the entire packet including headers, and extra data; i.e. the size
    * needed to encode this packet}
    */
   int getPacketSize();

   /**
    * {@return true if a confirmation should be sent on receipt of this packet}
    */
   boolean isRequiresConfirmations();

   /**
    * The packet wasn't used because the stream is closed. This gives a chance to sub classes to cleanup anything that
    * won't be used.
    */
   default void release() {
   }
}
