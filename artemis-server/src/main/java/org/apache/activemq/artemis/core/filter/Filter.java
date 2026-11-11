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
package org.apache.activemq.artemis.core.filter;

import java.util.Map;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.selector.filter.Filterable;

public interface Filter {

   /**
    * JMS Topics (which are outside of the scope of the core API) will require a dumb subscription with a dummy-filter
    * at this current version as a way to keep its existence valid and TCK tests. That subscription needs an invalid
    * filter, however paging needs to ignore any subscription with this filter. For that reason, this filter needs to be
    * rejected on paging or any other component on the system, and just be ignored for any purpose It's declared here as
    * this filter is considered a global ignore
    */
   String GENERIC_IGNORED_FILTER = "__AMQX=-1";

   boolean match(Message message);

   boolean match(Map<String, String> map);

   boolean match(Filterable filterable);

   SimpleString getFilterString();

   static SimpleString toFilterString(Filter filter) {
      return filter == null ? null : filter.getFilterString();
   }
}
