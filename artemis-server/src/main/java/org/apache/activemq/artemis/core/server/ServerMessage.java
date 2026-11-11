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

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.message.impl.MessageInternal;
import org.apache.activemq.artemis.core.paging.PagingStore;

@Deprecated
public interface ServerMessage extends MessageInternal {

   ICoreMessage getICoreMessage();

   MessageReference createReference(Queue queue);

   /**
    * This will force encoding of the address, and will re-check the buffer. This is to avoid setMessageTransient which
    * set the address without changing the buffer
    */
   void forceAddress(SimpleString address);

   ServerMessage makeCopyForExpiryOrDLA(long newID,
                                        MessageReference originalReference,
                                        boolean expiry,
                                        boolean copyOriginalHeaders) throws Exception;

   void setOriginalHeaders(ServerMessage other, MessageReference originalReference, boolean expiry);

   void setPagingStore(PagingStore store);

   PagingStore getPagingStore();

   boolean storeIsPaging();

   void encodeMessageIDToBuffer();

}
