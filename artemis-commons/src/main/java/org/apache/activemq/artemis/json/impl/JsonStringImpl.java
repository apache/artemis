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

import org.apache.activemq.artemis.json.JsonString;

public class JsonStringImpl extends JsonValueImpl implements JsonString {

   private final javax.json.JsonString rawString;

   public javax.json.JsonString getRawString() {
      return rawString;
   }

   public JsonStringImpl(javax.json.JsonString rawString) {
      super(rawString);
      this.rawString = rawString;
   }

   @Override
   public String getString() {
      return rawString.getString();
   }

   @Override
   public CharSequence getChars() {
      return rawString.getChars();
   }
}
