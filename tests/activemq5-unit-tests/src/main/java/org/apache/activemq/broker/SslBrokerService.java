/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.broker;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.SecureRandom;

import org.apache.activemq.transport.TransportServer;

/**
 * A BrokerService that allows access to the key and trust managers used by SSL connections. There is no reason to use
 * this class unless SSL is being used AND the key and trust managers need to be specified from within code. In fact, if
 * the URI passed to this class does not have an "ssl" scheme, this class will pass all work on to its superclass.
 */
public class SslBrokerService extends BrokerService {

   public TransportConnector addSslConnector(String bindAddress,
                                             KeyManager[] km,
                                             TrustManager[] tm,
                                             SecureRandom random) throws Exception {
      return null;
   }

   public TransportConnector addSslConnector(URI bindAddress,
                                             KeyManager[] km,
                                             TrustManager[] tm,
                                             SecureRandom random) throws Exception {
      return null;
   }

   protected TransportServer createSslTransportServer(URI brokerURI,
                                                      KeyManager[] km,
                                                      TrustManager[] tm,
                                                      SecureRandom random) throws IOException, KeyManagementException {
      return null;
   }
}
