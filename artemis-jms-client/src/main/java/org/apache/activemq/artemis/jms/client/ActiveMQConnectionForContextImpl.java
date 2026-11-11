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

import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.Session;
import javax.jms.XAJMSContext;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSConstants;
import org.apache.activemq.artemis.utils.ReferenceCounter;
import org.apache.activemq.artemis.utils.ReferenceCounterUtil;

public abstract class ActiveMQConnectionForContextImpl implements ActiveMQConnectionForContext {

   final ReferenceCounter refCounter = new ReferenceCounterUtil(() -> {
      try {
         close();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   });

   protected final ThreadAwareContext threadAwareContext = new ThreadAwareContext();

   @Override
   public JMSContext createContext(int sessionMode) {
      switch (sessionMode) {
         case Session.AUTO_ACKNOWLEDGE:
         case Session.CLIENT_ACKNOWLEDGE:
         case Session.DUPS_OK_ACKNOWLEDGE:
         case Session.SESSION_TRANSACTED:
         case ActiveMQJMSConstants.INDIVIDUAL_ACKNOWLEDGE:
         case ActiveMQJMSConstants.PRE_ACKNOWLEDGE:
            break;
         default:
            throw new JMSRuntimeException("Invalid ackmode: " + sessionMode);
      }
      refCounter.increment();

      return new ActiveMQJMSContext(this, sessionMode, threadAwareContext);
   }

   @Override
   public XAJMSContext createXAContext() {
      refCounter.increment();

      return new ActiveMQXAJMSContext(this, threadAwareContext);
   }

   @Override
   public void closeFromContext() {
      refCounter.decrement();
   }

   protected void incrementRefCounter() {
      refCounter.increment();
   }

   public ThreadAwareContext getThreadAwareContext() {
      return threadAwareContext;
   }
}
