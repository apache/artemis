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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.SimpleManagement;
import org.apache.activemq.artemis.cli.Terminal;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.messages.ConnectionAbstract;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.utils.TableOut;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "stat", description = "Print basic stats of a queue. Output includes CONSUMER_COUNT (number of consumers), MESSAGE_COUNT (current message count on the queue, including scheduled, paged and in-delivery messages), MESSAGES_ADDED (messages added to the queue), DELIVERING_COUNT (messages broker is currently delivering to consumer(s)), MESSAGES_ACKED (messages acknowledged from the consumer(s))." + " Queues can be filtered using EITHER '--queueName X' where X is contained in the queue name OR using a full filter '--field NAME --operation EQUALS --value X'.")
public class StatQueue extends ConnectionAbstract {

   private static final String NOT_APPLICABLE = "";

   private static final String MANAGEMENT_QUEUE = "activemq.management";

   public enum FIELD {
      NAME("name", false), ADDRESS("address", false), CONSUMER_COUNT("consumerCount", true), MESSAGE_COUNT("messageCount", true), MESSAGES_ADDED("messagesAdded", true), DELIVERING_COUNT("deliveringCount", true), MESSAGES_ACKED("messagesAcked", true), SCHEDULED_COUNT("scheduledCount", true), ROUTING_TYPE("routingType", true), INTERNAL("internalQueue", true);

      private static final Map<String, FIELD> lookup = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

      static {
         for (FIELD e: values()) {
            lookup.put(e.jsonId, e);
         }
      }

      private String jsonId;
      private boolean center;

      FIELD(String jsonId, boolean center) {
         this.jsonId = jsonId;
         this.center = center;
      }

      String getJsonId() {
         return this.jsonId;
      }

      public static FIELD valueOfJsonId(String jsonId) {
         return lookup.get(jsonId);
      }
   }

   public enum OPERATION {
      CONTAINS, NOT_CONTAINS, EQUALS, GREATER_THAN, LESS_THAN
   }

   public static final int DEFAULT_MAX_ROWS = 50;

   public static final int DEFAULT_MAX_COLUMN_SIZE = 25;

   @Option(names = "--queueName", description = "Display queue stats for queue(s) with names containing this string.")
   private String queueName;

   @Option(names = "--field", description = "The field to filter. Possible values: NAME, ADDRESS, MESSAGE_COUNT, MESSAGES_ADDED, DELIVERING_COUNT, MESSAGES_ACKED, SCHEDULED_COUNT, ROUTING_TYPE.")
   private String fieldName;

   @Option(names = "--operation", description = "The operation to filter. Possible values: CONTAINS, NOT_CONTAINS, EQUALS, GREATER_THAN, LESS_THAN.")
   private String operationName;

   @Option(names = "--value", description = "The value to filter.")
   private String value;

   @Option(names = "--maxRows", description = "The max number of queues displayed. Default is 50.")
   private int maxRows = DEFAULT_MAX_ROWS;

   @Option(names = "--maxColumnSize", description = "The max width of data column. Set to -1 for no limit. Default is 25.")
   private int maxColumnSize = DEFAULT_MAX_COLUMN_SIZE;

   @Option(names = "--clustered", description = "Expands the report for all nodes on the topology")
   private boolean clustered = false;

   @Option(names = "--include-management", description = "Include queues created for notification management in the output")
   private boolean includeManagement = false;

   @Option(names = "--loop", description = "Keep Queue Stat in a forever loop, that you can interrupt with Ctrl-C, sleeping for --loop-time between each iteration.")
   private boolean useLoop = false;

   private static final long DEFAULT_SLEEP = 60_000;
   @Option(names = "--loop-sleep", description = "Amount of Milliseconds to sleep before each iteration on queue stat. Default=60000", hidden = true)
   private long oldSleep = -1;

   @Option(names = "--sleep", description = "Amount of Milliseconds to sleep before each iteration on queue stat. Default=60000")
   private long loopSleep = -1;

   @Option(names = "--single-line-header", description = "Use a single line on the header titles")
   private boolean singleLineHeader = false;

   @Option(names = "--json", description = "Prints the queue stat information in JSON format, useful for scripts")
   private boolean json;

   @Option(names = "--ascii", description = "Use ASCII table output style (plain text separators instead of Unicode box-drawing characters)")
   private boolean ascii = false;

   public boolean isAscii() {
      return ascii;
   }

   public StatQueue setAscii(boolean ascii) {
      this.ascii = ascii;
      return this;
   }

   public boolean isSingleLineHeader() {
      return singleLineHeader;
   }

   public StatQueue setSingleLineHeader(boolean singleLineHeader) {
      this.singleLineHeader = singleLineHeader;
      return this;
   }

   private int statCount = 0;

   //easier for testing
   public StatQueue setQueueName(String queueName) {
      this.queueName = queueName;
      return this;
   }

   public StatQueue setOperationName(String operationName) {
      this.operationName = operationName;
      return this;
   }

   public StatQueue setFieldName(String fieldName) {
      this.fieldName = fieldName;
      return this;
   }

   public StatQueue setValue(String value) {
      this.value = value;
      return this;
   }

   public StatQueue setMaxRows(int maxRows) {
      this.maxRows = maxRows;
      return this;
   }

   public StatQueue setMaxColumnSize(int maxColumnSize) {
      int maxFieldSize = 0;
      for (FIELD e : FIELD.values()) {
         if (e.jsonId.length() > maxFieldSize) {
            maxFieldSize = e.jsonId.length();
         }
      }
      if (maxColumnSize != -1 && maxColumnSize < maxFieldSize) {
         throw new IllegalArgumentException("maxColumnSize must be " + maxFieldSize + " or greater or -1 (i.e. no limit).");
      }
      this.maxColumnSize = maxColumnSize;
      return this;
   }

   public StatQueue setverbose(boolean verbose) {
      this.verbose = verbose;
      return this;
   }

   public boolean isJson() {
      return json;
   }

   public StatQueue setJson(boolean json) {
      this.json = json;
      return this;
   }

   @Override
   public Object execute(ActionContext context) throws Exception {
      if (json) {
         silent = true;
      }
      super.execute(context);
      String filter = createFilter();

      //should always get an initialised filter back when values correct
      if (filter == null) {
         return null;
      }

      if (verbose) {
         getActionContext().out.println("filter is '" + filter + "'");
         getActionContext().out.println("maxRows='" + maxRows + "'");
      }
      createConnectionFactory().close();

      if (json) {
         jsonExecution(context, filter);
         return 1;
      }

      singleExeuction(context, filter);

      // if using the hidden parameter
      if (oldSleep != -1) {
         loopSleep = oldSleep;
      }

      if (loopSleep != -1) {
         // if --loop-sleep was passed as an argument, it is assumed the user also meant --loop
         useLoop = true;
      }

      if (useLoop && loopSleep == -1) {
         loopSleep = DEFAULT_SLEEP;
      }

      while (useLoop) {
         getActionContext().out.println("Waiting " + loopSleep + " before another queue stat iteration");
         Thread.sleep(loopSleep);
         getActionContext().out.println(new Date() + ">> Queue stat results for " + getBrokerInstance());
         try {
            singleExeuction(context, filter);
         } catch (Throwable e) {
            e.printStackTrace(getActionContext().err);
         }
      }

      return statCount;
   }

   private void jsonExecution(ActionContext context, String filter) throws Exception {
      performCoreManagement(brokerURL, user, password, message -> {
         ManagementHelper.putOperationInvocation(message, "broker", "listQueues", filter, 1, maxRows);
      }, reply -> {
         // some formatting, to make it more palatable
         String result = (String) ManagementHelper.getResult(reply, String.class);
         result = result.replace("{\"id\"", "\n   {\"id\"");
         result = result.replace("]", "\n]");
         context.out.println(result);
      }, reply -> {
         String errMsg = (String) ManagementHelper.getResult(reply, String.class);
         getActionContext().err.println("Failed to get Stats for Queues. Reason: " + errMsg);
      });
   }

   private void singleExeuction(ActionContext context, String filter) throws Exception {
      try (SimpleManagement simpleManagement = new SimpleManagement(brokerURL, user, password).open()) {
         String nodeID = simpleManagement.getNodeID();
         JsonArray topology = simpleManagement.listNetworkTopology();

         if (clustered && topology.size() > 1) {
            context.out.println(Terminal.INFO_COLOR_UNICODE + "*******************************************************************************************************************************");
            context.out.println(">>> Queue stats on node " + nodeID + ", url=" + brokerURL + Terminal.CLEAR_UNICODE);
            printStats(brokerURL, filter);

            for (int i = 0; i < topology.size(); i++) {
               JsonObject node = topology.getJsonObject(i);
               if (node.getString("nodeID").equals(nodeID) || node.getJsonString("live") == null) {
                  continue;
               }

               String url = "tcp://" + node.getString("live");

               context.out.println(Terminal.INFO_COLOR_UNICODE + "*******************************************************************************************************************************");
               context.out.println(">>> Queue stats on node " + node.getString("nodeID") + ", url=" + url + Terminal.CLEAR_UNICODE);

               printStats(url, filter);
            }
         } else {
            printStats(brokerURL, filter);
            if (topology.size() > 1) {
               context.out.println();
               context.out.println("Note: Use " + Terminal.WARNING_COLOR_UNICODE + "--clustered" + Terminal.CLEAR_UNICODE + " to expand the report to other nodes in the topology.");
               context.out.println();
            }
         }
      }
   }

   private void printStats(String uri, final String filter) throws Exception {
      performCoreManagement(uri, user, password, message -> {
         ManagementHelper.putOperationInvocation(message, "broker", "listQueues", filter, 1, maxRows);
      }, reply -> {
         final String result = (String) ManagementHelper.getResult(reply, String.class);
         printStats(result);
      }, reply -> {
         String errMsg = (String) ManagementHelper.getResult(reply, String.class);
         getActionContext().err.println("Failed to get Stats for Queues. Reason: " + errMsg);
      });
   }

   private void printStats(String result) {

      //should not happen but...
      if (result == null) {
         if (verbose) {
            getActionContext().err.println("printStats(): got NULL result string.");
         }
         return;
      }

      JsonObject queuesAsJsonObject = JsonUtil.readJsonObject(result);
      int count = queuesAsJsonObject.getInt("count");
      JsonArray array = queuesAsJsonObject.getJsonArray("data");

      int[] columnSizes = new int[FIELD.values().length];
      boolean[] centralize = new boolean[columnSizes.length];

      List<String>[] fieldTitles = new ArrayList[columnSizes.length];

      FIELD[] fields = FIELD.values();
      for (int i = 0; i < fields.length; i++) {
         if (singleLineHeader) {
            columnSizes[i] = fields[i].toString().length();
         } else {
            List<String> splitTitleArrayList = new ArrayList<>();
            String[] splitTitleStringArray = fields[i].toString().split("_");
            centralize[i] = fields[i].center;

            for (String s : splitTitleStringArray) {
               splitTitleArrayList.add(s);
               columnSizes[i] = Math.max(columnSizes[i], s.length());
            }

            fieldTitles[i] = splitTitleArrayList;
         }
      }

      for (int i = 0; i < array.size(); i++) {
         getColumnSizes(array.getJsonObject(i), columnSizes);
      }

      TableOut tableOut = new TableOut("|", 2, columnSizes).setAscii(ascii);

      if (singleLineHeader) {
         printHeadings(columnSizes);
      } else {
         tableOut.printTopSeparator(getActionContext().out);
         tableOut.print(getActionContext().out, fieldTitles, centralize);
         if (!ascii) {
            tableOut.printSeparator(getActionContext().out);
         }
      }

      for (int i = 0; i < array.size(); i++) {
         if (!includeManagement && array.getJsonObject(i).getString("name").contains(MANAGEMENT_QUEUE)) {
            continue;
         }
         printQueueStats(array.getJsonObject(i), columnSizes, centralize, tableOut);
         statCount++;
      }
      tableOut.printBottomSeparator(getActionContext().out);

      if (count > maxRows) {
         getActionContext().out.println(String.format("WARNING: the displayed queues are %d/%d, set maxRows to display more queues.", maxRows, count));
      }
   }

   private void getColumnSizes(JsonObject jsonObject, int[] columnSizes) {
      int i = 0;
      if (!includeManagement && jsonObject.getString("name").startsWith(MANAGEMENT_QUEUE)) {
         return;
      }
      for (FIELD e: FIELD.values()) {
         if (jsonObject.containsKey(e.jsonId)) {
            if (jsonObject.getString(e.jsonId).length() > columnSizes[i]) {
               columnSizes[i] = jsonObject.getString(e.jsonId).length();
            }
         } else {
            if (NOT_APPLICABLE.length() > columnSizes[i]) {
               columnSizes[i] = NOT_APPLICABLE.length();
            }
         }
         // enforce max
         if (columnSizes[i] > maxColumnSize && maxColumnSize != -1) {
            columnSizes[i] = maxColumnSize;
         }
         i++;
      }
   }

   private void printHeadings(int[] columnSizes) {
      // add 10 for the various '|' characters
      StringBuilder stringBuilder = new StringBuilder(Arrays.stream(columnSizes).sum() + FIELD.values().length + 1).append('|');

      int i = 0;
      for (FIELD e: FIELD.values()) {
         stringBuilder.append(paddingString(new StringBuilder(e.toString()), columnSizes[i++])).append('|');
      }

      getActionContext().out.println(stringBuilder);
   }


   private void printQueueStats(JsonObject jsonObject, int[] columnSizes, boolean[] center, TableOut tableOut) {

      //should not happen but just in case..
      if (jsonObject == null) {
         if (verbose) {
            getActionContext().err.println("printQueueStats(): jsonObject is null");
         }
         return;
      }

      int i = 0;
      String[] columns = new String[columnSizes.length];
      for (FIELD e: FIELD.values()) {
         if (!jsonObject.containsKey(e.jsonId)) {
            columns[i++] = NOT_APPLICABLE;
         } else {
            columns[i++] = jsonObject.getString(e.jsonId);
         }
      }
      tableOut.print(getActionContext().out, columns, center);
   }

   private StringBuilder paddingString(StringBuilder value, int maxColumnSize) {

      //should not happen but just in case ...
      if (value == null) {
         return new StringBuilder(maxColumnSize);
      }

      //would expect to have some data
      if (value.isEmpty()) {
         value.append("NO DATA");
      }

      int length = value.length();

      if (length > maxColumnSize && this.maxColumnSize != -1) {
         // truncate if necessary
         return new StringBuilder(value.substring(0, maxColumnSize - 3) + "...");
      }

      for (int i = 1; (i + length) <= maxColumnSize; i++) {
         value.append(' ');
      }

      return value;
   }

   //creates filter used for listQueues()
   private String createFilter() {

      Map<String, Object> filterMap = new HashMap<>();

      if (((fieldName != null) && (!fieldName.trim().isEmpty())) && ((queueName != null && !queueName.trim().isEmpty()))) {
         getActionContext().err.println("'--field' and '--queueName' cannot be specified together.");
         return null;
      }

      if ((fieldName != null) && (!fieldName.trim().isEmpty())) {
         try {
            FIELD field = FIELD.valueOfJsonId(fieldName);

            //for backward compatibility
            if (field == null) {
               field = FIELD.valueOf(fieldName);
            }

            filterMap.put("field", field.toString());
         } catch (IllegalArgumentException ex) {
            getActionContext().err.println("'--field' must be set to one of the following " + Arrays.toString(FIELD.values()));
            return null;
         }

         //full filter being set ensure value is set
         if (value == null || value.trim().isEmpty()) {
            getActionContext().err.println("'--value' needs to be set when '--field' is specified");
            return null;
         }
         filterMap.put("value", value);

         if (operationName == null) {
            getActionContext().err.println("'--operation' must be set when '--field' is specified " + Arrays.toString(OPERATION.values()));
            return null;
         }

         try {
            OPERATION operation = OPERATION.valueOf(operationName);
            filterMap.put("operation", operation.toString());
         } catch (IllegalArgumentException ex) {
            getActionContext().err.println("'--operation' must be set to one of the following " + Arrays.toString(OPERATION.values()));
            return null;
         }

      } else if (queueName != null && !queueName.trim().isEmpty()) {
         filterMap.put("field", FIELD.NAME.toString());
         filterMap.put("value", queueName);
         filterMap.put("operation", OPERATION.CONTAINS.toString());
      } else {
         filterMap.put("field", "");
         filterMap.put("value", "");
         filterMap.put("operation", "");
      }

      JsonObject filterJsonObject = JsonUtil.toJsonObject(filterMap);
      return filterJsonObject.toString();
   }
}