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
package org.apache.activemq.artemis.jms.tests.message.foreign;

import java.io.Serializable;

/**
 * A Simple Serializable Object
 */
public class ForeignTestObject implements Serializable {

   private static final long serialVersionUID = -7503042537789321104L;

   private String s1;

   private double d1;

   public ForeignTestObject(final String s, final double d) {
      s1 = s;
      d1 = d;
   }

   public double getD1() {
      return d1;
   }

   public void setD1(final double d1) {
      this.d1 = d1;
   }

   public String getS1() {
      return s1;
   }

   public void setS1(final String s1) {
      this.s1 = s1;
   }

   @Override
   public boolean equals(final Object obj) {
      if (obj instanceof ForeignTestObject other) {
         return s1.equals(other.getS1()) && d1 == other.getD1();
      }
      return super.equals(obj);
   }

   @Override
   public int hashCode() {
      // TODO
      return 0;
   }

}
