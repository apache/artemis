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
 * Reset a user's password or roles, example:
 * <pre>{@code
 * ./artemis user reset --user guest --role admin --password ***
 * }</pre>
 */
@Command(name = "reset", description = "Reset user's password or roles.")
public class ResetUser extends PasswordAction {

   @Option(names = "--plaintext", description = "Store the password in plaintext. Default: false.")
   boolean plaintext = false;

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);
      checkInputUser();
      checkInputPassword();
      reset();
      return null;
   }

   private void reset() throws Exception {
      performCoreManagement(message -> {
         ManagementHelper.putOperationInvocation(message, "broker", "resetUser", userCommandUser, userCommandPassword, role, plaintext);
      }, reply -> {
         getActionContext().out.println(userCommandUser + " reset successfully.");
      }, reply -> {
         String errMsg = (String) ManagementHelper.getResult(reply, String.class);
         getActionContext().err.println("Failed to reset user " + userCommandUser + ". Reason: " + errMsg);
      });
   }
}
