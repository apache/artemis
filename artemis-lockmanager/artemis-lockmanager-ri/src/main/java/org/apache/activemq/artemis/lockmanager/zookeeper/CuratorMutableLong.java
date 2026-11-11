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
package org.apache.activemq.artemis.lockmanager.zookeeper;

import org.apache.activemq.artemis.lockmanager.MutableLong;
import org.apache.activemq.artemis.lockmanager.UnavailableStateException;
import org.apache.activemq.artemis.lockmanager.zookeeper.CuratorDistributedLockManager.PrimitiveId;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;

final class CuratorMutableLong extends CuratorDistributedPrimitive implements MutableLong {

   private final DistributedAtomicLong atomicLong;

   CuratorMutableLong(PrimitiveId id, CuratorDistributedLockManager manager, DistributedAtomicLong atomicLong) {
      super(id, manager);
      this.atomicLong = atomicLong;
   }

   @Override
   public String getMutableLongId() {
      return getId().id;
   }

   @Override
   public long get() throws UnavailableStateException {
      return run(() -> {
         checkUnavailable();
         try {
            AtomicValue<Long> atomicValue = atomicLong.get();
            if (!atomicValue.succeeded()) {
               throw new UnavailableStateException("cannot query long " + getId());
            }
            return atomicValue.postValue();
         } catch (Throwable e) {
            throw new UnavailableStateException(e);
         }
      });
   }

   @Override
   public void set(long value) throws UnavailableStateException {
      run(() -> {
         checkUnavailable();
         try {
            atomicLong.forceSet(value);
            return null;
         } catch (Throwable e) {
            throw new UnavailableStateException(e);
         }
      });
   }
}
