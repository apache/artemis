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
package org.apache.activemq.artemis.core.replication;

import org.apache.activemq.artemis.api.core.Message;

/**
 * {@link org.apache.activemq.artemis.core.server.LargeServerMessage} methods used by the {@link ReplicationEndpoint}.
 * <p>
 * In practice a subset of the methods necessary to have a
 * {@link org.apache.activemq.artemis.core.server.LargeServerMessage}
 *
 * @see org.apache.activemq.artemis.core.persistence.impl.journal.LargeServerMessageInSync
 */
public interface ReplicatedLargeMessage {

   /**
    * @see org.apache.activemq.artemis.core.server.LargeServerMessage#setDurable(boolean)
    */
   Message setDurable(boolean b);

   /**
    * @see org.apache.activemq.artemis.core.server.LargeServerMessage#setMessageID(long)
    */
   Message setMessageID(long id);

   Message getMessage();

   /**
    * @see org.apache.activemq.artemis.core.server.LargeServerMessage#releaseResources(boolean,boolean)
    */
   void releaseResources(boolean sync, boolean sendEvent);

   /**
    * @see org.apache.activemq.artemis.core.server.LargeServerMessage#deleteFile()
    */
   void deleteFile() throws Exception;

   /**
    * @see org.apache.activemq.artemis.core.server.LargeServerMessage#addBytes(byte[])
    */
   void addBytes(byte[] body) throws Exception;

}
