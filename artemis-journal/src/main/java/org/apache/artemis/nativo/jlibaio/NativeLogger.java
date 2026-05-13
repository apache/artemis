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
package org.apache.artemis.nativo.jlibaio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NativeLogger {

   private static final Logger logger = LoggerFactory.getLogger(NativeLogger.class);

   public static final String PROJECT_PREFIX = "jlibaio";

   private static final int DIFFERENT_VERSION_ID = 163001;
   private static final String DIFFERENT_VERSION = PROJECT_PREFIX + DIFFERENT_VERSION_ID + " You have a native library with a different version than expected";

   public static final void incompatibleNativeLibrary() {
      logger.warn(DIFFERENT_VERSION);
   }
}
