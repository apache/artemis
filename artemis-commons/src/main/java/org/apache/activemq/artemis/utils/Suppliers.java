/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.activemq.artemis.utils;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Supplier;

public class Suppliers {

   /**
    * Returns a supplier which caches the instance retrieved during the first call to {@code get()} and returns that
    * value on subsequent calls to {@code get()}. See:
    * <a href="http://en.wikipedia.org/wiki/Memoization">memoization</a>
    * <p>
    * The returned supplier is thread-safe. The delegate's {@code get()} method will be invoked at most once. The
    * supplier's serialized form does not contain the cached value, which will be recalculated when {@code get()} is
    * called on the reserialized instance.
    * <p>
    * If {@code delegate} is an instance created by an earlier call to {@code memoize}, it is returned directly.
    */
   public static <T> Supplier<T> memoize(Supplier<T> delegate) {
      return (delegate instanceof MemoizingSupplier)
            ? delegate
            : new MemoizingSupplier<T>(Objects.requireNonNull(delegate));
   }

   private static class MemoizingSupplier<T> implements Supplier<T>, Serializable {
      final Supplier<T> delegate;
      transient volatile boolean initialized;
      // "value" does not need to be volatile; visibility piggy-backs
      // on volatile read of "initialized".
      transient T value;

      MemoizingSupplier(Supplier<T> delegate) {
         this.delegate = delegate;
      }

      @Override
      public T get() {
         // A 2-field variant of Double Checked Locking.
         if (!initialized) {
            synchronized (this) {
               if (!initialized) {
                  T t = delegate.get();
                  value = t;
                  initialized = true;
                  return t;
               }
            }
         }
         return value;
      }

      @Override
      public String toString() {
         return "Suppliers.memoize(" + delegate + ")";
      }

      private static final long serialVersionUID = 0;
   }


}
