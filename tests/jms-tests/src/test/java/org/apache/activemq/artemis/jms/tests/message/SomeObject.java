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
package org.apache.activemq.artemis.jms.tests.message;

import java.io.Serializable;
import java.util.Objects;

/**
 * ObjectMessageTest needed a simple class to test ClassLoadingIsolations
 */
public class SomeObject implements Serializable {

   private static final long serialVersionUID = -2939720794544432834L;

   int i;

   int j;

   public SomeObject(final int i, final int j) {
      this.i = i;
      this.j = j;
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof SomeObject other)) {
         return false;
      }

      return i == other.i &&
             j == other.j;
   }

   @Override
   public int hashCode() {
      return Objects.hash(i, j);
   }
}
