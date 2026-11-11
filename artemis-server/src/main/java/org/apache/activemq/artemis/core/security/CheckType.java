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
package org.apache.activemq.artemis.core.security;

public enum CheckType {
   SEND {
      @Override
      public boolean hasRole(final Role role) {
         return role.isSend();
      }
   },
   CONSUME {
      @Override
      public boolean hasRole(final Role role) {
         return role.isConsume();
      }
   },
   CREATE_ADDRESS {
      @Override
      public boolean hasRole(final Role role) {
         return role.isCreateAddress();
      }
   },
   DELETE_ADDRESS {
      @Override
      public boolean hasRole(final Role role) {
         return role.isDeleteAddress();
      }
   },
   CREATE_DURABLE_QUEUE {
      @Override
      public boolean hasRole(final Role role) {
         return role.isCreateDurableQueue();
      }
   },
   DELETE_DURABLE_QUEUE {
      @Override
      public boolean hasRole(final Role role) {
         return role.isDeleteDurableQueue();
      }
   },
   CREATE_NON_DURABLE_QUEUE {
      @Override
      public boolean hasRole(final Role role) {
         return role.isCreateNonDurableQueue();
      }
   },
   DELETE_NON_DURABLE_QUEUE {
      @Override
      public boolean hasRole(final Role role) {
         return role.isDeleteNonDurableQueue();
      }
   },
   MANAGE {
      @Override
      public boolean hasRole(final Role role) {
         return role.isManage();
      }
   },
   BROWSE {
      @Override
      public boolean hasRole(final Role role) {
         return role.isBrowse();
      }
   },
   VIEW {
      @Override
      public boolean hasRole(final Role role) {
         return role.isView();
      }
   },
   EDIT {
      @Override
      public boolean hasRole(final Role role) {
         return role.isEdit();
      }
   };

   public static Object[] asObjectArray(Role role) {
      // order is important!
      return new Object[]{
         role.getName(),
         CheckType.SEND.hasRole(role),
         CheckType.CONSUME.hasRole(role),
         CheckType.CREATE_DURABLE_QUEUE.hasRole(role),
         CheckType.DELETE_DURABLE_QUEUE.hasRole(role),
         CheckType.CREATE_NON_DURABLE_QUEUE.hasRole(role),
         CheckType.DELETE_NON_DURABLE_QUEUE.hasRole(role),
         CheckType.MANAGE.hasRole(role),
         CheckType.BROWSE.hasRole(role),
         CheckType.CREATE_ADDRESS.hasRole(role),
         CheckType.DELETE_ADDRESS.hasRole(role),
         CheckType.VIEW.hasRole(role),
         CheckType.EDIT.hasRole(role)
      };
   }

   public abstract boolean hasRole(Role role);
}
