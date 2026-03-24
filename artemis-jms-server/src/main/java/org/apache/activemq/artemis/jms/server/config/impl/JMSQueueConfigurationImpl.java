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
package org.apache.activemq.artemis.jms.server.config.impl;

import org.apache.activemq.artemis.jms.server.config.JMSQueueConfiguration;

public class JMSQueueConfigurationImpl implements JMSQueueConfiguration {


   private String name = null;

   private String selector = null;

   private boolean durable = true;

   private String[] bindings = null;


   public JMSQueueConfigurationImpl() {
   }

   // QueueConfiguration implementation -----------------------------

   @Override
   public String[] getBindings() {
      return bindings;
   }

   @Override
   public JMSQueueConfigurationImpl setBindings(String... bindings) {
      this.bindings = bindings;
      return this;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public JMSQueueConfigurationImpl setName(String name) {
      this.name = name;
      return this;
   }

   @Override
   public String getSelector() {
      return selector;
   }

   @Override
   public JMSQueueConfigurationImpl setSelector(String selector) {
      this.selector = selector;
      return this;
   }

   @Override
   public boolean isDurable() {
      return durable;
   }

   @Override
   public JMSQueueConfigurationImpl setDurable(boolean durable) {
      this.durable = durable;
      return this;
   }


}
