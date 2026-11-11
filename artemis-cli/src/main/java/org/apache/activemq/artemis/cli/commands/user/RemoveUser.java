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

import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import picocli.CommandLine.Command;

/**
 * Remove a user, example:
 * <pre>{@code
 * ./artemis user rm --user guest
 * }</pre>
 */
@Command(name = "rm", description = "Remove an existing user.")
public class RemoveUser extends UserAction {

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);
      checkInputUser();
      remove();
      return null;
   }

   private void remove() throws Exception {
      performCoreManagement(message -> {
         ManagementHelper.putOperationInvocation(message, "broker", "removeUser", userCommandUser);
      }, reply -> {
         getActionContext().out.println(userCommandUser + " removed successfully.");
      }, reply -> {
         String errMsg = (String) ManagementHelper.getResult(reply, String.class);
         getActionContext().err.println("Failed to remove user " + userCommandUser + ". Reason: " + errMsg);
      });
   }

}
