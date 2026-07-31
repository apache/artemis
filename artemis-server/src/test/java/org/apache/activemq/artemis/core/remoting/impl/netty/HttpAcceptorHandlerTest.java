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
package org.apache.activemq.artemis.core.remoting.impl.netty;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
public class HttpAcceptorHandlerTest {

   private static final String HTTP_HANDLER = "http-handler";

   private HttpKeepAliveRunnable spy;

   @BeforeEach
   public void setUp() throws Exception {
      spy = spy(new HttpKeepAliveRunnable());
   }

   @Test
   public void testUnregisterIsCalledTwiceWhenChannelIsInactive() {
      EmbeddedChannel channel = new EmbeddedChannel();

      HttpAcceptorHandler httpHandler = new HttpAcceptorHandler(spy, 1000, channel);
      channel.pipeline().addLast(HTTP_HANDLER, httpHandler);

      channel.close();

      Mockito.verify(spy, Mockito.times(2)).unregisterKeepAliveHandler(httpHandler);
   }

   @Test
   public void testUnregisterIsCalledWhenHandlerIsRemovedFromPipeline() {
      EmbeddedChannel channel = new EmbeddedChannel();

      HttpAcceptorHandler httpHandler = new HttpAcceptorHandler(spy, 1000, channel);
      channel.pipeline().addLast(HTTP_HANDLER, httpHandler);

      channel.pipeline().remove(HTTP_HANDLER);

      Mockito.verify(spy).unregisterKeepAliveHandler(httpHandler);
   }
}
