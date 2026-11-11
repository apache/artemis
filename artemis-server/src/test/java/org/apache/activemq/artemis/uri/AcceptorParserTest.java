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
package org.apache.activemq.artemis.uri;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.ConfigurationUtils;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptor;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.utils.ConfigurationHelper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AcceptorParserTest {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testAcceptor() throws Exception {
      List<TransportConfiguration> configs = ConfigurationUtils.parseAcceptorURI("test", "tcp://localhost:8080?tcpSendBufferSize=1048576&tcpReceiveBufferSize=1048576&protocols=openwire&banana=x");

      for (TransportConfiguration config : configs) {
         logger.debug("config: {}", config);
         assertEquals("x", config.getExtraParams().get("banana"));
      }
   }

   @Test
   public void testAcceptorShutdownTimeout() {
      List<TransportConfiguration> configs = ConfigurationUtils.parseAcceptorURI("test", "tcp://localhost:8080?quietPeriod=33;shutdownTimeout=55");

      assertEquals(1, configs.size());

      assertEquals(33, ConfigurationHelper.getIntProperty(TransportConstants.QUIET_PERIOD, -1, configs.get(0).getParams()));
      assertEquals(55, ConfigurationHelper.getIntProperty(TransportConstants.SHUTDOWN_TIMEOUT, -1, configs.get(0).getParams()));

      NettyAcceptor nettyAcceptor = new NettyAcceptor("name", null, configs.get(0).getParams(), null, null, null, null, new HashMap<>(), "threadFactoryGroupName", null);

      assertEquals(33, nettyAcceptor.getQuietPeriod());
      assertEquals(55, nettyAcceptor.getShutdownTimeout());
   }

   @Test
   public void testAcceptorWithQueryParamEscapes() throws Exception {
      List<TransportConfiguration> configs = ConfigurationUtils.parseAcceptorURI("test", "tcp://0.0.0.0:5672?tcpSendBufferSize=1048576;tcpReceiveBufferSize=1048576;virtualTopicConsumerWildcards=Consumer.*.%3E%3B2");

      for (TransportConfiguration config : configs) {
         logger.debug("config: {}", config);
         logger.debug("{}", config.getExtraParams().get("virtualTopicConsumerWildcards"));
         assertEquals("Consumer.*.>;2", config.getExtraParams().get("virtualTopicConsumerWildcards"));
      }
   }
}
