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

package org.apache.activemq.artemis.cli.commands.queue;

import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.messages.ConnectionAbstract;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "purge", description = "Delete all messages in a queue.")
public class PurgeQueue extends ConnectionAbstract {

   @Option(names = "--name", description = "The queue's name.")
   String name;

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);
      purgeQueue(context);
      return null;
   }

   private void purgeQueue(final ActionContext context) throws Exception {
      performCoreManagement(message -> {
         ManagementHelper.putOperationInvocation(message, ResourceNames.QUEUE + getName(), "removeAllMessages");
      }, reply -> {
         context.out.println("Queue " + getName() + " purged successfully.");
      }, reply -> {
         String errMsg = (String) ManagementHelper.getResult(reply, String.class);
         context.err.println("Failed to purge queue " + getName() + ". Reason: " + errMsg);
      });
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getName() {
      if (name == null) {
         name = input("--name", "What is the name of the queue?", "");
      }

      return name;
   }
}
