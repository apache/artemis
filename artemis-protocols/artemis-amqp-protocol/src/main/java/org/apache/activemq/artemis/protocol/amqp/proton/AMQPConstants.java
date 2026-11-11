/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.protocol.amqp.proton;

/**
 * Constants derived from the AMQP spec
 */
public class AMQPConstants {

   /*
    * Connection Properties
    * http://docs.oasis-open.org/amqp/core/v1.0/amqp-core-complete-v1.0.pdf#subsection.2.7.1
    */
   public static class Connection {

      public static final int DEFAULT_IDLE_TIMEOUT = -1;

      public static final int DEFAULT_CHANNEL_MAX = 65535;
   }
}
