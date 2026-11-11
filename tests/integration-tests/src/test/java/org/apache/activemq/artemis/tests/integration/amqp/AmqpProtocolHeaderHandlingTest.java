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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.tests.util.Wait;
import org.fusesource.hawtbuf.Buffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class AmqpProtocolHeaderHandlingTest extends AmqpClientTestSupport {

   @Override
   protected boolean isSecurityEnabled() {
      return true;
   }

   @Test
   @Timeout(60)
   public void testNonSaslHeaderRejectedOnConnect() throws Exception {
      final AmqpHeader header = new AmqpHeader();

      header.setProtocolId(0);
      header.setMajor(1);
      header.setMinor(0);
      header.setRevision(0);

      final ClientConnection connection = new ClientConnection();
      connection.open("localhost", AMQP_PORT);
      connection.send(header);

      AmqpHeader response = connection.readAmqpHeader();
      assertNotNull(response);
      assertEquals(3, response.getProtocolId());

      // pump some bytes down the wire until broker closes the connection
      assertTrue(Wait.waitFor(() -> {
         try {
            connection.send(header);
            return false;
         } catch (Exception e) {
            return true;
         }
      }, TimeUnit.SECONDS.toMillis(15), TimeUnit.MILLISECONDS.toMillis(250)), "Broker should have closed client connection");
   }

   private class ClientConnection {

      protected static final long RECEIVE_TIMEOUT = 10000;
      protected Socket clientSocket;

      public void open(String host, int port) throws IOException {
         clientSocket = new Socket(host, port);
         clientSocket.setTcpNoDelay(true);
      }

      public void send(AmqpHeader header) throws Exception {
         OutputStream outputStream = clientSocket.getOutputStream();
         header.getBuffer().writeTo(outputStream);
         outputStream.flush();
      }

      public AmqpHeader readAmqpHeader() throws Exception {
         clientSocket.setSoTimeout((int) RECEIVE_TIMEOUT);
         InputStream is = clientSocket.getInputStream();

         byte[] header = new byte[8];
         int read = is.read(header);
         if (read == header.length) {
            return new AmqpHeader(new Buffer(header));
         } else {
            return null;
         }
      }
   }

   @SuppressWarnings("unused")
   private class AmqpHeader {

      final Buffer PREFIX = new Buffer(new byte[]{'A', 'M', 'Q', 'P'});

      private Buffer buffer;

      AmqpHeader() {
         this(new Buffer(new byte[]{'A', 'M', 'Q', 'P', 0, 1, 0, 0}));
      }

      AmqpHeader(Buffer buffer) {
         this(buffer, true);
      }

      AmqpHeader(Buffer buffer, boolean validate) {
         setBuffer(buffer, validate);
      }

      public int getProtocolId() {
         return buffer.get(4) & 0xFF;
      }

      public void setProtocolId(int value) {
         buffer.data[buffer.offset + 4] = (byte) value;
      }

      public int getMajor() {
         return buffer.get(5) & 0xFF;
      }

      public void setMajor(int value) {
         buffer.data[buffer.offset + 5] = (byte) value;
      }

      public int getMinor() {
         return buffer.get(6) & 0xFF;
      }

      public void setMinor(int value) {
         buffer.data[buffer.offset + 6] = (byte) value;
      }

      public int getRevision() {
         return buffer.get(7) & 0xFF;
      }

      public void setRevision(int value) {
         buffer.data[buffer.offset + 7] = (byte) value;
      }

      public Buffer getBuffer() {
         return buffer;
      }

      public void setBuffer(Buffer value) {
         setBuffer(value, true);
      }

      public void setBuffer(Buffer value, boolean validate) {
         if (validate && !value.startsWith(PREFIX) || value.length() != 8) {
            throw new IllegalArgumentException("Not an AMQP header buffer");
         }
         buffer = value.buffer();
      }

      public boolean hasValidPrefix() {
         return buffer.startsWith(PREFIX);
      }

      @Override
      public String toString() {
         StringBuilder sb = new StringBuilder();
         for (int i = 0; i < buffer.length(); ++i) {
            char value = (char) buffer.get(i);
            if (Character.isLetter(value)) {
               sb.append(value);
            } else {
               sb.append(",");
               sb.append((int) value);
            }
         }
         return sb.toString();
      }
   }
}
