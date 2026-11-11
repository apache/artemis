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
package org.apache.activemq.artemis.protocol.amqp.broker;

import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Header;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Properties;

import static org.apache.activemq.artemis.protocol.amqp.connect.mirror.AMQPMirrorControllerSource.ACK_REASON;

/**
 * <b>Warning:</b> do not use this class outside of the broker implementation.
 * This is exposing package methods on this package that are not meant to be used on user's application.
 */
public class AMQPMessageBrokerAccessor {

   /**
    * Warning: this is a method specific to the broker. Do not use it on user's application.
    */
   public static Object getDeliveryAnnotationProperty(AMQPMessage message, Symbol symbol) {
      return message.getDeliveryAnnotationProperty(symbol);
   }

   /**
    * Warning: this is a method specific to the broker. Do not use it on user's application.
    */
   public static Object getMessageAnnotationProperty(AMQPMessage message, Symbol symbol) {
      return message.getMessageAnnotation(symbol);
   }

   public static AckReason getMessageAnnotationAckReason(AMQPMessage message) {
      Number reasonVal = (Number) getMessageAnnotationProperty(message, ACK_REASON);
      return reasonVal == null ? AckReason.NORMAL : AckReason.fromValue(reasonVal.byteValue());
   }

   /**
    * Warning: this is a method specific to the broker. Do not use it on user's application.
    */
   public static Header getCurrentHeader(AMQPMessage message) {
      return message.getCurrentHeader();
   }

   /**
    * Warning: this is a method specific to the broker. Do not use it on user's application.
    */
   public static ApplicationProperties getDecodedApplicationProperties(AMQPMessage message) {
      return message.getDecodedApplicationProperties();
   }

   /**
    * Warning: this is a method specific to the broker. Do not use it on user's application.
    */
   public static int getRemainingBodyPosition(AMQPMessage message) {
      return message.remainingBodyPosition;
   }

   /**
    * Warning: this is a method specific to the broker. Do not use it on user's application.
    */
   public static Properties getCurrentProperties(AMQPMessage message) {
      return message.getCurrentProperties();
   }

   public static MessageAnnotations getDecodedMessageAnnotations(AMQPMessage message) {
      return message.getDecodedMessageAnnotations();
   }

}
