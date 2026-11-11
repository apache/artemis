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
package org.apache.activemq.artemis.utils;

/**
 * This class can throttle to a specific rate, using an algorithm based on the <a
 * href="http://en.wikipedia.org/wiki/Token_bucket">Token Bucket metaphor</a>.
 * <p>
 * The rate is specified in cycles per second (or 'Hertz').
 *
 * @see <a href="http://en.wikipedia.org/wiki/Token_bucket">Token bucket</a>
 */
public interface TokenBucketLimiter {

   /**
    * {@return the rate in cycles per second (which is the same as saying 'in Hertz')}
    *
    * @see <a href="https://en.wikipedia.org/wiki/Hertz">Hertz</a>
    */
   int getRate();

   boolean isSpin();

   void limit();
}
