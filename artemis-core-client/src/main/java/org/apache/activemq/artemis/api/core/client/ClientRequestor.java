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
package org.apache.activemq.artemis.api.core.client;

import java.util.UUID;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;

/**
 * The ClientRequestor class helps making requests.
 * <p>
 * The ClientRequestor constructor is given a ClientSession and a request address. It creates a temporary queue for the
 * responses and provides a request method that sends the request message and waits for its reply.
 */
public final class ClientRequestor implements AutoCloseable {

   private final ClientSession queueSession;

   private final ClientProducer requestProducer;

   private final ClientConsumer replyConsumer;

   private final SimpleString replyQueue;

   /**
    * Constructor for the ClientRequestor.
    * <p>
    * The implementation expects a ClientSession with automatic commits of sends and acknowledgements
    *
    * @param session        a ClientSession uses to handle requests and replies
    * @param requestAddress the address to send request messages to
    */
   public ClientRequestor(final ClientSession session, final SimpleString requestAddress) throws Exception {
      queueSession = session;

      requestProducer = queueSession.createProducer(requestAddress);
      replyQueue = SimpleString.of(requestAddress + "." + UUID.randomUUID());
      queueSession.createQueue(QueueConfiguration.of(replyQueue).setDurable(false).setTemporary(true));
      replyConsumer = queueSession.createConsumer(replyQueue);
   }

   /**
    * @see ClientRequestor#ClientRequestor(ClientSession, SimpleString)
    */
   public ClientRequestor(final ClientSession session, final String requestAddress) throws Exception {
      this(session, SimpleString.of(requestAddress));
   }

   /**
    * Sends a message to the request address and wait indefinitely for a reply. The temporary queue is used for the
    * REPLYTO_HEADER_NAME, and only one reply per request is expected
    *
    * @param request the message to send
    * @return the reply message
    */
   public ClientMessage request(final ClientMessage request) throws Exception {
      return request(request, 0);
   }

   /**
    * Sends a message to the request address and wait for the given timeout for a reply. The temporary queue is used for
    * the REPLYTO_HEADER_NAME, and only one reply per request is expected
    *
    * @param request the message to send
    * @param timeout the timeout to wait for a reply (in milliseconds)
    * @return the reply message or {@code null} if no message is replied before the timeout elapses
    */
   public ClientMessage request(final ClientMessage request, final long timeout) throws Exception {
      request.putStringProperty(ClientMessageImpl.REPLYTO_HEADER_NAME, replyQueue);
      requestProducer.send(request);
      return replyConsumer.receive(timeout);
   }

   /**
    * Closes the ClientRequestor and its session.
    *
    * @throws Exception if an exception occurs while closing the ClientRequestor
    */
   @Override
   public void close() throws Exception {
      replyConsumer.close();
      requestProducer.close();
      queueSession.deleteQueue(replyQueue);
   }

}
