/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.filter;

public final class FilterUtils {

   private FilterUtils() {

   }

   /**
    * {@return {@code true} if {@code filter} is not {@code null} and is a {@link
    * org.apache.activemq.artemis.core.filter.Filter#GENERIC_IGNORED_FILTER}}
    *
    * @param filter a subscription filter
    */
   public static boolean isTopicIdentification(final Filter filter) {
      return filter != null && filter.getFilterString() != null && filter.getFilterString().toString().equals(Filter.GENERIC_IGNORED_FILTER);
   }

}
