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
package org.apache.activemq.artemis.ra;

import javax.jms.ExceptionListener;
import javax.jms.JMSContext;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionForContext;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSContext;
import org.apache.activemq.artemis.jms.client.ThreadAwareContext;

public class ActiveMQRAJMSContext extends ActiveMQJMSContext {

   public ActiveMQRAJMSContext(ActiveMQConnectionForContext connection,
                               int ackMode,
                               ThreadAwareContext threadAwareContext) {
      super(connection, ackMode, threadAwareContext);
   }

   public ActiveMQRAJMSContext(ActiveMQConnectionForContext connection, ThreadAwareContext threadAwareContext) {
      super(connection, threadAwareContext);
   }

   @Override
   public JMSContext createContext(int sessionMode) {
      throw ActiveMQRABundle.BUNDLE.illegalJEEMethod();
   }

   @Override
   public void stop() {
      throw ActiveMQRABundle.BUNDLE.illegalJEEMethod();
   }

   @Override
   public void setClientID(String clientID) {
      throw ActiveMQRABundle.BUNDLE.illegalJEEMethod();
   }

   @Override
   public void setExceptionListener(ExceptionListener listener) {
      throw ActiveMQRABundle.BUNDLE.illegalJEEMethod();
   }
}
