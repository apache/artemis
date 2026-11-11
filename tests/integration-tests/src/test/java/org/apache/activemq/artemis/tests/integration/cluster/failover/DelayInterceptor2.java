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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

public class DelayInterceptor2 implements Interceptor {

   private volatile boolean loseResponse = true;

   private final CountDownLatch latch = new CountDownLatch(1);

   @Override
   public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
      if (packet.getType() == PacketImpl.NULL_RESPONSE && loseResponse) {
         // Lose the response from the commit - only lose the first one

         loseResponse = false;

         latch.countDown();

         return false;
      } else {
         return true;
      }
   }

   public boolean await() throws InterruptedException {
      return latch.await(10, TimeUnit.SECONDS);
   }
}
