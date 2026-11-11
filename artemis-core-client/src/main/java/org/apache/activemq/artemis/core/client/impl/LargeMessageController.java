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
package org.apache.activemq.artemis.core.client.impl;

import java.io.OutputStream;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;

public interface LargeMessageController extends ActiveMQBuffer {

   /**
    * Message was originally a regular message.
    * */
   boolean isOriginallyRegular();

   void setOriginallyRegular(boolean regular);

   /**
    * {@return the size of this buffer.}
    */
   long getSize();

   /**
    * Discards packets unused by this buffer.
    */
   void discardUnusedPackets();

   /**
    * Closes this buffer.
    */
   void close();

   /**
    * Cancels this buffer.
    */
   void cancel();

   /**
    * Sets the OutputStream of this buffer to the specified output.
    */
   void setOutputStream(OutputStream output) throws ActiveMQException;

   /**
    * Saves this buffer to the specified output. This is just a blocking version of
    * {@link #setOutputStream(OutputStream)}.
    */
   void saveBuffer(OutputStream output) throws ActiveMQException;

   void addPacket(byte[] chunk, int flowControlSize, boolean isContinues);

   /**
    * Waits for the completion for the specified waiting time (in milliseconds).
    */
   boolean waitCompletion(long timeWait) throws ActiveMQException;

   LargeMessageControllerImpl.LargeData take() throws InterruptedException;
}
