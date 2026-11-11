package takeoverReattach
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

import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.apache.activemq.artemis.api.core.QueueConfiguration
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import org.apache.activemq.artemis.api.core.client.ClientProducer
import org.apache.activemq.artemis.api.core.client.ServerLocator
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal
import org.apache.activemq.artemis.core.client.impl.ClientSessionImpl
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal
import org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQSessionContext
import org.apache.activemq.artemis.core.protocol.core.impl.RemotingConnectionImpl
import java.lang.reflect.Constructor
import java.lang.reflect.Field

// Create target session with "admin" user (full permissions)
ServerLocator locator = ActiveMQClient.createServerLocator("tcp://localhost:61616")
locator.setRetryInterval(100)
      .setRetryIntervalMultiplier(1)
      .setReconnectAttempts(-1)
      .setConfirmationWindowSize(1024 * 1024)

ClientSessionFactoryInternal sfTarget = locator.createSessionFactory()
ClientSessionImpl targetSession = sfTarget.createSession("admin", "admin", false, true, true, false, 0)

SimpleString address = SimpleString.of("TakeoverTestAddress")
targetSession.createQueue(QueueConfiguration.of(address).setDurable(false))

// Get session info for reattachment attempt
String targetSessionName = targetSession.getName()
int targetSessionChannel = targetSession.getSessionContext().getSessionChannel().getID()
int version = targetSession.getSessionContext().getServerVersion()

// Attempt to reattach with "guest" user (limited permissions)
ServerLocator locator2 = ActiveMQClient.createServerLocator("tcp://localhost:61616")
locator2.setRetryInterval(100)
       .setRetryIntervalMultiplier(1)
       .setReconnectAttempts(-1)
       .setConfirmationWindowSize(1024 * 1024)

ClientSessionFactoryInternal sfReattaching = locator2.createSessionFactory()
RemotingConnectionImpl connection = sfReattaching.getConnection()

// Create a reattach session using the target session's info but with "guest" credentials
ActiveMQSessionContext context = new ActiveMQSessionContext(
    targetSessionName,
    sfReattaching.getConnection(),
    connection.getChannel(targetSessionChannel, 1024 * 1024),
    version,
    1024 * 1024
)

// Using reflection to introduce a fake session into the ClientSessionFactory::sessions
Constructor<ClientSessionImpl> constructor = ClientSessionImpl.class.getDeclaredConstructor(
    ClientSessionFactoryInternal.class,
    String.class,  // name
    String.class,  // username
    String.class,  // password
    boolean.class, // xa
    boolean.class, // autoCommitSends
    boolean.class, // autoCommitAcks
    boolean.class, // preAcknowledge
    boolean.class, // blockOnAcknowledge
    boolean.class, // autoGroup
    int.class,     // ackBatchSize
    int.class,     // consumerWindowSize
    int.class,     // consumerMaxRate
    int.class,     // confirmationWindowSize
    int.class,     // producerWindowSize
    int.class,     // producerMaxRate
    boolean.class, // blockOnNonDurableSend
    boolean.class, // blockOnDurableSend
    boolean.class, // cacheLargeMessageClient
    int.class,     // minLargeMessageSize
    boolean.class, // compressLargeMessages
    int.class,     // compressionLevel
    int.class,     // initialMessagePacketSize
    String.class,  // groupID
    int.class,     // onMessageCloseTimeout
    org.apache.activemq.artemis.spi.core.remoting.SessionContext.class, // sessionContext
    java.util.concurrent.Executor.class, // executor
    java.util.concurrent.Executor.class, // confirmationExecutor
    java.util.concurrent.Executor.class, // flowControlExecutor
    java.util.concurrent.Executor.class  // closeExecutor
)
constructor.setAccessible(true)

ClientSessionInternal reattachSession = constructor.newInstance(
    sfReattaching,
    targetSessionName,
    "guest",
    "guest",
    false, false, false, false, false, false,
    1, 1024, -1, -1, -1, -1,
    false, false, false,
    100 * 1024,
    false,
    1, 1024,
    null,
    60000,
    context,
    null, null, null, null
)

// Add session to factory's sessions list using reflection
ClientSessionFactoryImpl factoryImpl = (ClientSessionFactoryImpl) sfReattaching
Field sessionsField = ClientSessionFactoryImpl.class.getDeclaredField("sessions")
sessionsField.setAccessible(true)
def sessions = sessionsField.get(factoryImpl)
synchronized (sessions) {
    sessions.add(reattachSession)
}

// Fail the connection to trigger reattachment
connection.fail(new ActiveMQNotConnectedException())

// Waiting half second to make sure reconnect had a chance to happen
Thread.sleep(500)

String nonExistingQueue = "iDontExist"

// The session should not have permissions to create queues.
// if this succeeds it means the session is using wrong credentials
boolean securityExceptionThrown = false
try {
    reattachSession.createAddress(SimpleString.of(nonExistingQueue), RoutingType.ANYCAST, true)
    reattachSession.createQueue(QueueConfiguration.of(nonExistingQueue)
        .setAddress(nonExistingQueue)
        .setRoutingType(RoutingType.ANYCAST)
        .setDurable(true))
} catch (ActiveMQSecurityException e) {
    securityExceptionThrown = true
} catch (Exception e) {
    println "Unexpected exception: " + e.getMessage()
    e.printStackTrace()
    throw e
}

// Verify the session still works for permitted operations
ClientProducer producer = reattachSession.createProducer(address)
producer.send(reattachSession.createMessage(false))
reattachSession.commit()

ClientConsumer consumer = reattachSession.createConsumer(address)
reattachSession.start()
def receivedMessage = consumer.receive(5000)
boolean messageReceived = receivedMessage != null
reattachSession.commit()

targetSession.close()
sfTarget.close()
locator.close()

try {
    reattachSession.close()
} catch (Exception ignored) {
}
try {
    sfReattaching.close()
} catch (Exception ignored) {
}
try {
    locator2.close()
} catch (Exception ignored) {
}

// return good test conditions
return securityExceptionThrown && messageReceived
