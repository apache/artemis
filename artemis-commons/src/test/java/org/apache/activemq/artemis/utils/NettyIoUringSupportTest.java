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
package org.apache.activemq.artemis.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

/**
 * Sanity checks for {@link NettyIoUringSupport}, the single class that references the Netty io_uring transport types.
 * The io_uring classes are on this module's (optional) test classpath, so the class-level assertions below always run;
 * the native-availability assertion is guarded because it needs a supported Linux platform with the native lib loaded.
 */
public class NettyIoUringSupportTest {

   @Test
   public void testChannelClassesAreIoUringTypes() {
      assertEquals("io.netty.channel.uring.IoUringSocketChannel", NettyIoUringSupport.socketChannelClass().getName());
      assertEquals("io.netty.channel.uring.IoUringServerSocketChannel", NettyIoUringSupport.serverSocketChannelClass().getName());
   }

   @Test
   public void testAvailabilityCheckDoesNotThrow() {
      // this must always return cleanly (true or false); it must never propagate an exception, since callers rely on it
      // as the gate before touching any io_uring type
      boolean available = CheckDependencies.isIoUringAvailable();

      // when Env reports a non-Linux platform io_uring can never be considered available
      if (!Env.isLinuxOs()) {
         assertEquals(false, available);
      }
   }

   @Test
   public void testHandlerFactoryWhenAvailable() {
      assumeTrue(CheckDependencies.isIoUringAvailable());

      // on a platform where io_uring is available the isolated factory/channel accessors must produce usable objects
      assertNotNull(NettyIoUringSupport.newHandlerFactory());
      assertNotNull(NettyIoUringSupport.socketChannelClass());
      assertNotNull(NettyIoUringSupport.serverSocketChannelClass());
   }
}
