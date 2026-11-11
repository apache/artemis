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

@Command(name = "show", description = "Show the selected address.")
public class ShowAddress extends AddressAbstract {

   @Option(names = "--bindings", description = "Show the bindings for this address.")
   boolean bindings;

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);
      showAddress(context);
      return null;
   }

   private void showAddress(final ActionContext context) throws Exception {
      performCoreManagement(message -> {
         if (getName(false) == null) {
            ManagementHelper.putOperationInvocation(message, "broker", "listAddresses", "\n");
         } else if (bindings) {
            ManagementHelper.putOperationInvocation(message, "broker", "listBindingsForAddress", getName(false));
         } else {
            ManagementHelper.putOperationInvocation(message, "broker", "getAddressInfo", getName(false));
         }
      }, reply -> {
         final String result = (String) ManagementHelper.getResult(reply, String.class);
         context.out.println(result);
      }, reply -> {
         String errMsg = (String) ManagementHelper.getResult(reply, String.class);
         context.err.println("Failed to show address " + getName(false) + ". Reason: " + errMsg);
      });
   }

   public boolean isBindings() {
      return bindings;
   }

   public void setBindings(boolean bindings) {
      this.bindings = bindings;
   }
}
