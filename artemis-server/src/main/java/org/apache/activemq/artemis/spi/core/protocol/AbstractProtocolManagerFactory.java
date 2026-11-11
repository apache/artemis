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

package org.apache.activemq.artemis.spi.core.protocol;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServer;

public abstract class AbstractProtocolManagerFactory<P extends BaseInterceptor> implements ProtocolManagerFactory<P> {

   /**
    * This method exists because Java templates won't store the type of P at runtime. So it's not possible to write a
    * generic method with having the Class Type. This will serve as a tool for sub classes to filter properly,
    */
   protected List<P> internalFilterInterceptors(Class<P> type, List<? extends BaseInterceptor> listIn) {
      if (listIn == null) {
         return Collections.emptyList();
      } else {
         List<P> listOut = new CopyOnWriteArrayList<>();
         for (BaseInterceptor<?> in : listIn) {
            if (type.isInstance(in)) {
               listOut.add((P) in);
            }
         }
         return listOut;
      }
   }

   @Override
   public void loadProtocolServices(ActiveMQServer server, List<ActiveMQComponent> services) throws Exception {
   }

   @Override
   public void updateProtocolServices(ActiveMQServer server, List<ActiveMQComponent> services) throws Exception {
   }
}
