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
package org.apache.activemq.artemis.selector.filter;

import org.apache.activemq.artemis.api.core.SimpleString;

/**
 * A Filterable is the object being evaluated by the filters.  It provides access to filtered properties.
 */
public interface Filterable {

   /**
    * This method is used by message filters which do content based routing (Like the XPath based selectors).
    */
   <T> T getBodyAs(Class<T> type) throws FilterException;

   /**
    * Extracts the named message property
    */
   Object getProperty(SimpleString name);

   /**
    * Used by the NoLocal filter.
    *
    * @return a unique id for the connection that produced the message
    */
   Object getLocalConnectionId();

}
