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
package org.apache.activemq.artemis.core.server.files;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Used to move files away. Each time a backup starts its formeter data will be moved to a backup folder called bkp.1,
 * bkp.2, ... etc We may control the maximum number of folders so we remove old ones.
 */
public class FileMoveManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final File folder;
   private final String[] prefixesToPreserve;
   private int maxFolders;
   public static final String PREFIX = "oldreplica.";

   private static final FilenameFilter isPrefix = (dir, name) -> {
      boolean prefixed = name.contains(PREFIX);

      if (prefixed) {
         try {
            Integer.parseInt(name.substring(PREFIX.length()));
         } catch (NumberFormatException e) {
            // This function is not really used a lot
            // so I don't really mind about performance here
            // this is good enough for what we need
            prefixed = false;
         }
      }

      return prefixed;
   };

   private static final FilenameFilter notPrefix = (dir, name) -> !isPrefix.accept(dir, name);

   public FileMoveManager(File folder) {
      this(folder, -1);
   }

   public FileMoveManager(File folder, int maxFolders, String... prefixesToPreserve) {
      this.folder = folder;
      this.maxFolders = maxFolders;
      this.prefixesToPreserve = prefixesToPreserve != null ? Arrays.copyOf(prefixesToPreserve, prefixesToPreserve.length) : null;
   }

   public int getMaxFolders() {
      return maxFolders;
   }

   public FileMoveManager setMaxFolders(int maxFolders) {
      this.maxFolders = maxFolders;
      return this;
   }

   public void doMove() throws IOException {
      String[] files = getFiles();

      if (files == null || files.length == 0) {
         // if no files, nothing to be done, no backup, no deletes... nothing!
         return;
      }

      int whereToMove = getMaxID() + 1;

      if (maxFolders == 0) {
         internalCheckOldFolders(0);
         ActiveMQServerLogger.LOGGER.backupDeletingData(folder.getPath());
         for (String fileMove : files) {
            File fileFrom = new File(folder, fileMove);
            if (prefixesToPreserve != null) {
               boolean skip = false;
               for (String prefixToPreserve : prefixesToPreserve) {
                  if (fileMove.startsWith(prefixToPreserve)) {
                     logger.trace("skipping {}", fileFrom);
                     skip = true;
                     break;
                  }
               }
               if (!skip) {
                  logger.trace("deleting {}", fileFrom);
                  deleteTree(fileFrom);
               }
            } else {
               logger.trace("deleting {}", fileFrom);
               deleteTree(fileFrom);
            }
         }
      } else {
         // Since we will create one folder, we are already taking that one into consideration
         internalCheckOldFolders(1);
         File folderTo = getFolder(whereToMove);
         folderTo.mkdirs();

         ActiveMQServerLogger.LOGGER.backupMovingDataAway(folder.getPath(), folderTo.getPath());

         for (String fileMove : files) {
            File fileFrom = new File(folder, fileMove);
            File fileTo = new File(folderTo, fileMove);
            if (prefixesToPreserve != null) {
               boolean copy = false;
               for (String prefixToPreserve : prefixesToPreserve) {
                  if (fileMove.startsWith(prefixToPreserve)) {
                     logger.trace("skipping {}", fileFrom);
                     copy = true;
                     break;
                  }
               }
               if (copy) {
                  logger.trace("copying {} to {}", fileFrom, fileTo);
                  Files.copy(fileFrom.toPath(), fileTo.toPath());
               } else {
                  logger.trace("doMove:: moving {} as {}", fileFrom, fileTo);
                  Files.move(fileFrom.toPath(), fileTo.toPath());
               }
            } else {
               logger.trace("doMove:: moving {} as {}", fileFrom, fileTo);
               Files.move(fileFrom.toPath(), fileTo.toPath());
            }
         }
      }

   }

   public void checkOldFolders() {
      internalCheckOldFolders(0);
   }

   private void internalCheckOldFolders(int creating) {
      if (maxFolders >= 0) {
         int folders = getNumberOfFolders();

         if (folders == 0) {
            // no folders.. nothing to be done
            return;
         }

         // We are counting the next one to be created
         int foldersToDelete = folders + creating - maxFolders;

         if (foldersToDelete > 0) {
            logger.trace("There are {} folders to delete", foldersToDelete);
            int[] ids = getIDlist();
            for (int i = 0; i < foldersToDelete; i++) {
               File file = getFolder(ids[i]);
               ActiveMQServerLogger.LOGGER.removingBackupData(file.getPath());
               deleteTree(file);
            }
         }
      }
   }

   /**
    * It will return non backup folders
    */
   public String[] getFiles() {
      return folder.list(notPrefix);
   }

   public int getNumberOfFolders() {
      return getFolders().length;
   }

   public String[] getFolders() {
      String[] list = folder.list(isPrefix);

      if (list == null) {
         list = new String[0];
      }

      return list;
   }

   public int getMinID() {
      int[] list = getIDlist();

      if (list.length == 0) {
         return 0;
      }

      return list[0];
   }

   public int getMaxID() {
      int[] list = getIDlist();

      if (list.length == 0) {
         return 0;
      }

      return list[list.length - 1];
   }

   public int[] getIDlist() {
      String[] list = getFolders();
      int[] ids = new int[list.length];
      for (int i = 0; i < ids.length; i++) {
         ids[i] = getID(list[i]);
      }

      Arrays.sort(ids);

      return ids;
   }

   public int getID(String folderName) {
      return Integer.parseInt(folderName.substring(PREFIX.length()));
   }

   public File getFolder(int id) {
      return new File(folder, PREFIX + id);
   }

   private void deleteTree(File file) {
      File[] files = file.listFiles();

      if (files != null) {
         for (File fileDelete : files) {
            deleteTree(fileDelete);
         }
      }

      file.delete();
   }

}
