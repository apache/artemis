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
 * AIO2 helper for JDK 24+.
 * This version uses the real AIO2SequentialFileFactory implementation with Panama FFM support.
 * For JDK < 24, see the stub version in src/main/java24.
 */
public class AIO2Helper {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static boolean isSupported() {
      return AIO2SequentialFileFactory.isSupported();
   }

   public static long getTotalMaxIO() {
      return 0;
   }

   public static SequentialFileFactory getAIO2SequentialFileFactory(File journalDir, int maxIO) {
      try {
         return new AIO2SequentialFileFactory(journalDir, maxIO);
      } catch (UnsupportedOperationException | LinkageError e) {
         logger.debug("AIO2 not available: {}", e.getMessage(), e);
         return null;
      }
   }

   public static SequentialFileFactory getAIO2SequentialFileFactory(File journalDir,
                                                                    IOCriticalErrorListener listener,
                                                                    int maxIO) {
      try {
         return new AIO2SequentialFileFactory(journalDir, listener, maxIO);
      } catch (UnsupportedOperationException | LinkageError e) {
         logger.debug("AIO2 not available: {}", e.getMessage(), e);
         return null;
      }
   }

   public static SequentialFileFactory getAIO2SequentialFileFactory(File journalDir,
                                                                    int bufferSize,
                                                                    int bufferTimeout,
                                                                    int maxIO,
                                                                    boolean logRates) {
      try {
         return new AIO2SequentialFileFactory(journalDir, bufferSize, bufferTimeout, maxIO, logRates);
      } catch (UnsupportedOperationException | LinkageError e) {
         logger.debug("AIO2 not available: {}", e.getMessage(), e);
         return null;
      }
   }

   public static SequentialFileFactory getAIO2SequentialFileFactory(File journalDir,
                                                                    int bufferSize,
                                                                    int bufferTimeout,
                                                                    int maxIO,
                                                                    boolean logRates,
                                                                    IOCriticalErrorListener listener,
                                                                    CriticalAnalyzer analyzer) {
      try {
         return new AIO2SequentialFileFactory(journalDir, bufferSize, bufferTimeout, maxIO, logRates, listener, analyzer);
      } catch (UnsupportedOperationException | LinkageError e) {
         logger.debug("AIO2 not available: {}", e.getMessage(), e);
         return null;
      }
   }

}