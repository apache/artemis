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
package org.apache.activemq.artemis.protocol.amqp.connect.federation;

/**
 * AMQPFederationAsyncCompletion type used to implement the handlers for asynchronous calls in AMQP federation types.
 *
 * @param <E> The type that defines the context provided to the completion events
 */
public interface AMQPFederationAsyncCompletion<E> {

   /**
    * Called when the asynchronous operation has succeeded.
    *
    * @param context The context object provided for this asynchronous event.
    */
   void onComplete(E context);

   /**
    * Called when the asynchronous operation has failed due to an error.
    *
    * @param context The context object provided for this asynchronous event.
    * @param error   The error that describes the failure that occurred.
    */
   void onException(E context, Exception error);

}
