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
package org.apache.activemq.artemis.api.core.management;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;

/**
 * Helper class to create Java Objects from the JSON serialization returned by {@link AddressControl#getRolesAsJSON()}.
 */
public final class RoleInfo extends Role {

   /**
    * {@return an array of RoleInfo corresponding to the JSON serialization returned by {@link
    * AddressControl#getRolesAsJSON()}}
    */
   public static RoleInfo[] from(final String jsonString) throws Exception {
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      RoleInfo[] roles = new RoleInfo[array.size()];
      for (int i = 0; i < array.size(); i++) {
         JsonObject r = array.getJsonObject(i);
         RoleInfo role = new RoleInfo(
                 r.getString("name"),
                 r.getBoolean(SEND_PERMISSION),
                 r.getBoolean(CONSUME_PERMISSION),
                 r.getBoolean(CREATE_DURABLE_QUEUE_PERMISSION),
                 r.getBoolean(DELETE_DURABLE_QUEUE_PERMISSION),
                 r.getBoolean(CREATE_NONDURABLE_QUEUE_PERMISSION),
                 r.getBoolean(DELETE_NONDURABLE_QUEUE_PERMISSION),
                 r.getBoolean(MANAGE_PERMISSION),
                 r.getBoolean(BROWSE_PERMISSION),
                 r.getBoolean(CREATE_ADDRESS_PERMISSION),
                 r.getBoolean(DELETE_ADDRESS_PERMISSION));
         roles[i] = role;
      }
      return roles;
   }

   private RoleInfo(final String name,
                    final boolean send,
                    final boolean consume,
                    final boolean createDurableQueue,
                    final boolean deleteDurableQueue,
                    final boolean createNonDurableQueue,
                    final boolean deleteNonDurableQueue,
                    final boolean manage,
                    final boolean browse,
                    final boolean createAddress,
                    final boolean deleteAddress) {
      this.name = name;
      this.send = send;
      this.consume = consume;
      this.createDurableQueue = createDurableQueue;
      this.deleteDurableQueue = deleteDurableQueue;
      this.createNonDurableQueue = createNonDurableQueue;
      this.deleteNonDurableQueue = deleteNonDurableQueue;
      this.manage = manage;
      this.browse = browse;
      this.createAddress = createAddress;
      this.deleteAddress = deleteAddress;
   }
}
