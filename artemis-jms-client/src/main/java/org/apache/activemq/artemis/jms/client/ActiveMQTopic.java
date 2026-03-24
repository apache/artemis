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
package org.apache.activemq.artemis.jms.client;

import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.SimpleString;

/**
 * ActiveMQ Artemis implementation of a JMS Topic.
 * <br>
 * This class can be instantiated directly.
 */
public class ActiveMQTopic extends ActiveMQDestination implements Topic {


   private static final long serialVersionUID = 7873614001276404156L;

   public ActiveMQTopic() {
      this((SimpleString) null);
   }

   public ActiveMQTopic(final String address) {
      this(address, false);
   }

   public ActiveMQTopic(final SimpleString address) {
      super(address, TYPE.TOPIC, null);
   }

   @Deprecated
   public ActiveMQTopic(final String address, final String name) {
      super(address, name, TYPE.TOPIC, null);
   }

   public ActiveMQTopic(final String address, boolean temporary) {
      this(address, temporary, null);
   }

   /**
    * @param address
    * @param temporary
    * @param session
    */
   protected ActiveMQTopic(String address, boolean temporary, ActiveMQSession session) {
      super(address, temporary ? TYPE.TEMP_TOPIC : TYPE.TOPIC, session);
   }

   // Topic implementation ------------------------------------------

   @Override
   public String getTopicName() {
      return getName();
   }


   @Override
   public String toString() {
      return "ActiveMQTopic[" + getName() + "]";
   }

   @Override
   public boolean equals(final Object o) {
      if (this == o) {
         return true;
      }

      if (!(o instanceof ActiveMQTopic)) {
         return false;
      }

      ActiveMQTopic that = (ActiveMQTopic) o;

      return super.getAddress().equals(that.getAddress());
   }

   @Override
   public int hashCode() {
      return super.getAddress().hashCode();
   }


}
