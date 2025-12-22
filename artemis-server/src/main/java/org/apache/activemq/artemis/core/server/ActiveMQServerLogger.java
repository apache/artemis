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
package org.apache.activemq.artemis.core.server;

import javax.naming.NamingException;
import javax.transaction.xa.Xid;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.netty.channel.Channel;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.client.impl.Topology;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.BackupReplicationStartFailedMessage;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.cluster.Bridge;
import org.apache.activemq.artemis.core.server.cluster.impl.BridgeImpl;
import org.apache.activemq.artemis.core.server.cluster.impl.ClusterConnectionImpl;
import org.apache.activemq.artemis.core.server.cluster.quorum.ServerConnectVote;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.ServerSessionImpl;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.core.server.routing.targets.Target;
import org.apache.activemq.artemis.logs.BundleFactory;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;
import org.apache.activemq.artemis.spi.core.remoting.Connection;

/**
 * Logger Codes 220000 - 228999
 */
@LogBundle(projectCode = "AMQ", regexID = "22[0-8][0-9]{3}", retiredIDs = {221026, 221052, 222003, 222012, 222015, 222020, 222021, 222022, 222024, 222027, 222028, 222029, 222048, 222052, 222058, 222064, 222071, 222078, 222079, 222083, 222084, 222088, 222090, 222102, 222105, 222110, 222128, 222134, 222135, 222152, 222159, 222163, 222167, 222170, 222171, 222182, 222190, 222192, 222193, 222204, 222252, 222255, 222257, 222259, 222260, 222276, 222277, 222288, 224001, 224002, 224003, 224005, 224013, 224031, 224035, 224070, 224100, 224121})
public interface ActiveMQServerLogger {

   // Note: logger ID 224127 uses "org.apache.activemq.artemis.core.server.Queue" for its logger category, rather than ActiveMQServerLogger.class.getPackage().getName()

   ActiveMQServerLogger LOGGER = BundleFactory.newBundle(ActiveMQServerLogger.class, ActiveMQServerLogger.class.getPackage().getName());

   @LogMessage(id = 223000, value = "Received Interrupt Exception whilst waiting for component to shutdown: {}", level = LogMessage.Level.DEBUG)
   void interruptWhilstStoppingComponent(String componentClassName);

   @LogMessage(id = 223001, value = "Ignored quorum vote due to quorum reached or vote casted: {}", level = LogMessage.Level.DEBUG)
   void ignoredQuorumVote(ServerConnectVote vote);

   @LogMessage(id = 221000, value = "{} message broker is starting with configuration {}", level = LogMessage.Level.INFO)
   void serverStarting(String type, Configuration configuration);

   @LogMessage(id = 221001, value = "Apache Artemis Message Broker version {} [{}, nodeID={}] {}", level = LogMessage.Level.INFO)
   void serverStarted(String fullVersion, String name, SimpleString nodeId, String identity);

   @LogMessage(id = 221002, value = "Apache Artemis Message Broker version {} [{}] stopped, uptime {}", level = LogMessage.Level.INFO)
   void serverStopped(String version, SimpleString nodeId, String uptime);

   @LogMessage(id = 221003, value = "Deploying {} queue {} on address {}", level = LogMessage.Level.INFO)
   void deployQueue(String routingType, String queueName, String addressName);

   @LogMessage(id = 221004, value = "{}", level = LogMessage.Level.INFO)
   void dumpServerInfo(String serverInfo);

   @LogMessage(id = 221005, value = "Deleting pending large message as it was not completed: {}", level = LogMessage.Level.INFO)
   void deletingPendingMessage(Pair<Long, Long> msgToDelete);

   @LogMessage(id = 221006, value = "Waiting to obtain primary lock", level = LogMessage.Level.INFO)
   void awaitingPrimaryLock();

   @LogMessage(id = 221007, value = "Server is now active", level = LogMessage.Level.INFO)
   void serverIsActive();

   @LogMessage(id = 221008, value = "primary server wants to restart, restarting server in backup", level = LogMessage.Level.INFO)
   void awaitFailBack();

   @LogMessage(id = 221109, value = "Apache Artemis Backup Server version {} [{}] started; waiting for primary to fail before activating", level = LogMessage.Level.INFO)
   void backupServerStarted(String version, SimpleString nodeID);

   @LogMessage(id = 221010, value = "Backup Server is now active", level = LogMessage.Level.INFO)
   void backupServerIsActive();

   @LogMessage(id = 221011, value = "Server {} is now active", level = LogMessage.Level.INFO)
   void serverIsActive(String identity);

   @LogMessage(id = 221012, value = "Using AIO Journal", level = LogMessage.Level.INFO)
   void journalUseAIO();

   @LogMessage(id = 221013, value = "Using NIO Journal", level = LogMessage.Level.INFO)
   void journalUseNIO();

   @LogMessage(id = 221014, value = "{}% loaded", level = LogMessage.Level.INFO)
   void percentLoaded(Long percent);

   @LogMessage(id = 221015, value = "Can not find queue {} while reloading ACKNOWLEDGE_CURSOR, deleting record now", level = LogMessage.Level.INFO)
   void journalCannotFindQueueReloading(Long queueID);

   @LogMessage(id = 221016, value = "Can not find queue {} while reloading PAGE_CURSOR_COUNTER_VALUE, deleting record now", level = LogMessage.Level.INFO)
   void journalCannotFindQueueReloadingPage(Long queueID);

   @LogMessage(id = 221017, value = "Can not find queue {} while reloading PAGE_CURSOR_COUNTER_INC, deleting record now", level = LogMessage.Level.INFO)
   void journalCannotFindQueueReloadingPageCursor(Long queueID);

   @LogMessage(id = 221018, value = "Large message: {} did not have any associated reference, file will be deleted", level = LogMessage.Level.INFO)
   void largeMessageWithNoRef(Long messageID);

   @LogMessage(id = 221019, value = "Deleting unreferenced message id={} from the journal", level = LogMessage.Level.INFO)
   void journalUnreferencedMessage(Long messageID);

   @LogMessage(id = 221020, value = "Started {} Acceptor at {}:{} for protocols [{}]", level = LogMessage.Level.INFO)
   void startedAcceptor(String acceptorType, String host, int port, String enabledProtocols);

   @LogMessage(id = 221021, value = "failed to remove connection", level = LogMessage.Level.INFO)
   void errorRemovingConnection();

   @LogMessage(id = 221022, value = "unable to start connector service: {}", level = LogMessage.Level.INFO)
   void errorStartingConnectorService(String name, Throwable e);

   @LogMessage(id = 221023, value = "unable to stop connector service: {}", level = LogMessage.Level.INFO)
   void errorStoppingConnectorService(String name, Throwable e);

   @LogMessage(id = 221024, value = "Backup server {} is synchronized with primary server, nodeID={}.", level = LogMessage.Level.INFO)
   void backupServerSynchronized(ActiveMQServerImpl server, String nodeID);

   @LogMessage(id = 221025, value = "Replication: sending {} (size={}) to replica, origin={}", level = LogMessage.Level.INFO)
   void replicaSyncFile(SequentialFile jf, Long size, Object identification);

   @LogMessage(id = 221027, value = "Bridge {} is connected", level = LogMessage.Level.INFO)
   void bridgeConnected(BridgeImpl name);

   @LogMessage(id = 221028, value = "Bridge is {}, will not retry", level = LogMessage.Level.INFO)
   void bridgeWillNotRetry(String operation);

   @LogMessage(id = 221029, value = "Stopped bridge {}", level = LogMessage.Level.INFO)
   void bridgeStopped(String name);

   @LogMessage(id = 221030, value = "Paused bridge {}", level = LogMessage.Level.INFO)
   void bridgePaused(String name);

   @LogMessage(id = 221031, value = "backup announced", level = LogMessage.Level.INFO)
   void backupAnnounced();

   @LogMessage(id = 221032, value = "Waiting to become backup node", level = LogMessage.Level.INFO)
   void waitingToBecomeBackup();

   @LogMessage(id = 221033, value = "** got backup lock", level = LogMessage.Level.INFO)
   void gotBackupLock();

   @LogMessage(id = 221034, value = "Waiting {} to obtain primary lock", level = LogMessage.Level.INFO)
   void waitingToObtainPrimaryLock(String timeoutMessage);

   @LogMessage(id = 221035, value = "Primary Server Obtained primary lock", level = LogMessage.Level.INFO)
   void obtainedPrimaryLock();

   @LogMessage(id = 221036, value = "Message with duplicate ID {} was already set at {}. Move from {} being ignored and message removed from {}", level = LogMessage.Level.INFO)
   void messageWithDuplicateID(Object duplicateProperty,
                               SimpleString toAddress,
                               SimpleString address,
                               SimpleString simpleString);

   @LogMessage(id = 221037, value = "{} to become 'active'", level = LogMessage.Level.INFO)
   void becomingActive(ActiveMQServer server);

   @LogMessage(id = 221038, value = "Configuration option '{}' is deprecated and will be removed in a future version. Use '{}' instead. Consult the manual for details.", level = LogMessage.Level.INFO)
   void deprecatedConfigurationOption(String deprecatedOption, String alternative);

   @LogMessage(id = 221039, value = "Restarting as replicating backup server after primary restart", level = LogMessage.Level.INFO)
   void restartingReplicatedBackupAfterFailback();

   @LogMessage(id = 221040, value = "Remote group coordinators has not started.", level = LogMessage.Level.INFO)
   void remoteGroupCoordinatorsNotStarted();

   @LogMessage(id = 221041, value = "Cannot find queue {} while reloading PAGE_CURSOR_COMPLETE, deleting record now", level = LogMessage.Level.INFO)
   void cantFindQueueOnPageComplete(long queueID);

   @LogMessage(id = 221042, value = "{} bridge {} timed out waiting for the send acknowledgement of {} messages. Messages may be duplicated between the bridge's source and the target.", level = LogMessage.Level.INFO)
   void timedOutWaitingForSendAcks(String operation, String bridgeName, long numberOfMessages);

   @LogMessage(id = 221043, value = "Protocol module found: [{}]. Adding protocol support for: {}", level = LogMessage.Level.INFO)
   void addingProtocolSupport(String moduleName, String protocolKey);

   @LogMessage(id = 221045, value = "libaio is not available, switching the configuration into NIO", level = LogMessage.Level.INFO)
   void switchingNIO();

   @LogMessage(id = 221046, value = "Unblocking message production on address '{}'; {}", level = LogMessage.Level.INFO)
   void unblockingMessageProduction(SimpleString addressName, String sizeInfo);

   @LogMessage(id = 221047, value = "Backup Server has scaled down to primary server", level = LogMessage.Level.INFO)
   void backupServerScaledDown();

   @LogMessage(id = 221048, value = "Consumer {}:{} attached to queue '{}' from {} identified as 'slow.' Expected consumption rate: {} msgs/second; actual consumption rate: {} msgs/second.", level = LogMessage.Level.INFO)
   void slowConsumerDetected(String sessionID,
                             long consumerID,
                             String queueName,
                             String remoteAddress,
                             float slowConsumerThreshold,
                             float consumerRate);

   @LogMessage(id = 221049, value = "Activating Replica for node: {}", level = LogMessage.Level.INFO)
   void activatingReplica(SimpleString nodeID);

   @LogMessage(id = 221050, value = "Activating Shared Store Backup", level = LogMessage.Level.INFO)
   void activatingSharedStoreBackup();

   @LogMessage(id = 221051, value = "Populating security roles from LDAP at: {}", level = LogMessage.Level.INFO)
   void populatingSecurityRolesFromLDAP(String url);

   @LogMessage(id = 221053, value = "Disallowing use of vulnerable protocol '{}' on acceptor '{}'. See http://www.oracle.com/technetwork/topics/security/poodlecve-2014-3566-2339408.html for more details.", level = LogMessage.Level.INFO)
   void disallowedProtocol(String protocol, String acceptorName);

   @LogMessage(id = 221054, value = "libaio was found but the filesystem does not support AIO. Switching the configuration into NIO. Journal path: {}", level = LogMessage.Level.INFO)
   void switchingNIOonPath(String journalPath);

   @LogMessage(id = 221055, value = "There were too many old replicated folders upon startup, removing {}", level = LogMessage.Level.INFO)
   void removingBackupData(String path);

   @LogMessage(id = 221056, value = "Reloading configuration: {}", level = LogMessage.Level.INFO)
   void reloadingConfiguration(String module);

   @LogMessage(id = 221057, value = "Global Max Size is being adjusted to 1/2 of the JVM max size (-Xmx). being defined as {}", level = LogMessage.Level.INFO)
   void usingDefaultPaging(long bytes);

   @LogMessage(id = 221058, value = "resetting Journal File size from {} to {} to fit with alignment of {}", level = LogMessage.Level.INFO)
   void invalidJournalFileSize(int journalFileSize, int fileSize, int alignment);

   @LogMessage(id = 221059, value = "Deleting old data directory {} as the max folders is set to 0", level = LogMessage.Level.INFO)
   void backupDeletingData(String oldPath);

   @LogMessage(id = 221060, value = "Sending quorum vote request to {}: {}", level = LogMessage.Level.INFO)
   void sendingQuorumVoteRequest(String remoteAddress, String vote);

   @LogMessage(id = 221061, value = "Received quorum vote response from {}: {}", level = LogMessage.Level.INFO)
   void receivedQuorumVoteResponse(String remoteAddress, String vote);

   @LogMessage(id = 221062, value = "Received quorum vote request: {}", level = LogMessage.Level.INFO)
   void receivedQuorumVoteRequest(String vote);

   @LogMessage(id = 221063, value = "Sending quorum vote response: {}", level = LogMessage.Level.INFO)
   void sendingQuorumVoteResponse(String vote);

   @LogMessage(id = 221064, value = "Node {} found in cluster topology", level = LogMessage.Level.INFO)
   void nodeFoundInClusterTopology(String nodeId);

   @LogMessage(id = 221065, value = "Node {} not found in cluster topology", level = LogMessage.Level.INFO)
   void nodeNotFoundInClusterTopology(String nodeId);

   @LogMessage(id = 221066, value = "Initiating quorum vote: {}", level = LogMessage.Level.INFO)
   void initiatingQuorumVote(SimpleString vote);

   @LogMessage(id = 221067, value = "Waiting {} {} for quorum vote results.", level = LogMessage.Level.INFO)
   void waitingForQuorumVoteResults(int timeout, String unit);

   @LogMessage(id = 221068, value = "Received all quorum votes.", level = LogMessage.Level.INFO)
   void receivedAllQuorumVotes();

   @LogMessage(id = 221069, value = "Timeout waiting for quorum vote responses.", level = LogMessage.Level.INFO)
   void timeoutWaitingForQuorumVoteResponses();

   @LogMessage(id = 221070, value = "Restarting as backup based on quorum vote results.", level = LogMessage.Level.INFO)
   void restartingAsBackupBasedOnQuorumVoteResults();

   @LogMessage(id = 221071, value = "Failing over based on quorum vote results.", level = LogMessage.Level.INFO)
   void failingOverBasedOnQuorumVoteResults();

   @LogMessage(id = 221072, value = "Can't find roles for the subject.", level = LogMessage.Level.INFO)
   void failedToFindRolesForTheSubject(Exception e);

   @LogMessage(id = 221073, value = "Can't add role principal.", level = LogMessage.Level.INFO)
   void failedAddRolePrincipal(Exception e);

   @LogMessage(id = 221074, value = "Debug started : size = {} bytes, messages = {}", level = LogMessage.Level.INFO)
   void debugStarted(Long globalSizeBytes, Long numberOfMessages);

   @LogMessage(id = 221075, value = "Usage of wildcardRoutingEnabled configuration property is deprecated, please use wildCardConfiguration.enabled instead", level = LogMessage.Level.INFO)
   void deprecatedWildcardRoutingEnabled();

   @LogMessage(id = 221076, value = "{}", level = LogMessage.Level.INFO)
   void onDestroyConnectionWithSessionMetadata(String msg);

   @LogMessage(id = 221077, value = "There is no queue with ID {}, deleting record {}", level = LogMessage.Level.INFO)
   void infoNoQueueWithID(Long id, Long record);

   @LogMessage(id = 221078, value = "Scaled down {} messages total.", level = LogMessage.Level.INFO)
   void infoScaledDownMessages(Long num);

   @LogMessage(id = 221079, value = "Ignoring prepare on xid as already called : {}", level = LogMessage.Level.INFO)
   void ignoringPrepareOnXidAlreadyCalled(String xid);

   @LogMessage(id = 221080, value = "Deploying address {} supporting {}", level = LogMessage.Level.INFO)
   void deployAddress(String addressName, String routingTypes);

   @LogMessage(id = 221081, value = "There is no address with ID {}, deleting record {}", level = LogMessage.Level.INFO)
   void infoNoAddressWithID(Long id, Long record);

   @LogMessage(id = 221082, value = "Initializing metrics plugin {} with properties: {}", level = LogMessage.Level.INFO)
   void initializingMetricsPlugin(String clazz, String properties);

   @LogMessage(id = 221083, value = "ignoring quorum vote as max cluster size is {}.", level = LogMessage.Level.INFO)
   void ignoringQuorumVote(int maxClusterSize);

   @LogMessage(id = 221084, value = "Requested {} quorum votes", level = LogMessage.Level.INFO)
   void requestedQuorumVotes(int vote);

   @LogMessage(id = 221085, value = "Route {} to {}", level = LogMessage.Level.INFO)
   void routeClientConnection(Connection connection, Target target);

   @LogMessage(id = 221086, value = "Cannot route {}", level = LogMessage.Level.INFO)
   void cannotRouteClientConnection(Connection connection);

   @LogMessage(id = 222000, value = "ActiveMQServer is being finalized and has not been stopped. Please remember to stop the server before letting it go out of scope", level = LogMessage.Level.WARN)
   void serverFinalisedWIthoutBeingSTopped();

   @LogMessage(id = 222001, value = "Error closing sessions while stopping server", level = LogMessage.Level.WARN)
   void errorClosingSessionsWhileStoppingServer(Exception e);

   @LogMessage(id = 222002, value = "Timed out waiting for pool to terminate {}. Interrupting all its threads!", level = LogMessage.Level.WARN)
   void timedOutStoppingThreadpool(ExecutorService service);

   @LogMessage(id = 222004, value = "Must specify an address for each divert. This one will not be deployed.", level = LogMessage.Level.WARN)
   void divertWithNoAddress();

   @LogMessage(id = 222005, value = "Must specify a forwarding address for each divert. This one will not be deployed.", level = LogMessage.Level.WARN)
   void divertWithNoForwardingAddress();

   @LogMessage(id = 222006, value = "Binding already exists with name {}, divert will not be deployed", level = LogMessage.Level.WARN)
   void divertBindingAlreadyExists(SimpleString bindingName);

   @LogMessage(id = 222007, value = "Security risk! Apache Artemis is running with the default cluster admin user and default password. Please see the cluster chapter in the Artemis User Guide for instructions on how to change this.", level = LogMessage.Level.WARN)
   void clusterSecurityRisk();

   @LogMessage(id = 222008, value = "unable to restart server, please kill and restart manually", level = LogMessage.Level.WARN)
   void serverRestartWarning(Exception e);

   @LogMessage(id = 222009, value = "Unable to announce backup for replication. Trying to stop the server.", level = LogMessage.Level.WARN)
   void replicationStartProblem(Exception e);

   @LogMessage(id = 222010, value = "Critical IO Error, shutting down the server. file={}, message={}", level = LogMessage.Level.ERROR)
   void ioCriticalIOError(String message, String file, Throwable code);

   @LogMessage(id = 222011, value = "Error stopping server", level = LogMessage.Level.WARN)
   void errorStoppingServer(Exception e);

   @LogMessage(id = 222013, value = "Error when trying to start replication", level = LogMessage.Level.WARN)
   void errorStartingReplication(Exception e);

   @LogMessage(id = 222014, value = "Error when trying to stop replication", level = LogMessage.Level.WARN)
   void errorStoppingReplication(Exception e);

   @LogMessage(id = 222016, value = "Cannot deploy a connector with no name specified.", level = LogMessage.Level.WARN)
   void connectorWithNoName();

   @LogMessage(id = 222017, value = "There is already a connector with name {} deployed. This one will not be deployed.", level = LogMessage.Level.WARN)
   void connectorAlreadyDeployed(String name);

   @LogMessage(id = 222018, value = "AIO was not located on this platform, it will fall back to using pure Java NIO. If your platform is Linux, install LibAIO to enable the AIO journal", level = LogMessage.Level.WARN)
   void AIONotFound();

   @LogMessage(id = 222019, value = "There is already a discovery group with name {} deployed. This one will not be deployed.", level = LogMessage.Level.WARN)
   void discoveryGroupAlreadyDeployed(String name);

   @LogMessage(id = 222023, value = "problem cleaning page address {}", level = LogMessage.Level.WARN)
   void problemCleaningPageAddress(SimpleString address, Throwable e);

   @LogMessage(id = 222025, value = "Problem cleaning page subscription counter", level = LogMessage.Level.WARN)
   void problemCleaningPagesubscriptionCounter(Throwable e);

   @LogMessage(id = 222026, value = "Error on cleaning up cursor pages", level = LogMessage.Level.WARN)
   void problemCleaningCursorPages(Exception e);

   @LogMessage(id = 222030, value = "File {} being renamed to {}.invalidPage as it was loaded partially. Please verify your data.", level = LogMessage.Level.WARN)
   void pageInvalid(String fileName, String name);

   @LogMessage(id = 222031, value = "Error while deleting page file", level = LogMessage.Level.WARN)
   void pageDeleteError(Exception e);

   @LogMessage(id = 222033, value = "Page file {} had incomplete records at position {} at record number {}", level = LogMessage.Level.WARN)
   void pageSuspectFile(String fileName, int position, int msgNumber);

   @LogMessage(id = 222034, value = "Can not delete page transaction id={}", level = LogMessage.Level.WARN)
   void pageTxDeleteError(long recordID, Exception e);

   @LogMessage(id = 222035, value = "Directory {} did not have an identification file {}", level = LogMessage.Level.WARN)
   void pageStoreFactoryNoIdFile(String s, String addressFile);

   @LogMessage(id = 222036, value = "Timed out on waiting PagingStore {} to shutdown", level = LogMessage.Level.WARN)
   void pageStoreTimeout(SimpleString address);

   @LogMessage(id = 222037, value = "IO Error, impossible to start paging", level = LogMessage.Level.WARN)
   void pageStoreStartIOError(Exception e);

   @LogMessage(id = 222038, value = "Starting paging on address '{}'; {}", level = LogMessage.Level.INFO)
   void pageStoreStart(SimpleString storeName, String sizeInfo);

   @LogMessage(id = 222039, value = "Messages sent to address '{}' are being dropped; {}", level = LogMessage.Level.WARN)
   void pageStoreDropMessages(SimpleString storeName, String sizeInfo);

   @LogMessage(id = 222040, value = "Server is stopped", level = LogMessage.Level.WARN)
   void serverIsStopped();

   @LogMessage(id = 222041, value = "Cannot find queue {} to update delivery count", level = LogMessage.Level.WARN)
   void journalCannotFindQueueDelCount(Long queueID);

   @LogMessage(id = 222042, value = "Cannot find message {} to update delivery count", level = LogMessage.Level.WARN)
   void journalCannotFindMessageDelCount(Long msg);

   @LogMessage(id = 222043, value = "Message for queue {} which does not exist. This message will be ignored.", level = LogMessage.Level.WARN)
   void journalCannotFindQueueForMessage(Long queueID);

   @LogMessage(id = 222044, value = "It was not possible to delete message {}", level = LogMessage.Level.WARN)
   void journalErrorDeletingMessage(Long messageID, Exception e);

   @LogMessage(id = 222045, value = "Message in prepared tx for queue {} which does not exist. This message will be ignored.", level = LogMessage.Level.WARN)
   void journalMessageInPreparedTX(Long queueID);

   @LogMessage(id = 222046, value = "Failed to remove reference for {}", level = LogMessage.Level.WARN)
   void journalErrorRemovingRef(Long messageID);

   @LogMessage(id = 222047, value = "Can not find queue {} while reloading ACKNOWLEDGE_CURSOR", level = LogMessage.Level.WARN)
   void journalCannotFindQueueReloadingACK(Long queueID);

   @LogMessage(id = 222049, value = "InternalError: Record type {} not recognized. Maybe you are using journal files created on a different version", level = LogMessage.Level.WARN)
   void journalInvalidRecordType(Byte recordType);

   @LogMessage(id = 222050, value = "Can not locate recordType={} on loadPreparedTransaction//deleteRecords", level = LogMessage.Level.WARN)
   void journalInvalidRecordTypeOnPreparedTX(Byte recordType);

   @LogMessage(id = 222051, value = "Journal Error", level = LogMessage.Level.WARN)
   void journalError(Exception e);

   @LogMessage(id = 222053, value = "Error on copying large message {} for DLA or Expiry", level = LogMessage.Level.WARN)
   void lareMessageErrorCopying(LargeServerMessage largeServerMessage, Exception e);

   @LogMessage(id = 222054, value = "Error on executing IOCallback", level = LogMessage.Level.WARN)
   void errorExecutingAIOCallback(Throwable t);

   @LogMessage(id = 222055, value = "Error on deleting duplicate cache", level = LogMessage.Level.WARN)
   void errorDeletingDuplicateCache(Exception e);

   @LogMessage(id = 222056, value = "Did not route to any bindings for address {} and sendToDLAOnNoRoute is true but there is no DLA configured for the address, the message will be ignored.", level = LogMessage.Level.WARN)
   void noDLA(SimpleString address);

   @LogMessage(id = 222057, value = "It was not possible to add references due to an IO error code {} message = {}", level = LogMessage.Level.WARN)
   void ioErrorAddingReferences(Integer errorCode, String errorMessage);

   @LogMessage(id = 222059, value = "Duplicate message detected - message will not be routed. Message information:\n{}", level = LogMessage.Level.WARN)
   void duplicateMessageDetected(org.apache.activemq.artemis.api.core.Message message);

   @LogMessage(id = 222060, value = "Error while confirming large message completion on rollback for recordID={}", level = LogMessage.Level.WARN)
   void journalErrorConfirmingLargeMessage(Long messageID, Throwable e);

   @LogMessage(id = 222061, value = "Client connection failed, clearing up resources for session {}", level = LogMessage.Level.WARN)
   void clientConnectionFailed(String name);

   @LogMessage(id = 222062, value = "Cleared up resources for session {}", level = LogMessage.Level.WARN)
   void clearingUpSession(String name);

   @LogMessage(id = 222063, value = "Error processing IOCallback; code = {}, message = {}", level = LogMessage.Level.WARN)
   void errorProcessingIOCallback(Integer errorCode, String errorMessage);

   @LogMessage(id = 222065, value = "Client is not being consistent on the request versioning. It just sent a version id={} while it informed {} previously", level = LogMessage.Level.DEBUG)
   void incompatibleVersionAfterConnect(int version, int clientVersion);

   @LogMessage(id = 222066, value = "Reattach request from {} failed as there is no confirmationWindowSize configured, which may be ok for your system", level = LogMessage.Level.WARN)
   void reattachRequestFailed(String remoteAddress);

   @LogMessage(id = 222067, value = "Connection failure has been detected: {} [code={}]", level = LogMessage.Level.WARN)
   void connectionFailureDetected(String message, ActiveMQExceptionType type);

   @LogMessage(id = 222069, value = "error cleaning up stomp connection", level = LogMessage.Level.WARN)
   void errorCleaningStompConn(Exception e);

   @LogMessage(id = 222070, value = "Stomp Transactional acknowledgement is not supported", level = LogMessage.Level.WARN)
   void stompTXAckNorSupported();

   @LogMessage(id = 222072, value = "Timed out flushing channel on InVMConnection", level = LogMessage.Level.WARN)
   void timedOutFlushingInvmChannel();

   @LogMessage(id = 222074, value = "channel group did not completely close", level = LogMessage.Level.WARN)
   void nettyChannelGroupError();

   @LogMessage(id = 222075, value = "{} is still connected to {}", level = LogMessage.Level.WARN)
   void nettyChannelStillOpen(Channel channel, String remoteAddress);

   @LogMessage(id = 222076, value = "channel group did not completely unbind", level = LogMessage.Level.WARN)
   void nettyChannelGroupBindError();

   @LogMessage(id = 222077, value = "{} is still bound to {}", level = LogMessage.Level.WARN)
   void nettyChannelStillBound(Channel channel, String remoteAddress);

   @LogMessage(id = 222080, value = "Error creating acceptor: {}", level = LogMessage.Level.WARN)
   void errorCreatingAcceptor(String name, Exception e);

   @LogMessage(id = 222081, value = "Timed out waiting for remoting thread pool to terminate", level = LogMessage.Level.WARN)
   void timeoutRemotingThreadPool();

   @LogMessage(id = 222082, value = "error on connection failure check", level = LogMessage.Level.WARN)
   void errorOnFailureCheck(Throwable e);

   @LogMessage(id = 222085, value = "Packet {} can not be processed by the ReplicationEndpoint", level = LogMessage.Level.WARN)
   void invalidPacketForReplication(Packet packet);

   @LogMessage(id = 222086, value = "error handling packet {} for replication", level = LogMessage.Level.WARN)
   void errorHandlingReplicationPacket(Packet packet, Exception e);

   @LogMessage(id = 222087, value = "Replication Error while closing the page on backup", level = LogMessage.Level.WARN)
   void errorClosingPageOnReplication(Exception e);

   @LogMessage(id = 222089, value = "Replication Error deleting large message ID = {}", level = LogMessage.Level.WARN)
   void errorDeletingLargeMessage(long messageId, Exception e);

   @LogMessage(id = 222091, value = "The backup node has been shut-down, replication will now stop", level = LogMessage.Level.WARN)
   void replicationStopOnBackupShutdown();

   @LogMessage(id = 222092, value = "Connection to the backup node failed, removing replication now", level = LogMessage.Level.WARN)
   void replicationStopOnBackupFail(Exception e);

   @LogMessage(id = 222093, value = "Timed out waiting to stop Bridge", level = LogMessage.Level.WARN)
   void timedOutWaitingToStopBridge();

   @LogMessage(id = 222094, value = "Bridge unable to send message {}, will try again once bridge reconnects", level = LogMessage.Level.WARN)
   void bridgeUnableToSendMessage(MessageReference ref, Exception e);

   @LogMessage(id = 222095, value = "Connection failed with failedOver={}", level = LogMessage.Level.WARN)
   void bridgeConnectionFailed(Boolean failedOver);

   @LogMessage(id = 222096, value = "Error on querying binding on bridge {}. Retrying in 100 milliseconds", level = LogMessage.Level.WARN)
   void errorQueryingBridge(String name, Throwable t);

   @LogMessage(id = 222097, value = "Address {} does not have any bindings, retry #({})", level = LogMessage.Level.WARN)
   void errorQueryingBridge(String address, Integer retryCount);

   @LogMessage(id = 222098, value = "Server is starting, retry to create the session for bridge {}", level = LogMessage.Level.WARN)
   void errorStartingBridge(String name);

   @LogMessage(id = 222099, value = "Bridge {} is unable to connect to destination. It will be disabled.", level = LogMessage.Level.WARN)
   void errorConnectingBridge(Bridge bridge, Exception e);

   @LogMessage(id = 222100, value = "ServerLocator was shutdown, can not retry on opening connection for bridge", level = LogMessage.Level.WARN)
   void bridgeLocatorShutdown();

   @LogMessage(id = 222101, value = "Bridge {} achieved {} maxattempts={} it will stop retrying to reconnect", level = LogMessage.Level.WARN)
   void bridgeAbortStart(String name, Integer retryCount, Integer reconnectAttempts);

   @LogMessage(id = 222103, value = "transaction with xid {} timed out", level = LogMessage.Level.WARN)
   void timedOutXID(Xid xid);

   @LogMessage(id = 222104, value = "IO Error completing transaction {}; code = {}, message = {}", level = LogMessage.Level.WARN)
   void ioErrorOnTX(String op, Integer errorCode, String errorMessage);

   @LogMessage(id = 222106, value = "Replacing incomplete LargeMessage with ID={}", level = LogMessage.Level.WARN)
   void replacingIncompleteLargeMessage(Long messageID);

   @LogMessage(id = 222107, value = "Cleared up resources for session {}", level = LogMessage.Level.WARN)
   void clientConnectionFailedClearingSession(String name);

   @LogMessage(id = 222108, value = "unable to send notification when broadcast group is stopped", level = LogMessage.Level.WARN)
   void broadcastGroupClosed(Exception e);

   @LogMessage(id = 222109, value = "Timed out waiting for write lock on consumer {} from {}. Check the Thread dump", level = LogMessage.Level.WARN)
   void timeoutLockingConsumer(String consumer, String remoteAddress);

   @LogMessage(id = 222111, value = "exception while invoking {} on {}", level = LogMessage.Level.TRACE)
   void managementOperationError(String op, String resourceName, Exception e);

   @LogMessage(id = 222112, value = "exception while retrieving attribute {} on {}", level = LogMessage.Level.TRACE)
   void managementAttributeError(String att, String resourceName, Exception e);

   @LogMessage(id = 222113, value = "On ManagementService stop, there are {} unexpected registered MBeans: {}", level = LogMessage.Level.WARN)
   void managementStopError(Integer size, List<String> unexpectedResourceNames);

   @LogMessage(id = 222114, value = "Unable to delete group binding info {}", level = LogMessage.Level.WARN)
   void unableToDeleteGroupBindings(SimpleString groupId, Exception e);

   @LogMessage(id = 222115, value = "Error closing serverLocator={}", level = LogMessage.Level.WARN)
   void errorClosingServerLocator(ServerLocatorInternal clusterLocator, Exception e);

   @LogMessage(id = 222116, value = "unable to start broadcast group {}", level = LogMessage.Level.WARN)
   void unableToStartBroadcastGroup(String name, Exception e);

   @LogMessage(id = 222117, value = "unable to start cluster connection {}", level = LogMessage.Level.WARN)
   void unableToStartClusterConnection(SimpleString name, Exception e);

   @LogMessage(id = 222118, value = "unable to start Bridge {}", level = LogMessage.Level.WARN)
   void unableToStartBridge(SimpleString name, Exception e);

   @LogMessage(id = 222119, value = "No connector with name {}. backup cannot be announced.", level = LogMessage.Level.WARN)
   void announceBackupNoConnector(String connectorName);

   @LogMessage(id = 222120, value = "no cluster connections defined, unable to announce backup", level = LogMessage.Level.WARN)
   void announceBackupNoClusterConnections();

   @LogMessage(id = 222121, value = "Must specify a unique name for each bridge. This one will not be deployed.", level = LogMessage.Level.WARN)
   void bridgeNotUnique();

   @LogMessage(id = 222122, value = "Must specify a queue name for each bridge. This one {} will not be deployed.", level = LogMessage.Level.WARN)
   void bridgeNoQueue(String name);

   @LogMessage(id = 222123, value = "Forward address is not specified on bridge {}. Will use original message address instead", level = LogMessage.Level.WARN)
   void bridgeNoForwardAddress(String bridgeName);

   @LogMessage(id = 222124, value = "There is already a bridge with name {} deployed. This one will not be deployed.", level = LogMessage.Level.WARN)
   void bridgeAlreadyDeployed(String name);

   @LogMessage(id = 222125, value = "No queue found with name {} bridge {} will not be deployed.", level = LogMessage.Level.WARN)
   void bridgeQueueNotFound(String queueName, String bridgeName);

   @LogMessage(id = 222126, value = "No discovery group found with name {} bridge will not be deployed.", level = LogMessage.Level.WARN)
   void bridgeNoDiscoveryGroup(String name);

   @LogMessage(id = 222127, value = "Must specify a unique name for each cluster connection. This one will not be deployed.", level = LogMessage.Level.WARN)
   void clusterConnectionNotUnique();

   @LogMessage(id = 222129, value = "No connector with name {}. The cluster connection will not be deployed.", level = LogMessage.Level.WARN)
   void clusterConnectionNoConnector(String connectorName);

   @LogMessage(id = 222130, value = "Cluster Configuration  {} already exists. The cluster connection will not be deployed.", level = LogMessage.Level.WARN)
   void clusterConnectionAlreadyExists(String connectorName);

   @LogMessage(id = 222131, value = "No discovery group with name {}. The cluster connection will not be deployed.", level = LogMessage.Level.WARN)
   void clusterConnectionNoDiscoveryGroup(String discoveryGroupName);

   @LogMessage(id = 222132, value = "There is already a broadcast-group with name {} deployed. This one will not be deployed.", level = LogMessage.Level.WARN)
   void broadcastGroupAlreadyExists(String name);

   @LogMessage(id = 222133, value = "There is no connector deployed with name {}. The broadcast group with name {} will not be deployed.", level = LogMessage.Level.WARN)
   void broadcastGroupNoConnector(String connectorName, String bgName);

   @LogMessage(id = 222136, value = "IO Error during redistribution, errorCode = {} message = {}", level = LogMessage.Level.WARN)
   void ioErrorRedistributing(Integer errorCode, String errorMessage);

   @LogMessage(id = 222137, value = "Unable to announce backup, retrying", level = LogMessage.Level.WARN)
   void errorAnnouncingBackup(Throwable e);

   @LogMessage(id = 222138, value = "Local Member is not set at on ClusterConnection {}", level = LogMessage.Level.WARN)
   void noLocalMemborOnClusterConnection(ClusterConnectionImpl clusterConnection);

   @LogMessage(id = 222139, value = "{}::Remote queue binding {} has already been bound in the post office. Most likely cause for this is you have a loop in your cluster due to cluster max-hops being too large or you have multiple cluster connections to the same nodes using overlapping addresses", level = LogMessage.Level.WARN)
   void remoteQueueAlreadyBoundOnClusterConnection(Object messageFlowRecord, SimpleString clusterName);

   @LogMessage(id = 222141, value = "Node Manager can not open file {}", level = LogMessage.Level.WARN)
   void nodeManagerCantOpenFile(File file, Exception e);

   @LogMessage(id = 222142, value = "Error on resetting large message deliver - {}", level = LogMessage.Level.WARN)
   void errorResttingLargeMessage(Object deliverer, Throwable e);

   @LogMessage(id = 222143, value = "Timed out waiting for executor to complete", level = LogMessage.Level.WARN)
   void errorTransferringConsumer();

   @LogMessage(id = 222144, value = "Queue could not finish waiting executors. Try increasing the thread pool size", level = LogMessage.Level.WARN)
   void errorFlushingExecutorsOnQueue();

   @LogMessage(id = 222145, value = "Error expiring reference {} on queue", level = LogMessage.Level.WARN)
   void errorExpiringReferencesOnQueue(MessageReference ref, Exception e);

   @LogMessage(id = 222146, value = "Message has expired. No bindings for Expiry Address {} so dropping it", level = LogMessage.Level.WARN)
   void errorExpiringReferencesNoBindings(SimpleString expiryAddress);

   @LogMessage(id = 222147, value = "Messages are being expired on queue {}, but there is no Expiry Address configured so messages will be dropped.", level = LogMessage.Level.WARN)
   void errorExpiringReferencesNoAddress(SimpleString name);

   @LogMessage(id = 222148, value = "Sending message {} to Dead Letter Address {}, but it has no bindings so dropping it", level = LogMessage.Level.WARN)
   void noBindingsOnDLA(MessageReference ref, SimpleString name);

   @LogMessage(id = 222149, value = "Sending message {} to Dead Letter Address {} from {}", level = LogMessage.Level.WARN)
   void sendingMessageToDLA(MessageReference ref, SimpleString name, SimpleString simpleString);

   @LogMessage(id = 222150, value = "Sending message {} to Dead Letter Address, but there is no Dead Letter Address configured for queue {} so dropping it", level = LogMessage.Level.WARN)
   void sendingMessageToDLAnoDLA(MessageReference ref, SimpleString name);

   @LogMessage(id = 222151, value = "removing consumer which did not handle a message, consumer={}, message={}", level = LogMessage.Level.WARN)
   void removingBadConsumer(Consumer consumer, Object reference, Throwable e);

   @LogMessage(id = 222153, value = "Cannot locate record for message id = {} on Journal", level = LogMessage.Level.WARN)
   void cannotFindMessageOnJournal(Long messageID, Throwable e);

   @LogMessage(id = 222154, value = "Error checking DLQ", level = LogMessage.Level.WARN)
   void errorCheckingDLQ(Throwable e);

   @LogMessage(id = 222155, value = "Failed to register as backup. Stopping the server.", level = LogMessage.Level.WARN)
   void errorRegisteringBackup();

   @LogMessage(id = 222156, value = "Less than {}%\n{}\nYou are in danger of running out of RAM. Have you set paging parameters on your addresses? (See user manual \"Paging\" chapter)", level = LogMessage.Level.WARN)
   void memoryError(Integer memoryWarningThreshold, String info);

   @LogMessage(id = 222157, value = "Error completing callback on replication manager", level = LogMessage.Level.WARN)
   void errorCompletingCallbackOnReplicationManager(Throwable e);

   @LogMessage(id = 222158, value = "{} activation thread did not finish.", level = LogMessage.Level.WARN)
   void activationDidntFinish(ActiveMQServer server);

   @LogMessage(id = 222160, value = "unable to send notification for bridge {}: {}", level = LogMessage.Level.WARN)
   void notificationBridgeError(String bridge, CoreNotificationType type, Exception e);

   @LogMessage(id = 222161, value = "Group Handler timed-out waiting for sendCondition", level = LogMessage.Level.WARN)
   void groupHandlerSendTimeout();

   @LogMessage(id = 222162, value = "Moving data directory {} to {}", level = LogMessage.Level.INFO)
   void backupMovingDataAway(String oldPath, String newPath);

   @LogMessage(id = 222164, value = "Error when trying to start replication {}", level = LogMessage.Level.WARN)
   void errorStartingReplication(BackupReplicationStartFailedMessage.BackupRegistrationProblem problem);

   @LogMessage(id = 222165, value = "No Dead Letter Address configured for queue {} in AddressSettings", level = LogMessage.Level.WARN)
   void AddressSettingsNoDLA(SimpleString name);

   @LogMessage(id = 222166, value = "No Expiry Address configured for queue {} in AddressSettings", level = LogMessage.Level.WARN)
   void AddressSettingsNoExpiryAddress(SimpleString name);

   @SuppressWarnings("deprecation")
   @LogMessage(id = 222168, value = "The '" + TransportConstants.PROTOCOL_PROP_NAME + "' property is deprecated. If you want this Acceptor to support multiple protocols, use the '" + TransportConstants.PROTOCOLS_PROP_NAME + "' property, e.g. with value 'CORE,AMQP,STOMP'", level = LogMessage.Level.WARN)
   void warnDeprecatedProtocol();

   @LogMessage(id = 222169, value = "Server needs to disconnect the consumer because of ( {} ) but you have a legacy client connected and it cannot do so, these consumers may just hang", level = LogMessage.Level.WARN)
   void warnDisconnectOldClient(String message);

   @LogMessage(id = 222172, value = "Queue {} was busy for more than {} milliseconds. There are possibly consumers hanging on a network operation", level = LogMessage.Level.WARN)
   void queueBusy(String name, long timeout);

   @LogMessage(id = 222173, value = "Queue {} is duplicated during reload. This queue will be renamed as {}", level = LogMessage.Level.WARN)
   void queueDuplicatedRenaming(String name, String newName);

   @LogMessage(id = 222174, value = "Queue {}, on address={}, is taking too long to flush deliveries. Watch out for frozen clients.", level = LogMessage.Level.WARN)
   void timeoutFlushInTransit(String queueName, String addressName);

   @LogMessage(id = 222175, value = "Bridge {} could not find configured connectors", level = LogMessage.Level.WARN)
   void bridgeCantFindConnectors(String bridgeName);

   @LogMessage(id = 222176, value = "A session that was already doing XA work on {} is replacing the xid by {} " + ". This was most likely caused from a previous communication timeout", level = LogMessage.Level.WARN)
   void xidReplacedOnXStart(String xidOriginalToString, String xidReplacedToString);

   @LogMessage(id = 222177, value = "Wrong configuration for role, {} is not a valid permission", level = LogMessage.Level.WARN)
   void rolePermissionConfigurationError(String permission);

   @LogMessage(id = 222178, value = "Error during recovery of page counters", level = LogMessage.Level.WARN)
   void errorRecoveringPageCounter(Throwable error);

   @LogMessage(id = 222181, value = "Unable to scaleDown messages", level = LogMessage.Level.WARN)
   void failedToScaleDown(Throwable e);

   @LogMessage(id = 222183, value = "Blocking message production on address '{}'; {}", level = LogMessage.Level.WARN)
   void blockingMessageProduction(SimpleString addressName, String pageInfo);

   @LogMessage(id = 222184, value = "Unable to recover group bindings in SCALE_DOWN mode, only FULL backup server can do this", level = LogMessage.Level.WARN)
   void groupBindingsOnRecovery();

   @LogMessage(id = 222185, value = "no cluster connection for specified replication cluster", level = LogMessage.Level.WARN)
   void noClusterConnectionForReplicationCluster();

   @LogMessage(id = 222186, value = "unable to authorise cluster control: {}", level = LogMessage.Level.WARN)
   void clusterControlAuthfailure(String causeMessage);

   @LogMessage(id = 222187, value = "Failed to activate replicated backup", level = LogMessage.Level.WARN)
   void activateReplicatedBackupFailed(Throwable e);

   @LogMessage(id = 222188, value = "Unable to find target queue for node {}", level = LogMessage.Level.WARN)
   void unableToFindTargetQueue(String targetNodeID);

   @LogMessage(id = 222189, value = "Failed to activate shared store backup", level = LogMessage.Level.WARN)
   void activateSharedStoreBackupFailed(Throwable e);

   @LogMessage(id = 222191, value = "Could not find any configured role for user {}.", level = LogMessage.Level.WARN)
   void cannotFindRoleForUser(String user);

   @LogMessage(id = 222194, value = "PageCursorInfo == null on address {}, pos = {}, queue = {}.", level = LogMessage.Level.WARN)
   void nullPageCursorInfo(String address, String position, long id);

   @LogMessage(id = 222195, value = "Large message {} wasn't found when dealing with add pending large message", level = LogMessage.Level.WARN)
   void largeMessageNotFound(long id);

   @LogMessage(id = 222196, value = "Could not find binding with id={} on routeFromCluster for message={} binding = {}", level = LogMessage.Level.WARN)
   void bindingNotFound(long id, String message, String binding);

   @LogMessage(id = 222197, value = "Internal error! Delivery logic has identified a non delivery and still handled a consumer!", level = LogMessage.Level.WARN)
   void nonDeliveryHandled();

   @LogMessage(id = 222198, value = "Could not flush ClusterManager executor ({}) in 10 seconds, verify your thread pool size", level = LogMessage.Level.WARN)
   void couldNotFlushClusterManager(String manager);

   @LogMessage(id = 222199, value = "Thread dump: {}", level = LogMessage.Level.WARN)
   void threadDump(String manager);

   @LogMessage(id = 222200, value = "Could not finish executor on {}", level = LogMessage.Level.WARN)
   void couldNotFinishExecutor(String clusterConnection);

   @LogMessage(id = 222201, value = "Timed out waiting for activation to exit", level = LogMessage.Level.WARN)
   void activationTimeout();

   @LogMessage(id = 222202, value = "{}: <{}> should not be set to the same value as <{}>.  " + "If a system is under high load, or there is a minor network delay, " + "there is a high probability of a cluster split/failure due to connection timeout.", level = LogMessage.Level.WARN)
   void connectionTTLEqualsCheckPeriod(String connectionName, String ttl, String checkPeriod);

   @LogMessage(id = 222203, value = "Classpath lacks a protocol-manager for protocol {}, Protocol being ignored on acceptor {}", level = LogMessage.Level.WARN)
   void noProtocolManagerFound(String protocol, String host);

   @LogMessage(id = 222205, value = "OutOfMemoryError possible! There are currently {} addresses with a total max-size-bytes of {} bytes, but the maximum memory available is {} bytes.", level = LogMessage.Level.WARN)
   void potentialOOME(long addressCount, long totalMaxSizeBytes, long maxMemory);

   @LogMessage(id = 222206, value = "Connection limit of {} reached. Refusing connection from {}.", level = LogMessage.Level.WARN)
   void connectionLimitReached(long connectionsAllowed, String address);

   @LogMessage(id = 222207, value = "The backup server is not responding promptly introducing latency beyond the limit. Replication server being disconnected now.", level = LogMessage.Level.WARN)
   void slowReplicationResponse();

   @LogMessage(id = 222208, value = "SSL handshake failed for client from {}: {}.", level = LogMessage.Level.WARN)
   void sslHandshakeFailed(String clientAddress, String cause);

   @LogMessage(id = 222209, value = "Could not contact group handler coordinator after 10 retries, message being routed without grouping information", level = LogMessage.Level.WARN)
   void impossibleToRouteGrouped();

   @LogMessage(id = 222210, value = "Free storage space is at {} of {} total. Usage rate is {} which is beyond the configured <max-disk-usage>. System will start blocking producers.", level = LogMessage.Level.WARN)
   void maxDiskUsageReached(String usableSpace, String totalSpace, String usage);

   @LogMessage(id = 222211, value = "Free storage space is at {} of {} total. Usage rate is {} which is below the configured <max-disk-usage>. System will unblock producers.", level = LogMessage.Level.INFO)
   void maxDiskUsageRestored(String usableSpace, String totalSpace, String usage);

   @LogMessage(id = 222212, value = "Disk Full! Blocking message production on address '{}'. Clients will report blocked.", level = LogMessage.Level.WARN)
   void blockingDiskFull(SimpleString addressName);

   @LogMessage(id = 222213, value = "There was an issue on the network, server is isolated!", level = LogMessage.Level.WARN)
   void serverIsolatedOnNetwork();

   @LogMessage(id = 222214, value = "Destination {} has an inconsistent and negative address size={}.", level = LogMessage.Level.WARN)
   void negativeAddressSize(String destination, long size);

   @LogMessage(id = 222215, value = "Global Address Size has negative and inconsistent value as {}", level = LogMessage.Level.WARN)
   void negativeGlobalAddressSize(long size);

   @LogMessage(id = 222216, value = "Security problem while authenticating: {}", level = LogMessage.Level.WARN)
   void securityProblemWhileAuthenticating(String message);

   @LogMessage(id = 222217, value = "Cannot find connector-ref {}. The cluster-connection {} will not be deployed.", level = LogMessage.Level.WARN)
   void connectorRefNotFound(String connectorRef, String clusterConnection);

   @LogMessage(id = 222218, value = "Server disconnecting: {}", level = LogMessage.Level.WARN)
   void disconnectCritical(String reason, Exception e);

   @LogMessage(id = 222219, value = "File {} does not exist", level = LogMessage.Level.WARN)
   void fileDoesNotExist(String path);

   @LogMessage(id = 222220, value = "   Error while cleaning paging on queue {}", level = LogMessage.Level.WARN)
   void errorCleaningPagingOnQueue(String queue, Exception e);

   @LogMessage(id = 222221, value = "Error while cleaning page, during the commit", level = LogMessage.Level.WARN)
   void errorCleaningPagingDuringCommit(Exception e);

   @LogMessage(id = 222222, value = "Error while deleting page-complete-record", level = LogMessage.Level.WARN)
   void errorDeletingPageCompleteRecord(Exception e);

   @LogMessage(id = 222223, value = "Failed to calculate message memory estimate", level = LogMessage.Level.WARN)
   void errorCalculateMessageMemoryEstimate(Throwable e);

   @LogMessage(id = 222224, value = "Failed to calculate scheduled delivery time", level = LogMessage.Level.WARN)
   void errorCalculateScheduledDeliveryTime(Throwable e);

   @LogMessage(id = 222225, value = "Sending unexpected exception to the client", level = LogMessage.Level.WARN)
   void sendingUnexpectedExceptionToClient(Throwable e);

   @LogMessage(id = 222226, value = "Connection configuration is null for connectorName {}", level = LogMessage.Level.WARN)
   void connectionConfigurationIsNull(String connectorName);

   @LogMessage(id = 222227, value = "Failed to process an event", level = LogMessage.Level.WARN)
   void failedToProcessEvent(NamingException e);

   @LogMessage(id = 222228, value = "Missing replication token on queue", level = LogMessage.Level.WARN)
   void missingReplicationTokenOnQueue();

   @LogMessage(id = 222229, value = "Failed to perform rollback", level = LogMessage.Level.WARN)
   void failedToPerformRollback(IllegalStateException e);

   @LogMessage(id = 222230, value = "Failed to send notification", level = LogMessage.Level.WARN)
   void failedToSendNotification(Exception e);

   @LogMessage(id = 222231, value = "Failed to flush outstanding data from the connection", level = LogMessage.Level.WARN)
   void failedToFlushOutstandingDataFromTheConnection(Throwable e);

   @LogMessage(id = 222232, value = "Unable to acquire lock", level = LogMessage.Level.WARN)
   void unableToAcquireLock(Exception e);

   @LogMessage(id = 222233, value = "Unable to destroy connection with session metadata", level = LogMessage.Level.WARN)
   void unableDestroyConnectionWithSessionMetadata(Throwable e);

   @LogMessage(id = 222234, value = "Unable to invoke a callback", level = LogMessage.Level.WARN)
   void unableToInvokeCallback(Throwable e);

   @LogMessage(id = 222235, value = "Unable to inject a monitor", level = LogMessage.Level.WARN)
   void unableToInjectMonitor(Throwable e);

   @LogMessage(id = 222236, value = "Unable to flush deliveries", level = LogMessage.Level.WARN)
   void unableToFlushDeliveries(Exception e);

   @LogMessage(id = 222237, value = "Unable to stop redistributor", level = LogMessage.Level.WARN)
   void unableToCancelRedistributor(Exception e);

   @LogMessage(id = 222238, value = "Unable to commit transaction", level = LogMessage.Level.WARN)
   void unableToCommitTransaction(Exception e);

   @LogMessage(id = 222239, value = "Unable to delete Queue status", level = LogMessage.Level.WARN)
   void unableToDeleteQueueStatus(Exception e);

   @LogMessage(id = 222240, value = "Unable to pause a Queue", level = LogMessage.Level.WARN)
   void unableToPauseQueue(Exception e);

   @LogMessage(id = 222241, value = "Unable to resume a Queue", level = LogMessage.Level.WARN)
   void unableToResumeQueue(Exception e);

   @LogMessage(id = 222242, value = "Unable to obtain message priority, using default ", level = LogMessage.Level.WARN)
   void unableToGetMessagePriority(Throwable e);

   @LogMessage(id = 222243, value = "Unable to extract GroupID from message", level = LogMessage.Level.WARN)
   void unableToExtractGroupID(Throwable e);

   @LogMessage(id = 222244, value = "Unable to check if message expired", level = LogMessage.Level.WARN)
   void unableToCheckIfMessageExpired(Throwable e);

   @LogMessage(id = 222245, value = "Unable to perform post acknowledge", level = LogMessage.Level.WARN)
   void unableToPerformPostAcknowledge(Throwable e);

   @LogMessage(id = 222246, value = "Unable to rollback on close", level = LogMessage.Level.WARN)
   void unableToRollbackOnClose(Exception e);

   @LogMessage(id = 222247, value = "Unable to close consumer", level = LogMessage.Level.WARN)
   void unableToCloseConsumer(Throwable e);

   @LogMessage(id = 222248, value = "Unable to remove consumer", level = LogMessage.Level.WARN)
   void unableToRemoveConsumer(Throwable e);

   @LogMessage(id = 222249, value = "Unable to rollback on TX timed out", level = LogMessage.Level.WARN)
   void unableToRollbackOnTxTimedOut(Exception e);

   @LogMessage(id = 222250, value = "Unable to delete heuristic completion from storage manager", level = LogMessage.Level.WARN)
   void unableToDeleteHeuristicCompletion(Exception e);

   @LogMessage(id = 222251, value = "Unable to start replication", level = LogMessage.Level.WARN)
   void unableToStartReplication(Exception e);

   @LogMessage(id = 222253, value = "Error while syncing data on largeMessageInSync:: {}", level = LogMessage.Level.WARN)
   void errorWhileSyncingData(String target, Throwable e);

   @LogMessage(id = 222254, value = "Invalid record type {}", level = LogMessage.Level.WARN)
   void invalidRecordType(byte type, Throwable e);

   @LogMessage(id = 222256, value = "Failed to unregister acceptor: {}", level = LogMessage.Level.WARN)
   void failedToUnregisterAcceptor(String acceptor, Exception e);

   @LogMessage(id = 222258, value = "Error on deleting queue {}", level = LogMessage.Level.WARN)
   void errorOnDeletingQueue(String queueName, Exception e);

   @LogMessage(id = 222261, value = "Failed to activate a backup", level = LogMessage.Level.WARN)
   void failedToActivateBackup(Exception e);

   @LogMessage(id = 222262, value = "Failed to stop cluster manager", level = LogMessage.Level.WARN)
   void failedToStopClusterManager(Exception e);

   @LogMessage(id = 222263, value = "Failed to stop cluster connection", level = LogMessage.Level.WARN)
   void failedToStopClusterConnection(Exception e);

   @LogMessage(id = 222264, value = "Failed to process message reference after rollback", level = LogMessage.Level.WARN)
   void failedToProcessMessageReferenceAfterRollback(Exception e);

   @LogMessage(id = 222265, value = "Failed to finish delivery, unable to lock delivery", level = LogMessage.Level.WARN)
   void failedToFinishDelivery(Exception e);

   @LogMessage(id = 222266, value = "Failed to send request to the node", level = LogMessage.Level.WARN)
   void failedToSendRequestToNode(Exception e);

   @LogMessage(id = 222267, value = "Failed to disconnect bindings", level = LogMessage.Level.WARN)
   void failedToDisconnectBindings(Exception e);

   @LogMessage(id = 222268, value = "Failed to remove a record", level = LogMessage.Level.WARN)
   void failedToRemoveRecord(Exception e);

   @LogMessage(id = 222269, value = "Please use a fixed value for \"journal-pool-files\". Default changed per https://issues.apache.org/jira/browse/ARTEMIS-1628", level = LogMessage.Level.WARN)
   void useFixedValueOnJournalPoolFiles();

   @LogMessage(id = 222270, value = "Unable to create management notification address: {}", level = LogMessage.Level.WARN)
   void unableToCreateManagementNotificationAddress(SimpleString addressName, Exception e);

   @LogMessage(id = 222702, value = "Message ack in prepared tx for queue {} which does not exist. This ack will be ignored.", level = LogMessage.Level.WARN)
   void journalMessageAckMissingQueueInPreparedTX(Long queueID);

   @LogMessage(id = 222703, value = "Address \"{}\" is full. Bridge {} will disconnect", level = LogMessage.Level.WARN)
   void bridgeAddressFull(String addressName, String bridgeName);

   @LogMessage(id = 222274, value = "Failed to deploy address {}: {}", level = LogMessage.Level.WARN)
   void problemDeployingAddress(String addressName, String message);

   @LogMessage(id = 222275, value = "Failed to deploy queue {}: {}", level = LogMessage.Level.WARN)
   void problemDeployingQueue(String queueName, String message);

   @LogMessage(id = 222278, value = "Unable to extract GroupSequence from message", level = LogMessage.Level.WARN)
   void unableToExtractGroupSequence(Throwable e);

   @LogMessage(id = 222279, value = "Federation upstream {} policy ref {} could not be resolved in federation configuration", level = LogMessage.Level.WARN)
   void federationCantFindPolicyRef(String upstreamName, String policyRef);

   @LogMessage(id = 222280, value = "Federation upstream {} policy ref {} is of unknown type in federation configuration", level = LogMessage.Level.WARN)
   void federationUnknownPolicyType(String upstreamName, String policyRef);

   @LogMessage(id = 222281, value = "Federation upstream {} policy ref {} are too self referential, avoiding stack overflow , ", level = LogMessage.Level.WARN)
   void federationAvoidStackOverflowPolicyRef(String upstreamName, String policyRef);

   @LogMessage(id = 222282, value = "Federation downstream {} upstream transport configuration ref {} could not be resolved in federation configuration", level = LogMessage.Level.WARN)
   void federationCantFindUpstreamConnector(String downstreamName, String upstreamRef);

   @LogMessage(id = 222283, value = "Federation downstream {} has been deployed", level = LogMessage.Level.INFO)
   void federationDownstreamDeployed(String downstreamName);

   @LogMessage(id = 222284, value = "Federation downstream {} has been undeployed", level = LogMessage.Level.INFO)
   void federationDownstreamUnDeployed(String downstreamName);

   @LogMessage(id = 222285, value = "File {} at {} is empty. Delete the empty file to stop this message.", level = LogMessage.Level.WARN)
   void emptyAddressFile(String addressFile, String directory);

   @LogMessage(id = 222286, value = "Error executing {} federation plugin method.", level = LogMessage.Level.WARN)
   void federationPluginExecutionError(String pluginMethod, Throwable e);

   @LogMessage(id = 222287, value = "Error looking up bindings for address {}.", level = LogMessage.Level.WARN)
   void federationBindingsLookupError(SimpleString address, Throwable e);

   @LogMessage(id = 222289, value = "Did not route to any matching bindings on dead-letter-address {} and auto-create-dead-letter-resources is true; dropping message: {}", level = LogMessage.Level.WARN)
   void noMatchingBindingsOnDLAWithAutoCreateDLAResources(SimpleString address, String message);

   @LogMessage(id = 222290, value = "Failed to find cluster-connection when handling cluster-connect packet. Ignoring: {}", level = LogMessage.Level.WARN)
   void failedToFindClusterConnection(String packet);

   @LogMessage(id = 222291, value = "The metrics-plugin element is deprecated and replaced by the metrics element", level = LogMessage.Level.WARN)
   void metricsPluginElementDeprecated();

   @LogMessage(id = 222292, value = "The metrics-plugin element is ignored because the metrics element is defined", level = LogMessage.Level.WARN)
   void metricsPluginElementIgnored();

   // I really want emphasis on this logger, so adding the stars
   @LogMessage(id = 222294, value = "There is a possible split brain on nodeID {}, coming from connectors {}. Topology update ignored.", level = LogMessage.Level.WARN)
   void possibleSplitBrain(String nodeID, String connectionPairInformation);

   // I really want emphasis on this logger, so adding the stars
   @LogMessage(id = 222295, value = "There is a possible split brain on nodeID {}. Topology update ignored", level = LogMessage.Level.WARN)
   void possibleSplitBrain(String nodeID);

   @LogMessage(id = 222296, value = "Unable to deploy Hawtio MBeam, console client side RBAC not available", level = LogMessage.Level.WARN)
   void unableToDeployHawtioMBean(Throwable e);

   @LogMessage(id = 222297, value = "Unable to start Management Context, RBAC not available", level = LogMessage.Level.WARN)
   void unableStartManagementContext(Exception e);

   @LogMessage(id = 222298, value = "Failed to create bootstrap user \"{}\". User management may not function.", level = LogMessage.Level.WARN)
   void failedToCreateBootstrapCredentials(String user, Exception e);

   @LogMessage(id = 222299, value = "No bootstrap credentials found. User management may not function.", level = LogMessage.Level.WARN)
   void noBootstrapCredentialsFound();

   @LogMessage(id = 222300, value = "Getting SSL handler failed when serving client from {}: {}.", level = LogMessage.Level.WARN)
   void gettingSslHandlerFailed(String clientAddress, String cause);

   @LogMessage(id = 222301, value = "Duplicate address-setting match found: {}. These settings will be ignored! Please review your broker.xml and consolidate any duplicate address-setting elements.", level = LogMessage.Level.WARN)
   void duplicateAddressSettingMatch(String match);

   @LogMessage(id = 222302, value = "Failed to deal with property {} when converting message from core to OpenWire: {}", level = LogMessage.Level.WARN)
   void failedToDealWithObjectProperty(SimpleString property, String exceptionMessage);

   @LogMessage(id = 222303, value = "Redistribution by {} of messageID = {} failed", level = LogMessage.Level.WARN)
   void errorRedistributing(String queueName, String m, Throwable t);

   @LogMessage(id = 222304, value = "Unable to load message from journal", level = LogMessage.Level.WARN)
   void unableToLoadMessageFromJournal(Throwable t);

   @LogMessage(id = 222305, value = "Error federating message {}.", level = LogMessage.Level.WARN)
   void federationDispatchError(String message, Throwable e);

   @LogMessage(id = 222306, value = "Failed to load prepared TX and it will be rolled back: {}", level = LogMessage.Level.WARN)
   void failedToLoadPreparedTX(String message, Throwable e);

   @LogMessage(id = 222307, value = "The queues element is deprecated and replaced by the addresses element", level = LogMessage.Level.WARN)
   void queuesElementDeprecated();

   @LogMessage(id = 222308, value = "Unable to listen for incoming fail-back request because {} is null. Ensure the broker has the proper cluster-connection configuration.", level = LogMessage.Level.WARN)
   void failBackCheckerFailure(String component);

   @LogMessage(id = 222309, value = "Trying to remove a producer with ID {} that doesnt exist from session {} on Connection {}.", level = LogMessage.Level.WARN)
   void producerDoesNotExist(int id, String session, String remoteAddress);

   @LogMessage(id = 222310, value = "Trying to add a producer with ID {} that already exists to session {} on Connection {}.", level = LogMessage.Level.WARN)
   void producerAlreadyExists(int id, String session, String remoteAddress);

   @LogMessage(id = 224000, value = "Failure in initialisation", level = LogMessage.Level.ERROR)
   void initializationError(Throwable e);

   @LogMessage(id = 224006, value = "Invalid filter: {}", level = LogMessage.Level.ERROR)
   void invalidFilter(SimpleString filter);

   @LogMessage(id = 224007, value = "page subscription = {} error={}", level = LogMessage.Level.ERROR)
   void pageSubscriptionError(IOCallback IOCallback, String error);

   @LogMessage(id = 224008, value = "Failed to store id", level = LogMessage.Level.ERROR)
   void batchingIdError(Exception e);

   @LogMessage(id = 224009, value = "Cannot find message {}", level = LogMessage.Level.ERROR)
   void cannotFindMessage(Long id);

   @LogMessage(id = 224010, value = "Cannot find queue messages for queueID={} on ack for messageID={}", level = LogMessage.Level.ERROR)
   void journalCannotFindQueue(Long queue, Long id);

   @LogMessage(id = 224011, value = "Cannot find queue messages {} for message {} while processing scheduled messages", level = LogMessage.Level.ERROR)
   void journalCannotFindQueueScheduled(Long queue, Long id);

   @LogMessage(id = 224012, value = "error releasing resources", level = LogMessage.Level.ERROR)
   void largeMessageErrorReleasingResources(Exception e);

   @LogMessage(id = 224014, value = "Failed to close session", level = LogMessage.Level.ERROR)
   void errorClosingSession(Exception e);

   @LogMessage(id = 224015, value = "Caught XA exception", level = LogMessage.Level.ERROR)
   void caughtXaException(Exception e);

   @LogMessage(id = 224016, value = "Caught exception", level = LogMessage.Level.ERROR)
   void caughtException(Throwable e);

   @LogMessage(id = 224017, value = "Invalid packet {}", level = LogMessage.Level.ERROR)
   void invalidPacket(Packet packet);

   @LogMessage(id = 224018, value = "Failed to create session", level = LogMessage.Level.ERROR)
   void failedToCreateSession(Exception e);

   @LogMessage(id = 224019, value = "Failed to reattach session", level = LogMessage.Level.ERROR)
   void failedToReattachSession(Exception e);

   @LogMessage(id = 224020, value = "Failed to handle create queue", level = LogMessage.Level.ERROR)
   void failedToHandleCreateQueue(Exception e);

   @LogMessage(id = 224021, value = "Failed to decode packet", level = LogMessage.Level.ERROR)
   void errorDecodingPacket(Exception e);

   @LogMessage(id = 224022, value = "Failed to execute failure listener", level = LogMessage.Level.ERROR)
   void errorCallingFailureListener(Throwable e);

   @LogMessage(id = 224024, value = "Stomp Error, tx already exist! {}", level = LogMessage.Level.ERROR)
   void stompErrorTXExists(String txID);

   @LogMessage(id = 224027, value = "Failed to write to handler on invm connector {}", level = LogMessage.Level.ERROR)
   void errorWritingToInvmConnector(Runnable runnable, Exception e);

   @LogMessage(id = 224028, value = "Failed to stop acceptor {}", level = LogMessage.Level.ERROR)
   void errorStoppingAcceptor(String name);

   @LogMessage(id = 224029, value = "large message sync: largeMessage instance is incompatible with it, ignoring data", level = LogMessage.Level.ERROR)
   void largeMessageIncompatible();

   @LogMessage(id = 224030, value = "Could not cancel reference {}", level = LogMessage.Level.ERROR)
   void errorCancellingRefOnBridge(MessageReference ref2, Exception e);

   @LogMessage(id = 224032, value = "Failed to pause bridge: {}", level = LogMessage.Level.ERROR)
   void errorPausingBridge(String bridgeName, Exception e);

   @LogMessage(id = 224033, value = "Failed to broadcast connector configs", level = LogMessage.Level.ERROR)
   void errorBroadcastingConnectorConfigs(Exception e);

   @LogMessage(id = 224034, value = "Failed to close consumer", level = LogMessage.Level.ERROR)
   void errorClosingConsumer(Exception e);

   @LogMessage(id = 224036, value = "Failed to update cluster connection topology", level = LogMessage.Level.ERROR)
   void errorUpdatingTopology(Exception e);

   @LogMessage(id = 224037, value = "cluster connection Failed to handle message", level = LogMessage.Level.ERROR)
   void errorHandlingMessage(Exception e);

   @LogMessage(id = 224038, value = "Failed to ack old reference", level = LogMessage.Level.ERROR)
   void errorAckingOldReference(Exception e);

   @LogMessage(id = 224039, value = "Failed to expire message reference", level = LogMessage.Level.ERROR)
   void errorExpiringRef(Exception e);

   @LogMessage(id = 224040, value = "Failed to remove consumer", level = LogMessage.Level.ERROR)
   void errorRemovingConsumer(Exception e);

   @LogMessage(id = 224041, value = "Failed to deliver", level = LogMessage.Level.ERROR)
   void errorDelivering(Exception e);

   @LogMessage(id = 224042, value = "Error while restarting the backup server: {}", level = LogMessage.Level.ERROR)
   void errorRestartingBackupServer(ActiveMQServer backup, Exception e);

   @LogMessage(id = 224043, value = "Failed to send forced delivery message", level = LogMessage.Level.ERROR)
   void errorSendingForcedDelivery(Exception e);

   @LogMessage(id = 224044, value = "error acknowledging message", level = LogMessage.Level.ERROR)
   void errorAckingMessage(Exception e);

   @LogMessage(id = 224045, value = "Failed to run large message deliverer", level = LogMessage.Level.ERROR)
   void errorRunningLargeMessageDeliverer(Exception e);

   @LogMessage(id = 224046, value = "Exception while browser handled from {}", level = LogMessage.Level.ERROR)
   void errorBrowserHandlingMessage(MessageReference current, Exception e);

   @LogMessage(id = 224047, value = "Failed to delete large message file", level = LogMessage.Level.ERROR)
   void errorDeletingLargeMessageFile(Throwable e);

   @LogMessage(id = 224048, value = "Failed to remove temporary resource {}", level = LogMessage.Level.ERROR)
   void errorRemovingTempResource(SimpleString resourceName, Exception e);

   @LogMessage(id = 224049, value = "Cannot find consumer with id {}", level = LogMessage.Level.ERROR)
   void cannotFindConsumer(long consumerID);

   @LogMessage(id = 224050, value = "Failed to close connection {}", level = LogMessage.Level.ERROR)
   void errorClosingConnection(ServerSessionImpl serverSession);

   @LogMessage(id = 224051, value = "Failed to call notification listener", level = LogMessage.Level.ERROR)
   void errorCallingNotifListener(Exception e);

   @LogMessage(id = 224052, value = "Unable to call Hierarchical Repository Change Listener", level = LogMessage.Level.ERROR)
   void errorCallingRepoListener(Throwable e);

   @LogMessage(id = 224053, value = "failed to timeout transaction, xid:{}", level = LogMessage.Level.ERROR)
   void errorTimingOutTX(Xid xid, Exception e);

   @LogMessage(id = 224054, value = "exception while stopping the replication manager", level = LogMessage.Level.ERROR)
   void errorStoppingReplicationManager(Throwable t);

   @LogMessage(id = 224055, value = "Bridge Failed to ack", level = LogMessage.Level.ERROR)
   void bridgeFailedToAck(Throwable t);

   @LogMessage(id = 224056, value = "Primary server will not fail-back automatically", level = LogMessage.Level.ERROR)
   void autoFailBackDenied();

   @LogMessage(id = 224057, value = "Backup server that requested fail-back was not announced. Server will not stop for fail-back.", level = LogMessage.Level.ERROR)
   void failbackMissedBackupAnnouncement();

   @LogMessage(id = 224058, value = "Stopping ClusterManager. As it failed to authenticate with the cluster: {}", level = LogMessage.Level.ERROR)
   void clusterManagerAuthenticationError(String msg);

   @LogMessage(id = 224059, value = "Invalid cipher suite specified. Supported cipher suites are: {}", level = LogMessage.Level.ERROR)
   void invalidCipherSuite(String validSuites);

   @LogMessage(id = 224060, value = "Invalid protocol specified. Supported protocols are: {}", level = LogMessage.Level.ERROR)
   void invalidProtocol(String validProtocols);

   @Deprecated(since = "1.0.0")
   @LogMessage(id = 224061, value = "Setting both <{}> and <ha-policy> is invalid. Please use <ha-policy> exclusively. Ignoring value.", level = LogMessage.Level.ERROR)
   void incompatibleWithHAPolicy(String parameter);

   @LogMessage(id = 224062, value = "Failed to send SLOW_CONSUMER notification: {}", level = LogMessage.Level.ERROR)
   void failedToSendSlowConsumerNotification(Notification notification, Exception e);

   @LogMessage(id = 224063, value = "Failed to close consumer connections for address {}", level = LogMessage.Level.ERROR)
   void failedToCloseConsumerConnectionsForAddress(String address, Exception e);

   @Deprecated(since = "1.0.0")
   @LogMessage(id = 224064, value = "Setting <{}> is invalid with this HA Policy Configuration. Please use <ha-policy> exclusively or remove. Ignoring value.", level = LogMessage.Level.ERROR)
   void incompatibleWithHAPolicyChosen(String parameter);

   @LogMessage(id = 224065, value = "Failed to remove auto-created {} {}", level = LogMessage.Level.ERROR)
   void errorRemovingAutoCreatedDestination(String destinationType, SimpleString bindingName, Exception e);

   @LogMessage(id = 224066, value = "Error opening context for LDAP", level = LogMessage.Level.ERROR)
   void errorOpeningContextForLDAP(Exception e);

   @LogMessage(id = 224067, value = "Error populating security roles from LDAP", level = LogMessage.Level.ERROR)
   void errorPopulatingSecurityRolesFromLDAP(Exception e);

   @LogMessage(id = 224068, value = "Unable to stop component: {}", level = LogMessage.Level.ERROR)
   void errorStoppingComponent(String componentClassName, Throwable t);

   @LogMessage(id = 224069, value = "Change detected in broker configuration file, but reload failed", level = LogMessage.Level.ERROR)
   void configurationReloadFailed(Throwable t);

   @LogMessage(id = 224072, value = "Message Counter Sample Period too short: {}", level = LogMessage.Level.WARN)
   void invalidMessageCounterPeriod(long value);

   @LogMessage(id = 224073, value = "Using MAPPED Journal", level = LogMessage.Level.INFO)
   void journalUseMAPPED();

   @LogMessage(id = 224074, value = "Failed to purge queue {} on no consumers", level = LogMessage.Level.ERROR)
   void failedToPurgeQueue(SimpleString bindingName, Exception e);

   @LogMessage(id = 224075, value = "Cannot find pageTX id = {}", level = LogMessage.Level.ERROR)
   void journalCannotFindPageTX(Long id);

   @LogMessage(id = 224079, value = "The process for the virtual machine will be killed, as component {} is not responsive", level = LogMessage.Level.ERROR)
   void criticalSystemHalt(Object component);

   @LogMessage(id = 224080, value = "The server process will now be stopped, as component {} is not responsive", level = LogMessage.Level.ERROR)
   void criticalSystemShutdown(Object component);

   @LogMessage(id = 224081, value = "The component {} is not responsive", level = LogMessage.Level.ERROR)
   void criticalSystemLog(Object component);

   @LogMessage(id = 224076, value = "Undeploying address {}", level = LogMessage.Level.INFO)
   void undeployAddress(SimpleString addressName);

   @LogMessage(id = 224077, value = "Undeploying queue {}", level = LogMessage.Level.INFO)
   void undeployQueue(SimpleString queueName);

   @LogMessage(id = 224078, value = "The size of duplicate cache detection (<id_cache-size/>) appears to be too large {}. It should be no greater than the number of messages that can be squeezed into confirmation window buffer (<confirmation-window-size/>) {}.", level = LogMessage.Level.WARN)
   void duplicateCacheSizeWarning(int idCacheSize, int confirmationWindowSize);

   @LogMessage(id = 224082, value = "Failed to invoke an interceptor", level = LogMessage.Level.ERROR)
   void failedToInvokeAnInterceptor(Exception e);

   @LogMessage(id = 224083, value = "Failed to close context", level = LogMessage.Level.ERROR)
   void failedToCloseContext(Exception e);

   @LogMessage(id = 224084, value = "Failed to open context", level = LogMessage.Level.ERROR)
   void failedToOpenContext(Exception e);

   @LogMessage(id = 224085, value = "Failed to load property {}, reason: {}", level = LogMessage.Level.ERROR)
   void failedToLoadProperty(String key, String reason, Exception e);

   @LogMessage(id = 224086, value = "Caught unexpected exception", level = LogMessage.Level.ERROR)
   void caughtUnexpectedException(NamingException e);

   @LogMessage(id = 224087, value = "Error announcing backup: backupServerLocator is null. {}", level = LogMessage.Level.ERROR)
   void errorAnnouncingBackup(String backupManager);

   @LogMessage(id = 224088, value = "Timeout ({} seconds) on acceptor \"{}\" during protocol handshake with {} has occurred.", level = LogMessage.Level.ERROR)
   void handshakeTimeout(int timeout, String acceptorName, String remoteAddress);

   @LogMessage(id = 224089, value = "Failed to calculate persistent size", level = LogMessage.Level.WARN)
   void errorCalculatePersistentSize(Throwable e);

   @LogMessage(id = 224090, value = "This node is not configured for the proper Quorum Voting, all nodes must be configured for the same policy. Handler received = {}", level = LogMessage.Level.WARN)
   void noVoteHandlerConfigured(SimpleString handlerReceived);

   @LogMessage(id = 224091, value = "Bridge {} is unable to connect to destination. Retrying", level = LogMessage.Level.WARN)
   void errorConnectingBridgeRetry(Bridge bridge);

   @LogMessage(id = 224092, value = "Despite disabled persistence, page files will be persisted.", level = LogMessage.Level.INFO)
   void pageWillBePersisted();

   @LogMessage(id = 224093, value = "Reference to message is null", level = LogMessage.Level.ERROR)
   void nullRefMessage();

   @LogMessage(id = 224094, value = "Quorum vote result await is interrupted", level = LogMessage.Level.TRACE)
   void quorumVoteAwaitInterrupted();

   @LogMessage(id = 224095, value = "Error updating Consumer Count: {}", level = LogMessage.Level.ERROR)
   void consumerCountError(String reason);

   @LogMessage(id = 224096, value = "Error setting up connection from {} to {}; protocol {} not found in map: {}", level = LogMessage.Level.ERROR)
   void failedToFindProtocolManager(String remoteAddress, String localAddress, String intendedProtocolManager, String protocolMap);

   @LogMessage(id = 224097, value = "Failed to start server", level = LogMessage.Level.ERROR)
   void failedToStartServer(Throwable t);

   @LogMessage(id = 224098, value = "Received a vote saying the backup is active with connector: {}", level = LogMessage.Level.INFO)
   void quorumBackupIsActive(String connector);

   @LogMessage(id = 224099, value = "Message with ID {} has a header too large. More information available on debug level for class {}", level = LogMessage.Level.WARN)
   void messageWithHeaderTooLarge(Long messageID, String loggerClass);

   @LogMessage(id = 224101, value = "Apache Artemis is using a scheduled pool without remove on cancel policy, so a cancelled task could be not automatically removed from the work queue, it may also cause unbounded retention of cancelled tasks.", level = LogMessage.Level.WARN)
   void scheduledPoolWithNoRemoveOnCancelPolicy();

   @LogMessage(id = 224102, value = "unable to undeploy address {} : reason {}", level = LogMessage.Level.INFO)
   void unableToUndeployAddress(SimpleString addressName, String reason);

   @LogMessage(id = 224103, value = "unable to undeploy queue {} : reason {}", level = LogMessage.Level.INFO)
   void unableToUndeployQueue(SimpleString queueName, String reason);

   @LogMessage(id = 224104, value = "Error starting the Acceptor {} {}", level = LogMessage.Level.ERROR)
   void errorStartingAcceptor(String name, Object configuration);

   @LogMessage(id = 224105, value = "Connecting to cluster failed", level = LogMessage.Level.WARN)
   void failedConnectingToCluster(Exception e);

   @LogMessage(id = 224106, value = "failed to remove transaction, xid:{}", level = LogMessage.Level.ERROR)
   void errorRemovingTX(Xid xid, Exception e);

   @LogMessage(id = 224107, value = "The Critical Analyzer detected slow paths on the broker.  It is recommended that you enable trace logs on org.apache.activemq.artemis.utils.critical while you troubleshoot this issue. You should disable the trace logs when you have finished troubleshooting.", level = LogMessage.Level.INFO)
   void enableTraceForCriticalAnalyzer();

   @LogMessage(id = 224108, value = "Stopped paging on address '{}'; {}", level = LogMessage.Level.INFO)
   void pageStoreStop(SimpleString storeName, String pageInfo);

   @LogMessage(id = 224109, value = "ConnectionRouter {} not found", level = LogMessage.Level.WARN)
   void connectionRouterNotFound(String name);

   @LogMessage(id = 224110, value = "Configuration 'whitelist' is deprecated, please use the 'allowlist' configuration", level = LogMessage.Level.WARN)
   void useAllowList();

   @LogMessage(id = 224111, value = "Both 'whitelist' and 'allowlist' detected. Configuration 'whitelist' is deprecated, please use only the 'allowlist' configuration", level = LogMessage.Level.WARN)
   void useOnlyAllowList();

   @LogMessage(id = 224112, value = "Auto removing queue {} with queueID={} on address={}", level = LogMessage.Level.INFO)
   void autoRemoveQueue(String name, long queueID, String address);

   @LogMessage(id = 224113, value = "Auto removing address {}", level = LogMessage.Level.INFO)
   void autoRemoveAddress(String name);

   @LogMessage(id = 224114, value = "Address control block, blocking message production on address '{}'. Clients will not get further credit.", level = LogMessage.Level.INFO)
   void blockingViaControl(SimpleString addressName);

   @LogMessage(id = 224115, value = "Address control unblock of address '{}'. Clients will be granted credit as normal.", level = LogMessage.Level.INFO)
   void unblockingViaControl(SimpleString addressName);

   @LogMessage(id = 224116, value = "The component {} is not responsive during start up. The Server may be taking too long to start", level = LogMessage.Level.WARN)
   void tooLongToStart(Object component);

   @LogMessage(id = 224117, value = "\"page-max-cache-size\" being used on broker.xml. This configuration attribute is no longer used and it will be ignored.", level = LogMessage.Level.INFO)
   void pageMaxSizeUsed();

   @LogMessage(id = 224118, value = "The SQL Database is returning a current time too far from this system current time. Adjust clock on the SQL Database server. DatabaseTime={}, CurrentTime={}, allowed variance={}", level = LogMessage.Level.WARN)
   void dbReturnedTimeOffClock(long dbTime, long systemTime, long variance);

   @LogMessage(id = 224119, value = "Unable to refresh security settings: {}", level = LogMessage.Level.WARN)
   void unableToRefreshSecuritySettings(String exceptionMessage);

   @LogMessage(id = 224120, value = "Queue {} on Address {} has more messages than configured page limit. PageLimitMessages={} while currentValue={}", level = LogMessage.Level.WARN)
   void pageFull(SimpleString queue, SimpleString address, Object pageLImitMessage, Object currentValue);

   @LogMessage(id = 224122, value = "Address {} number of messages is under page limit again, and it should be allowed to page again.", level = LogMessage.Level.INFO)
   void pageFree(SimpleString address);

   @LogMessage(id = 224123, value = "Address {} has more pages than allowed. System currently has {} pages, while the estimated max number of pages is {} based on the page-limit-bytes ({}) / page-size ({})", level = LogMessage.Level.WARN)
   void pageFullMaxBytes(SimpleString address, long pages, Long maxPages, Long limitBytes, int bytes);

   @LogMessage(id = 224124, value = "Address {} has page-full-policy={} but neither page-limit-bytes nor page-limit-messages are set. Page full configuration being ignored on this address.", level = LogMessage.Level.WARN)
   void noPageLimitsSet(Object address, Object policy);

   @LogMessage(id = 224125, value = "Address {} has page-limit-bytes={} and page-limit-messages={} but no page-full-policy set. Page full configuration being ignored on this address", level = LogMessage.Level.WARN)
   void noPagefullPolicySet(Object address, Object limitBytes, Object limitMessages);

   @LogMessage(id = 224126, value = "Failure during protocol handshake on connection to {} from {}", level = LogMessage.Level.ERROR)
   void failureDuringProtocolHandshake(String localAddress, String remoteAddress, Throwable e);

   // Note the custom loggerName rather than the overall LogBundle-wide definition used by other methods.
   @LogMessage(id = 224127, value = "Message dispatch from paging is blocked. Address {}/Queue {} will not read any more messages from paging until pending messages are acknowledged. "
                                  + "There are currently {} messages pending ({} bytes) with max reads at maxPageReadMessages({}) and maxPageReadBytes({}). "
                                  + "Either increase reading attributes at the address-settings or change your consumers to acknowledge more often.",
                                  loggerName = "org.apache.activemq.artemis.core.server.Queue", level = LogMessage.Level.WARN)
   void warnPageFlowControl(String address, String queue, long messageCount, long messageBytes, long maxMessages, long maxMessagesBytes);

   @LogMessage(id = 224128, value = "Free storage space is at {} which is below the configured <min-disk-free>. System will start blocking producers.", level = LogMessage.Level.WARN)
   void minDiskFreeReached(String usableSpace);

   @LogMessage(id = 224129, value = "Free storage space is at {} which is above the configured <min-disk-free>. System will unblock producers.", level = LogMessage.Level.WARN)
   void minDiskFreeRestored(String usableSpace);

   @LogMessage(id = 224130, value = "The {} value {} will override the {} value {} since both are set.", level = LogMessage.Level.INFO)
   void configParamOverride(String overridingParam, Object overridingParamValue, String overriddenParam, Object overriddenParamValue);

   @LogMessage(id = 224131, value = "Removing orphaned page transaction {}", level = LogMessage.Level.INFO)
   void removeOrphanedPageTransaction(long transactionID);

   @LogMessage(id = 224132, value = "{} orphaned page transactions were removed", level = LogMessage.Level.INFO)
   void completeOrphanedTXCleanup(long numberOfPageTx);

   @LogMessage(id = 224133, value = "{} orphaned page transactions have been removed", level = LogMessage.Level.INFO)
   void cleaningOrphanedTXCleanup(long numberOfPageTx);

   @LogMessage(id = 224134, value = "Connection closed with failedOver={}", level = LogMessage.Level.INFO)
   void bridgeConnectionClosed(Boolean failedOver);

   @LogMessage(id = 224135, value = "nodeID {} is closing. Topology update ignored", level = LogMessage.Level.INFO)
   void nodeLeavingCluster(String nodeID);

   @LogMessage(id = 224136, value = "Skipping correlation ID when converting message to OpenWire since byte[] value is not valid UTF-8: {}", level = LogMessage.Level.WARN)
   void unableToDecodeCorrelationId(String message);

   @LogMessage(id = 224137, value = "Skipping SSL auto reload for sources of store {} because of {}", level = LogMessage.Level.WARN)
   void skipSSLAutoReloadForSourcesOfStore(String storePath, String reason);

   @LogMessage(id = 224138, value = "Error Registering DuplicateCacheSize on namespace {}", level = LogMessage.Level.WARN)
   void errorRegisteringDuplicateCacheSize(String address, Exception reason);

   @LogMessage(id = 224139, value = "Failed to stop bridge: {}", level = LogMessage.Level.ERROR)
   void errorStoppingBridge(String bridgeName, Exception e);

   @LogMessage(id = 224140, value = "Clearing bindings on cluster-connection {} failed to remove binding {}: {}", level = LogMessage.Level.WARN)
   void clusterConnectionFailedToRemoveBindingOnClear(String clusterConnection, String binding, String exceptionMessage);

   @LogMessage(id = 224141, value = "Illegal implementation(s) of java.security.Principal returned from LoginModule:  {}", level = LogMessage.Level.WARN)
   void illegalPrincipal(String principals);

   @LogMessage(id = 224142, value = "Unexpected result disconnecting consumer {} while closing session {}: {}", level = LogMessage.Level.WARN)
   void unexpectedResultDisconnectingConsumer(long consumerId, String sessionId, String exceptionMessage);

   @LogMessage(id = 224143, value = "Bridge {} failed to send {}: {} {}", level = LogMessage.Level.WARN)
   void bridgeFailedToSend(String bridgeName, String message, String exceptionName, String exceptionMessage);

   @LogMessage(id = 224144, value = "The topology of the cluster connection {} doesn't include all the expected members. "
       + "Check the discovery group or the static connectors of the cluster connection if the topology is correct: {} / {}", level = LogMessage.Level.WARN)
   void incompleteClusterTopology(String clusterConnection, Topology topology, String topologyMembers);

   @LogMessage(id = 224145, value = "Error looking up bindings for address {}.", level = LogMessage.Level.WARN)
   void bridgeBindingsLookupError(SimpleString address, Throwable e);

   @LogMessage(id = 224146, value = "Purging Page Folder {} on address {}", level = LogMessage.Level.INFO)
   void purgingPageFolder(String folder, SimpleString address);

   @LogMessage(id = 224147, value = "Failed purging folder {}", level = LogMessage.Level.WARN)
   void failedPurgingFolder(String folder);

   @LogMessage(id = 224148, value = "Messages are being dropped on address {} / queue {}. Queue is disabled.", level = LogMessage.Level.INFO)
   void noRouteDisabledQueue(SimpleString addressName, SimpleString queueName);

   @LogMessage(id = 224149, value = "Messages are being dropped on address {} / queue {}. PurgeOnNoConsumers=true and there are no consumers on this queue.", level = LogMessage.Level.INFO)
   void noRouteNoConsumers(SimpleString addressName, SimpleString queueName);

   @LogMessage(id = 224150, value = "Messages will be dropped on address {} / queue {}. Queue is disabled.", level = LogMessage.Level.INFO)
   void noRouteMessagesWillBeDropped(SimpleString addressName, SimpleString queueName);

   @LogMessage(id = 224151, value = "Bridge {} unable to handle message: {}. Root cause: {}", level = LogMessage.Level.INFO)
   void bridgeUnableToHandleMessage(SimpleString bridgeName, String message, String exceptionMessage);

   @LogMessage(id = 224152, value = "Closing connection from {} for PROXY Protocol violation. Acceptor {} uses proxyProtocolEnabled={}, but connection {} PROXY Protocol.", level = LogMessage.Level.WARN)
   void proxyProtocolViolation(String remoteAddress, String acceptorName, boolean proxyProtocolEnabled, String actualUsage);

   @LogMessage(id = 224153, value = "Unable to find page {} on Address {} while reloading ACKNOWLEDGE_CURSOR, deleting record {}.", level = LogMessage.Level.INFO)
   void cannotFindPageFileDuringPageAckReload(long pageNr, Object address, long id);
}