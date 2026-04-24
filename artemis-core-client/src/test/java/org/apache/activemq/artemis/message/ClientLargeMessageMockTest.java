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

package org.apache.activemq.artemis.message;

import java.io.OutputStream;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.core.client.impl.ClientLargeMessageImpl;
import org.apache.activemq.artemis.core.client.impl.LargeMessageController;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ClientLargeMessageMockTest {


   @Test
   public void testLargeBatch() throws Exception {

      ClientLargeMessageImpl clientLargeMessage = Mockito.spy(new ClientLargeMessageImpl());

      // this is bypassing initBuffer since it would replace the mockedBuffer
      Mockito.doReturn(clientLargeMessage).when(clientLargeMessage).initBuffer(Mockito.anyInt());

      LargeMessageController fakeController = Mockito.mock(LargeMessageController.class);
      Mockito.doAnswer(invocation -> {
         OutputStream output = invocation.getArgument(0);
         output.write(RandomUtil.randomBytes(1024));
         return null;
      }).when(fakeController).saveBuffer(Mockito.any(OutputStream.class));

      ByteBuf mockedBuffer = Mockito.mock(ByteBuf.class);

      Mockito.when(mockedBuffer.writeBytes(Mockito.any(byte[].class), Mockito.any(int.class), Mockito.any(int.class))).thenReturn(mockedBuffer);

      Mockito.when(mockedBuffer.duplicate()).thenReturn(mockedBuffer);

      clientLargeMessage.setBuffer(mockedBuffer);
      clientLargeMessage.setLargeMessageController(fakeController);

      clientLargeMessage.getBodyBuffer();

      // if this fails it means it's not using the batched write, and it would be a lot slower
      Mockito.verify(mockedBuffer).writeBytes(Mockito.any(byte[].class), Mockito.any(int.class), Mockito.any(int.class));
   }

}
