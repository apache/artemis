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

import javax.jms.TemporaryQueue;
import java.util.Objects;

/**
 * Apache Artemis implementation of a JMS TemporaryQueue.
 * <p>
 * This class can be instantiated directly.
 */
public class ActiveMQTemporaryQueue extends ActiveMQQueue implements TemporaryQueue {


   private static final long serialVersionUID = -4624930377557954624L;


   // TemporaryQueue implementation ------------------------------------------

   public ActiveMQTemporaryQueue() {
      this(null, null);
   }

   public ActiveMQTemporaryQueue(String address, ActiveMQSession session) {
      super(address, true, session);
   }

   @Override
   public String toString() {
      return "ActiveMQTemporaryQueue[" + getAddress() + "]";
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof ActiveMQTemporaryQueue other)) {
         return false;
      }

      return Objects.equals(super.getAddress(), other.getAddress());
   }

   @Override
   public int hashCode() {
      return super.getAddress().hashCode();
   }


}
