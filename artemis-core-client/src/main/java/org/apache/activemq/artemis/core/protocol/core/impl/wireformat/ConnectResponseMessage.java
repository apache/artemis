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
import org.apache.activemq.artemis.utils.AbstractMapPersister;

/**
 * Response to a {@link ConnectMessage}.
 * <p>
 * Replaces the former {@code CheckFailoverReplyMessage} on the same wire packet ID
 * ({@link PacketImpl#CONNECT_RESPONSE}, {@code -5}). The {@code okToFailover}
 * field is unchanged; an optional server version may follow as an extension
 * field for backward-compatible decoding.
 */
public class ConnectResponseMessage extends PacketImpl {

   static final short FIELD_SERVER_VERSION = 1;

   private static final ConnectResponseMapSerializer mapSerializer = new ConnectResponseMapSerializer();

   private static class ConnectResponseMapSerializer extends AbstractMapPersister<ConnectResponseMessage> {
      public void encode(ActiveMQBuffer buffer, ConnectResponseMessage message) {
         int recordSize = headerSize() + (message.serverVersion > 0 ? payloadSizeInteger() : 0);
         int fields = message.serverVersion > 0 ? 1 : 0;
         // since we don't use strings, we can calculate the size ahead, no need to rewrite the header like how it's done in ConnectMessage
         writeHeader(buffer, recordSize, fields);

         if (message.serverVersion > 0) {
            writeInteger(buffer, FIELD_SERVER_VERSION, message.serverVersion);
         }

      }

      @Override
      protected int getMaxAllowedElements() {
         return 10;
      }

      @Override
      protected void onMapReadInteger(short key, int value, ConnectResponseMessage decodingObject) {
         switch (key) {
            case FIELD_SERVER_VERSION -> {
               decodingObject.serverVersion = value;
            }
         }
      }

      @Override
      protected void onMapReadByte(short key, byte value, ConnectResponseMessage decodingObject) {
      }

      @Override
      protected void onMapReadBoolean(short key, boolean value, ConnectResponseMessage decodingObject) {
      }

      @Override
      protected void onMapReadLong(short key, long value, ConnectResponseMessage decodingObject) {
      }

      @Override
      protected void onMapReadString(short key, String value, ConnectResponseMessage decodingObject) {
      }

      @Override
      protected void onMapReadByteArray(short key, ActiveMQBuffer slice, ConnectResponseMessage decodingObject) {
      }
   }

   private boolean okToFailover;

   private int serverVersion;

   public ConnectResponseMessage(boolean okToFailover, int serverVersion) {
      super(CONNECT_RESPONSE);
      this.okToFailover = okToFailover;
      this.serverVersion = serverVersion;
   }

   public ConnectResponseMessage() {
      super(CONNECT_RESPONSE);
   }

   @Override
   public boolean isResponse() {
      return true;
   }

   @Override
   public void encodeRest(ActiveMQBuffer buffer) {
      buffer.writeBoolean(okToFailover);

      mapSerializer.encode(buffer, this);
   }

   @Override
   public void decodeRest(ActiveMQBuffer buffer) {
      okToFailover = buffer.readBoolean();

      if (buffer.readableBytes() > 0) {
         mapSerializer.decode(buffer, this);
      }
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", okToFailover=" + okToFailover);
      sb.append(", serverVersion=" + serverVersion);
      return sb.toString();
   }

   public boolean isOkToFailover() {
      return okToFailover;
   }

   public int getServerVersion() {
      return serverVersion;
   }

}
