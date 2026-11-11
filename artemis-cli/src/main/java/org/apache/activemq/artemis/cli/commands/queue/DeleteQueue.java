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
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.messages.ConnectionAbstract;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "delete", description = "Delete a queue.")
public class DeleteQueue extends ConnectionAbstract {

   @Option(names = "--name", description = "The queue's name")
   String name;

   @Option(names = "--removeConsumers", description = "Whether to delete the queue even if it has active consumers. Default: false.")
   boolean removeConsumers = false;

   @Option(names = "--autoDeleteAddress", description = "Whether to delete the address if this is its only queue.")
   boolean autoDeleteAddress = false;

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);
      deleteQueue(context);
      return null;
   }

   private void deleteQueue(final ActionContext context) throws Exception {
      performCoreManagement(message -> {
         ManagementHelper.putOperationInvocation(message, "broker", "destroyQueue", getName(), removeConsumers, autoDeleteAddress);
      }, reply -> {
         context.out.println("Queue " + getName() + " deleted successfully.");
      }, reply -> {
         String errMsg = (String) ManagementHelper.getResult(reply, String.class);
         context.err.println("Failed to delete queue " + getName() + ". Reason: " + errMsg);
      });
   }

   public void setRemoveConsumers(boolean removeConsumers) {
      this.removeConsumers = removeConsumers;
   }

   public void setAutoDeleteAddress(boolean autoDeleteAddress) {
      this.autoDeleteAddress = autoDeleteAddress;
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
