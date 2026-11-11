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
package org.apache.activemq.artemis.core.journal.impl;

import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;

/**
 * The journal consists of an ordered list of journal files Fn where {@code 0 <= n <= N}. A journal file can contain
 * either positives (pos) or negatives (neg) (Positives correspond either to adds or updates, and negatives correspond
 * to deletes).
 * <p>
 * A file Fn can be deleted if and only if the following criteria are satisfied:
 * <ol>
 * <li>All pos in a file Fn must have corresponding neg in any file Fm where {@code m >= n}. *
 * <li>All pos that correspond to any neg in file Fn, must all live in any file Fm where {@code 0 <= m <= n} which are
 * also marked for deletion in the same pass of the algorithm.
 * </ol>
 */
public final class Reclaimer {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private Reclaimer() {
   }

   // The files are scanned in two stages. First we only check for 2) and do so while that criteria is not met.
   // When 2) is met, set the first reclaim flag in the journal. After that point only check for 1)
   // until that criteria is met as well. When 1) is met we set the second flag and the file can be reclaimed.

   public static void scan(final JournalFile[] files) {
      for (int i = 0; i < files.length; i++) {
         JournalFile currentFile = files[i];

         // criterion 2) --- this file deletes are from pos on files marked for reclaim or reclaimed
         if (!currentFile.isNegReclaimCriteria()) {
            boolean outstandingNeg = false;

            for (int j = i - 1; j >= 0 && !outstandingNeg; j--) {
               JournalFile file = files[j];
               if (!file.isCanReclaim() && currentFile.getNegCount(file) != 0) {
                  logger.trace("{} can't be reclaimed because {} has negative values", currentFile, file);
                  outstandingNeg = true;
               }
            }

            if (outstandingNeg) {
               continue; // Move to next file as we already know that this file can't be reclaimed because criterion 2)
            } else {
               currentFile.setNegReclaimCriteria();
            }
         }

         // criterion 1) --- this files additions all have matching deletes
         if (!currentFile.isPosReclaimCriteria()) {
            int negCount = 0, posCount = currentFile.getPosCount();
            logger.trace("posCount on {} = {}", currentFile, posCount);

            for (int j = i; j < files.length && negCount < posCount; j++) {
               int toNeg = files[j].getNegCount(currentFile);
               negCount += toNeg;

               if (logger.isTraceEnabled() && toNeg != 0) {
                  logger.trace("Negative from {} into {} = {}", files[j], currentFile, toNeg);
               }
            }

            if (negCount < posCount) {
               logger.trace("{} can't be reclaimed because there are not enough negatives {}", currentFile, negCount);
            } else {
               logger.trace("{} can be reclaimed", currentFile);
               currentFile.setPosReclaimCriteria();
            }
         }
      }
   }
}
