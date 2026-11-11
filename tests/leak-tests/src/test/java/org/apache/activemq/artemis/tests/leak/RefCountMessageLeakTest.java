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

package org.apache.activemq.artemis.tests.leak;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import io.github.checkleak.core.CheckLeak;
import org.apache.activemq.artemis.api.core.RefCountMessage;
import org.apache.activemq.artemis.api.core.RefCountMessageAccessor;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.junit.jupiter.api.Test;

public class RefCountMessageLeakTest extends AbstractLeakTest {

   static class DebugMessage extends RefCountMessage {
      final String string;

      DebugMessage(String str) {
         this.string = str;
         registerDebug();
      }

      @Override
      public String toString() {
         return "debugMessage(" + string + ")";
      }

      public void fired() {
         this.released();
      }
   }

   @Test
   public void testLeakRefCount() throws Exception {
      String strMessage = RandomUtil.randomUUIDString();
      String strMessageFired = RandomUtil.randomUUIDString();

      ReusableLatch latchLeaked = new ReusableLatch(1);
      DebugMessage message = new DebugMessage(strMessage);
      message.refUp();
      message.durableUp();
      RefCountMessageAccessor.setRunOnLeak(message, latchLeaked::countDown);
      message = null;
      // I know it's null, I'm just doing this to make sure there are no optimizations from the JVM delaying the GC cleanup
      assertNull(message);

      MemoryAssertions.assertMemory(new CheckLeak(), 0, RefCountMessage.class.getName());
      assertTrue(latchLeaked.await(1, TimeUnit.SECONDS));

      DebugMessage message2 = new DebugMessage(strMessageFired);
      message2.refUp();
      latchLeaked.setCount(1);

      RefCountMessageAccessor.setRunOnLeak(message2, latchLeaked::countDown);
      message2.fired();
      message2 = null;
      // I know it's null, I'm just doing this to make sure there are no optimizations from the JVM delaying the GC cleanup
      assertNull(message2);

      MemoryAssertions.assertMemory(new CheckLeak(), 0, RefCountMessage.class.getName());
      assertFalse(latchLeaked.await(100, TimeUnit.MILLISECONDS));
   }
}