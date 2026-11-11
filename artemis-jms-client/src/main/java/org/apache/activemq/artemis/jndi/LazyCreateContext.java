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
package org.apache.activemq.artemis.jndi;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

public abstract class LazyCreateContext extends ReadOnlyContext {

   @Override
   public Object lookup(String name) throws NamingException {
      /*
       * The lookup and the internalBind should be performed atomically to avoid
       * race conditions between multiple threads where the lookup fails for both
       * threads initially then the internalBind fails for the second thread.
       */
      synchronized (this) {
         try {
            return super.lookup(name);
         } catch (NameNotFoundException e) {
            Object answer = createEntry(name);
            if (answer == null) {
               throw e;
            }
            internalBind(name, answer);
            return answer;
         }
      }
   }

   protected abstract Object createEntry(String name);
}
