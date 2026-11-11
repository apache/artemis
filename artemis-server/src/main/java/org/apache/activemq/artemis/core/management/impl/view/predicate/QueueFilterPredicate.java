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
package org.apache.activemq.artemis.core.management.impl.view.predicate;

import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.management.impl.view.QueueField;
import org.apache.activemq.artemis.core.server.ActiveMQServer;

public class QueueFilterPredicate extends ActiveMQFilterPredicate<QueueControl, QueuePredicateFilterPart> {

   private QueueField f;

   private ActiveMQServer server;

   public QueueFilterPredicate(ActiveMQServer server) {
      super();
      this.server = server;
   }

   @Override
   protected boolean filter(QueueControl queue, QueuePredicateFilterPart filterPart) throws Exception {
      return filterPart.filterPart(queue);
   }

   @Override
   public QueuePredicateFilterPart createFilterPart(String field, String operation, String value) {
      return new QueuePredicateFilterPart(server, field, operation, value);
   }
}
