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

package org.apache.activemq.artemis.cli.commands.address;

import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "delete", description = "Delete an address.")
public class DeleteAddress extends AddressAbstract {

   @Option(names = "--force", description = "Delete the address even if it has queues. All messages in those queues will be deleted! Default: false.")
   private Boolean force = false;

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);
      deleteAddress(context);
      return null;
   }

   private void deleteAddress(final ActionContext context) throws Exception {
      performCoreManagement(message -> {
         ManagementHelper.putOperationInvocation(message, "broker", "deleteAddress", getName(true), force);
      }, reply -> {
         context.out.println("Address " + getName(true) + " deleted successfully.");
      }, reply -> {
         String errMsg = (String) ManagementHelper.getResult(reply, String.class);
         context.err.println("Failed to delete address " + getName(true) + ". Reason: " + errMsg);
      });
   }

   public void setForce(Boolean force) {
      this.force = force;
   }
}
