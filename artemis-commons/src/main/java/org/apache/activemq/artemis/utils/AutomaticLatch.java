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

/**
 * An automatic latch has the same semantic as the ReusableLatch However this class has a replaceable callback that
 * could be called when the number of elements reach zero. With that you can either block to wait completion, or to send
 * a callback to be used when it reaches 0.
 */
public class AutomaticLatch extends AbstractLatch {

   volatile Runnable afterCompletion;

   public AutomaticLatch() {
   }

   public AutomaticLatch(int count) {
      super(count);
   }

   // it will execute when the counter reaches 0.
   // notice that since the latch is reusable,
   // the runnable will be cleared once it reached 0
   public void afterCompletion(final Runnable newRun) {
      // We first raise one element up
      // to avoid a race on it being called while another thread sets it down to 0
      countUp();
      if (this.afterCompletion != null) {
         // this should not happen really,
         // but just in case it ever happens,
         // I would rather have a runnable depending into other runnables instead of a collection here
         // as the use case I'm after is a single runnable
         final Runnable oldRun = afterCompletion;
         this.afterCompletion = () -> {
            oldRun.run();
            newRun.run();
         };
      } else {
         this.afterCompletion = newRun;
      }
      // then we countDown so it will be instantly 0 if nothing else done it
      // or it then just keep flow as usual
      countDown();
   }

   @Override
   public final void countDown() {
      if (control.releaseShared(1)) {
         doRun();
      }
   }

   private void doRun() {
      Runnable toRun = afterCompletion;
      afterCompletion = null;
      if (toRun != null) {
         toRun.run();
      }
   }

   @Override
   public final void countDown(final int count) {
      if (control.releaseShared(count)) {
         doRun();
      }
   }
}
