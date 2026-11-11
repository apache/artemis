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

package org.apache.activemq.artemis.cli.commands.messages;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Session;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "browser", description = "Browse messages on a queue.")
public class Browse extends DestAbstract {

   @Option(names = "--filter", description = "The message filter.")
   String filter;

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      context.out.println("Consumer:: filter = " + filter);

      ConnectionFactory factory = createConnectionFactory();

      try (Connection connection = factory.createConnection()) {
         ConsumerThread[] threadsArray = new ConsumerThread[threads];
         for (int i = 0; i < threads; i++) {
            Session session;
            if (txBatchSize > 0) {
               session = connection.createSession(true, Session.SESSION_TRANSACTED);
            } else {
               session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            }
            Destination dest = getDestination(session);
            threadsArray[i] = new ConsumerThread(session, dest, i, context);

            threadsArray[i]
               .setVerbose(verbose)
               .setSleep(sleep)
               .setMessageCount(messageCount)
               .setFilter(filter)
               .setBrowse(true);
         }

         for (ConsumerThread thread : threadsArray) {
            thread.start();
         }

         connection.start();

         long received = 0;

         for (ConsumerThread thread : threadsArray) {
            thread.join();
            received += thread.getReceived();
         }

         return received;
      }
   }
}
