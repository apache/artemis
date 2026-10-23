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


import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.server.Bindable;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.group.UnproposalListener;

public interface Binding extends UnproposalListener {

   default boolean isLocal() {
      return false;
   }

   SimpleString getAddress();

   Bindable getBindable();

   BindingType getType();

   SimpleString getUniqueName();

   SimpleString getRoutingName();

   SimpleString getClusterName();

   Filter getFilter();

   boolean isHighAcceptPriority(Message message);

   boolean isExclusive();

   Long getID();

   int getDistance();

   void route(Message message, RoutingContext context) throws Exception;

   void routeWithAck(Message message, RoutingContext context) throws Exception;

   void close() throws Exception;

   /**
    * This method will create a string representation meant for management operations.
    * <p>
    * This is different from the toString() method that is meant for debugging and will contain information that regular
    * users won't understand well.
    */
   String toManagementString();

   boolean isConnected();

   /**
    * Whether this binding is eligible for routing. {@link org.apache.activemq.artemis.core.postoffice.impl.BindingsImpl}
    * skips disabled bindings both when routing and when redistributing.
    * <p>
    * The default <b>must</b> remain {@code true}: bindings that do not override it (such as local queue bindings and
    * diverts) have to keep receiving messages. Only remote queue bindings are temporarily disabled, while a node is
    * being drained during scale-down, to stop store-and-forward ingress/egress toward the departing node.
    */
   default boolean isEnabled() {
      return true;
   }
}
