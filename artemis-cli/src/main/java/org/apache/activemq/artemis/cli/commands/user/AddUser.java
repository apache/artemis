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
import picocli.CommandLine.Option;

/**
 * Adding a new user, example:
 * {@literal ./artemis user add --user-command-user guest --role admin --user-command-password ***}
 */
@Command(name = "add", description = "Add a user.")
public class AddUser extends PasswordAction {

   @Option(names = "--plaintext", description = "Store the password in plaintext. Default: false.")
   boolean plaintext = false;

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);
      checkInputUser();
      checkInputPassword();
      checkInputRole();
      add();
      return null;
   }

   /**
    * Add a new user
    *
    * @throws Exception if communication with the broker fails
    */
   private void add() throws Exception {
      performCoreManagement(message -> {
         ManagementHelper.putOperationInvocation(message, "broker", "addUser", userCommandUser, userCommandPassword, role, plaintext);
      }, reply -> {
         getActionContext().out.println(userCommandUser + " added successfully.");
      }, reply -> {
         String errMsg = (String) ManagementHelper.getResult(reply, String.class);
         getActionContext().err.println("Failed to add user " + userCommandUser + ". Reason: " + errMsg);
      });
   }
}
