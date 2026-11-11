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

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.TopologyMember;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * There are a few properties that were changed between HornetQ and Artemis. When sending topology updates to clients,
 * if these properties are used we need to convert them properly
 */
public class BackwardsCompatibilityUtils {

   private static int INITIAL_ACTIVEMQ_INCREMENTING_VERSION = 126;


   public static final String SSL_ENABLED_PROP_NAME = "ssl-enabled";

   public static final String HTTP_ENABLED_PROP_NAME = "http-enabled";

   public static final String HTTP_CLIENT_IDLE_PROP_NAME = "http-client-idle-time";

   public static final String HTTP_CLIENT_IDLE_SCAN_PERIOD = "http-client-idle-scan-period";

   public static final String HTTP_RESPONSE_TIME_PROP_NAME = "http-response-time";

   public static final String HTTP_SERVER_SCAN_PERIOD_PROP_NAME = "http-server-scan-period";

   public static final String HTTP_REQUIRES_SESSION_ID = "http-requires-session-id";

   public static final String USE_SERVLET_PROP_NAME = "use-servlet";

   public static final String SERVLET_PATH = "servlet-path";

   public static final String USE_NIO_PROP_NAME = "use-nio";

   public static final String USE_NIO_GLOBAL_WORKER_POOL_PROP_NAME = "use-nio-global-worker-pool";

   public static final String USE_INVM_PROP_NAME = "use-invm";

   public static final String PROTOCOL_PROP_NAME = "protocol";

   public static final String HOST_PROP_NAME = "host";

   public static final String PORT_PROP_NAME = "port";

   public static final String LOCAL_ADDRESS_PROP_NAME = "local-address";

   public static final String LOCAL_PORT_PROP_NAME = "local-port";

   public static final String KEYSTORE_PROVIDER_PROP_NAME = "key-store-provider";

   public static final String KEYSTORE_TYPE_PROP_NAME = "key-store-type";

   public static final String KEYSTORE_PATH_PROP_NAME = "key-store-path";

   public static final String KEYSTORE_PASSWORD_PROP_NAME = "key-store-password";

   public static final String TRUSTSTORE_PROVIDER_PROP_NAME = "trust-store-provider";

   public static final String TRUSTSTORE_TYPE_PROP_NAME = "trust-store-type";

   public static final String TRUSTSTORE_PATH_PROP_NAME = "trust-store-path";

   public static final String TRUSTSTORE_PASSWORD_PROP_NAME = "trust-store-password";

   public static final String NEED_CLIENT_AUTH_PROP_NAME = "need-client-auth";

   public static final String BACKLOG_PROP_NAME = "backlog";

   public static final String TCP_NODELAY_PROPNAME = "tcp-no-delay";

   public static final String TCP_SENDBUFFER_SIZE_PROPNAME = "tcp-send-buffer-size";

   public static final String TCP_RECEIVEBUFFER_SIZE_PROPNAME = "tcp-receive-buffer-size";

   public static final String NIO_REMOTING_THREADS_PROPNAME = "nio-remoting-threads";

   public static final String BATCH_DELAY = "batch-delay";

   public static final String DIRECT_DELIVER = "direct-deliver";

   public static final String CLUSTER_CONNECTION = "cluster-connection";

   public static final String STOMP_CONSUMERS_CREDIT = "stomp-consumer-credits";

   public static final Map<String, String> OLD_PARAMETERS_MAP = new HashMap<>();

   static {
      OLD_PARAMETERS_MAP.put(TransportConstants.SSL_ENABLED_PROP_NAME, SSL_ENABLED_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.HTTP_ENABLED_PROP_NAME, HTTP_ENABLED_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.HTTP_CLIENT_IDLE_PROP_NAME, HTTP_CLIENT_IDLE_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.HTTP_CLIENT_IDLE_SCAN_PERIOD, HTTP_CLIENT_IDLE_SCAN_PERIOD);
      OLD_PARAMETERS_MAP.put(TransportConstants.HTTP_RESPONSE_TIME_PROP_NAME, HTTP_RESPONSE_TIME_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.HTTP_SERVER_SCAN_PERIOD_PROP_NAME, HTTP_SERVER_SCAN_PERIOD_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.HTTP_REQUIRES_SESSION_ID, HTTP_REQUIRES_SESSION_ID);
      OLD_PARAMETERS_MAP.put(TransportConstants.USE_SERVLET_PROP_NAME, USE_SERVLET_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.SERVLET_PATH, SERVLET_PATH);
      OLD_PARAMETERS_MAP.put(TransportConstants.USE_NIO_PROP_NAME, USE_NIO_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.USE_NIO_GLOBAL_WORKER_POOL_PROP_NAME, USE_NIO_GLOBAL_WORKER_POOL_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.USE_INVM_PROP_NAME, USE_INVM_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.PROTOCOL_PROP_NAME, PROTOCOL_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.HOST_PROP_NAME, HOST_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.PORT_PROP_NAME, PORT_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.LOCAL_ADDRESS_PROP_NAME, LOCAL_ADDRESS_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.LOCAL_PORT_PROP_NAME, LOCAL_PORT_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.KEYSTORE_PROVIDER_PROP_NAME, KEYSTORE_PROVIDER_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.KEYSTORE_TYPE_PROP_NAME, KEYSTORE_TYPE_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.KEYSTORE_PATH_PROP_NAME, KEYSTORE_PATH_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, KEYSTORE_PASSWORD_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.TRUSTSTORE_PROVIDER_PROP_NAME, TRUSTSTORE_PROVIDER_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.TRUSTSTORE_TYPE_PROP_NAME, TRUSTSTORE_TYPE_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, TRUSTSTORE_PATH_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, TRUSTSTORE_PASSWORD_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.NEED_CLIENT_AUTH_PROP_NAME, NEED_CLIENT_AUTH_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.BACKLOG_PROP_NAME, BACKLOG_PROP_NAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.TCP_NODELAY_PROPNAME, TCP_NODELAY_PROPNAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.TCP_SENDBUFFER_SIZE_PROPNAME, TCP_SENDBUFFER_SIZE_PROPNAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.TCP_RECEIVEBUFFER_SIZE_PROPNAME, TCP_RECEIVEBUFFER_SIZE_PROPNAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.NIO_REMOTING_THREADS_PROPNAME, NIO_REMOTING_THREADS_PROPNAME);
      OLD_PARAMETERS_MAP.put(TransportConstants.BATCH_DELAY, BATCH_DELAY);
      OLD_PARAMETERS_MAP.put(TransportConstants.DIRECT_DELIVER, DIRECT_DELIVER);
      OLD_PARAMETERS_MAP.put(TransportConstants.CLUSTER_CONNECTION, CLUSTER_CONNECTION);
      OLD_PARAMETERS_MAP.put(TransportConstants.STOMP_CONSUMERS_CREDIT, STOMP_CONSUMERS_CREDIT);
   }

   /**
    * Translates V3 strings to V2 strings.
    *
    * @return the string as if it's not found in the conversion map.
    */
   public static String convertParameter(String name) {
      String oldParameter = OLD_PARAMETERS_MAP.get(name);
      if (oldParameter != null) {
         return oldParameter;
      } else {
         return name;
      }
   }



   public static Pair<TransportConfiguration, TransportConfiguration> checkTCPPairConversion(int clientIncrementingVersion,
                                                                                             TopologyMember member) {
      if (clientIncrementingVersion < INITIAL_ACTIVEMQ_INCREMENTING_VERSION) {
         return new Pair<>(convertTransport(member.getPrimary()), convertTransport(member.getBackup()));
      }
      return new Pair<>(member.getPrimary(), member.getBackup());
   }

   public static Pair<TransportConfiguration, TransportConfiguration> checkTCPPairConversion(int clientIncrementingVersion,
                                                                                             Pair<TransportConfiguration, TransportConfiguration> pair) {
      if (clientIncrementingVersion < INITIAL_ACTIVEMQ_INCREMENTING_VERSION) {
         return new Pair<>(convertTransport(pair.getA()), convertTransport(pair.getB()));
      }
      return pair;
   }

   /**
    * Replaces class name and parameter names to HornetQ values.
    */
   private static TransportConfiguration convertTransport(TransportConfiguration tc) {
      if (tc != null) {
         String className = tc.getFactoryClassName().replace("org.apache.activemq.artemis", "org.hornetq").replace("ActiveMQ", "HornetQ");
         return new TransportConfiguration(className, convertParameters(tc.getParams()), tc.getName(), convertParameters(tc.getExtraParams()));
      }
      return tc;
   }

   private static Map<String, Object> convertParameters(Map<String, Object> params) {
      if (params == null) {
         return null;
      }
      Map<String, Object> convertedParams = new HashMap<>(params.size());
      for (Map.Entry<String, Object> entry: params.entrySet()) {
         convertedParams.put(convertParameter(entry.getKey()), entry.getValue());
      }
      return convertedParams;
   }

}
