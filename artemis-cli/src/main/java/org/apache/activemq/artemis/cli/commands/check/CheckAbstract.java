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
package org.apache.activemq.artemis.cli.commands.check;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.activemq.artemis.api.core.management.ActiveMQManagementProxy;
import org.apache.activemq.artemis.cli.CLIException;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.messages.ConnectionAbstract;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.commons.lang3.time.StopWatch;
import picocli.CommandLine.Option;

public abstract class CheckAbstract extends ConnectionAbstract {

   @Option(names = "--name", description = "Name of the target to check.")
   protected String name;

   @Option(names = "--timeout", description = "Time to wait for the check to complete (in milliseconds).")
   private int timeout = 30000;

   @Option(names = "--fail-at-end", description = "Continue with the rest of the checks even if a particular module check fails.")
   private boolean failAtEnd = false;

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public int getTimeout() {
      return timeout;
   }

   public void setTimeout(int timeout) {
      this.timeout = timeout;
   }

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);
      ExecutorService executor = Executors.newFixedThreadPool(1);

      Future<Integer> checkTask = executor.submit(() -> {
         int errorTasks = 0;
         int failedTasks = 0;
         int successTasks = 0;

         try (ActiveMQConnectionFactory factory = createCoreConnectionFactory();
              ActiveMQManagementProxy managementProxy = new ActiveMQManagementProxy(factory.getServerLocator(), user, password)) {

            StopWatch watch = new StopWatch();
            CheckTask[] checkTasks = getCheckTasks();
            CheckContext checkContext = new CheckContext(context, factory, managementProxy);

            context.out.println("Running " + this.getClass().getSimpleName());

            watch.start();

            try {
               for (CheckTask task: checkTasks) {
                  try {
                     context.out.print("Checking that " + task.getAssertion() + " ... ");

                     task.getCallback().run(checkContext);
                     successTasks++;

                     context.out.println("success");
                  } catch (Exception e) {
                     String reason;

                     if (e instanceof CheckException) {
                        failedTasks++;
                        reason = "failure: " + e.getMessage();
                     } else {
                        errorTasks++;
                        reason = "error: " + e.getMessage();
                     }

                     context.out.println(reason);
                     if (verbose) {
                        context.out.println(e.toString());
                        e.printStackTrace(context.out);
                     }

                     if (!failAtEnd) {
                        fail(reason);
                     }
                  }
               }
            } finally {
               watch.stop();

               int skippedTasks = checkTasks.length - failedTasks - errorTasks - successTasks;

               context.out.println(String.format("Checks run: %d, Failures: %d, Errors: %d, Skipped: %d, Time elapsed: %.03f sec - %s",
                                                 checkTasks.length, failedTasks, errorTasks, skippedTasks,
                                                 ((float)watch.getTime()) / 1000, this.getClass().getSimpleName()));
            }

            if (successTasks < checkTasks.length) {
               fail("checks not successful");
            }
         }

         return successTasks;
      });

      try {
         return checkTask.get(timeout, TimeUnit.MILLISECONDS);
      } catch (ExecutionException e) {
         Throwable cause = e.getCause();
         if (cause instanceof CLIException cliException) {
            throw cliException;
         } else {
            fail(cause.toString());
         }
      } catch (TimeoutException e) {
         fail("timeout");
      } finally {
         executor.shutdownNow();
      }

      return 0;
   }

   private void fail(String reason) throws Exception {
      throw new CLIException(this.getClass().getSimpleName() + " failed. Reason: " + reason);
   }

   protected abstract CheckTask[] getCheckTasks();
}
