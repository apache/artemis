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

package org.apache.activemq.artemis.core.persistence;

/**
 * This is a list for all the persisters. The sole purpose of this is to make sure these IDs will not be duplicate so we
 * know where to find IDs.
 */
public class PersisterIDs {

   public static final int MAX_PERSISTERS = 7;

   public static final byte CoreLargeMessagePersister_ID = (byte)0;

   public static final byte CoreMessagePersister_ID = (byte)1;

   public static final byte AMQPMessagePersister_ID = (byte)2;

   public static final byte AMQPMessagePersisterV2_ID = (byte)3;

   public static final byte AMQPLargeMessagePersister_ID = (byte)4;

   public static final byte AMQPMessagePersisterV3_ID = (byte)5;

   public static final byte AMQPMessagePersisterV4_ID = (byte)6;

   public static final byte AMQPLargeMessagePersisterV2_ID = (byte)7;

}
