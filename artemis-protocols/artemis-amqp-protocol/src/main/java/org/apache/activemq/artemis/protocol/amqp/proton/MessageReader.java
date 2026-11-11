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

package org.apache.activemq.artemis.protocol.amqp.proton;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.engine.Delivery;

/**
 * Message reader for incoming messages from and AMQP receiver context which will handle the read and decode of message
 * payload into am AMQP message.
 */
public interface MessageReader {

   /**
    * Closes the reader and releases any in use resources. If the reader was not finished processing an incoming message
    * when closed the reader should release any resources that might be held such as large message files etc.
    */
   void close();

   /**
    * Reset any internal state of this reader and prepares it to begin processing a new delivery. A previously closed
    * reader can be reset for reuse.
    *
    * @return this {@link MessageReader} instance
    */
   MessageReader open();

   /**
    * Reads the bytes from an incoming delivery which might not be complete yet but allows the reader to consume pending
    * bytes to prevent stalling the sender because the session window was exhausted. Once a delivery has been fully read
    * and is no longer partial the readBytes method will return the decoded message for dispatch.
    * <p>
    * Notice that asynchronous Readers will never return the Message but will rather call a complete operation on the
    * Server Receiver.
    *
    * @param delivery The delivery that has pending incoming bytes.
    */
   Message readBytes(Delivery delivery) throws Exception;

   /**
    * Once a message has been read but before the reader is closed this API offers access to any delivery annotations
    * that were present upon decode of the read message.
    *
    * @return any DeliveryAnnotations that were read as part of decoding the message
    */
   DeliveryAnnotations getDeliveryAnnotations();

}
