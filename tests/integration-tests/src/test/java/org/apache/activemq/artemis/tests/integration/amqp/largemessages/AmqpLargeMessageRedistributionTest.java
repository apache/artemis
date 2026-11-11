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

package org.apache.activemq.artemis.tests.integration.amqp.largemessages;

import org.apache.activemq.artemis.tests.integration.amqp.AmqpBridgeClusterRedistributionTest;
import org.apache.activemq.transport.amqp.client.AmqpMessage;

public class AmqpLargeMessageRedistributionTest extends AmqpBridgeClusterRedistributionTest {
   int frameSize = 1024 * 1024;
   @Override
   protected String getServer0URL() {
      return "tcp://localhost:61616?maxFrameSize=" + frameSize;
   }

   @Override
   protected String getServer1URL() {
      return "tcp://localhost:61617?maxFrameSize=" + frameSize;
   }

   @Override
   protected String getServer2URL() {
      return "tcp://localhost:61618?maxFrameSize=" + frameSize;
   }

   @Override
   protected void setData(AmqpMessage amqpMessage) throws Exception {
      amqpMessage.setBytes(new byte[110 * 1024]);
   }
}
