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
package org.apache.activemq.artemis;

public class ArtemisConstants {

   public static final int DEFAULT_JOURNAL_BUFFER_SIZE_AIO = 490 * 1024;
   public static final int DEFAULT_JOURNAL_BUFFER_TIMEOUT_AIO = (int) (1000000000d / 2000);
   public static final int DEFAULT_JOURNAL_BUFFER_TIMEOUT_NIO = (int) (1000000000d / 300);
   public static final int DEFAULT_JOURNAL_BUFFER_SIZE_NIO = 490 * 1024;
}
