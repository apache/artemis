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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.utils.AbstractMapPersister;

/**
 * Core connect handshake sent after the transport is established.
 * <p>
 * Replaces the former {@code CheckFailoverMessage} on the same wire packet ID
 * ({@link PacketImpl#CONNECT}, {@code -4}). The node id field is unchanged;
 * optional client version and auth fields may follow as extension fields for
 * backward-compatible decoding.
 * <p>
 * Supported auth mechanisms:
 * <ul>
 *   <li>{@link #MECHANISM_PLAIN} – SASL PLAIN as defined in
 *       <a href="https://www.rfc-editor.org/rfc/rfc4616">RFC 4616</a>;
 *       {@code FIELD_AUTH_DATA} encodes
 *       {@code authzid NUL authcid NUL passwd} as UTF-8 bytes,
 *       where {@code authzid} (authorization identity) is always empty.</li>
 * </ul>
 */
public class ConnectMessage extends PacketImpl {

   /**
    * SASL PLAIN mechanism name as defined in
    * <a href="https://www.rfc-editor.org/rfc/rfc4616">RFC 4616</a>.
    */
   public static final String MECHANISM_PLAIN = "PLAIN";

   static final short FIELD_CLIENT_VERSION = 1;
   /**
    * Auth mechanism name, e.g. {@code "PLAIN"}.
    */
   static final short FIELD_AUTH_MECHANISM = 2;
   /**
    * Auth data bytes. For PLAIN (RFC 4616):
    * {@code authzid NUL authcid NUL passwd} encoded as UTF-8,
    * where {@code authzid} is always empty.
    */
   static final short FIELD_AUTH_DATA = 3;

   // ConnectMapSerializer is stateless and safe to be used from multiple threads.
   // The state needed is on ConnectMessage and ActiveMQBuffer only
   private static final ConnectMapSerializer mapSerializer = new ConnectMapSerializer();

   private static class ConnectMapSerializer extends AbstractMapPersister<ConnectMessage> {
      public void encode(ActiveMQBuffer buffer, ConnectMessage message) {
         int headerPosition = buffer.writerIndex();
         writeHeader(buffer, 0, 0); // to reserve the header, will be rewritten at the end

         int fields = 0;

         if (message.clientVersion > 0) {
            writeInteger(buffer, FIELD_CLIENT_VERSION, message.clientVersion);
            fields++;
         }

         if (message.authMechanism != null) {
            writeString(buffer, FIELD_AUTH_MECHANISM, message.authMechanism);
            fields++;
         }

         if (message.authData != null) {
            byte[] data = message.authData;
            writeByteArray(buffer, FIELD_AUTH_DATA, data.length, buf -> buf.writeBytes(data));
            fields++;
         }

         int endPosition = buffer.writerIndex();
         buffer.writerIndex(headerPosition);
         writeHeader(buffer, endPosition - headerPosition, fields);
         buffer.writerIndex(endPosition);
      }

      @Override
      protected int getMaxAllowedElements() {
         return 10;
      }

      @Override
      protected void onMapReadInteger(short key, int value, ConnectMessage decodingObject) {
         switch (key) {
            case FIELD_CLIENT_VERSION -> {
               decodingObject.clientVersion = value;
            }
         }
      }

      @Override
      protected void onMapReadByte(short key, byte value, ConnectMessage decodingObject) {
      }

      @Override
      protected void onMapReadBoolean(short key, boolean value, ConnectMessage decodingObject) {
      }

      @Override
      protected void onMapReadLong(short key, long value, ConnectMessage decodingObject) {
      }

      @Override
      protected void onMapReadString(short key, String value, ConnectMessage decodingObject) {
         switch (key) {
            case FIELD_AUTH_MECHANISM -> {
               decodingObject.authMechanism = value;
            }
         }
      }

      @Override
      protected void onMapReadByteArray(short key, ActiveMQBuffer slice, ConnectMessage decodingObject) {
         switch (key) {
            case FIELD_AUTH_DATA -> {
               int len = slice.readableBytes();
               byte[] bytes = new byte[len];
               slice.readBytes(bytes);
               decodingObject.authData = bytes;
            }
         }
      }
   }

   private String nodeID;

   private int clientVersion;

   /**
    * Auth mechanism name; {@code null} means legacy client — skip connection auth.
    */
   private String authMechanism;

   /**
    * Raw auth data; {@code null} for legacy clients.
    */
   private byte[] authData;

   /**
    * Creates a ConnectMessage with PLAIN credentials.
    * <p>
    * The auth data is encoded per
    * <a href="https://www.rfc-editor.org/rfc/rfc4616">RFC 4616</a>:
    * {@code authzid NUL authcid NUL passwd} as UTF-8 bytes.
    * The authorization identity ({@code authzid}) is always empty;
    * {@code authcid} and {@code passwd} are set from the supplied arguments.
    */
   public static ConnectMessage withPlainCredentials(String nodeID, int clientVersion, String username, String password) {
      ConnectMessage msg = new ConnectMessage(nodeID, clientVersion);
      msg.authMechanism = MECHANISM_PLAIN;
      String authcid = username != null ? username : "";
      String passwd = password != null ? password : "";
      // RFC 4616: authzid NUL authcid NUL passwd
      msg.authData = ("\0" + authcid + "\0" + passwd).getBytes(StandardCharsets.UTF_8);
      return msg;
   }

   public ConnectMessage() {
      super(CONNECT);
   }

   public ConnectMessage(String nodeID, int clientVersion) {
      super(CONNECT);
      this.nodeID = nodeID;
      this.clientVersion = clientVersion;
   }

   @Override
   public void encodeRest(ActiveMQBuffer buffer) {
      buffer.writeNullableString(nodeID);

      mapSerializer.encode(buffer, this);
   }

   @Override
   public void decodeRest(ActiveMQBuffer buffer) {
      nodeID = buffer.readNullableString();

      if (buffer.readableBytes() > 0) {
         mapSerializer.decode(buffer, this);
      }
   }

   @Override
   protected String getPacketString() {
      StringBuilder sb = new StringBuilder(super.getPacketString());
      sb.append(", nodeID=").append(nodeID);
      sb.append(", clientVersion=").append(clientVersion);
      if (authMechanism != null) {
         sb.append(", authMechanism=").append(authMechanism);
      }
      return sb.toString();
   }

   public String getNodeID() {
      return nodeID;
   }

   public int getClientVersion() {
      return clientVersion;
   }

   /**
    * Returns the auth mechanism name (e.g. {@code "PLAIN"}),
    * or {@code null} if no mechanism was sent (legacy client — skip connection auth).
    */
   public String getAuthMechanism() {
      return authMechanism;
   }

   /**
    * Sets the auth mechanism.
    */
   public void setAuthMechanism(String authMechanism) {
      this.authMechanism = authMechanism;
   }

   /**
    * Returns the raw auth data bytes, or {@code null} if not present
    * (e.g. legacy client).
    */
   public byte[] getAuthData() {
      return authData;
   }

   /**
    * Sets the raw auth data bytes.
    */
   void setAuthData(byte[] authData) {
      this.authData = authData;
   }

   /**
    * Decodes PLAIN auth data per
    * <a href="https://www.rfc-editor.org/rfc/rfc4616">RFC 4616</a>
    * ({@code authzid NUL authcid NUL passwd}) and returns a two-element array
    * {@code [authcid, passwd]} (i.e. {@code [username, password]}).
    * The {@code authzid} field is parsed but not returned, as it is always empty.
    */
   public String[] decodePlainAuthData() {
      byte[] data = authData;

      if (data == null || data.length == 0) {
         return new String[]{"", ""};
      }

      // RFC 4616: authzid NUL authcid NUL passwd
      // Split on NUL to obtain the three fields. Limit 3 keeps a NUL inside
      // passwd intact should one ever appear.
      String payload = new String(data, StandardCharsets.UTF_8);
      String[] fields = payload.split("\0", 3);

      // fields[0] = authzid (always empty), fields[1] = authcid, fields[2] = passwd
      String authcid = fields.length > 1 ? fields[1] : "";
      String passwd = fields.length > 2 ? fields[2] : "";
      return new String[]{authcid, passwd};
   }

   @Override
   public int hashCode() {
      return super.hashCode() + Objects.hash(nodeID, clientVersion, authMechanism) + Arrays.hashCode(authData);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof ConnectMessage other)) {
         return false;
      }

      return Objects.equals(nodeID, other.nodeID)
         && Objects.equals(clientVersion, other.clientVersion)
         && Objects.equals(authMechanism, other.authMechanism)
         && Arrays.equals(authData, other.authData);
   }
}
