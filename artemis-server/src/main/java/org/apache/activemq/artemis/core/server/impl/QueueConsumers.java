/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.impl;

import org.apache.activemq.artemis.core.PriorityAware;
import org.apache.activemq.artemis.utils.collections.RepeatableIterator;
import org.apache.activemq.artemis.utils.collections.ResettableIterator;

import java.util.Set;
import java.util.stream.Stream;

public interface QueueConsumers<T extends PriorityAware> extends Iterable<T>, RepeatableIterator<T>, ResettableIterator<T> {

   Set<Integer> getPriorites();

   boolean add(T t);

   boolean remove(T t);

   int size();

   boolean isEmpty();

   Stream<T> stream();

}
