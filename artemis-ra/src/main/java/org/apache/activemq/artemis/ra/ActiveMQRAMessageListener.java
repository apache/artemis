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

import javax.jms.Message;
import javax.jms.MessageListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A wrapper for a {@link MessageListener}.
 */
public class ActiveMQRAMessageListener implements MessageListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final MessageListener listener;

   private final ActiveMQRAMessageConsumer consumer;

   public ActiveMQRAMessageListener(final MessageListener listener, final ActiveMQRAMessageConsumer consumer) {
      logger.trace("constructor({}, {})", listener, consumer);

      this.listener = listener;
      this.consumer = consumer;
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public void onMessage(Message message) {
      logger.trace("onMessage({})", message);

      message = consumer.wrapMessage(message);
      listener.onMessage(message);
   }
}
