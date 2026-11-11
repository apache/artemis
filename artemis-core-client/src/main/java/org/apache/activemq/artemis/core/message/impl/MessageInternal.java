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
package org.apache.activemq.artemis.core.message.impl;

import java.io.InputStream;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.message.BodyEncoder;
import org.apache.activemq.artemis.utils.TypedProperties;

@Deprecated
public interface MessageInternal extends Message {

   void decodeFromBuffer(ActiveMQBuffer buffer);

   int getEndOfMessagePosition();

   int getEndOfBodyPosition();

   void bodyChanged();

   boolean isServerMessage();

   ActiveMQBuffer getEncodedBuffer();

   int getHeadersAndPropertiesEncodeSize();

   ActiveMQBuffer getWholeBuffer();

   void encodeHeadersAndProperties(ActiveMQBuffer buffer);

   void decodeHeadersAndProperties(ActiveMQBuffer buffer);

   BodyEncoder getBodyEncoder() throws ActiveMQException;

   InputStream getBodyInputStream();

   void setAddressTransient(SimpleString address);

   TypedProperties getTypedProperties();
}
