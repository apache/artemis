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
package org.apache.activemq.artemis.core.postoffice.impl;

import java.util.Objects;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.postoffice.Address;

import static org.apache.activemq.artemis.core.config.WildcardConfiguration.DEFAULT_WILDCARD_CONFIGURATION;

/**
 * Splits an address string into its hierarchical parts using {@link WildcardConfiguration#getDelimiter()} as delimiter.
 */
public class AddressImpl implements Address {

   private final SimpleString address;

   private final SimpleString[] addressParts;

   private final boolean containsWildCard;

   private final WildcardConfiguration wildcardConfiguration;

   public AddressImpl(final SimpleString address) {
      this(address, DEFAULT_WILDCARD_CONFIGURATION);
   }

   public AddressImpl(final SimpleString address, final WildcardConfiguration wildcardConfiguration) {
      this.address = address;
      this.wildcardConfiguration = wildcardConfiguration;
      addressParts = address.split(wildcardConfiguration.getDelimiter());
      containsWildCard = wildcardConfiguration.isWild(address);
   }

   @Override
   public SimpleString getAddress() {
      return address;
   }

   @Override
   public SimpleString[] getAddressParts() {
      return addressParts;
   }

   @Override
   public boolean containsWildCard() {
      return containsWildCard;
   }

   /**
    * This method should actually be called `isMatchedBy`.
    *
    * @return {@code true} if this equals otherAddr or this address is matched by a pattern represented by otherAddr
    */
   @Override
   public boolean matches(final Address otherAddr) {
      if (otherAddr == null) {
         return false;
      }

      if (address.equals(otherAddr.getAddress())) {
         return true;
      }

      final char sepAnyWords = wildcardConfiguration.getAnyWords();
      final char sepSingleWord = wildcardConfiguration.getSingleWord();

      final int thisAddrPartsLen = addressParts.length;
      final int thisAddrPartsLastIdx = thisAddrPartsLen - 1;

      final SimpleString[] otherAddrParts = otherAddr.getAddressParts();
      final int otherAddrPartsLen = otherAddrParts.length;
      final int otherAddrPartsLastIdx = otherAddrPartsLen - 1;

      int thisIdx = 0;
      int otherIdx = 0;

      // iterate through all parts of otherAddr
      while (otherIdx < otherAddrPartsLen) {

         // check if we already tested the last part of this address
         if (thisIdx > thisAddrPartsLastIdx) {
            // check if last part of otherAddr is the any-words wildcard and report a match if so
            if (otherIdx == otherAddrPartsLastIdx) {
               final SimpleString otherAddrLastPart = otherAddrParts[otherAddrPartsLastIdx];
               return !otherAddrLastPart.isEmpty() && otherAddrLastPart.charAt(0) == sepAnyWords;
            }
            return false;
         }

         SimpleString thisCurr = addressParts[thisIdx];
         final SimpleString otherCurr = otherAddrParts[otherIdx];
         final boolean otherCurrPartIsSingleChar = otherCurr.length() == 1;

         // handle single-word wildcard found in otherAddr
         if (otherCurrPartIsSingleChar && otherCurr.charAt(0) == sepSingleWord) {
            thisIdx++;
            otherIdx++;
            continue;
         }

         // handle any-words wildcard found in otherAddr
         if (otherCurrPartIsSingleChar && otherCurr.charAt(0) == sepAnyWords) {

            // if last part of otherAddr is any-words wildcard report a match
            if (otherIdx == otherAddrPartsLastIdx) {
               return true;
            }

            SimpleString thisNext;
            // check if this address has more parts to check
            if (thisIdx < thisAddrPartsLastIdx) {
               thisNext = addressParts[thisIdx + 1];
            } else {
               // no more parts to check, thus check the current part against the next part of otherAddr
               thisNext = thisCurr;
            }

            final SimpleString otherNext = otherAddrParts[otherIdx + 1];
            // iterate through the remaining parts of this address until the part after the any-words wildcard of otherAddr is matched
            while (thisCurr != null) {
               if (thisCurr.equals(otherNext)) {
                  break;
               }
               thisIdx++;
               thisCurr = thisNext;
               thisNext = thisAddrPartsLastIdx > thisIdx ? addressParts[thisIdx + 1] : null;
            }
            // if no further part in this address matched the next part in otherAddr report a mismatch
            if (thisCurr == null) {
               return false;
            }
            otherIdx++;
            continue;
         }

         // compare current parts of bothaddresses and report mismatch if they differ
         if (!thisCurr.equals(otherCurr)) {
            return false;
         }

         thisIdx++;
         otherIdx++;
      }

      // report match if all parts of this address were checked
      return thisIdx == thisAddrPartsLen;
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof AddressImpl other)) {
         return false;
      }

      return Objects.equals(address, other.address);
   }

   @Override
   public int hashCode() {
      return address.hashCode();
   }
}
