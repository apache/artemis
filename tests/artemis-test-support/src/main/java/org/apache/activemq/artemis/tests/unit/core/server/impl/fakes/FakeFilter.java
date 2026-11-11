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
package org.apache.activemq.artemis.tests.unit.core.server.impl.fakes;


import java.util.Map;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.selector.filter.Filterable;

public class FakeFilter implements Filter {

   private String headerName;

   private Object headerValue;

   public FakeFilter(final String headerName, final Object headerValue) {
      this.headerName = headerName;

      this.headerValue = headerValue;
   }

   public FakeFilter() {
   }

   @Override
   public boolean match(final Message message) {
      if (headerName != null) {
         Object value = message.getObjectProperty(headerName);

         if (value instanceof SimpleString simpleString) {
            value = simpleString.toString();
         }

         if (value != null && headerValue.equals(value)) {
            return true;
         }

         return false;
      }

      return true;
   }

   @Override
   public boolean match(Map<String, String> map) {
      return false;
   }

   @Override
   public boolean match(Filterable filterable) {
      return false;
   }

   @Override
   public SimpleString getFilterString() {
      return null;
   }
}
