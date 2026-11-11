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
package org.apache.activemq.artemis.api.core;

import java.io.Serializable;
import java.util.Objects;

/**
 * A Pair is a holder for 1 object and 'a' being greater or equal than 0.
 * <p>
 * This is a utility class.
 */
public class ObjLongPair<A> implements Serializable {

   private static final long serialVersionUID = 7749478219139339853L;

   public static final long NIL = -1;

   public ObjLongPair(final A a, final long b) {
      this.a = a;
      this.b = b;
      if (b < 0 && b != NIL) {
         throw new IllegalStateException("b must be >= 0 or == NIL = " + NIL);
      }
   }

   public ObjLongPair(final A a) {
      this.a = a;
      this.b = NIL;
   }

   private A a;
   private long b;

   @Override
   public int hashCode() {
      if (a == null && b == NIL) {
         return super.hashCode();
      }
      // it's ok to use b to compute hashCode although NIL
      return Objects.hash(a, b);
   }

   @Override
   public boolean equals(final Object obj) {
      if (obj == this) {
         return true;
      }
      if (!(obj instanceof ObjLongPair other)) {
         return false;
      }

      return Objects.equals(other.a, a) &&
             other.b == b;
   }

   @Override
   public String toString() {
      return "ObjLongPair[a=" + a + ", b=" + (b == NIL ? "NIL" : b) + "]";
   }

   public void setA(A a) {
      if (this.a == a) {
         return;
      }
      this.a = a;
   }

   public A getA() {
      return a;
   }

   public void setB(long b) {
      if (b < 0 && b != NIL) {
         throw new IllegalStateException("b must be >= 0 or == NIL = " + NIL);
      }
      this.b = b;
   }

   public long getB() {
      return b;
   }
}
