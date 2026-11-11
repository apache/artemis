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
package org.apache.activemq.artemis.core.persistence;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;

public interface Persister<T extends Object> {

   /**
    * This is to be used to store the protocol-id on Messages. Messages are stored on their bare format. The protocol
    * manager will be responsible to code or decode messages. The caveat here is that the first short-sized bytes need
    * to be this constant.
    */
   byte getID();

   int getEncodeSize(T record);

   void encode(ActiveMQBuffer buffer, T record);

   T decode(ActiveMQBuffer buffer, T record, CoreMessageObjectPools pool);

}
