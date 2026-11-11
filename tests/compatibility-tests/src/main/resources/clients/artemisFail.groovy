package clients

import org.apache.activemq.artemis.api.core.ActiveMQException
import org.apache.activemq.artemis.api.core.client.FailoverEventListener
import org.apache.activemq.artemis.api.core.client.FailoverEventType
import org.apache.activemq.artemis.jms.client.ActiveMQConnection

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

// Create a client connection factory

import org.apache.activemq.artemis.tests.compatibility.GroovyRun

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit;

if (ActiveMQConnection.class.equals(connectionToFail.getClass())) {
    CountDownLatch latch = new CountDownLatch(1);
    ((ActiveMQConnection)connectionToFail).setFailoverListener(new FailoverEventListener() {
        @Override
        void failoverEvent(FailoverEventType eventType) {
            latch.countDown();
        }
    })
    ((ActiveMQConnection)connectionToFail).getSessionFactory().getConnection().fail(new ActiveMQException("fail"));
    GroovyRun.assertTrue(latch.await(10, TimeUnit.SECONDS));
}
