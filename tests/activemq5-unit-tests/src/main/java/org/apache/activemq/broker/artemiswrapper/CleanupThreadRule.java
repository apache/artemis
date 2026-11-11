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
package org.apache.activemq.broker.artemiswrapper;

import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.persistence.impl.journal.OperationContextImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnector;
import org.junit.rules.ExternalResource;

public class CleanupThreadRule extends ExternalResource {

   @Override
   protected void before() throws Throwable {

   }

   @Override
   protected void after() {
      OperationContextImpl.clearContext();

      // We shutdown the global pools to give a better isolation between tests
      try {
         ServerLocatorImpl.clearThreadPools();
      } catch (Throwable e) {
         e.printStackTrace();
      }

      try {
         NettyConnector.clearThreadPools();
      } catch (Exception e) {
         e.printStackTrace();
      }

   }
}
