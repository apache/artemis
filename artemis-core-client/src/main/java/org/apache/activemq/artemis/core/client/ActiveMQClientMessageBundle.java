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
package org.apache.activemq.artemis.core.client;

import org.apache.activemq.artemis.api.core.ActiveMQAddressFullException;
import org.apache.activemq.artemis.api.core.ActiveMQConnectionTimedOutException;
import org.apache.activemq.artemis.api.core.ActiveMQDisconnectedException;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.ActiveMQInterceptorRejectedPacketException;
import org.apache.activemq.artemis.api.core.ActiveMQInternalErrorException;
import org.apache.activemq.artemis.api.core.ActiveMQInterruptedException;
import org.apache.activemq.artemis.api.core.ActiveMQLargeMessageException;
import org.apache.activemq.artemis.api.core.ActiveMQLargeMessageInterruptedException;
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException;
import org.apache.activemq.artemis.api.core.ActiveMQRoutingException;
import org.apache.activemq.artemis.api.core.ActiveMQTransactionOutcomeUnknownException;
import org.apache.activemq.artemis.api.core.ActiveMQTransactionRolledBackException;
import org.apache.activemq.artemis.api.core.ActiveMQUnBlockedException;
import org.apache.activemq.artemis.core.cluster.DiscoveryGroup;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.Message;
import org.apache.activemq.artemis.logs.BundleFactory;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.w3c.dom.Node;

/**
 * Logger Codes 219000 - 219999
 */
@LogBundle(projectCode = "AMQ", regexID = "219[0-9]{3}", retiredIDs = {219002, 219005, 219034, 219037, 219063})
public interface ActiveMQClientMessageBundle {

   ActiveMQClientMessageBundle BUNDLE = BundleFactory.newBundle(ActiveMQClientMessageBundle.class);

   @Message(id = 219000, value = "ClientSession closed while creating session")
   ActiveMQInternalErrorException clientSessionClosed();

   @Message(id = 219001, value = "Failed to create session")
   ActiveMQInternalErrorException failedToCreateSession(Throwable t);

   @Message(id = 219003, value = "Queue can not be both durable and temporary")
   ActiveMQInternalErrorException queueMisConfigured();

   @Message(id = 219004, value = "Failed to initialise session factory")
   ActiveMQInternalErrorException failedToInitialiseSessionFactory(Exception e);

   @Message(id = 219006, value = "Channel disconnected")
   ActiveMQNotConnectedException channelDisconnected();

   @Message(id = 219007, value = "Cannot connect to server(s). Tried with all available servers.")
   ActiveMQNotConnectedException cannotConnectToServers();

   @Message(id = 219008, value = "Failed to connect to any static connectors")
   ActiveMQNotConnectedException cannotConnectToStaticConnectors(Exception e);

   @Message(id = 219009, value = "Failed to connect to any static connectors")
   ActiveMQNotConnectedException cannotConnectToStaticConnectors2();

   @Message(id = 219010, value = "Connection is destroyed")
   ActiveMQNotConnectedException connectionDestroyed();

   @Message(id = 219011, value = "Did not receive data from server for {}")
   ActiveMQConnectionTimedOutException connectionTimedOut(Connection transportConnection);

   @Message(id = 219012, value = "Timed out waiting to receive initial broadcast from cluster")
   ActiveMQConnectionTimedOutException connectionTimedOutInInitialBroadcast();

   @Message(id = 219013, value = "Timed out waiting to receive cluster topology. Group:{}")
   ActiveMQConnectionTimedOutException connectionTimedOutOnReceiveTopology(DiscoveryGroup discoveryGroup);

   @Message(id = 219014, value = "Timed out after waiting {} ms for response when sending packet {}")
   ActiveMQConnectionTimedOutException timedOutSendingPacket(long timeout, Byte type);

   @Message(id = 219015, value = "The connection was disconnected because of server shutdown")
   ActiveMQDisconnectedException disconnected();

   @Message(id = 219016, value = "Connection failure detected. Unblocking a blocking call that will never get a response")
   ActiveMQUnBlockedException unblockingACall(Throwable t);

   @Message(id = 219017, value = "Consumer is closed")
   ActiveMQObjectClosedException consumerClosed();

   @Message(id = 219018, value = "Producer is closed")
   ActiveMQObjectClosedException producerClosed();

   @Message(id = 219019, value = "Session is closed")
   ActiveMQObjectClosedException sessionClosed();

   @Message(id = 219020, value = "Cannot call receive(...) - a MessageHandler is set")
   ActiveMQIllegalStateException messageHandlerSet();

   @Message(id = 219021, value = "Cannot set MessageHandler - consumer is in receive(...)")
   ActiveMQIllegalStateException inReceive();

   @Message(id = 219022, value = "Header size ({}) is too big, use the messageBody for large data, or increase minLargeMessageSize")
   ActiveMQIllegalStateException headerSizeTooBig(Integer headerSize);

   @Message(id = 219023, value = "The large message lost connection with its session, either because of a rollback or a closed session")
   ActiveMQIllegalStateException largeMessageLostSession();

   @Message(id = 219024, value = "Could not select a TransportConfiguration to create SessionFactory")
   ActiveMQIllegalStateException noTCForSessionFactory();

   @Message(id = 219025, value = "Error saving the message body")
   ActiveMQLargeMessageException errorSavingBody(Exception e);

   @Message(id = 219026, value = "Error reading the LargeMessageBody")
   ActiveMQLargeMessageException errorReadingBody(Exception e);

   @Message(id = 219027, value = "Error closing stream from LargeMessageBody")
   ActiveMQLargeMessageException errorClosingLargeMessage(Exception e);

   @Message(id = 219028, value = "Timeout waiting for LargeMessage Body")
   ActiveMQLargeMessageException timeoutOnLargeMessage();

   @Message(id = 219029, value = "Error writing body of message")
   ActiveMQLargeMessageException errorWritingLargeMessage(Exception e);

   @Message(id = 219030, value = "The transaction was rolled back on failover to a backup server")
   ActiveMQTransactionRolledBackException txRolledBack();

   @Message(id = 219031, value = "The transaction was rolled back on failover however commit may have been successful")
   ActiveMQTransactionOutcomeUnknownException txOutcomeUnknown();

   @Message(id = 219032, value = "Invalid type: {}")
   IllegalArgumentException invalidType(Object type);

   @Message(id = 219033, value = "Invalid type: {}")
   IllegalArgumentException invalidEncodeType(Object type);

   @Message(id = 219035, value = "Invalid window size {}")
   IllegalArgumentException invalidWindowSize(Integer size);

   @Message(id = 219038, value = "Cannot find channel with id {} to close")
   IllegalArgumentException noChannelToClose(Long id);

   @Message(id = 219039, value = "Close Listener cannot be null")
   IllegalArgumentException closeListenerCannotBeNull();

   @Message(id = 219040, value = "Fail Listener cannot be null")
   IllegalArgumentException failListenerCannotBeNull();

   @Message(id = 219041, value = "Connection already exists with id {}")
   IllegalArgumentException connectionExists(Object id);

   @Message(id = 219042, value = "Invalid argument null listener")
   IllegalArgumentException nullListener();

   @Message(id = 219043, value = "Invalid argument null handler")
   IllegalArgumentException nullHandler();

   @Message(id = 219045, value = "the first node to be compared is null")
   IllegalArgumentException firstNodeNull();

   @Message(id = 219046, value = "the second node to be compared is null")
   IllegalArgumentException secondNodeNull();

   @Message(id = 219047, value = "nodes have different node names")
   IllegalArgumentException nodeHaveDifferentNames();

   @Message(id = 219048, value = "nodes have a different number of attributes")
   IllegalArgumentException nodeHaveDifferentAttNumber();

   @Message(id = 219049, value = "attribute {}={} does not match")
   IllegalArgumentException attsDontMatch(String name, String value);

   @Message(id = 219050, value = "one node has children and the other does not")
   IllegalArgumentException oneNodeHasChildren();

   @Message(id = 219051, value = "nodes have a different number of children")
   IllegalArgumentException nodeHasDifferentChildNumber();

   @Message(id = 219052, value = "Element {} requires a valid Boolean value, but '{}' cannot be parsed as a Boolean")
   IllegalArgumentException mustBeBoolean(Node elem, String value);

   @Message(id = 219053, value = "Element {} requires a valid Double value, but '{}' cannot be parsed as a Double")
   IllegalArgumentException mustBeDouble(Node elem, String value);

   @Message(id = 219054, value = "Element {} requires a valid Integer value, but '{}' cannot be parsed as an Integer")
   IllegalArgumentException mustBeInteger(Node elem, String value);

   @Message(id = 219055, value = "Element {} requires a valid Long value, but '{}' cannot be parsed as a Long")
   IllegalArgumentException mustBeLong(Node element, String value);

   @Message(id = 219057, value = "Error decoding password")
   IllegalArgumentException errordecodingPassword(Exception e);

   @Message(id = 219058, value = "Address \"{}\" is full. Message encode size = {}B")
   ActiveMQAddressFullException addressIsFull(String addressName, int size);

   @Message(id = 219059, value = "Interceptor {} rejected packet in a blocking call. This call will never complete.")
   ActiveMQInterceptorRejectedPacketException interceptorRejectedPacket(String interceptionResult);

   @Message(id = 219060, value = "Large Message Transmission interrupted on consumer shutdown.")
   ActiveMQLargeMessageInterruptedException largeMessageInterrupted();

   @Message(id = 219061, value = "Cannot send a packet while channel is failing over.")
   IllegalStateException cannotSendPacketDuringFailover();

   @Message(id = 219062, value = "Multi-packet transmission (e.g. Large Messages) interrupted because of a reconnection.")
   ActiveMQInterruptedException packetTransmissionInterrupted();

   @Message(id = 219064, value = "Invalide packet: {}")
   IllegalStateException invalidPacket(byte type);

   @Message(id = 219065, value = "Failed to handle packet.")
   RuntimeException failedToHandlePacket(Exception e);

   @Message(id = 219066, value = "The connection was redirected")
   ActiveMQRoutingException redirected();

   @Message(id = 219067, value = "Keystore alias {} not found in {}")
   IllegalArgumentException keystoreAliasNotFound(String keystoreAlias, String keystorePath);

   @Message(id = 219068, value = "Connection closed while receiving cluster topology. Group:{}")
   ActiveMQObjectClosedException connectionClosedOnReceiveTopology(DiscoveryGroup discoveryGroup);

   @Message(id = 219069, value = "Unable to create Session. Either the ClientSessionFactory is closed or the ClientProtocolManager is dead.")
   IllegalStateException unableToCreateSession();

   @Message(id = 219070, value = "The server discovery is disabled by default. See the Server Discovery section in the user manual for security requirements and enablement.")
   IllegalStateException serverDiscoveryDisabled();
}
