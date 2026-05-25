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

package org.apache.activemq.artemis.cli.commands.lock;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.cli.Terminal;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.messages.ConnectionAbstract;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.utils.TableOut;
import picocli.CommandLine;

@CommandLine.Command(name = "list", description = "List the lock coordinators and their status on the server.")
public class LockList extends ConnectionAbstract {

   @CommandLine.Option(names = "--sleep", description = "Monitor locks continuously by repeating the command at the specified interval in milliseconds. Use -1 to disable (default).", defaultValue = "-1")
   private long sleep = -1;

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      do {
         context.out.println(new Date() + ">> Lock list results for " + getBrokerInstance());
         list(context);
         if (sleep > 0) {
            context.out.println("Waiting " + sleep + " before another lock list iteration");
            Thread.sleep(sleep);
         }
      }
      while (sleep > 0);

      return null;
   }

   private void list(final ActionContext context) throws Exception {
      try (SimpleManagement simpleManagement = new SimpleManagement(brokerURL, user, password).open()) {
         JsonArray lockList = simpleManagement.listLockCoordinators();
         printStats(context, lockList);
      }
   }

   private void printStats(ActionContext context, JsonArray array) {
      if (array.size() == 0) {
         context.out.println(Terminal.INFO_COLOR_UNICODE + "No Lock Coordinators configured" + Terminal.CLEAR_UNICODE);
         context.out.println();
         return;
      }

      String[] fieldNames = {"NAME", "ID", "STATUS"};
      int[] columnSizes = new int[fieldNames.length];
      boolean[] centralize = new boolean[fieldNames.length];
      List<String>[] fieldTitles = new ArrayList[fieldNames.length];

      // Calculate column sizes and split field names
      for (int i = 0; i < fieldNames.length; i++) {
         List<String> splitTitleArrayList = new ArrayList<>();
         String[] splitTitleStringArray = fieldNames[i].split("_");

         for (String s : splitTitleStringArray) {
            splitTitleArrayList.add(s);
            columnSizes[i] = Math.max(columnSizes[i], s.length());
         }

         fieldTitles[i] = splitTitleArrayList;
      }

      // Calculate column sizes based on data
      for (int i = 0; i < array.size(); i++) {
         JsonObject lock = array.getJsonObject(i);
         columnSizes[0] = Math.max(columnSizes[0], lock.getString("name", "").length());
         columnSizes[1] = Math.max(columnSizes[1], lock.getString("lockId", "").length());
         columnSizes[2] = Math.max(columnSizes[2], lock.getString("status", "").length());
      }

      TableOut tableOut = new TableOut("|", 2, columnSizes);

      // Print header
      tableOut.print(context.out, fieldTitles, centralize);

      // Print data rows
      for (int i = 0; i < array.size(); i++) {
         JsonObject lock = array.getJsonObject(i);
         String[] columns = {
            lock.getString("name", ""),
            lock.getString("lockId", ""),
            lock.getString("status")
         };
         tableOut.print(context.out, columns, centralize);
      }
      context.out.println();
   }

}
