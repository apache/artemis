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

import org.apache.activemq.artemis.core.protocol.core.Packet;

/**
 * This is class is a simple way to intercepting calls on Apache Artemis client and servers.
 * <p>
 * To add an interceptor to Apache Artemis server, you have to modify the server configuration file
 * {@literal broker.xml}.
 * <p>
 * To add it to a client, use
 * {@link org.apache.activemq.artemis.api.core.client.ServerLocator#addIncomingInterceptor(Interceptor)}
 */
public interface Interceptor extends BaseInterceptor<Packet> {

}
