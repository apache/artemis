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
      final int prime = 31;
      int result = super.hashCode();
      result = prime * result + (autoCommitAcks ? 1231 : 1237);
      result = prime * result + (autoCommitSends ? 1231 : 1237);
      result = prime * result + ((defaultAddress == null) ? 0 : defaultAddress.hashCode());
      result = prime * result + minLargeMessageSize;
      result = prime * result + ((name == null) ? 0 : name.hashCode());
      result = prime * result + ((password == null) ? 0 : password.hashCode());
      result = prime * result + (preAcknowledge ? 1231 : 1237);
      result = prime * result + (int) (sessionChannelID ^ (sessionChannelID >>> 32));
      result = prime * result + ((username == null) ? 0 : username.hashCode());
      result = prime * result + version;
      result = prime * result + windowSize;
      result = prime * result + (xa ? 1231 : 1237);
      return result;
   }

   @Override
   protected String getPacketString() {
      StringBuffer buff = new StringBuffer(super.getPacketString());
      buff.append(", autoCommitAcks=" + autoCommitAcks);
      buff.append(", autoCommitSends=" + autoCommitSends);
      buff.append(", defaultAddress=" + defaultAddress);
      buff.append(", minLargeMessageSize=" + minLargeMessageSize);
      buff.append(", name=" + name);
      buff.append(", password=****");
      buff.append(", preAcknowledge=" + preAcknowledge);
      buff.append(", sessionChannelID=" + sessionChannelID);
      buff.append(", username=" + username);
      buff.append(", version=" + version);
      buff.append(", windowSize=" + windowSize);
      buff.append(", xa=" + xa);
      return buff.toString();
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj)
         return true;
      if (!super.equals(obj))
         return false;
      if (!(obj instanceof CreateSessionMessage))
         return false;
      CreateSessionMessage other = (CreateSessionMessage) obj;
      if (autoCommitAcks != other.autoCommitAcks)
         return false;
      if (autoCommitSends != other.autoCommitSends)
         return false;
      if (defaultAddress == null) {
         if (other.defaultAddress != null)
            return false;
      } else if (!defaultAddress.equals(other.defaultAddress))
         return false;
      if (minLargeMessageSize != other.minLargeMessageSize)
         return false;
      if (name == null) {
         if (other.name != null)
            return false;
      } else if (!name.equals(other.name))
         return false;
      if (password == null) {
         if (other.password != null)
            return false;
      } else if (!password.equals(other.password))
         return false;
      if (preAcknowledge != other.preAcknowledge)
         return false;
      if (sessionChannelID != other.sessionChannelID)
         return false;
      if (username == null) {
         if (other.username != null)
            return false;
      } else if (!username.equals(other.username))
         return false;
      if (version != other.version)
         return false;
      if (windowSize != other.windowSize)
         return false;
      if (xa != other.xa)
         return false;
      return true;
   }
}
