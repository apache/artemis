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
package org.apache.activemq.artemis.core.journal;

import java.util.List;

/**
 * A Callback to receive information about bad transactions for extra cleanup required for broken transactions such as
 * large messages.
 */
public interface TransactionFailureCallback {

   /**
    * To be used to inform about transactions without commit records. This could be used to remove extra resources
    * associated with the transactions (such as external files received during the transaction)
    */
   void failedTransaction(long transactionID, List<RecordInfo> records, List<RecordInfo> recordsToDelete);

}
