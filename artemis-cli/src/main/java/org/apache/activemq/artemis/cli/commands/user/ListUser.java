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
package org.apache.activemq.artemis.cli.commands.user;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import picocli.CommandLine.Command;

/**
 * list existing users, example:
 * ./artemis user list --user guest
 */
@Command(name = "list", description = "List user(s).")
public class ListUser extends UserAction {

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      list();

      return null;
   }

   /**
    * List a single user or all users if username is not specified
    *
    * @throws Exception if communication with the broker fails
    */
   private void list() throws Exception {
      StringBuilder logMessage = new StringBuilder("--- \"user\"(roles) ---\n");
      int userCount = 0;
      final String[] result = new String[1];

      performCoreManagement(message -> {
         ManagementHelper.putOperationInvocation(message, "broker", "listUser", userCommandUser);
      }, reply -> {
         result[0] = (String) ManagementHelper.getResult(reply, String.class);
      }, reply -> {
         String errMsg = (String) ManagementHelper.getResult(reply, String.class);
         getActionContext().err.println("Failed to list user " + userCommandUser + ". Reason: " + errMsg);
      });

      // process the JSON results from the broker
      JsonArray array = JsonUtil.readJsonArray(result[0]);
      for (int arrayIndex = 0; arrayIndex < array.size(); arrayIndex++) {
         JsonObject object = array.getJsonObject(arrayIndex);
         logMessage.append("\"").append(object.getString("username")).append("\"").append("(");
         JsonArray roles = object.getJsonArray("roles");
         for (int i = 0; i < roles.size(); i++) {
            logMessage.append(roles.getString(i));
            if ((i + 1) < roles.size()) {
               logMessage.append(",");
            }
         }
         logMessage.append(")\n");
         userCount++;
      }
      logMessage.append("\n Total: ").append(userCount);
      getActionContext().out.println(logMessage);
   }

}
