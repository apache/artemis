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
package org.apache.activemq.artemis.core.postoffice;

import java.util.Collection;
import java.util.function.BiConsumer;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.group.UnproposalListener;

public interface Bindings extends UnproposalListener {

   // this is to inform the parent there was an update on the bindings
   void updated(QueueBinding binding);

   Collection<Binding> getBindings();

   Binding getBinding(String name);

   void addBinding(Binding binding);

   Binding removeBindingByUniqueName(SimpleString uniqueName);

   SimpleString getName();

   void setMessageLoadBalancingType(MessageLoadBalancingType messageLoadBalancingType);

   MessageLoadBalancingType getMessageLoadBalancingType();

   /**
    * @param message the message being copied
    * @return a Copy of the message if redistribution succeeded, or null if it wasn't redistributed
    */
   Message redistribute(Message message, Queue originatingQueue, RoutingContext context) throws Exception;

   void route(Message message, RoutingContext context) throws Exception;

   boolean allowRedistribute();

   void forEach(BiConsumer<String, Binding> bindingConsumer);

   int size();

   boolean hasLocalBinding();
}
