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

import org.apache.activemq.artemis.cli.commands.messages.ConnectionAbstract;
import picocli.CommandLine.Option;

public abstract class UserAction extends ConnectionAbstract {

   @Option(names = "--role", description = "The user's role(s). Separate multiple roles with comma.")
   String role;

   @Option(names = "--user-command-user", description = "The username to use for the chosen user command. Default: input.")
   String userCommandUser = null;

   void checkInputUser() {
      if (userCommandUser == null) {
         userCommandUser = input("--user-command-user", "Please provide the username to use for the chosen user command:", null);
      }
   }

   void checkInputRole() {
      if (role == null) {
         role = input("--role", "What is the user's role(s)? Separate multiple roles with comma.", null);
      }
   }

   public void setUserCommandUser(String userCommandUser) {
      this.userCommandUser = userCommandUser;
   }

   public void setRole(String role) {
      this.role = role;
   }
}
