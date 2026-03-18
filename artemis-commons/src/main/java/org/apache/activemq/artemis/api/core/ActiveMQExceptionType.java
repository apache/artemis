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
package org.apache.activemq.artemis.api.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines all {@link ActiveMQException} types and their codes.
 */
public enum ActiveMQExceptionType {

   // Error codes -------------------------------------------------

   INTERNAL_ERROR(000) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQInternalErrorException(msg);
      }
   },
   UNSUPPORTED_PACKET(001) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQUnsupportedPacketException(msg);
      }
   },
   NOT_CONNECTED(002) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQNotConnectedException(msg);
      }
   },
   CONNECTION_TIMEDOUT(003) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQConnectionTimedOutException(msg);
      }
   },
   DISCONNECTED(004) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQDisconnectedException(msg);
      }
   },
   UNBLOCKED(005) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQUnBlockedException(msg);
      }
   },
   IO_ERROR(006) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQIOErrorException(msg);
      }
   },
   QUEUE_DOES_NOT_EXIST(100) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQNonExistentQueueException(msg);
      }
   },
   QUEUE_EXISTS(101) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQQueueExistsException(msg);
      }
   },
   OBJECT_CLOSED(102) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQObjectClosedException(msg);
      }
   },
   INVALID_FILTER_EXPRESSION(103) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQInvalidFilterExpressionException(msg);
      }
   },
   ILLEGAL_STATE(104) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQIllegalStateException(msg);
      }
   },
   SECURITY_EXCEPTION(105) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQSecurityException(msg);
      }
   },
   ADDRESS_DOES_NOT_EXIST(106) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQAddressDoesNotExistException(msg);
      }
   },
   ADDRESS_EXISTS(107) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQAddressExistsException(msg);
      }
   },
   INCOMPATIBLE_CLIENT_SERVER_VERSIONS(108) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQIncompatibleClientServerException(msg);
      }
   },
   LARGE_MESSAGE_ERROR_BODY(110) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQLargeMessageException(msg);
      }
   },
   TRANSACTION_ROLLED_BACK(111) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQTransactionRolledBackException(msg);
      }
   },
   SESSION_CREATION_REJECTED(112) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQSessionCreationException(msg);
      }
   },
   DUPLICATE_ID_REJECTED(113) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQDuplicateIdException(msg);
      }
   },
   DUPLICATE_METADATA(114) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQDuplicateMetaDataException(msg);
      }
   },
   TRANSACTION_OUTCOME_UNKNOWN(115) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQTransactionOutcomeUnknownException(msg);
      }
   },
   ALREADY_REPLICATING(116) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQAlreadyReplicatingException(msg);
      }
   },
   INTERCEPTOR_REJECTED_PACKET(117) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQInterceptorRejectedPacketException(msg);
      }
   },
   INVALID_TRANSIENT_QUEUE_USE(118) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQInvalidTransientQueueUseException(msg);
      }
   },
   REMOTE_DISCONNECT(119) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQRemoteDisconnectException(msg);
      }
   },
   TRANSACTION_TIMEOUT(120) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQTransactionTimeoutException(msg);
      }
   },
   GENERIC_EXCEPTION(999),
   NATIVE_ERROR_INTERNAL(200),
   NATIVE_ERROR_INVALID_BUFFER(201),
   NATIVE_ERROR_NOT_ALIGNED(202),
   NATIVE_ERROR_CANT_INITIALIZE_AIO(203),
   NATIVE_ERROR_CANT_RELEASE_AIO(204),
   NATIVE_ERROR_CANT_OPEN_CLOSE_FILE(205),
   NATIVE_ERROR_CANT_ALLOCATE_QUEUE(206),
   NATIVE_ERROR_PREALLOCATE_FILE(208),
   NATIVE_ERROR_ALLOCATE_MEMORY(209),
   ADDRESS_FULL(210) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQAddressFullException(msg);
      }
   },
   LARGE_MESSAGE_INTERRUPTED(211) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQLargeMessageInterruptedException(msg);
      }
   },
   CLUSTER_SECURITY_EXCEPTION(212) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQClusterSecurityException(msg);
      }

   },
   NOT_IMPLEMTNED_EXCEPTION(213),
   MAX_CONSUMER_LIMIT_EXCEEDED(214) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQQueueMaxConsumerLimitReached(msg);
      }
   },
   UNEXPECTED_ROUTING_TYPE_FOR_ADDRESS(215) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQUnexpectedRoutingTypeForAddress(msg);
      }
   },
   INVALID_QUEUE_CONFIGURATION(216) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQInvalidQueueConfiguration(msg);
      }
   },
   DELETE_ADDRESS_ERROR(217) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQDeleteAddressException(msg);
      }
   },
   NULL_REF(218) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQNullRefException(msg);
      }
   },
   SHUTDOWN_ERROR(219) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQShutdownException(msg);
      }
   },
   REPLICATION_TIMEOUT_ERROR(220) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQReplicationTimeooutException(msg);
      }
   },
   DIVERT_DOES_NOT_EXIST(221) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQDivertDoesNotExistException(msg);
      }
   },
   ROUTING_EXCEPTION(222) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQRoutingException(msg);
      }
   },
   TIMEOUT_EXCEPTION(223) {
      @Override
      public ActiveMQException createException(String msg) {
         return new ActiveMQTimeoutException(msg);
      }
   };
   private static final Map<Integer, ActiveMQExceptionType> TYPE_MAP;

   static {
      Map<Integer, ActiveMQExceptionType> map = new HashMap<>();
      for (ActiveMQExceptionType type : EnumSet.allOf(ActiveMQExceptionType.class)) {
         map.put(type.getCode(), type);
      }
      TYPE_MAP = Collections.unmodifiableMap(map);
   }

   private final int code;

   ActiveMQExceptionType(int code) {
      this.code = code;
   }

   public int getCode() {
      return code;
   }

   public ActiveMQException createException(String msg) {
      return new ActiveMQException(msg + ", code:" + this);
   }

   public static ActiveMQException createException(int code, String msg) {
      return getType(code).createException(msg);
   }

   public static ActiveMQExceptionType getType(int code) {
      ActiveMQExceptionType type = TYPE_MAP.get(code);
      if (type != null) {
         return type;
      }
      return ActiveMQExceptionType.GENERIC_EXCEPTION;
   }
}