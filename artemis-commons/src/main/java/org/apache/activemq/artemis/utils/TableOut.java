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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class TableOut {

   // Unicode box-drawing characters
   public static final String BOX_HORIZONTAL = "─";
   public static final String BOX_VERTICAL = "│";
   public static final String BOX_TOP_LEFT = "┌";
   public static final String BOX_TOP_RIGHT = "┐";
   public static final String BOX_TOP_MID = "┬";
   public static final String BOX_MID_LEFT = "├";
   public static final String BOX_MID_RIGHT = "┤";
   public static final String BOX_MID_MID = "┼";
   public static final String BOX_BOTTOM_LEFT = "└";
   public static final String BOX_BOTTOM_RIGHT = "┘";
   public static final String BOX_BOTTOM_MID = "┴";

   final String separator;
   final int[] columnSizes;
   final int indentation;
   final String indentationString;


   boolean ascii;

   public boolean isAscii() {
      return ascii;
   }

   public TableOut setAscii(boolean ascii) {
      this.ascii = ascii;
      return this;
   }

   public TableOut(String separator, int indentation, int[] columnSizes) {
      this.separator = separator;
      this.columnSizes = columnSizes;
      this.indentation = indentation;

      // building the indentation String to be reused
      indentationString = " ".repeat(indentation);
   }

   /** Print the top border: ┌───┬───┐ (no-op in ascii style) */
   public void printTopSeparator(PrintStream stream) {
      if (!ascii) {
         printBoxLine(stream, BOX_TOP_LEFT, BOX_TOP_MID, BOX_TOP_RIGHT);
      }
   }

   /** Print a middle separator: ├───┼───┤ (or plain dashes in ascii style) */
   public void printSeparator(PrintStream stream) {
      if (ascii) {
         int totalWidth = separator.length() * (columnSizes.length + 1);
         for (int columnSize : columnSizes) {
            totalWidth += columnSize;
         }
         stream.println("-".repeat(totalWidth));
      } else {
         printBoxLine(stream, BOX_MID_LEFT, BOX_MID_MID, BOX_MID_RIGHT);
      }
   }

   /** Print the bottom border: └───┴───┘ (no-op in ascii style) */
   public void printBottomSeparator(PrintStream stream) {
      if (!ascii) {
         printBoxLine(stream, BOX_BOTTOM_LEFT, BOX_BOTTOM_MID, BOX_BOTTOM_RIGHT);
      }
   }

   private void printBoxLine(PrintStream stream, String left, String mid, String right) {
      StringBuilder line = new StringBuilder();
      line.append(left);
      for (int i = 0; i < columnSizes.length; i++) {
         line.append(BOX_HORIZONTAL.repeat(columnSizes[i]));
         line.append(i < columnSizes.length - 1 ? mid : right);
      }
      stream.println(line);
   }

   public void print(PrintStream stream, String[] columns) {
      print(stream, columns, null);
   }

   public void print(PrintStream stream, String[] columns, boolean[] center) {
      List<String>[] splitColumns = new ArrayList[columns.length];
      for (int i = 0; i < columns.length; i++) {
         splitColumns[i] = splitLine(columns[i], columnSizes[i]);
      }

      print(stream, splitColumns, center);
   }

   public void print(PrintStream stream, List<String>[] splitColumns) {
      print(stream, splitColumns, null);
   }

   public void print(PrintStream stream, List<String>[] splitColumns, boolean[] centralize) {
      boolean hasMoreLines;
      int lineNumber = 0;
      do {
         hasMoreLines = false;
         stream.print(ascii ? separator : BOX_VERTICAL);
         for (int column = 0; column < splitColumns.length; column++) {
            StringBuilder cell = new StringBuilder();

            String cellString;

            if (lineNumber < splitColumns[column].size()) {
               cellString = splitColumns[column].get(lineNumber);
            } else {
               cellString = "";
            }

            if (centralize != null && centralize[column] && !cellString.isEmpty()) {
               cell.append(" ".repeat((columnSizes[column] - cellString.length()) / 2));
            }

            cell.append(cellString);

            if (lineNumber + 1 < splitColumns[column].size()) {
               hasMoreLines = true;
            }
            while (cell.length() < columnSizes[column]) {
               cell.append(" ");
            }
            stream.print(cell);
            stream.print(ascii ? separator : BOX_VERTICAL);
         }
         stream.println();
         lineNumber++;
      }
      while (hasMoreLines);
   }

   public List<String> splitLine(final String column, int size) {
      List<String> cells = new ArrayList<>();

      for (int position = 0; position < column.length();) {
         int indentationUsed;
         String indentationStringUsed;
         if (position == 0 || indentation == 0) {
            indentationUsed = 0;
            indentationStringUsed = "";
         } else {
            indentationUsed = indentation;
            indentationStringUsed = this.indentationString;
         }
         int available = size - indentationUsed;
         int remaining = column.length() - position;

         if (remaining <= available) {
            // everything fits — no split needed
            cells.add(indentationStringUsed + column.substring(position));
            break;
         }

         // look backwards from the hard-break position for the last non-alphanumeric character
         // but only accept it if it falls at or beyond the halfway point of the available width
         int hardBreak = available;
         int naturalBreak = -1;
         for (int i = hardBreak - 1; i >= available / 2; i--) {
            if (!Character.isLetterOrDigit(column.charAt(position + i))) {
               naturalBreak = i + 1; // split after the non-alphanumeric character
               break;
            }
         }

         int splitAt = naturalBreak > 0 ? naturalBreak : hardBreak;
         cells.add(indentationStringUsed + column.substring(position, position + splitAt));
         position += splitAt;
      }

      return cells;
   }

}
