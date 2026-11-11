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
package org.apache.activemq.artemis.core.transaction;

public class TransactionPropertyIndexes {

   public static final int LARGE_MESSAGE_CONFIRMATIONS = 1;

   public static final int PAGE_SYNC = 2;

   public static final int PAGE_COUNT_INC = 3;

   public static final int PAGE_TRANSACTION_UPDATE = 4;

   public static final int PAGE_TRANSACTION = 5;

   public static final int REFS_OPERATION = 6;

   public static final int PAGE_DELIVERY = 7;

   public static final int PAGE_CURSOR_POSITIONS = 8;

   public static final int EXPIRY_LOGGER = 9;

   public static final int CONSUMER_METRICS_OPERATION = 10;

   public static final int MIRROR_ACK_OPERATION = 11;

   public static final int MIRROR_SEND_OPERATION = 12;

   public static final int MIRROR_DELIVERY_ASYNC = 13;

   public static final int ADD_MESSAGE = 14;
}
