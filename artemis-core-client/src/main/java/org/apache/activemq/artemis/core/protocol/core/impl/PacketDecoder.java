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
package org.apache.activemq.artemis.core.protocol.core.impl;

import java.io.Serializable;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ActiveMQExceptionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ActiveMQExceptionMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ConnectMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ConnectResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ClusterTopologyChangeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ClusterTopologyChangeMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ClusterTopologyChangeMessage_V3;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ClusterTopologyChangeMessage_V4;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateAddressMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateQueueMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateQueueMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSessionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSessionMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSessionResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSharedQueueMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSharedQueueMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.DisconnectConsumerMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.DisconnectConsumerWithKillMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.DisconnectMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.DisconnectMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.DisconnectMessage_V3;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.FederationDownstreamConnectMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.NullResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.NullResponseMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.PacketsConfirmedMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.Ping;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReattachSessionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.ReattachSessionResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.RollbackMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionAcknowledgeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionAddMetaDataMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionAddMetaDataMessageV2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage_V3;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage_V4;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionBindingQueryResponseMessage_V5;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionCloseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionCommitMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionCommitMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionConsumerCloseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionConsumerFlowCreditMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionCreateConsumerMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionDeleteQueueMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionExpireMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionForceConsumerDelivery;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionIndividualAcknowledgeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionProducerCreditsFailMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionProducerCreditsMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionQueueQueryMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionQueueQueryResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionQueueQueryResponseMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionQueueQueryResponseMessage_V3;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveContinuationMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionRequestProducerCreditsMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendContinuationMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendContinuationMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendContinuationMessage_V3;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionUniqueAddMetaDataMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAAfterFailedMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXACommitMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAEndMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAForgetMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAGetInDoubtXidsResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAGetTimeoutResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAJoinMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAPrepareMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAResponseMessage_V2;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAResumeMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXARollbackMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXASetTimeoutMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXASetTimeoutResponseMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionXAStartMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SubscribeClusterTopologyUpdatesMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SubscribeClusterTopologyUpdatesMessageV2;

import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CONNECT;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CLUSTER_TOPOLOGY;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CLUSTER_TOPOLOGY_V2;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CLUSTER_TOPOLOGY_V3;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CLUSTER_TOPOLOGY_V4;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CREATESESSION;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CREATESESSION_RESP;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CREATESESSION_V2;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CREATE_ADDRESS;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CREATE_QUEUE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CREATE_QUEUE_V2;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CREATE_SHARED_QUEUE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CREATE_SHARED_QUEUE_V2;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.DELETE_QUEUE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.DISCONNECT;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.DISCONNECT_CONSUMER;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.DISCONNECT_V2;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.DISCONNECT_V3;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.EXCEPTION;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.FEDERATION_DOWNSTREAM_CONNECT;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.NULL_RESPONSE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.PACKETS_CONFIRMED;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.CONNECT_RESPONSE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.PING;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.REATTACH_SESSION;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.REATTACH_SESSION_RESP;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_ACKNOWLEDGE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_ADD_METADATA;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_ADD_METADATA2;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_BINDINGQUERY;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_BINDINGQUERY_RESP;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_BINDINGQUERY_RESP_V2;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_BINDINGQUERY_RESP_V3;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_BINDINGQUERY_RESP_V4;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_BINDINGQUERY_RESP_V5;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_CLOSE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_COMMIT;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_CONSUMER_CLOSE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_CREATECONSUMER;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_EXPIRED;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_FLOWTOKEN;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_FORCE_CONSUMER_DELIVERY;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_INDIVIDUAL_ACKNOWLEDGE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_PRODUCER_CREDITS;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_PRODUCER_FAIL_CREDITS;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_PRODUCER_REQUEST_CREDITS;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_QUEUEQUERY;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_QUEUEQUERY_RESP;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_QUEUEQUERY_RESP_V2;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_QUEUEQUERY_RESP_V3;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_RECEIVE_CONTINUATION;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_ROLLBACK;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_SEND_CONTINUATION;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_START;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_STOP;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_UNIQUE_ADD_METADATA;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_COMMIT;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_END;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_FAILED;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_FORGET;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_GET_TIMEOUT;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_GET_TIMEOUT_RESP;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_INDOUBT_XIDS;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_INDOUBT_XIDS_RESP;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_JOIN;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_PREPARE;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_RESP;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_RESUME;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_ROLLBACK;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_SET_TIMEOUT;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_SET_TIMEOUT_RESP;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_START;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SESS_XA_SUSPEND;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SUBSCRIBE_TOPOLOGY;
import static org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl.SUBSCRIBE_TOPOLOGY_V2;

public abstract class PacketDecoder implements Serializable {

   public abstract Packet decode(ActiveMQBuffer in, CoreRemotingConnection connection);

   public Packet decode(byte packetType, CoreRemotingConnection connection) {
      Packet packet;

      switch (packetType) {
         case PING: {
            packet = new Ping();
            break;
         }
         case DISCONNECT: {
            packet = new DisconnectMessage();
            break;
         }
         case DISCONNECT_V2: {
            packet = new DisconnectMessage_V2();
            break;
         }
         case DISCONNECT_CONSUMER: {
            packet = new DisconnectConsumerMessage();
            break;
         }
         case EXCEPTION: {
            if (connection.isVersionBeforeAsyncResponseChange()) {
               packet = new ActiveMQExceptionMessage();
            } else {
               packet = new ActiveMQExceptionMessage_V2();
            }
            break;
         }
         case PACKETS_CONFIRMED: {
            packet = new PacketsConfirmedMessage();
            break;
         }
         case NULL_RESPONSE: {
            if (connection.isVersionBeforeAsyncResponseChange()) {
               packet = new NullResponseMessage();
            } else {
               packet = new NullResponseMessage_V2();
            }
            break;
         }
         case CREATESESSION: {
            packet = new CreateSessionMessage();
            break;
         }
         case CONNECT: {
            packet = new ConnectMessage();
            break;
         }
         case CREATESESSION_RESP: {
            packet = new CreateSessionResponseMessage();
            break;
         }
         case REATTACH_SESSION: {
            packet = new ReattachSessionMessage();
            break;
         }
         case REATTACH_SESSION_RESP: {
            packet = new ReattachSessionResponseMessage();
            break;
         }
         case SESS_CLOSE: {
            packet = new SessionCloseMessage();
            break;
         }
         case SESS_CREATECONSUMER: {
            packet = new SessionCreateConsumerMessage();
            break;
         }
         case SESS_ACKNOWLEDGE: {
            packet = new SessionAcknowledgeMessage();
            break;
         }
         case SESS_EXPIRED: {
            packet = new SessionExpireMessage();
            break;
         }
         case SESS_COMMIT: {
            if (!connection.isVersionSupportCommitV2()) {
               packet = new SessionCommitMessage();
            } else {
               packet = new SessionCommitMessage_V2();
            }
            break;
         }
         case SESS_ROLLBACK: {
            packet = new RollbackMessage();
            break;
         }
         case SESS_QUEUEQUERY: {
            packet = new SessionQueueQueryMessage();
            break;
         }
         case SESS_QUEUEQUERY_RESP: {
            packet = new SessionQueueQueryResponseMessage();
            break;
         }
         case SESS_QUEUEQUERY_RESP_V2: {
            packet = new SessionQueueQueryResponseMessage_V2();
            break;
         }
         case SESS_QUEUEQUERY_RESP_V3: {
            packet = new SessionQueueQueryResponseMessage_V3();
            break;
         }
         case CREATE_ADDRESS: {
            packet = new CreateAddressMessage();
            break;
         }
         case CREATE_QUEUE: {
            packet = new CreateQueueMessage();
            break;
         }
         case CREATE_QUEUE_V2: {
            packet = new CreateQueueMessage_V2();
            break;
         }
         case CREATE_SHARED_QUEUE: {
            packet = new CreateSharedQueueMessage();
            break;
         }
         case CREATE_SHARED_QUEUE_V2: {
            packet = new CreateSharedQueueMessage_V2();
            break;
         }
         case DELETE_QUEUE: {
            packet = new SessionDeleteQueueMessage();
            break;
         }
         case SESS_BINDINGQUERY: {
            packet = new SessionBindingQueryMessage();
            break;
         }
         case SESS_BINDINGQUERY_RESP: {
            packet = new SessionBindingQueryResponseMessage();
            break;
         }
         case SESS_BINDINGQUERY_RESP_V2: {
            packet = new SessionBindingQueryResponseMessage_V2();
            break;
         }
         case SESS_BINDINGQUERY_RESP_V3: {
            packet = new SessionBindingQueryResponseMessage_V3();
            break;
         }
         case SESS_BINDINGQUERY_RESP_V4: {
            packet = new SessionBindingQueryResponseMessage_V4();
            break;
         }
         case SESS_BINDINGQUERY_RESP_V5: {
            packet = new SessionBindingQueryResponseMessage_V5();
            break;
         }
         case SESS_XA_START: {
            packet = new SessionXAStartMessage();
            break;
         }
         case SESS_XA_FAILED: {
            packet = new SessionXAAfterFailedMessage();
            break;
         }
         case SESS_XA_END: {
            packet = new SessionXAEndMessage();
            break;
         }
         case SESS_XA_COMMIT: {
            packet = new SessionXACommitMessage();
            break;
         }
         case SESS_XA_PREPARE: {
            packet = new SessionXAPrepareMessage();
            break;
         }
         case SESS_XA_RESP: {
            if (connection.isVersionBeforeAsyncResponseChange()) {
               packet = new SessionXAResponseMessage();
            } else {
               packet = new SessionXAResponseMessage_V2();
            }
            break;
         }
         case SESS_XA_ROLLBACK: {
            packet = new SessionXARollbackMessage();
            break;
         }
         case SESS_XA_JOIN: {
            packet = new SessionXAJoinMessage();
            break;
         }
         case SESS_XA_SUSPEND: {
            packet = new PacketImpl(PacketImpl.SESS_XA_SUSPEND);
            break;
         }
         case SESS_XA_RESUME: {
            packet = new SessionXAResumeMessage();
            break;
         }
         case SESS_XA_FORGET: {
            packet = new SessionXAForgetMessage();
            break;
         }
         case SESS_XA_INDOUBT_XIDS: {
            packet = new PacketImpl(PacketImpl.SESS_XA_INDOUBT_XIDS);
            break;
         }
         case SESS_XA_INDOUBT_XIDS_RESP: {
            packet = new SessionXAGetInDoubtXidsResponseMessage();
            break;
         }
         case SESS_XA_SET_TIMEOUT: {
            packet = new SessionXASetTimeoutMessage();
            break;
         }
         case SESS_XA_SET_TIMEOUT_RESP: {
            packet = new SessionXASetTimeoutResponseMessage();
            break;
         }
         case SESS_XA_GET_TIMEOUT: {
            packet = new PacketImpl(PacketImpl.SESS_XA_GET_TIMEOUT);
            break;
         }
         case SESS_XA_GET_TIMEOUT_RESP: {
            packet = new SessionXAGetTimeoutResponseMessage();
            break;
         }
         case SESS_START: {
            packet = new PacketImpl(PacketImpl.SESS_START);
            break;
         }
         case SESS_STOP: {
            packet = new PacketImpl(PacketImpl.SESS_STOP);
            break;
         }
         case SESS_FLOWTOKEN: {
            packet = new SessionConsumerFlowCreditMessage();
            break;
         }
         case SESS_CONSUMER_CLOSE: {
            packet = new SessionConsumerCloseMessage();
            break;
         }
         case SESS_INDIVIDUAL_ACKNOWLEDGE: {
            packet = new SessionIndividualAcknowledgeMessage();
            break;
         }
         case SESS_RECEIVE_CONTINUATION: {
            packet = new SessionReceiveContinuationMessage();
            break;
         }
         case SESS_SEND_CONTINUATION: {
            if (connection.isVersionBeforeAsyncResponseChange()) {
               packet = new SessionSendContinuationMessage();
            } else if (connection.isBeforeProducerMetricsChanged()) {
               packet = new SessionSendContinuationMessage_V2();
            } else {
               packet = new SessionSendContinuationMessage_V3();
            }
            break;
         }
         case SESS_PRODUCER_REQUEST_CREDITS: {
            packet = new SessionRequestProducerCreditsMessage();
            break;
         }
         case SESS_PRODUCER_CREDITS: {
            packet = new SessionProducerCreditsMessage();
            break;
         }
         case SESS_PRODUCER_FAIL_CREDITS: {
            packet = new SessionProducerCreditsFailMessage();
            break;
         }
         case SESS_FORCE_CONSUMER_DELIVERY: {
            packet = new SessionForceConsumerDelivery();
            break;
         }
         case CLUSTER_TOPOLOGY: {
            packet = new ClusterTopologyChangeMessage();
            break;
         }
         case CLUSTER_TOPOLOGY_V2: {
            packet = new ClusterTopologyChangeMessage_V2();
            break;
         }
         case CLUSTER_TOPOLOGY_V3: {
            packet = new ClusterTopologyChangeMessage_V3();
            break;
         }
         case SUBSCRIBE_TOPOLOGY: {
            packet = new SubscribeClusterTopologyUpdatesMessage();
            break;
         }
         case SUBSCRIBE_TOPOLOGY_V2: {
            packet = new SubscribeClusterTopologyUpdatesMessageV2();
            break;
         }
         case SESS_ADD_METADATA: {
            packet = new SessionAddMetaDataMessage();
            break;
         }
         case SESS_ADD_METADATA2: {
            packet = new SessionAddMetaDataMessageV2();
            break;
         }
         case SESS_UNIQUE_ADD_METADATA: {
            packet = new SessionUniqueAddMetaDataMessage();
            break;
         }
         case CONNECT_RESPONSE: {
            packet = new ConnectResponseMessage();
            break;
         }
         case PacketImpl.DISCONNECT_CONSUMER_KILL: {
            packet = new DisconnectConsumerWithKillMessage();
            break;
         }
         case FEDERATION_DOWNSTREAM_CONNECT: {
            packet = new FederationDownstreamConnectMessage();
            break;
         }
         case CLUSTER_TOPOLOGY_V4: {
            packet = new ClusterTopologyChangeMessage_V4();
            break;
         }
         case CREATESESSION_V2: {
            packet = new CreateSessionMessage_V2();
            break;
         }
         case DISCONNECT_V3: {
            packet = new DisconnectMessage_V3();
            break;
         }
         default: {
            throw ActiveMQClientMessageBundle.BUNDLE.invalidType(packetType);
         }
      }

      return packet;
   }

}
