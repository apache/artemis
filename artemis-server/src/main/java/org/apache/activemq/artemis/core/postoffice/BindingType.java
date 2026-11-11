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

import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;

public enum BindingType {
   LOCAL_QUEUE, REMOTE_QUEUE, DIVERT;

   public static final int LOCAL_QUEUE_INDEX = 0;

   public static final int REMOTE_QUEUE_INDEX = 1;

   public static final int DIVERT_INDEX = 2;

   public int toInt() {
      if (equals(BindingType.LOCAL_QUEUE)) {
         return BindingType.LOCAL_QUEUE_INDEX;
      } else if (equals(BindingType.REMOTE_QUEUE)) {
         return BindingType.REMOTE_QUEUE_INDEX;
      } else if (equals(BindingType.DIVERT)) {
         return BindingType.DIVERT_INDEX;
      } else {
         throw ActiveMQMessageBundle.BUNDLE.cannotConvertToInt();
      }
   }

}
