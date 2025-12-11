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

import java.net.UnknownHostException;

import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;
import org.apache.activemq.artemis.logs.BundleFactory;
import org.w3c.dom.Node;

/**
 * Logger Codes 210000 - 218999
 */
@LogBundle(projectCode = "AMQ", regexID = "21[0-8][0-9]{3}", retiredIDs = {211001, 211002, 211003, 212000, 212006, 212029, 212074, 212078, 214012, 214023, 214024, 214026, 214027, 214028, 214029})
public interface ActiveMQClientLogger {

   ActiveMQClientLogger LOGGER = BundleFactory.newBundle(ActiveMQClientLogger.class, ActiveMQClientLogger.class.getPackage().getName());

   @LogMessage(id = 212001, value = "Error on clearing messages", level = LogMessage.Level.WARN)
   void errorClearingMessages(Throwable e);

   @LogMessage(id = 212002, value = "Timed out after waiting {}ms for handler to complete processing", level = LogMessage.Level.WARN)
   void timeOutWaitingForProcessing(long duration);

   @LogMessage(id = 212003, value = "Unable to close session", level = LogMessage.Level.WARN)
   void unableToCloseSession(Exception e);

   @LogMessage(id = 212004, value = "Failed to connect to server.", level = LogMessage.Level.WARN)
   void failedToConnectToServer();

   @LogMessage(id = 212005, value = "Tried {} times to connect. Now giving up on reconnecting it.", level = LogMessage.Level.WARN)
   void failedToConnectToServer(Integer reconnectAttempts);

   @LogMessage(id = 212007, value = "connector.create or connectorFactory.createConnector should never throw an exception, implementation is badly behaved, but we will deal with it anyway.", level = LogMessage.Level.WARN)
   void createConnectorException(Exception e);

   @LogMessage(id = 212008, value = "I am closing a core ClientSessionFactory you left open. Please make sure you close all ClientSessionFactories explicitly " + "before letting them go out of scope! {}", level = LogMessage.Level.WARN)
   void factoryLeftOpen(int i, Exception e);

   @LogMessage(id = 212009, value = "resetting session after failure", level = LogMessage.Level.WARN)
   void resettingSessionAfterFailure();

   @LogMessage(id = 212010, value = "Server is starting, retry to create the session {}", level = LogMessage.Level.WARN)
   void retryCreateSessionSeverStarting(String name);

   @LogMessage(id = 212011, value = "committing transaction after failover occurred, any non persistent messages may be lost", level = LogMessage.Level.WARN)
   void commitAfterFailover();

   @LogMessage(id = 212012, value = "failure occurred during commit throwing XAException", level = LogMessage.Level.WARN)
   void failoverDuringCommit();

   @LogMessage(id = 212014, value = "failover occurred during prepare rolling back", level = LogMessage.Level.WARN)
   void failoverDuringPrepareRollingBack();

   @LogMessage(id = 212015, value = "failover occurred during prepare rolling back", level = LogMessage.Level.WARN)
   void errorDuringPrepare(Throwable e);

   @LogMessage(id = 212016, value = "I am closing a core ClientSession you left open. Please make sure you close all ClientSessions explicitly before letting them go out of scope! {}", level = LogMessage.Level.WARN)
   void clientSessionNotClosed(int identity, Exception e);

   @LogMessage(id = 212017, value = "error adding packet", level = LogMessage.Level.WARN)
   void errorAddingPacket(Exception e);

   @LogMessage(id = 212018, value = "error calling cancel", level = LogMessage.Level.WARN)
   void errorCallingCancel(Exception e);

   @LogMessage(id = 212019, value = "error reading index", level = LogMessage.Level.WARN)
   void errorReadingIndex(Exception e);

   @LogMessage(id = 212020, value = "error setting index", level = LogMessage.Level.WARN)
   void errorSettingIndex(Exception e);

   @LogMessage(id = 212021, value = "error resetting index", level = LogMessage.Level.WARN)
   void errorReSettingIndex(Exception e);

   @LogMessage(id = 212022, value = "error reading LargeMessage file cache", level = LogMessage.Level.WARN)
   void errorReadingCache(Exception e);

   @LogMessage(id = 212023, value = "error closing LargeMessage file cache", level = LogMessage.Level.WARN)
   void errorClosingCache(Exception e);

   @LogMessage(id = 212024, value = "Exception during finalization for LargeMessage file cache", level = LogMessage.Level.WARN)
   void errorFinalisingCache(Exception e);

   @LogMessage(id = 212025, value = "did not connect the cluster connection to other nodes", level = LogMessage.Level.WARN)
   void errorConnectingToNodes(Exception e);

   @LogMessage(id = 212026, value = "Timed out waiting for pool to terminate", level = LogMessage.Level.WARN)
   void timedOutWaitingForTermination();

   @LogMessage(id = 212027, value = "Timed out waiting for scheduled pool to terminate", level = LogMessage.Level.WARN)
   void timedOutWaitingForScheduledPoolTermination();

   @LogMessage(id = 212028, value = "error starting server locator", level = LogMessage.Level.WARN)
   void errorStartingLocator(Exception e);


   @LogMessage(id = 212030, value = "error sending topology", level = LogMessage.Level.WARN)
   void errorSendingTopology(Throwable e);

   @LogMessage(id = 212031, value = "error sending topology", level = LogMessage.Level.WARN)
   void errorSendingTopologyNodedown(Throwable e);

   @LogMessage(id = 212032, value = "Timed out waiting to stop discovery thread", level = LogMessage.Level.WARN)
   void timedOutStoppingDiscovery();

   @LogMessage(id = 212033, value = "unable to send notification when discovery group is stopped", level = LogMessage.Level.WARN)
   void errorSendingNotifOnDiscoveryStop(Throwable e);

   @LogMessage(id = 212034, value = "There are more than one servers on the network broadcasting the same node id. You will see this message exactly once (per node) if a node is restarted, in which case it can be safely ignored. But if it is logged continuously it means you really do have more than one node on the same network active concurrently with the same node id. This could occur if you have a backup node active at the same time as its primary node. nodeID={}", level = LogMessage.Level.WARN)
   void multipleServersBroadcastingSameNode(String nodeId);

   @LogMessage(id = 212035, value = "error receiving packet in discovery", level = LogMessage.Level.WARN)
   void errorReceivingPacketInDiscovery(Throwable e);

   @LogMessage(id = 212036, value = "Can not find packet to clear: {} last received command id first stored command id {}", level = LogMessage.Level.WARN)
   void cannotFindPacketToClear(Integer lastReceivedCommandID, Integer firstStoredCommandID);

   @LogMessage(id = 212037, value = "{} connection failure to {} has been detected: {} [code={}]", level = LogMessage.Level.WARN)
   void connectionFailureDetected(String protocol, String remoteAddress, String message, ActiveMQExceptionType type);

   @LogMessage(id = 212038, value = "Failure in calling interceptor: {}", level = LogMessage.Level.WARN)
   void errorCallingInterceptor(Interceptor interceptor, Throwable e);

   @LogMessage(id = 212040, value = "Timed out waiting for netty ssl close future to complete", level = LogMessage.Level.WARN)
   void timeoutClosingSSL();

   @LogMessage(id = 212041, value = "Timed out waiting for netty channel to close", level = LogMessage.Level.WARN)
   void timeoutClosingNettyChannel();

   @LogMessage(id = 212042, value = "Timed out waiting for packet to be flushed", level = LogMessage.Level.WARN)
   void timeoutFlushingPacket();

   @LogMessage(id = 212043, value = "Property {} must be an Integer, it is {}", level = LogMessage.Level.WARN)
   void propertyNotInteger(String propName, String name);

   @LogMessage(id = 212044, value = "Property {} must be a Long, it is {}", level = LogMessage.Level.WARN)
   void propertyNotLong(String propName, String name);

   @LogMessage(id = 212045, value = "Property {} must be a Boolean, it is {}", level = LogMessage.Level.WARN)
   void propertyNotBoolean(String propName, String name);

   @LogMessage(id = 212046, value = "Cannot find activemq-version.properties on classpath: {}", level = LogMessage.Level.WARN)
   void noVersionOnClasspath(String classpath);

   @LogMessage(id = 212047, value = "Warning: JVM allocated more data what would make results invalid {}:{}", level = LogMessage.Level.WARN)
   void jvmAllocatedMoreMemory(Long totalMemory1, Long totalMemory2);

   @LogMessage(id = 212048, value = "Random address ({}) was already in use, trying another time", level = LogMessage.Level.WARN)
   void broadcastGroupBindErrorRetry(String hostAndPort, Throwable t);

   @LogMessage(id = 212049, value = "Could not bind to {} ({} address); " + "make sure your discovery group-address is of the same type as the IP stack (IPv4 or IPv6)." + "\nIgnoring discovery group-address, but this may lead to cross talking.", level = LogMessage.Level.WARN)
   void ioDiscoveryError(String hostAddress, String s);

   @LogMessage(id = 212050, value = "Compressed large message tried to read {} bytes from stream {}", level = LogMessage.Level.WARN)
   void compressedLargeMessageError(int length, int nReadBytes);

   @LogMessage(id = 212051, value = "Invalid concurrent session usage. Sessions are not supposed to be used by more than one thread concurrently.", level = LogMessage.Level.WARN)
   void invalidConcurrentSessionUsage(Throwable t);

   @LogMessage(id = 212052, value = "Packet {} was answered out of sequence due to a previous server timeout and it's being ignored", level = LogMessage.Level.WARN)
   void packetOutOfOrder(Object obj, Throwable t);

   /**
    * Warns about usage of {@link org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler} or JMS's
    * {@code CompletionWindow} with confirmations disabled (confirmationWindowSize=-1).
    */
   @LogMessage(id = 212053, value = "CompletionListener/SendAcknowledgementHandler used with confirmationWindowSize=-1. Enable confirmationWindowSize to receive acks from server!", level = LogMessage.Level.WARN)
   void confirmationWindowDisabledWarning();

   @LogMessage(id = 212054, value = "Destination address={} is blocked. If the system is configured to block make sure you consume messages on this configuration.", level = LogMessage.Level.WARN)
   void outOfCreditOnFlowControl(String address);

   @LogMessage(id = 212055, value = "Unable to close consumer", level = LogMessage.Level.WARN)
   void unableToCloseConsumer(Exception e);

   @LogMessage(id = 212056, value = "local-bind-address specified for broadcast group but no local-bind-port. Using random port for UDP Broadcast ({})", level = LogMessage.Level.WARN)
   void broadcastGroupBindError(String hostAndPort);

   @LogMessage(id = 212057, value = "Large Message Streaming is taking too long to flush on back pressure.", level = LogMessage.Level.WARN)
   void timeoutStreamingLargeMessage();

   @LogMessage(id = 212058, value = "Unable to get a message.", level = LogMessage.Level.WARN)
   void unableToGetMessage(Exception e);

   @LogMessage(id = 212059, value = "Failed to clean up: {} ", level = LogMessage.Level.WARN)
   void failedCleaningUp(String target);

   @LogMessage(id = 212060, value = "Unexpected null data received from DiscoveryEndpoint ", level = LogMessage.Level.WARN)
   void unexpectedNullDataReceived();

   @LogMessage(id = 212061, value = "Failed to perform force close ", level = LogMessage.Level.WARN)
   void failedForceClose(Throwable e);

   @LogMessage(id = 212062, value = "Failed to perform post actions on message processing ", level = LogMessage.Level.WARN)
   void failedPerformPostActionsOnMessage(Exception e);

   @LogMessage(id = 212063, value = "Unable to handle connection failure ", level = LogMessage.Level.WARN)
   void unableToHandleConnectionFailure(Throwable e);

   @LogMessage(id = 212064, value = "Unable to receive cluster topology ", level = LogMessage.Level.WARN)
   void unableToReceiveClusterTopology(Throwable e);

   @LogMessage(id = 212065, value = "{} getting exception when receiving broadcasting ", level = LogMessage.Level.WARN)
   void unableToReceiveBroadcast(String target, Exception e);

   @LogMessage(id = 212066, value = "failed to parse int property ", level = LogMessage.Level.WARN)
   void unableToParseValue(Throwable e);

   @LogMessage(id = 212067, value = "failed to get system property ", level = LogMessage.Level.WARN)
   void unableToGetProperty(Throwable e);

   @LogMessage(id = 212068, value = "Couldn't finish the client globalThreadPool in less than 10 seconds, interrupting it now ", level = LogMessage.Level.WARN)
   void unableToProcessGlobalThreadPoolIn10Sec();

   @LogMessage(id = 212069, value = "Couldn't finish the client scheduled in less than 10 seconds, interrupting it now ", level = LogMessage.Level.WARN)
   void unableToProcessScheduledlIn10Sec();

   @LogMessage(id = 212070, value = "Unable to initialize VersionLoader ", level = LogMessage.Level.WARN)
   void unableToInitVersionLoader(Throwable e);

   @LogMessage(id = 212071, value = "Unable to check Epoll availability ", level = LogMessage.Level.WARN)
   void unableToCheckEpollAvailability(Throwable e);

   @LogMessage(id = 212072, value = "Failed to change channel state to ReadyForWriting ", level = LogMessage.Level.WARN)
   void failedToSetChannelReadyForWriting(Throwable e);

   @LogMessage(id = 212073, value = "Unable to check KQueue availability ", level = LogMessage.Level.WARN)
   void unableToCheckKQueueAvailability(Throwable e);

   @LogMessage(id = 212075, value = "KQueue is not available, please add to the classpath or configure useKQueue=false to remove this warning", level = LogMessage.Level.WARN)
   void unableToCheckKQueueAvailabilityNoClass();

   @LogMessage(id = 212076, value = "Epoll is not available, please add to the classpath or configure useEpoll=false to remove this warning", level = LogMessage.Level.WARN)
   void unableToCheckEpollAvailabilitynoClass();

   @LogMessage(id = 212077, value = "Timed out waiting to receive initial broadcast from cluster. Retry {} of {}", level = LogMessage.Level.WARN)
   void broadcastTimeout(int retry, int maxretry);

   @LogMessage(id = 212079, value = "The upstream connector from the downstream federation will ignore url parameter {}", level = LogMessage.Level.WARN)
   void ignoredParameterForDownstreamFederation(String name);

   @LogMessage(id = 212080, value = "Using legacy SSL store provider value: {}. Please use either 'keyStoreType' or 'trustStoreType' instead as appropriate.", level = LogMessage.Level.WARN)
   void oldStoreProvider(String value);

   @LogMessage(id = 212081, value = "Soft failure checking the certificate [{}]: {}", level = LogMessage.Level.WARN)
   void softFailException(String certSubject, Exception e);

   @LogMessage(id = 214000, value = "Failed to call onMessage", level = LogMessage.Level.ERROR)
   void onMessageError(Throwable e);

   @LogMessage(id = 214001, value = "failed to cleanup session", level = LogMessage.Level.ERROR)
   void failedToCleanupSession(Exception e);

   @LogMessage(id = 214002, value = "Failed to execute failure listener", level = LogMessage.Level.ERROR)
   void failedToExecuteListener(Throwable t);

   @LogMessage(id = 214003, value = "Failed to handle failover", level = LogMessage.Level.ERROR)
   void failedToHandleFailover(Throwable t);

   @LogMessage(id = 214004, value = "XA end operation failed ", level = LogMessage.Level.ERROR)
   void errorCallingEnd(Throwable t);

   @LogMessage(id = 214005, value = "XA start operation failed {} code:{}", level = LogMessage.Level.ERROR)
   void errorCallingStart(String message, Integer code);

   @LogMessage(id = 214006, value = "Session is not XA", level = LogMessage.Level.ERROR)
   void sessionNotXA();

   @LogMessage(id = 214007, value = "Received exception asynchronously from server", level = LogMessage.Level.ERROR)
   void receivedExceptionAsynchronously(Exception e);

   @LogMessage(id = 214008, value = "Failed to handle packet", level = LogMessage.Level.ERROR)
   void failedToHandlePacket(Exception e);

   @LogMessage(id = 214009, value = "Failed to stop discovery group", level = LogMessage.Level.ERROR)
   void failedToStopDiscovery(Throwable e);

   @LogMessage(id = 214010, value = "Failed to receive datagram", level = LogMessage.Level.ERROR)
   void failedToReceiveDatagramInDiscovery(Throwable e);

   @LogMessage(id = 214011, value = "Failed to call discovery listener", level = LogMessage.Level.ERROR)
   void failedToCallListenerInDiscovery(Throwable e);

   @LogMessage(id = 214013, value = "Failed to decode packet", level = LogMessage.Level.ERROR)
   void errorDecodingPacket(Throwable e);

   @LogMessage(id = 214014, value = "Failed to execute failure listener", level = LogMessage.Level.ERROR)
   void errorCallingFailureListener(Throwable e);

   @LogMessage(id = 214015, value = "Failed to execute connection life cycle listener", level = LogMessage.Level.ERROR)
   void errorCallingLifeCycleListener(Throwable e);

   @LogMessage(id = 214016, value = "Failed to create netty connection", level = LogMessage.Level.ERROR)
   void errorCreatingNettyConnection(Throwable e);

   @LogMessage(id = 214017, value = "Caught unexpected Throwable", level = LogMessage.Level.ERROR)
   void caughtunexpectedThrowable(Throwable t);

   @LogMessage(id = 214018, value = "Failed to invoke getTextContent() on node {}", level = LogMessage.Level.ERROR)
   void errorOnXMLTransform(Node n, Throwable t);

   @LogMessage(id = 214019, value = "Invalid configuration", level = LogMessage.Level.ERROR)
   void errorOnXMLTransformInvalidConf(Throwable t);

   @LogMessage(id = 214020, value = "Exception happened while stopping Discovery BroadcastEndpoint {}", level = LogMessage.Level.ERROR)
   void errorStoppingDiscoveryBroadcastEndpoint(Object endpoint, Throwable t);

   @LogMessage(id = 214021, value = "Invalid cipher suite specified. Supported cipher suites are: {}", level = LogMessage.Level.ERROR)
   void invalidCipherSuite(String validSuites);

   @LogMessage(id = 214022, value = "Invalid protocol specified. Supported protocols are: {}", level = LogMessage.Level.ERROR)
   void invalidProtocol(String validProtocols);

   @LogMessage(id = 214025, value = "Invalid type {}, Using default connection factory at {}", level = LogMessage.Level.ERROR)
   void invalidCFType(String type, String uri);

   @LogMessage(id = 214030, value = "Failed to bind {}={}", level = LogMessage.Level.ERROR)
   void failedToBind(String p1, String p2, Throwable cause);

   @LogMessage(id = 214031, value = "Failed to decode buffer, disconnect immediately.", level = LogMessage.Level.ERROR)
   void disconnectOnErrorDecoding(Throwable cause);

   @LogMessage(id = 214032, value = "Unable to initialize VersionLoader ", level = LogMessage.Level.ERROR)
   void unableToInitVersionLoaderError(Throwable e);

   @LogMessage(id = 214033, value = "Cannot resolve host ", level = LogMessage.Level.ERROR)
   void unableToResolveHost(UnknownHostException e);

   @LogMessage(id = 214034, value = "{} has negative counts {}\n{}", level = LogMessage.Level.ERROR)
   void negativeRefCount(String message, String count, String debugString);

   @LogMessage(id = 214035, value = "Couldn't finish the client globalFlowControlThreadPool in less than 10 seconds, interrupting it now", level = LogMessage.Level.WARN)
   void unableToProcessGlobalFlowControlThreadPoolIn10Sec();

   @LogMessage(id = 214036, value = "Connection closure to {} has been detected: {} [code={}]", level = LogMessage.Level.INFO)
   void connectionClosureDetected(String remoteAddress, String message, ActiveMQExceptionType type);

   @LogMessage(id = 214037, value = "Unable to check IoUring availability ", level = LogMessage.Level.WARN)
   void unableToCheckIoUringAvailability(Throwable e);

   @LogMessage(id = 214038, value = "IoUring is not available, please add to the classpath or configure useIoUring=false to remove this warning", level = LogMessage.Level.WARN)
   void unableToCheckIoUringAvailabilitynoClass();
}
