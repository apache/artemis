/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.transport.amqp.client;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.activemq.transport.amqp.client.util.UnmodifiableProxy;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.DescribedType;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.message.Message;

public class AmqpMessage {

   private final AmqpReceiver receiver;
   private final Message message;
   private final Delivery delivery;

   private Map<Symbol, Object> deliveryAnnotationsMap;
   private Map<Symbol, Object> messageAnnotationsMap;
   private Map<String, Object> applicationPropertiesMap;

   /**
    * Creates a new AmqpMessage that wraps the information necessary to handle an outgoing message.
    */
   public AmqpMessage() {
      receiver = null;
      delivery = null;

      message = Proton.message();
   }

   /**
    * Creates a new AmqpMessage that wraps the information necessary to handle an outgoing message.
    *
    * @param message the Proton message that is to be sent.
    */
   public AmqpMessage(Message message) {
      this(null, message, null);
   }

   /**
    * Creates a new AmqpMessage that wraps the information necessary to handle an incoming delivery.
    *
    * @param receiver the AmqpReceiver that received this message.
    * @param message  the Proton message that was received.
    * @param delivery the Delivery instance that produced this message.
    */
   @SuppressWarnings("unchecked")
   public AmqpMessage(AmqpReceiver receiver, Message message, Delivery delivery) {
      this.receiver = receiver;
      this.message = message;
      this.delivery = delivery;

      if (message.getMessageAnnotations() != null) {
         messageAnnotationsMap = message.getMessageAnnotations().getValue();
      }

      if (message.getApplicationProperties() != null) {
         applicationPropertiesMap = message.getApplicationProperties().getValue();
      }

      if (message.getDeliveryAnnotations() != null) {
         deliveryAnnotationsMap = message.getDeliveryAnnotations().getValue();
      }
   }

   //----- Access to interal client resources -------------------------------//

   /**
    * {@return the AMQP Delivery object linked to a received message}
    */
   public Delivery getWrappedDelivery() {
      if (delivery != null) {
         return UnmodifiableProxy.deliveryProxy(delivery);
      }

      return null;
   }

   /**
    * {@return the AMQP Message that is wrapped by this object}
    */
   public Message getWrappedMessage() {
      return message;
   }

   /**
    * {@return the AmqpReceiver that consumed this message}
    */
   public AmqpReceiver getAmqpReceiver() {
      return receiver;
   }

   //----- Message disposition control --------------------------------------//

   /**
    * Accepts the message marking it as consumed on the remote peer.
    *
    * @throws Exception if an error occurs during the accept.
    */
   public void accept() throws Exception {
      accept(true);
   }

   /**
    * Accepts the message marking it as consumed on the remote peer.
    *
    * @param settle {@code true} if the client should also settle the delivery when sending the accept.
    * @throws Exception if an error occurs during the accept.
    */
   public void accept(boolean settle) throws Exception {
      if (receiver == null) {
         throw new IllegalStateException("Can't accept non-received message.");
      }

      receiver.accept(delivery, settle);
   }

   /**
    * Accepts the message marking it as consumed on the remote peer.
    *
    * @param txnSession The session that is used to manage acceptance of the message.
    * @throws Exception if an error occurs during the accept.
    */
   public void accept(AmqpSession txnSession) throws Exception {
      accept(txnSession, true);
   }

   /**
    * Accepts the message marking it as consumed on the remote peer.
    *
    * @param txnSession The session that is used to manage acceptance of the message.
    * @throws Exception if an error occurs during the accept.
    */
   public void accept(AmqpSession txnSession, boolean settle) throws Exception {
      if (receiver == null) {
         throw new IllegalStateException("Can't accept non-received message.");
      }

      receiver.accept(delivery, txnSession, settle);
   }

   /**
    * Marks the message as Modified, indicating whether it failed to deliver and is not deliverable here.
    *
    * @param deliveryFailed    indicates that the delivery failed for some reason.
    * @param undeliverableHere marks the delivery as not being able to be process by link it was sent to.
    * @throws Exception if an error occurs during the process.
    */
   public void modified(Boolean deliveryFailed, Boolean undeliverableHere) throws Exception {
      if (receiver == null) {
         throw new IllegalStateException("Can't modify non-received message.");
      }

      receiver.modified(delivery, deliveryFailed, undeliverableHere);
   }

   /**
    * Release the message, remote can redeliver it elsewhere.
    *
    * @throws Exception if an error occurs during the release.
    */
   public void release() throws Exception {
      if (receiver == null) {
         throw new IllegalStateException("Can't release non-received message.");
      }

      receiver.release(delivery);
   }

   /**
    * Reject the message, remote can redeliver it elsewhere.
    *
    * @throws Exception if an error occurs during the reject.
    */
   public void reject() throws Exception {
      if (receiver == null) {
         throw new IllegalStateException("Can't release non-received message.");
      }

      receiver.reject(delivery);
   }

   //----- Convenience methods for constructing outbound messages -----------//

   /**
    * Sets the address which is applied to the AMQP message To field in the message properties
    *
    * @param address The address that should be applied in the Message To field.
    */
   public void setAddress(String address) {
      checkReadOnly();
      lazyCreateProperties();
      getWrappedMessage().setAddress(address);
   }

   /**
    * {@return the address that was set in the Message To field or {@code null} if not set}
    */
   public String getAddress() {
      if (message.getProperties() == null) {
         return null;
      }

      return message.getProperties().getTo();
   }

   /**
    * Sets the replyTo address which is applied to the AMQP message reply-to field in the message properties
    *
    * @param address The replyTo address that should be applied in the Message To field.
    */
   public void setReplyToAddress(String address) {
      checkReadOnly();
      lazyCreateProperties();
      getWrappedMessage().setReplyTo(address);
   }

   /**
    * {@return the replyTo address that was set in the Message To field or {@code null} if not set}
    */
   public String getReplyToAddress() {
      if (message.getProperties() == null) {
         return null;
      }

      return message.getProperties().getReplyTo();
   }

   /**
    * Sets the MessageId property on an outbound message using the provided String
    *
    * @param messageId the String message ID value to set.
    */
   public void setMessageId(String messageId) {
      checkReadOnly();
      lazyCreateProperties();
      getWrappedMessage().setMessageId(messageId);
   }

   /**
    * {@return the the set MessageId value in String form or {@code null} if not set}
    */
   public String getMessageId() {
      if (message.getProperties() == null || message.getProperties().getMessageId() == null) {
         return null;
      }

      return message.getProperties().getMessageId().toString();
   }

   /**
    * {@return the set  MessageId value in the original form or {@code null} if not set}
    */
   public Object getRawMessageId() {
      if (message.getProperties() == null) {
         return null;
      }

      return message.getProperties().getMessageId();
   }

   /**
    * Sets the MessageId property on an outbound message using the provided value
    *
    * @param messageId the message ID value to set.
    */
   public void setRawMessageId(Object messageId) {
      checkReadOnly();
      lazyCreateProperties();
      getWrappedMessage().setMessageId(messageId);
   }

   /**
    * Sets the CorrelationId property on an outbound message using the provided String
    *
    * @param correlationId the String Correlation ID value to set.
    */
   public void setCorrelationId(String correlationId) {
      checkReadOnly();
      lazyCreateProperties();
      getWrappedMessage().setCorrelationId(correlationId);
   }

   /**
    * {@return the set correlation ID in String form or {@code null} if not set}
    */
   public String getCorrelationId() {
      if (message.getProperties() == null || message.getProperties().getCorrelationId() == null) {
         return null;
      }

      return message.getProperties().getCorrelationId().toString();
   }

   /**
    * {@return the set CorrelationId value in the original form or {@code null} if not set}
    */
   public Object getRawCorrelationId() {
      if (message.getProperties() == null) {
         return null;
      }

      return message.getProperties().getCorrelationId();
   }

   /**
    * Sets the CorrelationId property on an outbound message using the provided value
    *
    * @param correlationId the correlation ID value to set.
    */
   public void setRawCorrelationId(Object correlationId) {
      checkReadOnly();
      lazyCreateProperties();
      getWrappedMessage().setCorrelationId(correlationId);
   }

   /**
    * Sets the GroupId property on an outbound message using the provided String
    *
    * @param groupId the String Group ID value to set.
    */
   public void setGroupId(String groupId) {
      checkReadOnly();
      lazyCreateProperties();
      getWrappedMessage().setGroupId(groupId);
   }

   /**
    * {@return the set GroupID in String form or {@code null} if not set}
    */
   public String getGroupId() {
      if (message.getProperties() == null) {
         return null;
      }

      return message.getProperties().getGroupId();
   }

   /**
    * Sets the Subject property on an outbound message using the provided String
    *
    * @param subject the String Subject value to set.
    */
   public void setSubject(String subject) {
      checkReadOnly();
      lazyCreateProperties();
      getWrappedMessage().setSubject(subject);
   }

   /**
    * {@return the set Subject in String form or {@code null} if not set}
    */
   public String getSubject() {
      if (message.getProperties() == null) {
         return null;
      }

      return message.getProperties().getSubject();
   }

   /**
    * Sets the durable header on the outgoing message.
    *
    * @param durable the boolean durable value to set.
    */
   public void setDurable(boolean durable) {
      checkReadOnly();
      lazyCreateHeader();
      getWrappedMessage().setDurable(durable);
   }

   /**
    * {@return {@code true} if the message is marked as being durable; otherwise {@code false}}
    */
   public boolean isDurable() {
      if (message.getHeader() == null || message.getHeader().getDurable() == null) {
         return false;
      }

      return message.getHeader().getDurable();
   }


   public int getDeliveryCount() {
      if (message.getHeader() == null || message.getHeader().getDeliveryCount() == null) {
         return 0;
      }

      return message.getHeader().getDeliveryCount().intValue();
   }

   /**
    * Sets the priority header on the outgoing message.
    *
    * @param priority the priority value to set.
    */
   public void setPriority(short priority) {
      checkReadOnly();
      lazyCreateHeader();
      getWrappedMessage().setPriority(priority);
   }

   /**
    * {@return the priority header on the message}
    */
   public short getPriority() {
      return getWrappedMessage().getPriority();
   }

   /**
    * Sets the ttl header on the outgoing message.
    *
    * @param timeToLive the ttl value to set.
    */
   public void setTimeToLive(long timeToLive) {
      checkReadOnly();
      lazyCreateHeader();
      getWrappedMessage().setTtl(timeToLive);
   }

   /**
    * {@return the ttl header on the outgoing message}
    */
   public long getTimeToLive() {
      return getWrappedMessage().getTtl();
   }

   /**
    * Sets the absolute expiration time property on the message.
    *
    * @param absoluteExpiryTime the expiration time value to set.
    */
   public void setAbsoluteExpiryTime(long absoluteExpiryTime) {
      checkReadOnly();
      lazyCreateProperties();
      getWrappedMessage().setExpiryTime(absoluteExpiryTime);
   }

   /**
    * Gets the absolute expiration time property on the message.
    */
   public long getAbsoluteExpiryTime() {
      return getWrappedMessage().getExpiryTime();
   }

   /**
    * Sets the creation time property on the message.
    *
    * @param creationTime the time value to set.
    */
   public void setCreationTime(long creationTime) {
      checkReadOnly();
      lazyCreateProperties();
      getWrappedMessage().setCreationTime(creationTime);
   }

   /**
    * Gets the absolute expiration time property on the message.
    */
   public long getCreationTime() {
      return getWrappedMessage().getCreationTime();
   }

   /**
    * Sets a given application property on an outbound message.
    *
    * @param key   the name to assign the new property.
    * @param value the value to set for the named property.
    */
   public void setApplicationProperty(String key, Object value) {
      checkReadOnly();
      lazyCreateApplicationProperties();
      applicationPropertiesMap.put(key, value);
   }

   /**
    * Gets the application property that is mapped to the given name or null if no property has been set with that
    * name.
    *
    * @param key the name used to lookup the property in the application properties.
    * @return the property value or null if not set
    */
   public Object getApplicationProperty(String key) {
      if (applicationPropertiesMap == null) {
         return null;
      }

      return applicationPropertiesMap.get(key);
   }

   /**
    * Perform a proper annotation set on the AMQP Message based on a Symbol key and the target value to append to the
    * current annotations.
    *
    * @param key   The name of the Symbol whose value is being set.
    * @param value The new value to set in the annotations of this message.
    */
   public void setMessageAnnotation(String key, Object value) {
      checkReadOnly();
      lazyCreateMessageAnnotations();
      messageAnnotationsMap.put(Symbol.valueOf(key), value);
   }

   /**
    * Given a message annotation name, lookup and return the value associated with that annotation name.  If the message
    * annotations have not been created yet then this method will always return null.
    *
    * @param key the Symbol name that should be looked up in the message annotations.
    * @return the value of the annotation if it exists, or null if not set or not accessible
    */
   public Object getMessageAnnotation(String key) {
      if (messageAnnotationsMap == null) {
         return null;
      }

      return messageAnnotationsMap.get(Symbol.valueOf(key));
   }

   /**
    * Perform a proper delivery annotation set on the AMQP Message based on a Symbol key and the target value to append
    * to the current delivery annotations.
    *
    * @param key   The name of the Symbol whose value is being set.
    * @param value The new value to set in the delivery annotations of this message.
    */
   public void setDeliveryAnnotation(String key, Object value) {
      checkReadOnly();
      lazyCreateDeliveryAnnotations();
      deliveryAnnotationsMap.put(Symbol.valueOf(key), value);
   }

   /**
    * Given a message annotation name, lookup and return the value associated with that annotation name.  If the message
    * annotations have not been created yet then this method will always return null.
    *
    * @param key the Symbol name that should be looked up in the message annotations.
    * @return the value of the annotation if it exists, or null if not set or not accessible
    */
   public Object getDeliveryAnnotation(String key) {
      if (deliveryAnnotationsMap == null) {
         return null;
      }

      return deliveryAnnotationsMap.get(Symbol.valueOf(key));
   }

   //----- Methods for manipulating the Message body ------------------------//

   /**
    * Sets a String value into the body of an outgoing Message, throws an exception if this is an incoming message
    * instance.
    *
    * @param value the String value to store in the Message body.
    * @throws IllegalStateException if the message is read only.
    */
   public void setText(String value) throws IllegalStateException {
      checkReadOnly();
      AmqpValue body = new AmqpValue(value);
      getWrappedMessage().setBody(body);
   }

   /**
    * {@return the message body as a String from an AmqpValue body}
    *
    * @throws NoSuchElementException if the body does not contain a AmqpValue with String.
    */
   public String getText() throws NoSuchElementException {
      Section body = getWrappedMessage().getBody();
      if (body instanceof AmqpValue amqpValue) {

         if (amqpValue.getValue() instanceof String) {
            return (String) amqpValue.getValue();
         }
      }

      throw new NoSuchElementException("Message does not contain a String body");
   }

   /**
    * Sets a byte array value into the body of an outgoing Message, throws an exception if this is an incoming message
    * instance.
    *
    * @param bytes the byte array value to store in the Message body.
    * @throws IllegalStateException if the message is read only.
    */
   public void setBytes(byte[] bytes) throws IllegalStateException {
      checkReadOnly();
      Data body = new Data(new Binary(bytes));
      getWrappedMessage().setBody(body);
   }

   /**
    * Sets a described type into the body of an outgoing Message, throws an exception if this is an incoming message
    * instance.
    *
    * @param described the described type value to store in the Message body.
    * @throws IllegalStateException if the message is read only.
    */
   public void setDescribedType(DescribedType described) throws IllegalStateException {
      checkReadOnly();
      AmqpValue body = new AmqpValue(described);
      getWrappedMessage().setBody(body);
   }

   /**
    * {@return the message body as an DescribedType instance if possible; {@code null} otherwise}
    *
    * @throws NoSuchElementException if the body does not contain a DescribedType.
    */
   public DescribedType getDescribedType() throws NoSuchElementException {
      DescribedType result = null;

      if (getWrappedMessage().getBody() == null) {
         return null;
      } else {
         if (getWrappedMessage().getBody() instanceof AmqpValue value) {

            if (value.getValue() == null) {
               result = null;
            } else if (value.getValue() instanceof DescribedType) {
               result = (DescribedType) value.getValue();
            } else {
               throw new NoSuchElementException("Message does not contain a DescribedType body");
            }
         }
      }

      return result;
   }

   //----- Internal implementation ------------------------------------------//

   private void checkReadOnly() throws IllegalStateException {
      if (delivery != null) {
         throw new IllegalStateException("Message is read only.");
      }
   }

   private void lazyCreateMessageAnnotations() {
      if (messageAnnotationsMap == null) {
         messageAnnotationsMap = new HashMap<>();
         message.setMessageAnnotations(new MessageAnnotations(messageAnnotationsMap));
      }
   }

   private void lazyCreateDeliveryAnnotations() {
      if (deliveryAnnotationsMap == null) {
         deliveryAnnotationsMap = new HashMap<>();
         message.setDeliveryAnnotations(new DeliveryAnnotations(deliveryAnnotationsMap));
      }
   }

   private void lazyCreateApplicationProperties() {
      if (applicationPropertiesMap == null) {
         applicationPropertiesMap = new HashMap<>();
         message.setApplicationProperties(new ApplicationProperties(applicationPropertiesMap));
      }
   }

   private void lazyCreateHeader() {
      if (message.getHeader() == null) {
         message.setHeader(new Header());
      }
   }

   private void lazyCreateProperties() {
      if (message.getProperties() == null) {
         message.setProperties(new Properties());
      }
   }

   public void settle() {
      delivery.settle();
   }
}
