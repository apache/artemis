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
package org.apache.activemq.artemis.core.protocol.stomp;

import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.Message;
import org.apache.activemq.artemis.logs.BundleFactory;

/**
 * Logger Codes 339000 - 339999
 */
@LogBundle(projectCode = "AMQ", regexID = "339[0-9]{3}", retiredIDs = {339041})
public interface ActiveMQStompProtocolMessageBundle {

   ActiveMQStompProtocolMessageBundle BUNDLE = BundleFactory.newBundle(ActiveMQStompProtocolMessageBundle.class);

   @Message(id = 339000, value = "Stomp Connection TTL cannot be negative: {}")
   IllegalStateException negativeConnectionTTL(Long ttl);

   @Message(id = 339001, value = "Destination does not exist: {}")
   ActiveMQStompException destinationNotExist(String destination);

   @Message(id = 339002, value = "Stomp versions not supported: {}")
   ActiveMQStompException versionNotSupported(String acceptVersion);

   @Message(id = 339003, value = "Header host is null")
   ActiveMQStompException nullHostHeader();

   @Message(id = 339004, value = "Cannot accept null as host")
   String hostCannotBeNull();

   @Message(id = 339005, value = "Header host does not match server host")
   ActiveMQStompException hostNotMatch();

   @Message(id = 339006, value = "host {} does not match server host name")
   String hostNotMatchDetails(String host);

   @Message(id = 339007, value = "Connection was destroyed.")
   ActiveMQStompException connectionDestroyed();

   @Message(id = 339008, value = "Connection has not been established.")
   ActiveMQStompException connectionNotEstablished();

   @Message(id = 339009, value = "Exception getting session")
   ActiveMQStompException errorGetSession(Exception e);

   @Message(id = 339010, value = "Connection is not valid.")
   ActiveMQStompException invalidConnection();

   @Message(id = 339011, value = "Error sending message {}")
   ActiveMQStompException errorSendMessage(org.apache.activemq.artemis.api.core.Message message, Exception e);

   @Message(id = 339012, value = "Error beginning a transaction {}")
   ActiveMQStompException errorBeginTx(String txID, Exception e);

   @Message(id = 339013, value = "Error committing {}")
   ActiveMQStompException errorCommitTx(String txID, Exception e);

   @Message(id = 339014, value = "Error aborting {}")
   ActiveMQStompException errorAbortTx(String txID, Exception e);

   @Message(id = 339015, value = "Client must set destination or id header to a SUBSCRIBE command")
   ActiveMQStompException noDestination();

   @Message(id = 339016, value = "Error creating subscription {}")
   ActiveMQStompException errorCreatingSubscription(String subscriptionID, Exception e);

   @Message(id = 339017, value = "Error unsubscribing {}")
   ActiveMQStompException errorUnsubscribing(String subscriptionID, Exception e);

   @Message(id = 339018, value = "Error acknowledging message {}")
   ActiveMQStompException errorAck(String messageID, Exception e);

   @Message(id = 339019, value = "Invalid char sequence: two consecutive CRs.")
   ActiveMQStompException invalidTwoCRs();

   @Message(id = 339020, value = "Invalid char sequence: There is a CR not followed by an LF")
   ActiveMQStompException badCRs();

   @Message(id = 339021, value = "Expect new line char but is {}")
   ActiveMQStompException notValidNewLine(byte b);

   @Message(id = 339022, value = "Expect new line char but is {}")
   String unexpectedNewLine(byte b);

   @Message(id = 339023, value = "Invalid STOMP frame: {}")
   ActiveMQStompException invalidCommand(String dumpByteArray);

   @Message(id = 339024, value = "Invalid STOMP frame: {}")
   String invalidFrame(String dumpByteArray);

   @Message(id = 339025, value = "failed to ack because no message with id: {}")
   ActiveMQStompException failToAckMissingID(String id);

   @Message(id = 339026, value = "subscription id {} does not match {}")
   ActiveMQStompException subscriptionIDMismatch(String subscriptionID, String actualID);

   @Message(id = 339027, value = "Cannot create a subscriber on the durable subscription if the client-id of the connection is not set")
   IllegalStateException missingClientID();

   @Message(id = 339028, value = "Message header too big, increase minLargeMessageSize please.")
   Exception headerTooBig();

   @Message(id = 339029, value = "Unsupported command: {}")
   ActiveMQStompException unknownCommand(String command);

   @Message(id = 339030, value = "transaction header is mandatory to COMMIT a transaction")
   ActiveMQStompException needTxIDHeader();

   @Message(id = 339031, value = "Error handling send")
   ActiveMQStompException errorHandleSend(Exception e);

   @Message(id = 339032, value = "Need a transaction id to begin")
   ActiveMQStompException beginTxNoID();

   @Message(id = 339033, value = "transaction header is mandatory to ABORT a transaction")
   ActiveMQStompException abortTxNoID();

   @Message(id = 339034, value = "This method should not be called")
   IllegalStateException invalidCall();

   @Message(id = 339035, value = "Must specify the subscription's id or the destination you are unsubscribing from")
   ActiveMQStompException needIDorDestination();

   @Message(id = 339037, value = "Must specify the subscription's id")
   ActiveMQStompException needSubscriptionID();

   @Message(id = 339039, value = "No id header in ACK/NACK frame.")
   ActiveMQStompException noIDInAck();

   @Message(id = 339040, value = "Undefined escape sequence: {}")
   ActiveMQStompException undefinedEscapeSequence(String sequence);
}
