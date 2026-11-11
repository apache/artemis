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
package org.apache.activemq.artemis.core.io;

import java.util.Collection;
import java.util.Objects;

/**
 * It is a utility class to allow several {@link IOCallback}s to be used as one.
 */
public final class DelegateCallback implements IOCallback {

   private final Collection<? extends IOCallback> delegates;

   public static DelegateCallback wrap(final Collection<? extends IOCallback> delegates) {
      if (delegates == null) {
         return null;
      } else {
         return new DelegateCallback(delegates);
      }
   }

   /**
    * It doesn't copy defensively the given {@code delegates}.
    *
    * @throws NullPointerException if {@code delegates} is {@code null}
    */
   private DelegateCallback(final Collection<? extends IOCallback> delegates) {
      Objects.requireNonNull(delegates, "delegates cannot be null!");
      this.delegates = delegates;
   }

   @Override
   public void done() {
      IOCallback.done(delegates);
   }

   @Override
   public void onError(final int errorCode, final String errorMessage) {
      IOCallback.onError(delegates, errorCode, errorMessage);
   }

}
