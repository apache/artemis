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

package org.apache.artemis.database;

import org.apache.activemq.artemis.logs.BundleFactory;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.Message;

/**
 * Logger Codes 270000 - 270999
 */
@LogBundle(projectCode = "AMQ", regexID = "270[0-9]{3}")
public interface ActiveMQDirectDBBundle {

   ActiveMQDirectDBBundle BUNDLE = BundleFactory.newBundle(ActiveMQDirectDBBundle.class);

   @Message(id = 270000, value = "Timed out waiting for a database worker (timeout={} {})")
   Exception timedOutWaitingForWorker(long timeout, String unit);

}
