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
package org.apache.activemq.artemis.core.protocol.core.impl.wireformat;

import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

public class CreateSessionMessage extends PacketImpl {

   private String name;

   private long sessionChannelID;

   private int version;

   private String username;

   private String password;

   private int minLargeMessageSize;

   private boolean xa;

   private boolean autoCommitSends;

   private boolean autoCommitAcks;

   private boolean preAcknowledge;

   private int windowSize;

   private String defaultAddress;

   public CreateSessionMessage(final String name,
                               final long sessionChannelID,
                               final int version,
                               final String username,
                               final String password,
                               final int minLargeMessageSize,
                               final boolean xa,
                               final boolean autoCommitSends,
                               final boolean autoCommitAcks,
                               final boolean preAcknowledge,
                               final int windowSize,
                               final String defaultAddress) {
      this(CREATESESSION, name, sessionChannelID, version, username, password, minLargeMessageSize, xa, autoCommitSends, autoCommitAcks, preAcknowledge, windowSize, defaultAddress);
   }

   protected CreateSessionMessage(final byte type,
                               final String name,
                               final long sessionChannelID,
                               final int version,
                               final String username,
                               final String password,
                               final int minLargeMessageSize,
                               final boolean xa,
                               final boolean autoCommitSends,
                               final boolean autoCommitAcks,
                               final boolean preAcknowledge,
                               final int windowSize,
                               final String defaultAddress) {
      super(type);

      this.name = name;

      this.sessionChannelID = sessionChannelID;

      this.version = version;

      this.username = username;

      this.password = password;

      this.minLargeMessageSize = minLargeMessageSize;

      this.xa = xa;

      this.autoCommitSends = autoCommitSends;

      this.autoCommitAcks = autoCommitAcks;

      this.windowSize = windowSize;

      this.preAcknowledge = preAcknowledge;

      this.defaultAddress = defaultAddress;
   }

   public CreateSessionMessage() {
      super(CREATESESSION);
   }

   protected CreateSessionMessage(final byte type) {
      super(type);
   }

   public String getName() {
      return name;
   }

   public long getSessionChannelID() {
      return sessionChannelID;
   }

   public int getVersion() {
      return version;
   }

   public String getUsername() {
      return username;
   }

   public String getPassword() {
      return password;
   }

   public boolean isXA() {
      return xa;
   }

   public boolean isAutoCommitSends() {
      return autoCommitSends;
   }

   public boolean isAutoCommitAcks() {
      return autoCommitAcks;
   }

   public boolean isPreAcknowledge() {
      return preAcknowledge;
   }

   public int getWindowSize() {
      return windowSize;
   }

   public String getDefaultAddress() {
      return defaultAddress;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeString(name);
      buffer.writeLong(sessionChannelID);
      buffer.writeInt(version);
      buffer.writeNullableString(username);
      buffer.writeNullableString(password);
      buffer.writeInt(minLargeMessageSize);
      buffer.writeBoolean(xa);
      buffer.writeBoolean(autoCommitSends);
      buffer.writeBoolean(autoCommitAcks);
      buffer.writeInt(windowSize);
      buffer.writeBoolean(preAcknowledge);
      buffer.writeNullableString(defaultAddress);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      name = buffer.readString();
      sessionChannelID = buffer.readLong();
      version = buffer.readInt();
      username = buffer.readNullableString();
      password = buffer.readNullableString();
      minLargeMessageSize = buffer.readInt();
      xa = buffer.readBoolean();
      autoCommitSends = buffer.readBoolean();
      autoCommitAcks = buffer.readBoolean();
      windowSize = buffer.readInt();
      preAcknowledge = buffer.readBoolean();
      defaultAddress = buffer.readNullableString();
   }

   @Override
   public final boolean isRequiresConfirmations() {
      return false;
   }

   public int getMinLargeMessageSize() {
      return minLargeMessageSize;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), autoCommitAcks, autoCommitSends, defaultAddress, minLargeMessageSize, name,
                          password, preAcknowledge, sessionChannelID, username, version, windowSize, xa);
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", autoCommitAcks=" + autoCommitAcks);
      sb.append(", autoCommitSends=" + autoCommitSends);
      sb.append(", defaultAddress=" + defaultAddress);
      sb.append(", minLargeMessageSize=" + minLargeMessageSize);
      sb.append(", name=" + name);
      sb.append(", password=****");
      sb.append(", preAcknowledge=" + preAcknowledge);
      sb.append(", sessionChannelID=" + sessionChannelID);
      sb.append(", username=" + username);
      sb.append(", version=" + version);
      sb.append(", windowSize=" + windowSize);
      sb.append(", xa=" + xa);
      return sb.toString();
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof CreateSessionMessage other)) {
         return false;
      }

      return autoCommitAcks == other.autoCommitAcks &&
             autoCommitSends == other.autoCommitSends &&
             Objects.equals(defaultAddress, other.defaultAddress) &&
             minLargeMessageSize == other.minLargeMessageSize &&
             Objects.equals(name, other.name) &&
             Objects.equals(password, other.password) &&
             preAcknowledge == other.preAcknowledge &&
             sessionChannelID == other.sessionChannelID &&
             Objects.equals(username, other.username) &&
             version == other.version &&
             windowSize == other.windowSize &&
             xa == other.xa;
   }
}
