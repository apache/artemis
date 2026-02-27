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
package org.apache.activemq.artemis.core.postoffice.impl;

import java.util.Collections;
import java.util.List;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.transaction.Transaction;

public final class NoOpDuplicateIDCache implements DuplicateIDCache {

   @Override
   public boolean contains(byte[] duplicateID) {
      return false;
   }

   @Override
   public boolean atomicVerify(byte[] duplID, Transaction tx) throws Exception {
      return true;
   }

   @Override
   public void addToCache(byte[] duplicateID) throws Exception {

   }

   @Override
   public void addToCache(byte[] duplicateID, Transaction tx) throws Exception {

   }

   @Override
   public void addToCache(byte[] duplicateID, Transaction tx, boolean instantAdd) throws Exception {

   }

   @Override
   public void deleteFromCache(byte[] duplicateID) throws Exception {

   }

   @Override
   public void load(List<Pair<byte[], Long>> ids) throws Exception {

   }

   @Override
   public void load(Transaction tx, byte[] duplID) {

   }

   @Override
   public void clear() throws Exception {

   }

   @Override
   public List<Pair<byte[], Long>> getMap() {
      return Collections.emptyList();
   }

   @Override
   public int getSize() {
      return 0;
   }
}
