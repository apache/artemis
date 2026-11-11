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
package org.apache.activemq.artemis.core.version.impl;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

import org.apache.activemq.artemis.core.version.Version;

public class VersionImpl implements Version, Serializable {

   private static final long serialVersionUID = -5271227256591080403L;

   private final String versionName;

   private final int majorVersion;

   private final int minorVersion;

   private final int microVersion;

   private final int incrementingVersion;

   private final int[] compatibleVersionList;



   public VersionImpl(final String versionName,
                      final int majorVersion,
                      final int minorVersion,
                      final int microVersion,
                      final int incrementingVersion,
                      final int[] compatibleVersionList) {
      this.versionName = versionName;

      this.majorVersion = majorVersion;

      this.minorVersion = minorVersion;

      this.microVersion = microVersion;

      this.incrementingVersion = incrementingVersion;

      this.compatibleVersionList = Arrays.copyOf(compatibleVersionList, compatibleVersionList.length);
   }

   // Version implementation ------------------------------------------

   @Override
   public String getFullVersion() {
      return versionName;
   }

   @Override
   public String getVersionName() {
      return versionName;
   }

   @Override
   public int getMajorVersion() {
      return majorVersion;
   }

   @Override
   public int getMinorVersion() {
      return minorVersion;
   }

   @Override
   public int getMicroVersion() {
      return microVersion;
   }

   @Override
   public int getIncrementingVersion() {
      return incrementingVersion;
   }

   @Override
   public boolean isCompatible(int version) {
      for (int element : compatibleVersionList) {
         if (element == version) {
            return true;
         }
      }
      return false;
   }

   @Override
   public int hashCode() {
      return Objects.hash(Arrays.hashCode(compatibleVersionList), incrementingVersion, majorVersion, microVersion,
                          minorVersion, versionName);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof VersionImpl other)) {
         return false;
      }

      return Arrays.equals(compatibleVersionList, other.compatibleVersionList) &&
             incrementingVersion == other.incrementingVersion &&
             majorVersion == other.majorVersion &&
             microVersion == other.microVersion &&
             minorVersion == other.minorVersion &&
             Objects.equals(versionName, other.versionName);
   }
}
