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

import javax.jms.JMSException;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A wrapper for a {@link TopicSubscriber}.
 */
public class ActiveMQRATopicSubscriber extends ActiveMQRAMessageConsumer implements TopicSubscriber {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public ActiveMQRATopicSubscriber(final TopicSubscriber consumer, final ActiveMQRASession session) {
      super(consumer, session);

      logger.trace("constructor({}, {})", consumer, session);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean getNoLocal() throws JMSException {
      logger.trace("getNoLocal()");

      checkState();
      return ((TopicSubscriber) consumer).getNoLocal();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Topic getTopic() throws JMSException {
      logger.trace("getTopic()");

      checkState();
      return ((TopicSubscriber) consumer).getTopic();
   }
}
