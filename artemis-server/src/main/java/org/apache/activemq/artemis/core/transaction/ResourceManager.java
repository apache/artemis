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

import javax.transaction.xa.Xid;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

public interface ResourceManager extends ActiveMQComponent {

   boolean putTransaction(Xid xid, Transaction tx, RemotingConnection remotingConnection) throws ActiveMQException;

   Transaction getTransaction(Xid xid);

   Transaction removeTransaction(Xid xid, RemotingConnection remotingConnection) throws ActiveMQException;

   int getTimeoutSeconds();

   List<Xid> getPreparedTransactions();

   Map<Xid, Long> getPreparedTransactionsWithCreationTime();

   void putHeuristicCompletion(long txid, Xid xid, boolean b);

   long removeHeuristicCompletion(Xid xid);

   List<Xid> getHeuristicCommittedTransactions();

   List<Xid> getHeuristicRolledbackTransactions();

   List<Xid> getInDoubtTransactions();

   int size();

}
