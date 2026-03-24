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

import javax.jms.TemporaryTopic;

public class ActiveMQTemporaryTopic extends ActiveMQTopic implements TemporaryTopic {


   private static final long serialVersionUID = 845450764835635266L;


   public ActiveMQTemporaryTopic() {
      this(null, null);
   }

   public ActiveMQTemporaryTopic(final String address, final ActiveMQSession session) {
      super(address, true, session);
   }


   @Override
   public boolean equals(final Object o) {
      if (this == o) {
         return true;
      }

      if (!(o instanceof ActiveMQTemporaryTopic)) {
         return false;
      }

      ActiveMQTemporaryTopic that = (ActiveMQTemporaryTopic) o;

      return super.getAddress().equals(that.getAddress());
   }

   @Override
   public int hashCode() {
      return super.getAddress().hashCode();
   }

}
