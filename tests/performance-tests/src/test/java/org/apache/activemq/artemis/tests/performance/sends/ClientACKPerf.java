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
package org.apache.activemq.artemis.tests.performance.sends;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class ClientACKPerf extends AbstractSendReceivePerfTest {

   @Parameters(name = "batchSize={0}")
   public static Collection<Object[]> data() {
      List<Object[]> list = Arrays.asList(new Object[][]{{1}, {2000}});

      System.out.println("Size = " + list.size());
      return list;
   }

   public ClientACKPerf(int batchSize) {
      super();
      this.batchSize = batchSize;
   }

   public final int batchSize;

   @Override
   protected void consumeMessages(Connection c, String qName) throws Exception {
      int mode = 0;
      mode = Session.CLIENT_ACKNOWLEDGE;

      System.out.println("Receiver: Using PRE-ACK mode");

      Session s = c.createSession(false, mode);
      Queue q = s.createQueue(qName);
      MessageConsumer consumer = s.createConsumer(q, null, false);

      c.start();

      Message m = null;

      long totalTimeACKTime = 0;

      long start = System.currentTimeMillis();

      long nmessages = 0;
      long timeout = System.currentTimeMillis() + 60 * 1000;
      while (timeout > System.currentTimeMillis()) {
         m = consumer.receive(5000);
         afterConsume(m);

         if (m == null) {
            throw new Exception("Failed with m = null");
         }

         if (nmessages++ % batchSize == 0) {
            long startACK = System.nanoTime();
            m.acknowledge();
            long endACK = System.nanoTime();
            totalTimeACKTime += (endACK - startACK);
         }

         if (nmessages % 10000 == 0) {
            printMsgsSec(start, nmessages, totalTimeACKTime);
         }
      }

      printMsgsSec(start, nmessages, totalTimeACKTime);
   }

   protected void printMsgsSec(final long start, final double nmessages, final double totalTimeACKTime) {

      long end = System.currentTimeMillis();
      double elapsed = ((double) end - (double) start) / 1000f;

      double messagesPerSecond = nmessages / elapsed;
      double nAcks = nmessages / batchSize;

      System.out.println("batchSize=" + batchSize + ", numberOfMessages=" + nmessages + ", elapsedTime=" + elapsed + " msgs/sec= " + messagesPerSecond + ",totalTimeAcking=" + String.format("%10.4f", totalTimeACKTime) +
                            ", avgACKTime=" + String.format("%10.4f", (totalTimeACKTime / nAcks)));

   }

   @TestTemplate
   public void testSendReceive() throws Exception {
      super.doSendReceiveTestImpl();
   }
}
