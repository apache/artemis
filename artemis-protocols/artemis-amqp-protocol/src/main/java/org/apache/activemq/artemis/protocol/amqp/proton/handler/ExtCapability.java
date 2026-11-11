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
package org.apache.activemq.artemis.protocol.amqp.proton.handler;

import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.engine.Connection;

public class ExtCapability {

   public static final Symbol[] capabilities = new Symbol[]{AmqpSupport.SOLE_CONNECTION_CAPABILITY,
                                                            AmqpSupport.DELAYED_DELIVERY,
                                                            AmqpSupport.SHARED_SUBS,
                                                            AmqpSupport.ANONYMOUS_RELAY};

   public static Symbol[] getCapabilities() {
      return capabilities;
   }

   public static boolean needUniqueConnection(Connection connection) {
      Symbol[] extCapabilities = connection.getRemoteDesiredCapabilities();
      if (extCapabilities != null) {
         for (Symbol sym : extCapabilities) {
            if (sym.compareTo(AmqpSupport.SOLE_CONNECTION_CAPABILITY) == 0) {
               return true;
            }
         }
      }
      return false;
   }
}
