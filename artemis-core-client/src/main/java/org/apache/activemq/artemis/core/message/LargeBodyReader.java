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
package org.apache.activemq.artemis.core.message;

import java.nio.ByteBuffer;

import org.apache.activemq.artemis.api.core.ActiveMQException;

/**
 * Class used to readInto message body into buffers.
 * <p>
 * Used to send large streams over the wire
 * <p>
 * None of these methods should be caleld from Clients
 */
public interface LargeBodyReader extends AutoCloseable {

   /**
    * This method must not be called directly by Apache Artemis clients.
    */
   void open() throws ActiveMQException;

   /**
    * This method must not be called directly by Apache Artemis clients.
    * <p>
    * This is the reading position.
    */
   void position(long position) throws ActiveMQException;

   /**
    * This method must not be called directly by Apache Artemis clients.
    * <p>
    * This is the reading position.
    */
   long position();

   /**
    * This method must not be called directly by Apache Artemis clients.
    */
   @Override
   void close() throws ActiveMQException;

   /**
    * This method must not be called directly by Apache Artemis clients.
    */
   int readInto(ByteBuffer bufferRead) throws ActiveMQException;

   /**
    * This method must not be called directly by Apache Artemis clients.
    */
   long getSize() throws ActiveMQException;
}
