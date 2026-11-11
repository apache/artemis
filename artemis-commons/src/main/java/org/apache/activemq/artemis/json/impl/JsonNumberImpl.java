/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.json.impl;

import org.apache.activemq.artemis.json.JsonNumber;

import java.math.BigDecimal;
import java.math.BigInteger;

public class JsonNumberImpl extends JsonValueImpl implements JsonNumber {

   private final javax.json.JsonNumber rawNumber;

   public javax.json.JsonNumber getRawNumber() {
      return rawNumber;
   }

   public JsonNumberImpl(javax.json.JsonNumber rawNumber) {
      super(rawNumber);
      this.rawNumber = rawNumber;
   }

   @Override
   public boolean isIntegral() {
      return rawNumber.isIntegral();
   }

   @Override
   public int intValue() {
      return rawNumber.intValue();
   }

   @Override
   public int intValueExact() {
      return rawNumber.intValueExact();
   }

   @Override
   public long longValue() {
      return rawNumber.longValue();
   }

   @Override
   public long longValueExact() {
      return rawNumber.longValueExact();
   }

   @Override
   public BigInteger bigIntegerValue() {
      return rawNumber.bigIntegerValue();
   }

   @Override
   public BigInteger bigIntegerValueExact() {
      return rawNumber.bigIntegerValueExact();
   }

   @Override
   public double doubleValue() {
      return rawNumber.doubleValue();
   }

   @Override
   public BigDecimal bigDecimalValue() {
      return rawNumber.bigDecimalValue();
   }

   @Override
   public Number numberValue() {
      return rawNumber.numberValue();
   }
}
