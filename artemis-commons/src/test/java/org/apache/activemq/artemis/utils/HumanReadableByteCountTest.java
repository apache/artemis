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
package org.apache.activemq.artemis.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import org.junit.jupiter.api.Test;

public class HumanReadableByteCountTest {

   @Test
   public void testDefaultLocale() {
      internalTest(Locale.getDefault());
   }

   @Test
   public void testEnglishLocale() {
      internalTest(Locale.ENGLISH);
   }

   @Test
   public void testFrenchLocale() {
      // This locale will use commas instead of periods
      internalTest(Locale.FRENCH);
   }

   private void internalTest(final Locale testLocale) {
      // track the default
      Locale defaultLocale = Locale.getDefault();

      Locale.setDefault(testLocale);
      DecimalFormat decimalFormat = new DecimalFormat("###.0", DecimalFormatSymbols.getInstance(testLocale));

      try {
         String[] suffixes = new String[]{"K", "M", "G", "T", "P", "E"};

         assertEquals("0B", ByteUtil.getHumanReadableByteCount(0));
         assertEquals(decimalFormat.format(999.0) + "B", ByteUtil.getHumanReadableByteCount(999));
         assertEquals(decimalFormat.format(500.0) + "B", ByteUtil.getHumanReadableByteCount(500));

         for (int i = 0, j = 3; i < 6; i++, j += 3) {
            final long magnitude = (long) Math.pow(10, j);
            assertEquals(decimalFormat.format(1.0) + suffixes[i] + "B", ByteUtil.getHumanReadableByteCount(magnitude));
            assertEquals(decimalFormat.format(1.3) + suffixes[i] + "B", ByteUtil.getHumanReadableByteCount(magnitude + (long) (.25 * magnitude)));
            assertEquals(decimalFormat.format(1.5) + suffixes[i] + "B", ByteUtil.getHumanReadableByteCount(magnitude + (long) (.5 * magnitude)));
            assertEquals(decimalFormat.format(1.9) + suffixes[i] + "B", ByteUtil.getHumanReadableByteCount(magnitude + (long) (.9 * magnitude)));
            assertEquals(decimalFormat.format(4.2) + suffixes[i] + "B", ByteUtil.getHumanReadableByteCount(magnitude + (long) (3.2 * magnitude)));
         }
      } finally {
         // reset the default once the test is over
         Locale.setDefault(defaultLocale);
      }
   }
}
