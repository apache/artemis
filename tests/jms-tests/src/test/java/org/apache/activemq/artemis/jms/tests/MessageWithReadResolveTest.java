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
package org.apache.activemq.artemis.jms.tests;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import java.io.Serializable;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;

public class MessageWithReadResolveTest extends JMSTestCase {
   @Test
   public void testSendReceiveMessage() throws Exception {
      Connection conn = createConnection();

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = sess.createProducer(queue1);

      // Make persistent to make sure message gets serialized
      prod.setDeliveryMode(DeliveryMode.PERSISTENT);

      MessageConsumer cons = sess.createConsumer(queue1);

      TestMessage tm = new TestMessage(123, false);

      ObjectMessage om = sess.createObjectMessage();

      om.setObject(tm);

      conn.start();

      prod.send(om);

      ObjectMessage om2 = (ObjectMessage) cons.receive(1000);

      ProxyAssertSupport.assertNotNull(om2);

      TestMessage tm2 = (TestMessage) om2.getObject();

      ProxyAssertSupport.assertEquals(123, tm2.getID());

      conn.close();
   }




   // This class would trigger the exception when serialized with jboss serialization
   public static class TestMessage implements Serializable {

      private static final long serialVersionUID = -5932581134414145967L;

      private final long id;

      private Object clazz;

      public TestMessage(final long id, final boolean useSimpleObject) {
         this.id = id;
         if (useSimpleObject) {
            clazz = String.class;
         } else {
            clazz = TestEnum.class;
         }
      }

      @Override
      public String toString() {
         StringBuilder sb = new StringBuilder();
         sb.append("TestMessage(");
         sb.append("id=" + id);
         sb.append(", clazz=" + clazz);
         sb.append(")");
         return sb.toString();
      }

      public long getID() {
         return id;
      }

   }

   public static class TestEnum implements Serializable {

      private static final long serialVersionUID = 4306026990380393029L;

      public Object readResolve() {
         return null;
      }
   }

}
