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
package org.apache.activemq.artemis.core.journal;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.persistence.Persister;

/**
 * This is a facade between the new Persister and the former EncodingSupport. Methods using the old interface will use
 * this as a facade to provide the previous semantic.
 */
public class EncoderPersister implements Persister<EncodingSupport> {

   private static final EncoderPersister theInstance = new EncoderPersister();

   private EncoderPersister() {
   }

   @Override
   public byte getID() {
      return 0;
   }

   public static EncoderPersister getInstance() {
      return theInstance;
   }

   @Override
   public int getEncodeSize(EncodingSupport record) {
      return record.getEncodeSize();
   }

   @Override
   public void encode(ActiveMQBuffer buffer, EncodingSupport record) {
      record.encode(buffer);
   }

   @Override
   public EncodingSupport decode(ActiveMQBuffer buffer, EncodingSupport record, CoreMessageObjectPools pools) {
      record.decode(buffer);
      return record;
   }
}
