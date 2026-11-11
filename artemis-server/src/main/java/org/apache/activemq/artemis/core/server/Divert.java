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
package org.apache.activemq.artemis.core.server;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.server.transformer.Transformer;

public interface Divert extends Bindable {

   SimpleString getAddress();

   Filter getFilter();

   boolean isExclusive();

   SimpleString getUniqueName();

   SimpleString getRoutingName();

   Transformer getTransformer();

   SimpleString getForwardAddress();

   ComponentConfigurationRoutingType getRoutingType();

   void setFilter(Filter filter);

   void setTransformer(Transformer transformer);

   void setForwardAddress(SimpleString forwardAddress);

   void setRoutingType(ComponentConfigurationRoutingType routingType);
}
