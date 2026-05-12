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
package org.apache.activemq.artemis.cli.commands.user;

import picocli.CommandLine.Option;

public class PasswordAction extends UserAction {

   @Option(names = "--user-command-password", description = "The password to use for the chosen user command. Default: input.")
   String userCommandPassword;

   void checkInputPassword() {
      if (userCommandPassword == null) {
         userCommandPassword = inputPassword("--user-command-password", "What is the password to use for the chosen user command?");
      }
   }

   public void setUserCommandPassword(String userCommandPassword) {
      this.userCommandPassword = userCommandPassword;
   }

}
