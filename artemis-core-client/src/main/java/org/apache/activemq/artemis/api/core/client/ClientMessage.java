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

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.SimpleString;

/**
 * A ClientMessage represents a message sent and/or received by Apache Artemis.
 */
public interface ClientMessage extends ICoreMessage {

   /**
    * {@return the number of times this message was delivered.}
    */
   int getDeliveryCount();

   /**
    * Sets the delivery count for this message.
    * <p>
    * This method is not meant to be called by Apache Artemis clients.
    *
    * @param deliveryCount message delivery count
    * @return this ClientMessage
    */
   ClientMessage setDeliveryCount(int deliveryCount);

   /**
    * Acknowledges reception of this message.
    * <p>
    * If the session responsible to acknowledge this message has {@code autoCommitAcks} set to {@code true}, the
    * transaction will automatically commit the current transaction. Otherwise, this acknowledgement will not be
    * committed until the client commits the session transaction.
    *
    * @throws ActiveMQException if an error occurred while acknowledging the message.
    * @see ClientSession#isAutoCommitAcks()
    */
   ClientMessage acknowledge() throws ActiveMQException;

   /**
    * Acknowledges reception of a single message.
    * <p>
    * If the session responsible to acknowledge this message has {@code autoCommitAcks} set to {@code true}, the
    * transaction will automatically commit the current transaction. Otherwise, this acknowledgement will not be
    * committed until the client commits the session transaction.
    *
    * @throws ActiveMQException if an error occurred while acknowledging the message.
    * @see ClientSession#isAutoCommitAcks()
    */
   ClientMessage individualAcknowledge() throws ActiveMQException;

   /**
    * This can be optionally used to verify if the entire message has been received. It won't have any effect on regular
    * messages but it may be helpful on large messages. The use case for this is to make sure there won't be an
    * exception while getting the buffer. Using getBodyBuffer directly would have the same effect but you could get a
    * Runtime non checked Exception instead
    */
   void checkCompletion() throws ActiveMQException;

   /**
    * {@return the size (in bytes) of this message's body}
    */
   int getBodySize();

   /**
    * Sets the OutputStream that will receive the content of a message received in a non blocking way.
    * <p>
    * This method is used when consuming large messages
    *
    * @return this ClientMessage
    */
   ClientMessage setOutputStream(OutputStream out) throws ActiveMQException;

   /**
    * Saves the content of the message to the OutputStream. It will block until the entire content is transferred to the
    * OutputStream.
    */
   void saveToOutputStream(OutputStream out) throws ActiveMQException;

   /**
    * Wait the outputStream completion of the message.
    * <p>
    * This method is used when consuming large messages
    *
    * @param timeMilliseconds - 0 means wait forever
    * @return true if it reached the end
    */
   boolean waitOutputStreamCompletion(long timeMilliseconds) throws ActiveMQException;

   /**
    * Sets the body's IntputStream.
    * <p>
    * This method is used when sending large messages
    *
    * @return this ClientMessage
    */
   ClientMessage setBodyInputStream(InputStream bodyInputStream);

   /**
    * Return the bodyInputStream for large messages
    */
   @Override
   InputStream getBodyInputStream();

   /**
    * The buffer to write the body. Warning: If you just want to read the content of a message, use getDataBuffer() or
    * getReadOnlyBuffer();
    */
   @Override
   ActiveMQBuffer getBodyBuffer();

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putBooleanProperty(SimpleString key, boolean value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putBooleanProperty(String key, boolean value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putByteProperty(SimpleString key, byte value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putByteProperty(String key, byte value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putBytesProperty(SimpleString key, byte[] value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putBytesProperty(String key, byte[] value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putShortProperty(SimpleString key, short value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putShortProperty(String key, short value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putCharProperty(SimpleString key, char value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putCharProperty(String key, char value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putIntProperty(SimpleString key, int value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putIntProperty(String key, int value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putLongProperty(SimpleString key, long value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putLongProperty(String key, long value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putFloatProperty(SimpleString key, float value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putFloatProperty(String key, float value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putDoubleProperty(SimpleString key, double value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putDoubleProperty(String key, double value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   @Override
   ClientMessage putStringProperty(String key, String value);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   ClientMessage writeBodyBufferBytes(byte[] bytes);

   /**
    * Overridden from {@link org.apache.activemq.artemis.api.core.Message} to enable fluent API
    */
   ClientMessage writeBodyBufferString(String string);

}
