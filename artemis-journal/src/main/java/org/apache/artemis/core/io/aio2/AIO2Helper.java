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

package org.apache.artemis.core.io.aio2;

import java.io.File;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.io.SequentialFileFactory;
import org.apache.activemq.artemis.utils.critical.CriticalAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AIO2 helper for JDK less than version 24.
 * This version uses stub implementations that throw UnsupportedOperationException.
 * For JDK 24+, see the real implementation in src/main/java24.
 */
public class AIO2Helper {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static boolean isSupported() {
      logger.debug("AIO2Helper from earlier JDKs being used");
      return false;
   }

   public static long getTotalMaxIO() {
      return 0;
   }

   public static SequentialFileFactory getAIO2SequentialFileFactory(File journalDir, int maxIO) {
      return null;
   }

   public static SequentialFileFactory getAIO2SequentialFileFactory(File journalDir,
                                                                    IOCriticalErrorListener listener,
                                                                    int maxIO) {
      return null;
   }

   public static SequentialFileFactory getAIO2SequentialFileFactory(File journalDir,
                                                                    int bufferSize,
                                                                    int bufferTimeout,
                                                                    int maxIO,
                                                                    boolean logRates) {
      return null;
   }

   public static SequentialFileFactory getAIO2SequentialFileFactory(File journalDir,
                                                                    int bufferSize,
                                                                    int bufferTimeout,
                                                                    int maxIO,
                                                                    boolean logRates,
                                                                    IOCriticalErrorListener listener,
                                                                    CriticalAnalyzer analyzer) {
      return null;
   }

}