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
package org.apache.activemq.artemis.tests.soak.client;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;

public class Sender extends ClientAbstract {

   protected ClientProducer producer;

   protected String queue;

   protected long msgs = ClientSoakTest.MIN_MESSAGES_ON_QUEUE;

   protected int pendingMsgs = 0;

   protected final Receiver[] receivers;


   public Sender(final ClientSessionFactory sf, String queue, final Receiver[] receivers) {
      super(sf);
      this.receivers = receivers;
      this.queue = queue;
   }

   @Override
   protected void connectClients() throws Exception {
      producer = session.createProducer(queue);
   }

   @Override
   public void run() {
      super.run();
      while (running) {
         try {
            beginTX();
            for (int i = 0; i < 1000; i++) {
               ClientMessage msg = session.createMessage(true);
               msg.putLongProperty("count", pendingMsgs + msgs);
               msg.getBodyBuffer().writeBytes(new byte[10 * 1024]);
               producer.send(msg);
               pendingMsgs++;
            }
            endTX();
         } catch (Exception e) {
            connect();
         }
      }
   }

   @Override
   protected void onCommit() {
      this.msgs += pendingMsgs;
      for (Receiver rec : receivers) {
         rec.messageProduced(pendingMsgs);
      }

      pendingMsgs = 0;
   }

   @Override
   protected void onRollback() {
      pendingMsgs = 0;
   }

   @Override
   public String toString() {
      return "Sender, msgs=" + msgs + ", pending=" + pendingMsgs;
   }
}
