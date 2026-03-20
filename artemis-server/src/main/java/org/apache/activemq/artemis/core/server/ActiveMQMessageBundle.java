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

import java.util.Set;

import org.apache.activemq.artemis.api.core.ActiveMQAddressDoesNotExistException;
import org.apache.activemq.artemis.api.core.ActiveMQAddressExistsException;
import org.apache.activemq.artemis.api.core.ActiveMQAddressFullException;
import org.apache.activemq.artemis.api.core.ActiveMQAddressHasBindingsException;
import org.apache.activemq.artemis.api.core.ActiveMQClusterSecurityException;
import org.apache.activemq.artemis.api.core.ActiveMQConnectionTimedOutException;
import org.apache.activemq.artemis.api.core.ActiveMQDisconnectedException;
import org.apache.activemq.artemis.api.core.ActiveMQDivertDoesNotExistException;
import org.apache.activemq.artemis.api.core.ActiveMQDuplicateMetaDataException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQIOErrorException;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.ActiveMQIncompatibleClientServerException;
import org.apache.activemq.artemis.api.core.ActiveMQInternalErrorException;
import org.apache.activemq.artemis.api.core.ActiveMQInvalidFilterExpressionException;
import org.apache.activemq.artemis.api.core.ActiveMQInvalidTransientQueueUseException;
import org.apache.activemq.artemis.api.core.ActiveMQNonExistentQueueException;
import org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException;
import org.apache.activemq.artemis.api.core.ActiveMQQueueMaxConsumerLimitReached;
import org.apache.activemq.artemis.api.core.ActiveMQRemoteDisconnectException;
import org.apache.activemq.artemis.api.core.ActiveMQReplicationTimeooutException;
import org.apache.activemq.artemis.api.core.ActiveMQRoutingException;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.ActiveMQSessionCreationException;
import org.apache.activemq.artemis.api.core.ActiveMQTimeoutException;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReplicationSyncFileMessage;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.server.cluster.impl.BridgeImpl;
import org.apache.activemq.artemis.logs.BundleFactory;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.Message;

/**
 * Logger Codes 229000 - 229999
 */
@LogBundle(projectCode = "AMQ", regexID = "229[0-9]{3}", retiredIDs = {229001, 229003, 229008, 229010, 229060, 229064, 229075, 229100, 229101, 229118, 229201, 229202, 229246})
public interface ActiveMQMessageBundle {

   ActiveMQMessageBundle BUNDLE = BundleFactory.newBundle(ActiveMQMessageBundle.class);

   @Message(id = 229000, value = "Activation for server {}")
   String activationForServer(ActiveMQServer server);

   @Message(id = 229002, value = "Thread {} name = {} id = {} group = {}")
   String threadDump(Thread key, String name, Long id, ThreadGroup threadGroup);

   @Message(id = 229004, value = "Information about server {}\nCluster Connection:{}")
   String serverDescribe(String identity, String describe);

   @Message(id = 229005, value = "connections for {} closed by management")
   ActiveMQInternalErrorException connectionsClosedByManagement(String ipAddress);

   @Message(id = 229006, value = "journals are not JournalImpl. You can't set a replicator!")
   ActiveMQInternalErrorException notJournalImpl();

   @Message(id = 229007, value = "unhandled error during replication")
   ActiveMQInternalErrorException replicationUnhandledError(Exception e);

   @Message(id = 229009, value = "Unhandled file type {}")
   ActiveMQInternalErrorException replicationUnhandledFileType(ReplicationSyncFileMessage.FileType fileType);

   @Message(id = 229011, value = "unhandled data type!")
   ActiveMQInternalErrorException replicationUnhandledDataType();

   @Message(id = 229012, value = "No binding for divert {}")
   ActiveMQInternalErrorException noBindingForDivert(SimpleString name);

   @Message(id = 229013, value = "Binding {} is not a divert")
   ActiveMQInternalErrorException bindingNotDivert(SimpleString name);

   @Message(id = 229014, value = "Did not receive data from {} within the {}ms connection TTL. The connection will now be closed.")
   ActiveMQConnectionTimedOutException clientExited(String remoteAddress, long ttl);

   @Message(id = 229015, value = "Must specify a name for each divert. This one will not be deployed.")
   ActiveMQInternalErrorException divertWithNoName();

   @Message(id = 229017, value = "Queue {} does not exist")
   ActiveMQNonExistentQueueException noSuchQueue(SimpleString queueName);

   @Message(id = 229018, value = "Binding already exists {}")
   ActiveMQQueueExistsException bindingAlreadyExists(Binding binding);

   @Message(id = 229019, value = "Queue {} already exists on address {}")
   ActiveMQQueueExistsException queueAlreadyExists(SimpleString queueName, SimpleString addressName);

   @Message(id = 229020, value = "Invalid filter: {}")
   ActiveMQInvalidFilterExpressionException invalidFilter(SimpleString filter, Throwable e);

   @Message(id = 229021, value = "MessageId was not assigned to Message")
   ActiveMQIllegalStateException messageIdNotAssigned();

   @Message(id = 229022, value = "Cannot compare journals if not in sync!")
   ActiveMQIllegalStateException journalsNotInSync();

   @Message(id = 229023, value = "Connected server is not a backup server")
   ActiveMQIllegalStateException serverNotBackupServer();

   @Message(id = 229024, value = "Backup replication server is already connected to another server")
   ActiveMQIllegalStateException alreadyHaveReplicationServer();

   @Message(id = 229025, value = "Cannot delete queue {} on binding {} - it has consumers = {}")
   ActiveMQIllegalStateException cannotDeleteQueueWithConsumers(SimpleString name, SimpleString queueName, String s);

   @Message(id = 229026, value = "Backup Server was not yet in sync with live")
   ActiveMQIllegalStateException backupServerNotInSync();

   @Message(id = 229027, value = "Could not find reference on consumer ID={}, messageId = {} queue = {}")
   ActiveMQIllegalStateException consumerNoReference(Long id, Long messageID, SimpleString name);

   @Message(id = 229028, value = "Consumer {} doesn't exist on the server")
   ActiveMQIllegalStateException consumerDoesntExist(long consumerID);

   @Message(id = 229029, value = "No address configured on the Server's Session")
   ActiveMQIllegalStateException noAddress();

   @Message(id = 229030, value = "large-message not initialized on server")
   ActiveMQIllegalStateException largeMessageNotInitialised();

   @Message(id = 229031, value = "Unable to validate user from {}. Username: {}; SSL certificate subject DN: {}; UPN: {}")
   ActiveMQSecurityException unableToValidateUser(String remoteAddress, String user, String dn, String upn);

   @Message(id = 229032, value = "User: {} does not have permission='{}' on address {}")
   ActiveMQSecurityException userNoPermissions(String username, CheckType checkType, SimpleString address);

   @Message(id = 229033, value = "Server and client versions incompatible")
   ActiveMQIncompatibleClientServerException incompatibleClientServer();

   @Message(id = 229034, value = "Server not started")
   ActiveMQSessionCreationException serverNotStarted();

   @Message(id = 229035, value = "Metadata {}={} had been set already")
   ActiveMQDuplicateMetaDataException duplicateMetadata(String key, String data);

   @Message(id = 229036, value = "Invalid type: {}")
   IllegalArgumentException invalidType(Object type);

   @Message(id = 229038, value = "{} must neither be null nor empty")
   IllegalArgumentException emptyOrNull(String name);

   @Message(id = 229039, value = "{}  must be greater than 0 (actual value: {})")
   IllegalArgumentException greaterThanZero(String name, Number val);

   @Message(id = 229040, value = "{} must be a valid percentual value between 0 and 100 (actual value: {})")
   IllegalArgumentException notPercent(String name, Number val);

   @Message(id = 229041, value = "{}  must be equals to -1 or greater than 0 (actual value: {})")
   IllegalArgumentException greaterThanMinusOne(String name, Number val);

   @Message(id = 229042, value = "{}  must be equals to -1 or greater or equals to 0 (actual value: {})")
   IllegalArgumentException greaterThanZeroOrMinusOne(String name, Number val);

   @Message(id = 229043, value = "{} must be between {} and {} inclusive (actual value: {})")
   IllegalArgumentException mustbeBetween(String name, Integer minPriority, Integer maxPriority, Object value);

   @Message(id = 229044, value = "Invalid journal type {}")
   IllegalArgumentException invalidJournalType(String val);

   @Message(id = 229045, value = "Invalid address full message policy type {}")
   IllegalArgumentException invalidAddressFullPolicyType(String val);

   @Message(id = 229046, value = "invalid value: {} count must be greater than 0")
   IllegalArgumentException greaterThanZero(Integer count);

   @Message(id = 229047, value = "invalid value: {} sample period must be greater than 0")
   IllegalArgumentException periodMustGreaterThanZero(Long newPeriod);

   @Message(id = 229048, value = "invalid new Priority value: {}. It must be between 0 and 9 (both included)")
   IllegalArgumentException invalidNewPriority(Integer period);

   @Message(id = 229049, value = "No queue found for {}")
   IllegalArgumentException noQueueFound(String otherQueueName);

   @Message(id = 229050, value = "Only NIO and AsyncIO are supported journals")
   IllegalArgumentException invalidJournal();

   @Message(id = 229051, value = "Invalid journal type {}")
   IllegalArgumentException invalidJournalType2(JournalType journalType);

   @Message(id = 229052, value = "Directory {} does not exist and cannot be created")
   IllegalArgumentException cannotCreateDir(String dir);

   @Message(id = 229054, value = "Cannot convert to int")
   IllegalArgumentException cannotConvertToInt();

   @Message(id = 229055, value = "Routing name is null")
   IllegalArgumentException routeNameIsNull();

   @Message(id = 229056, value = "Cluster name is null")
   IllegalArgumentException clusterNameIsNull();

   @Message(id = 229057, value = "Address is null")
   IllegalArgumentException addressIsNull();

   @Message(id = 229058, value = "Binding type not specified")
   IllegalArgumentException bindingTypeNotSpecified();

   @Message(id = 229059, value = "Binding ID is null")
   IllegalArgumentException bindingIdNotSpecified();

   @Message(id = 229061, value = "Connection already exists with id {}")
   IllegalArgumentException connectionExists(Object id);

   @Message(id = 229062, value = "Acceptor with id {} already registered")
   IllegalArgumentException acceptorExists(Integer id);

   @Message(id = 229063, value = "Acceptor with id {} not registered")
   IllegalArgumentException acceptorNotExists(Integer id);

   @Message(id = 229065, value = "node id is null")
   IllegalArgumentException nodeIdNull();

   @Message(id = 229066, value = "Queue name is null")
   IllegalArgumentException queueNameIsNull();

   @Message(id = 229067, value = "Cannot find resource with name {}")
   IllegalArgumentException cannotFindResource(String resourceName);

   @Message(id = 229068, value = "no getter method for {}")
   IllegalArgumentException noGetterMethod(String resourceName);

   @Message(id = 229069, value = "no operation {}/{}")
   IllegalArgumentException noOperation(String operation, Integer length);

   @Message(id = 229070, value = "match can not be null")
   IllegalArgumentException nullMatch();

   @Message(id = 229071, value = "# can only be at end of match")
   IllegalArgumentException invalidMatch();

   @Message(id = 229072, value = "User cannot be null")
   IllegalArgumentException nullUser();

   @Message(id = 229073, value = "Password cannot be null")
   IllegalArgumentException nullPassword();

   @Message(id = 229074, value = "Error instantiating transformer class {}")
   IllegalArgumentException errorCreatingTransformerClass(String transformerClassName, Exception e);

   /**
    * Message used on on
    * {@link
    * org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl#destroyConnectionWithSessionMetadata(String,
    * String)}
    */
   @Message(id = 229076, value = "Executing destroyConnection with {}={} through management's request")
   String destroyConnectionWithSessionMetadataHeader(String key, String value);

   /**
    * Message used on on
    * {@link
    * org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl#destroyConnectionWithSessionMetadata(String,
    * String)}
    */
   @Message(id = 229077, value = "Closing connection {}")
   String destroyConnectionWithSessionMetadataClosingConnection(String serverSessionString);

   /**
    * Exception used on on
    * {@link
    * org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl#destroyConnectionWithSessionMetadata(String,
    * String)}
    */
   @Message(id = 229078, value = "Disconnected per admin's request on {}={}")
   ActiveMQDisconnectedException destroyConnectionWithSessionMetadataSendException(String key, String value);

   /**
    * Message used on on
    * {@link
    * org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl#destroyConnectionWithSessionMetadata(String,
    * String)}
    */
   @Message(id = 229079, value = "No session found with {}={}")
   String destroyConnectionWithSessionMetadataNoSessionFound(String key, String value);

   @Message(id = 229080, value = "Invalid Page IO, PagingManager was stopped or closed")
   ActiveMQIllegalStateException invalidPageIO();

   @Message(id = 229081, value = "No Discovery Group configuration named {} found")
   ActiveMQException noDiscoveryGroupFound(DiscoveryGroupConfiguration dg);

   @Message(id = 229082, value = "Queue {} already exists on another subscription")
   ActiveMQInvalidTransientQueueUseException queueSubscriptionBelongsToDifferentAddress(SimpleString queueName);

   @Message(id = 229083, value = "Queue {} has a different filter than requested")
   ActiveMQInvalidTransientQueueUseException queueSubscriptionBelongsToDifferentFilter(SimpleString queueName);

   // this code has to match with version 2.3.x as it's used on integration tests at Wildfly and JBoss EAP
   @Message(id = 229099, value = "Unable to authenticate cluster user: {}")
   ActiveMQClusterSecurityException unableToValidateClusterUser(String user);

   @Message(id = 229102, value = "Address \"{}\" is full.")
   ActiveMQAddressFullException addressIsFull(String addressName);

   @Message(id = 229103, value = "No Connectors or Discovery Groups configured for Scale Down")
   ActiveMQException noConfigurationFoundForScaleDown();

   @Message(id = 229104, value = "Server is stopping. Message grouping not allowed")
   ActiveMQException groupWhileStopping();

   @Message(id = 229106, value = "Invalid slow consumer policy type {}")
   IllegalArgumentException invalidSlowConsumerPolicyType(String val);

   @Message(id = 229107, value = "consumer connections for address {} closed by management")
   ActiveMQInternalErrorException consumerConnectionsClosedByManagement(String address);

   @Message(id = 229108, value = "connections for user {} closed by management")
   ActiveMQInternalErrorException connectionsForUserClosedByManagement(String userName);

   @Message(id = 229109, value = "unsupported HA Policy Configuration {}")
   ActiveMQIllegalStateException unsupportedHAPolicyConfiguration(Object o);

   @Message(id = 229110, value = "Too many sessions for user '{}'. Sessions allowed: {}.")
   ActiveMQSessionCreationException sessionLimitReached(String username, int limit);

   @Message(id = 229111, value = "Too many queues created by user '{}'. Queues allowed: {}.")
   ActiveMQSecurityException queueLimitReached(String username, int limit);

   @Message(id = 229112, value = "Cannot set MBeanServer during startup or while started")
   IllegalStateException cannotSetMBeanserver();

   @Message(id = 229113, value = "Invalid message load balancing type {}")
   IllegalArgumentException invalidMessageLoadBalancingType(String val);

   @Message(id = 229114, value = "Replication synchronization process timed out after waiting {} milliseconds")
   ActiveMQReplicationTimeooutException replicationSynchronizationTimeout(long timeout);

   @Message(id = 229115, value = "Colocated Policy hasn't different type primary and backup")
   ActiveMQIllegalStateException primaryBackupMismatch();

   @Message(id = 229116, value = "Netty Acceptor unavailable")
   IllegalStateException acceptorUnavailable();

   @Message(id = 229117, value = "Replicator is null. Replication was likely terminated.")
   ActiveMQIllegalStateException replicatorIsNull();

   @Message(id = 229119, value = "Free storage space is at {} of {} total. Usage rate is {} which is beyond the configured <max-disk-usage>. System will start blocking producers.")
   ActiveMQIOErrorException diskBeyondLimit(String usableSpace, String totalSpace, String usage);

   @Message(id = 229120, value = "connection with ID {} closed by management")
   ActiveMQInternalErrorException connectionWithIDClosedByManagement(String ID);

   @Message(id = 229200, value = "Maximum Consumer Limit Reached on Queue:(address={},queue={})")
   ActiveMQQueueMaxConsumerLimitReached maxConsumerLimitReachedForQueue(SimpleString address, SimpleString queueName);

   @Message(id = 229203, value = "Address Does Not Exist: {}")
   ActiveMQAddressDoesNotExistException addressDoesNotExist(SimpleString address);

   @Message(id = 229204, value = "Address already exists: {}")
   ActiveMQAddressExistsException addressAlreadyExists(SimpleString address);

   @Message(id = 229205, value = "Address {} has bindings")
   ActiveMQAddressHasBindingsException addressHasBindings(SimpleString address);

   @Message(id = 229206, value = "Queue {} has invalid max consumer setting: {}")
   IllegalArgumentException invalidMaxConsumers(String queueName, int value);

   @Message(id = 229207, value = "Can not create queue with routing type: {}, Supported routing types for address: {} are {}")
   IllegalArgumentException invalidRoutingTypeForAddress(RoutingType routingType,
                                                         String address,
                                                         Set<RoutingType> supportedRoutingTypes);

   @Message(id = 229208, value = "Invalid routing type {}")
   IllegalArgumentException invalidRoutingType(String val);

   @Message(id = 229209, value = "Can't remove routing type {}, queues exists for address: {}. Please delete queues before removing this routing type.")
   IllegalStateException invalidRoutingTypeDelete(RoutingType routingType, String address);

   @Message(id = 229210, value = "Can't update queue {} with maxConsumers: {}. Current consumers are {}.")
   IllegalStateException invalidMaxConsumersUpdate(String queueName, int maxConsumers, int consumers);

   @Message(id = 229211, value = "Can't update queue {} with routing type: {}, Supported routing types for address: {} are {}")
   IllegalStateException invalidRoutingTypeUpdate(String queueName,
                                                  RoutingType routingType,
                                                  String address,
                                                  Set<RoutingType> supportedRoutingTypes);

   @Message(id = 229212, value = "Invalid deletion policy type {}")
   IllegalArgumentException invalidDeletionPolicyType(String val);

   @Message(id = 229213, value = "User: {} does not have permission='{}' for queue {} on address {}")
   ActiveMQSecurityException userNoPermissionsQueue(String username, CheckType checkType, SimpleString queue, SimpleString address);

   @Message(id = 229214, value = "{} must be a valid percentage value between 0 and 100 or -1 (actual value: {})")
   IllegalArgumentException notPercentOrMinusOne(String name, Number val);

   @Message(id = 229215, value = "Cannot delete queue {} on binding {} - it has {} messages")
   ActiveMQIllegalStateException cannotDeleteQueueWithMessages(SimpleString name, SimpleString queueName, long messageCount);

   @Message(id = 229216, value = "Invalid queue name: {}")
   ActiveMQIllegalStateException invalidQueueName(SimpleString queueName);

   @Message(id = 229217, value = "Cannot write to closed file: {}")
   ActiveMQIOErrorException cannotWriteToClosedFile(SequentialFile file);

   @Message(id = 229218, value = "Failed to locate broker configuration URL")
   ActiveMQIllegalStateException failedToLocateConfigURL();

   @Message(id = 229219, value = "Failed to load security configuration")
   ActiveMQIllegalStateException failedToLoadSecurityConfig();

   @Message(id = 229220, value = "Failed to load user file: {}")
   ActiveMQIllegalStateException failedToLoadUserFile(String path);

   @Message(id = 229221, value = "Failed to load role file: {}")
   ActiveMQIllegalStateException failedToLoadRoleFile(String path);

   @Message(id = 229222, value = "Failed to find login module entry {} from JAAS configuration")
   ActiveMQIllegalStateException failedToFindLoginModuleEntry(String entry);

   @Message(id = 229223, value = "User {} already exists")
   IllegalArgumentException userAlreadyExists(String user);

   @Message(id = 229224, value = "User {} does not exist")
   IllegalArgumentException userDoesNotExist(String user);

   @Message(id = 229225, value = "Validated User is not set")
   ActiveMQIllegalStateException rejectEmptyValidatedUser();

   @Message(id = 229226, value = "{}  must be greater than 0 and less than or equal to Integer.MAX_VALUE (actual value: {})")
   IllegalArgumentException inRangeOfPositiveInt(String name, Number val);

   @Message(id = 229227, value = "{}  must be equals to -1 or greater than 0 and less than or equal to Integer.MAX_VALUE (actual value: {})")
   IllegalArgumentException inRangeOfPositiveIntThanMinusOne(String name, Number val);

   @Message(id = 229228, value = "{} must be less than or equal to 1 (actual value: {})")
   IllegalArgumentException lessThanOrEqualToOne(String name, Number val);

   @Message(id = 229229, value = "Failed to parse JSON queue configuration: {}")
   IllegalArgumentException failedToParseJson(String json);

   @Message(id = 229230, value = "Failed to bind acceptor {} to {}")
   IllegalStateException failedToBind(String acceptor, String hostPort, Exception e);

   @Message(id = 229231, value = "Divert Does Not Exist: {}")
   ActiveMQDivertDoesNotExistException divertDoesNotExist(String divert);

   @Message(id = 229232, value = "Cannot create consumer on {}. Session is closed.")
   ActiveMQIllegalStateException cannotCreateConsumerOnClosedSession(SimpleString queueName);

   @Message(id = 229233, value = "Cannot set ActiveMQSecurityManager during startup or while started")
   IllegalStateException cannotSetSecurityManager();

   @Message(id = 229234, value = "Invalid slow consumer threshold measurement unit {}")
   IllegalArgumentException invalidSlowConsumerThresholdMeasurementUnit(String val);

   @Message(id = 229235, value = "Incompatible binding with name {} already exists: {}")
   ActiveMQIllegalStateException bindingAlreadyExists(String name, String binding);

   @Message(id = 229236, value = "Invalid connection router key {}")
   IllegalArgumentException invalidConnectionRouterKey(String val);

   @Message(id = 229237, value = "Connection router {} redirected the connection to {}")
   ActiveMQRoutingException connectionRedirected(String connectionRouter, TransportConfiguration connector);

   @Message(id = 229238, value = "Connection router {} not ready")
   ActiveMQRoutingException connectionRouterNotReady(String connectionRouter);

   @Message(id = 229239, value = "There is no retention configured. In order to use the replay method you must specify journal-retention-directory element on the broker.xml")
   IllegalArgumentException noRetention();

   @Message(id = 229240, value = "Connection router {} rejected the connection")
   ActiveMQRemoteDisconnectException connectionRejected(String connectionRouter);

   @Message(id = 229241, value = "Embedded web server not found")
   ActiveMQIllegalStateException embeddedWebServerNotFound();

   @Message(id = 229242, value = "Embedded web server not restarted in {} milliseconds")
   ActiveMQTimeoutException embeddedWebServerRestartTimeout(long timeout);

   @Message(id = 229243, value = "Embedded web server restart failed")
   ActiveMQException embeddedWebServerRestartFailed(Exception e);

   @Message(id = 229244, value = "Meters already registered for {}")
   IllegalStateException metersAlreadyRegistered(String resource);

   @Message(id = 229245, value = "Management controller is busy with another task. Please try again")
   ActiveMQTimeoutException managementBusy();

   @Message(id = 229247, value = "Invalid address configuration for '{}'. Address must support multicast and/or anycast.")
   IllegalArgumentException addressWithNoRoutingType(String address);

   @Message(id = 229248, value = "Invalid value for webSocketEncoderType: '{}'. Supported values: 'binary', 'text'.")
   IllegalStateException invalidWebSocketEncoderType(String webSocketEncoderType);

   @Message(id = 229249, value = "Invalid Store property, only DATABASE property is supported")
   RuntimeException unsupportedStorePropertyType();

   @Message(id = 229250, value = "Connection has been marked as destroyed for remote connection {}.")
   ActiveMQException connectionDestroyed(String remoteAddress);

   @Message(id = 229251, value = "{} value '{}' is too long. It is {} characters but must be {} characters.")
   IllegalArgumentException wrongLength(String name, String val, int actualLength, int requiredLength);

   @Message(id = 229252, value = "Invalid HAPolicy property: {}")
   RuntimeException unsupportedHAPolicyPropertyType(String invalidHAPolicy);

   @Message(id = 229253, value = "Unable to acquire OperationContext when replicating packet: {}. ExecutorFactory: {}")
   IllegalStateException replicationFailureRepliTokenNull(String packet, String executorFactory);

   @Message(id = 229254, value = "Already replicating, started={}")
   ActiveMQIllegalStateException alreadyReplicating(boolean status);

   @Message(id = 229255, value = "Bridge {} cannot be {}. Current state: {}")
   ActiveMQIllegalStateException bridgeOperationCannotBeExecuted(String bridgeName, String failedOp, BridgeImpl.State currentState);

   @Message(id = 229256, value = "{} must be a positive power of 2 (actual value: {})")
   IllegalArgumentException positivePowerOfTwo(String name, Number val);

   @Message(id = 229257, value = "IDGenerator has been stopped")
   RuntimeException idGeneratorStopped();

   @Message(id = 229258, value = "Invalid cluster bridge message! No queue IDs defined in the property {}")
   ActiveMQIllegalStateException noQueueIdsDefined(SimpleString idsHeaderName);

   @Message(id = 229259, value = "Invalid disk full message policy type {}")
   IllegalArgumentException invalidDiskFullPolicyType(String val);

   @Message(id = 229260, value = "Invalid authentication cache key: {}")
   IllegalArgumentException invalidAuthenticationCacheKey(String val);

}
