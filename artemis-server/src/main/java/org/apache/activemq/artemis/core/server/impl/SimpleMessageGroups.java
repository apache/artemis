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
package org.apache.activemq.artemis.core.server.impl;

import java.util.HashMap;

/**
 * Implementation of MessageGroups that simply uses a HashMap, this is the existing and default behaviour of message
 * groups in artemis.
 * <p>
 * Effectively every Group Id is mapped raw, it also is unbounded.
 *
 * @param <C> the value type.
 */
public class SimpleMessageGroups<C> extends MapMessageGroups<C> {

   public SimpleMessageGroups() {
      super(new HashMap<>());
   }
}
