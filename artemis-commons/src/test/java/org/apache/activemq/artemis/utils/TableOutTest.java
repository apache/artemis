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

package org.apache.activemq.artemis.utils;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TableOutTest {

   @Test
   public void testSplitString() {
      String bigCell = "1234554321321";
      TableOut tableOut = new TableOut("|", 0, new int[]{10, 3, 3});
      List<String> lines = tableOut.splitLine(bigCell, 5);
      assertEquals(3, lines.size());
      assertEquals("12345", lines.get(0));
      assertEquals("54321", lines.get(1));
      assertEquals("321", lines.get(2));
   }

   @Test
   public void testNaturalSplit() {
      String big = "Thisisbigbig ";
      String bigCell = big + "short ".repeat(10);
      TableOut tableOut = new TableOut("|", 0, new int[]{10, 3, 3});
      List<String> lines = tableOut.splitLine(bigCell, 14);
      for (int i = 1; i < lines.size(); i++) {
         assertTrue(lines.get(i).startsWith("short "));
      }
   }

   @Test
   public void testSplitStringIdented() {
      String bigCell = "1234532132";
      TableOut tableOut = new TableOut("|", 2, new int[]{10, 3, 3});
      List<String> lines = tableOut.splitLine(bigCell, 5);
      assertEquals(3, lines.size());
      assertEquals("12345", lines.get(0));
      assertEquals("  321", lines.get(1));
      assertEquals("  32", lines.get(2));
   }

   @Test
   public void testOutLine() {
      // the output is visual, however this test is good to make sure the output at least works without any issues
      TableOut tableOut = new TableOut("|", 2, new int[]{5, 20, 20});
      tableOut.printTopSeparator(System.out);
      tableOut.print(System.out, new String[]{"This is a big title", "1234567", "1234"});
      tableOut.printSeparator(System.out);
      tableOut.print(System.out, new String[]{"row1", "value1", "value2"});
      tableOut.printBottomSeparator(System.out);
      tableOut = new TableOut("|", 0, new int[]{10, 20, 20});
      tableOut.printTopSeparator(System.out);
      tableOut.print(System.out, new String[]{"This is a big title", "1234567", "1234"}, new boolean[]{true, true, true});
      tableOut.printBottomSeparator(System.out);
   }

   @Test
   public void testBoxDrawingCharacters() {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      java.io.PrintStream ps = new java.io.PrintStream(baos);

      TableOut tableOut = new TableOut("|", 0, new int[]{5, 5});
      tableOut.printTopSeparator(ps);
      tableOut.print(ps, new String[]{"Col1", "Col2"});
      tableOut.printSeparator(ps);
      tableOut.print(ps, new String[]{"r1c1", "r1c2"});
      tableOut.printBottomSeparator(ps);

      String output = baos.toString();
      String[] lines = output.split(System.lineSeparator());

      // top border uses ┌ ┬ ┐
      assertTrue(lines[0].startsWith("┌"), "Top line should start with ┌");
      assertTrue(lines[0].contains("┬"), "Top line should contain ┬");
      assertTrue(lines[0].endsWith("┐"), "Top line should end with ┐");

      // data rows use │
      assertTrue(lines[1].startsWith("│"), "Data row should start with │");
      assertTrue(lines[1].endsWith("│"), "Data row should end with │");

      // middle separator uses ├ ┼ ┤
      assertTrue(lines[2].startsWith("├"), "Middle separator should start with ├");
      assertTrue(lines[2].contains("┼"), "Middle separator should contain ┼");
      assertTrue(lines[2].endsWith("┤"), "Middle separator should end with ┤");

      // bottom border uses └ ┴ ┘
      assertTrue(lines[4].startsWith("└"), "Bottom line should start with └");
      assertTrue(lines[4].contains("┴"), "Bottom line should contain ┴");
      assertTrue(lines[4].endsWith("┘"), "Bottom line should end with ┘");
   }

   @Test
   public void testAscii() {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      java.io.PrintStream ps = new java.io.PrintStream(baos);

      TableOut tableOut = new TableOut("|", 0, new int[]{5, 5});
      tableOut.setAscii(true);
      tableOut.printTopSeparator(ps);     // no-op in ascii mode
      tableOut.print(ps, new String[]{"Col1", "Col2"});
      tableOut.printSeparator(ps);        // plain dashes in ascii mode
      tableOut.print(ps, new String[]{"r1c1", "r1c2"});
      tableOut.printBottomSeparator(ps);  // no-op in ascii mode

      String output = baos.toString();
      String[] lines = output.split(System.lineSeparator());

      // ascii mode: only 3 lines — header row, dash separator, data row (no top/bottom border)
      assertEquals(3, lines.length, "ascii mode should produce exactly 3 lines");

      // header row uses plain | delimiter, no box-drawing characters
      assertTrue(lines[0].startsWith("|"), "Header row should start with |");
      assertTrue(lines[0].endsWith("|"), "Header row should end with |");
      assertTrue(lines[0].contains("Col1"), "Header row should contain Col1");
      assertTrue(lines[0].contains("Col2"), "Header row should contain Col2");

      // separator is plain dashes only
      assertTrue(lines[1].matches("-+"), "Separator should be plain dashes");

      // data row uses plain | delimiter, no box-drawing characters
      assertTrue(lines[2].startsWith("|"), "Data row should start with |");
      assertTrue(lines[2].endsWith("|"), "Data row should end with |");
      assertTrue(lines[2].contains("r1c1"), "Data row should contain r1c1");
      assertTrue(lines[2].contains("r1c2"), "Data row should contain r1c2");

      // no Unicode box-drawing characters anywhere
      assertFalse(output.contains(TableOut.BOX_TOP_LEFT), "ascii mode should not contain " + TableOut.BOX_TOP_LEFT);
      assertFalse(output.contains(TableOut.BOX_TOP_MID), "ascii mode should not contain " + TableOut.BOX_TOP_MID);
      assertFalse(output.contains(TableOut.BOX_TOP_RIGHT), "ascii mode should not contain " + TableOut.BOX_TOP_RIGHT);
      assertFalse(output.contains(TableOut.BOX_VERTICAL), "ascii mode should not contain " + TableOut.BOX_VERTICAL);
      assertFalse(output.contains(TableOut.BOX_MID_LEFT), "ascii mode should not contain " + TableOut.BOX_MID_LEFT);
      assertFalse(output.contains(TableOut.BOX_MID_MID), "ascii mode should not contain " + TableOut.BOX_MID_MID);
      assertFalse(output.contains(TableOut.BOX_MID_RIGHT), "ascii mode should not contain " + TableOut.BOX_MID_RIGHT);
      assertFalse(output.contains(TableOut.BOX_BOTTOM_LEFT), "ascii mode should not contain " + TableOut.BOX_BOTTOM_LEFT);
      assertFalse(output.contains(TableOut.BOX_BOTTOM_MID), "ascii mode should not contain " + TableOut.BOX_BOTTOM_MID);
      assertFalse(output.contains(TableOut.BOX_BOTTOM_RIGHT), "ascii mode should not contain " + TableOut.BOX_BOTTOM_RIGHT);
   }

}
