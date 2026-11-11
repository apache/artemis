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

package org.apache.activemq.artemis.cli.commands;

import org.apache.activemq.artemis.cli.Shell;
import org.apache.activemq.artemis.cli.commands.messages.ConnectionAbstract;
import picocli.CommandLine;

@CommandLine.Command(name = "disconnect", description = "Clear previously typed user credentials.")
public class Disconnect extends ConnectionAbstract {

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);
      CONNECTION_INFORMATION.remove();
      context.out.println("Connection information cleared!");

      if (Shell.inShell()) {
         Shell.setDefaultPrompt();
         Shell.setConnected(false);
      }

      return null;
   }
}
