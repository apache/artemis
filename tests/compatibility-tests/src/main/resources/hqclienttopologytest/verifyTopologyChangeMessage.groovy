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
package hqclienttopologytest

import org.apache.activemq.artemis.tests.compatibility.GroovyRun

import javax.jms.Connection;
import javax.jms.Session;
import java.util.concurrent.TimeUnit;

/*
 * Opens JMS connection and verifies that transport config with correct parameters is received from the server.
 */


// starts an artemis server
String serverType = arg[0];
String clientType = arg[1];

if (clientType.startsWith("ARTEMIS")) {
    // Can't depend directly on artemis, otherwise it wouldn't compile in hornetq
    GroovyRun.evaluate("hqclienttopologytest/artemisClient.groovy", "serverArg", serverType);
} else {
    // Can't depend directly on hornetq, otherwise it wouldn't compile in artemis
    GroovyRun.evaluate("hqclienttopologytest/hornetqClient.groovy");
}


Connection connection = cf.createConnection();
Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

try {
    latch.await(5, TimeUnit.SECONDS);

    // cluster topology message should arrive immediately after connecting
    GroovyRun.assertEquals(0, (int) latch.getCount());

    if (clientType.startsWith("ARTEMIS")) {
        // Artemis client - obtained params should be camelCase
        GroovyRun.assertFalse(transportParams.containsKey("ssl-enabled"));
        GroovyRun.assertTrue(transportParams.containsKey("sslEnabled"));
    } else {
        // HornetQ client - obtained params should be dash-delimited
        GroovyRun.assertTrue(transportParams.containsKey("ssl-enabled"));
        GroovyRun.assertFalse(transportParams.containsKey("sslEnabled"));
    }

    // parameter activemq.passwordcodec should not be present
    GroovyRun.assertFalse(transportParams.containsKey("activemq.passwordcodec"));
} finally {
    session.close();
    connection.close();
}
