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
package org.apache.activemq.artemis.core.list;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.netty.util.collection.LongObjectHashMap;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.collections.LinkedListImpl;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.apache.activemq.artemis.utils.collections.NodeStore;
import org.apache.activemq.artemis.utils.collections.PriorityLinkedListImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class PriorityLinkedListTest {

   protected Wibble a;

   protected Wibble b;

   protected Wibble c;

   protected Wibble d;

   protected Wibble e;

   protected Wibble f;

   protected Wibble g;

   protected Wibble h;

   protected Wibble i;

   protected Wibble j;

   protected Wibble k;

   protected Wibble l;

   protected Wibble m;

   protected Wibble n;

   protected Wibble o;

   protected Wibble p;

   protected Wibble q;

   protected Wibble r;

   protected Wibble s;

   protected Wibble t;

   protected Wibble u;

   protected Wibble v;

   protected Wibble w;

   protected Wibble x;

   protected Wibble y;

   protected Wibble z;

   private PriorityLinkedListImpl<Wibble> list;

   int lastRemovedLevel;
   Wibble lastRemovedWibble;

   private PriorityLinkedListImpl<Wibble> getList() {
      return new PriorityLinkedListImpl<>(10) {
         @Override
         protected void removed(int level, Wibble element) {
            super.removed(level, element);
            lastRemovedWibble = element;
            lastRemovedLevel = level;
         }
      };
   }

   @BeforeEach
   public void setUp() throws Exception {

      list = getList();

      a = new Wibble("a", 1);
      b = new Wibble("b", 2);
      c = new Wibble("c", 3);
      d = new Wibble("d", 4);
      e = new Wibble("e", 5);
      f = new Wibble("f", 6);
      g = new Wibble("g", 7);
      h = new Wibble("h", 8);
      i = new Wibble("i", 9);
      j = new Wibble("j", 10);
      k = new Wibble("k", 11);
      l = new Wibble("l", 12);
      m = new Wibble("m", 13);
      n = new Wibble("n", 14);
      o = new Wibble("o", 15);
      p = new Wibble("p", 16);
      q = new Wibble("q", 17);
      r = new Wibble("r", 18);
      s = new Wibble("s", 19);
      t = new Wibble("t", 20);
      u = new Wibble("u", 21);
      v = new Wibble("v", 22);
      w = new Wibble("w", 23);
      x = new Wibble("x", 24);
      y = new Wibble("y", 25);
      z = new Wibble("z", 26);
   }

   @Test
   public void testEmpty() throws Exception {
      assertTrue(list.isEmpty());

      list.addHead(a, 0);

      assertFalse(list.isEmpty());

      Wibble w1 = list.poll();
      assertEquals(a, w1);
      assertTrue(list.isEmpty());

      assertEquals(0, list.size());
   }

   @Test
   public void testaddHead() throws Exception {
      list.addHead(a, 0);
      list.addHead(b, 0);
      list.addHead(c, 0);
      list.addHead(d, 0);
      list.addHead(e, 0);

      assertEquals(5, list.size());

      assertEquals(e, list.poll());
      assertEquals(d, list.poll());
      assertEquals(c, list.poll());
      assertEquals(b, list.poll());
      assertEquals(a, list.poll());
      assertNull(list.poll());

      assertEquals(0, list.size());
   }

   @Test
   public void testaddTail() throws Exception {
      list.addTail(a, 0);
      list.addTail(b, 0);
      list.addTail(c, 0);
      list.addTail(d, 0);
      list.addTail(e, 0);
      assertEquals(5, list.size());

      assertEquals(a, list.poll());
      assertEquals(b, list.poll());
      assertEquals(c, list.poll());
      assertEquals(d, list.poll());
      assertEquals(e, list.poll());
      assertNull(list.poll());

      assertEquals(0, list.size());

   }

   @Test
   public void testAddLastAndFirst() throws Exception {
      list.addTail(a, 0);
      list.addTail(b, 0);
      list.addTail(c, 0);
      list.addTail(d, 0);
      list.addTail(e, 0);
      list.addTail(f, 0);
      list.addTail(g, 0);
      list.addTail(h, 0);
      list.addTail(i, 0);
      list.addTail(j, 0);

      list.addHead(k, 0);
      list.addHead(l, 0);
      list.addHead(m, 0);
      list.addHead(n, 0);
      list.addHead(o, 0);
      list.addHead(p, 0);
      list.addHead(q, 0);
      list.addHead(r, 0);
      list.addHead(s, 0);
      list.addHead(t, 0);

      assertEquals(t, list.poll());
      assertEquals(s, list.poll());
      assertEquals(r, list.poll());
      assertEquals(q, list.poll());
      assertEquals(p, list.poll());
      assertEquals(o, list.poll());
      assertEquals(n, list.poll());
      assertEquals(m, list.poll());
      assertEquals(l, list.poll());
      assertEquals(k, list.poll());

      assertEquals(a, list.poll());
      assertEquals(b, list.poll());
      assertEquals(c, list.poll());
      assertEquals(d, list.poll());
      assertEquals(e, list.poll());
      assertEquals(f, list.poll());
      assertEquals(g, list.poll());
      assertEquals(h, list.poll());
      assertEquals(i, list.poll());
      assertEquals(j, list.poll());
   }

   @Test
   public void testAddLastAndFirstWithIterator() throws Exception {
      list.addTail(a, 0);
      list.addTail(b, 0);
      list.addTail(c, 0);
      list.addTail(d, 0);
      list.addTail(e, 0);
      list.addTail(f, 0);
      list.addTail(g, 0);
      list.addTail(h, 0);
      list.addTail(i, 0);
      list.addTail(j, 0);

      list.addHead(k, 0);
      list.addHead(l, 0);
      list.addHead(m, 0);
      list.addHead(n, 0);
      list.addHead(o, 0);
      list.addHead(p, 0);
      list.addHead(q, 0);
      list.addHead(r, 0);
      list.addHead(s, 0);
      list.addHead(t, 0);

      LinkedListIterator<Wibble> iter = list.iterator();

      assertTrue(iter.hasNext());
      assertEquals(t, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(s, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(r, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(q, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(p, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(o, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(n, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(m, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(l, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(k, iter.next());

      assertTrue(iter.hasNext());
      assertEquals(a, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(b, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(c, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(d, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(e, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(f, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(g, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(h, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(i, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(j, iter.next());
   }

   @Test
   public void testPoll() throws Exception {
      list.addTail(a, 0);
      list.addTail(b, 1);
      list.addTail(c, 2);
      list.addTail(d, 3);
      list.addTail(e, 4);
      list.addTail(f, 5);
      list.addTail(g, 6);
      list.addTail(h, 7);
      list.addTail(i, 8);
      list.addTail(j, 9);

      assertEquals(j, list.poll());
      assertEquals(i, list.poll());
      assertEquals(h, list.poll());
      assertEquals(g, list.poll());
      assertEquals(f, list.poll());
      assertEquals(e, list.poll());
      assertEquals(d, list.poll());
      assertEquals(c, list.poll());
      assertEquals(b, list.poll());
      assertEquals(a, list.poll());

      assertNull(list.poll());

      list.addTail(a, 9);
      list.addTail(b, 8);
      list.addTail(c, 7);
      list.addTail(d, 6);
      list.addTail(e, 5);
      list.addTail(f, 4);
      list.addTail(g, 3);
      list.addTail(h, 2);
      list.addTail(i, 1);
      list.addTail(j, 0);

      assertEquals(a, list.poll());
      assertEquals(b, list.poll());
      assertEquals(c, list.poll());
      assertEquals(d, list.poll());
      assertEquals(e, list.poll());
      assertEquals(f, list.poll());
      assertEquals(g, list.poll());
      assertEquals(h, list.poll());
      assertEquals(i, list.poll());
      assertEquals(j, list.poll());

      assertNull(list.poll());

      list.addTail(a, 9);
      list.addTail(b, 0);
      list.addTail(c, 8);
      list.addTail(d, 1);
      list.addTail(e, 7);
      list.addTail(f, 2);
      list.addTail(g, 6);
      list.addTail(h, 3);
      list.addTail(i, 5);
      list.addTail(j, 4);

      assertEquals(a, list.poll());
      assertEquals(c, list.poll());
      assertEquals(e, list.poll());
      assertEquals(g, list.poll());
      assertEquals(i, list.poll());
      assertEquals(j, list.poll());
      assertEquals(h, list.poll());
      assertEquals(f, list.poll());
      assertEquals(d, list.poll());
      assertEquals(b, list.poll());

      assertNull(list.poll());

      list.addTail(a, 0);
      list.addTail(b, 3);
      list.addTail(c, 3);
      list.addTail(d, 3);
      list.addTail(e, 6);
      list.addTail(f, 6);
      list.addTail(g, 6);
      list.addTail(h, 9);
      list.addTail(i, 9);
      list.addTail(j, 9);

      assertEquals(h, list.poll());
      assertEquals(i, list.poll());
      assertEquals(j, list.poll());
      assertEquals(e, list.poll());
      assertEquals(f, list.poll());
      assertEquals(g, list.poll());
      assertEquals(b, list.poll());
      assertEquals(c, list.poll());
      assertEquals(d, list.poll());
      assertEquals(a, list.poll());

      assertNull(list.poll());

      list.addTail(a, 5);
      list.addTail(b, 5);
      list.addTail(c, 5);
      list.addTail(d, 5);
      list.addTail(e, 5);
      list.addTail(f, 5);
      list.addTail(g, 5);
      list.addTail(h, 5);
      list.addTail(i, 5);
      list.addTail(j, 5);

      assertEquals(a, list.poll());
      assertEquals(b, list.poll());
      assertEquals(c, list.poll());
      assertEquals(d, list.poll());
      assertEquals(e, list.poll());
      assertEquals(f, list.poll());
      assertEquals(g, list.poll());
      assertEquals(h, list.poll());
      assertEquals(i, list.poll());
      assertEquals(j, list.poll());

      assertNull(list.poll());

      list.addTail(j, 5);
      list.addTail(i, 5);
      list.addTail(h, 5);
      list.addTail(g, 5);
      list.addTail(f, 5);
      list.addTail(e, 5);
      list.addTail(d, 5);
      list.addTail(c, 5);
      list.addTail(b, 5);
      list.addTail(a, 5);

      assertEquals(j, list.poll());
      assertEquals(i, list.poll());
      assertEquals(h, list.poll());
      assertEquals(g, list.poll());
      assertEquals(f, list.poll());
      assertEquals(e, list.poll());
      assertEquals(d, list.poll());
      assertEquals(c, list.poll());
      assertEquals(b, list.poll());
      assertEquals(a, list.poll());

      assertNull(list.poll());

      assertEquals(0, list.size());

   }

   @Test
   public void testIterator() {
      list.addTail(a, 9);
      list.addTail(b, 9);
      list.addTail(c, 8);
      list.addTail(d, 8);
      list.addTail(e, 7);
      list.addTail(f, 7);
      list.addTail(g, 7);
      list.addTail(h, 6);
      list.addTail(i, 6);
      list.addTail(j, 6);
      list.addTail(k, 5);
      list.addTail(l, 5);
      list.addTail(m, 4);
      list.addTail(n, 4);
      list.addTail(o, 4);
      list.addTail(p, 3);
      list.addTail(q, 3);
      list.addTail(r, 3);
      list.addTail(s, 2);
      list.addTail(t, 2);
      list.addTail(u, 2);
      list.addTail(v, 1);
      list.addTail(w, 1);
      list.addTail(x, 1);
      list.addTail(y, 0);
      list.addTail(z, 0);

      LinkedListIterator<Wibble> iter = list.iterator();

      int count = 0;
      Wibble w1;
      while (iter.hasNext()) {
         w1 = iter.next();
         count++;
      }
      assertEquals(26, count);
      assertEquals(26, list.size());

      iter = list.iterator();

      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertTrue(iter.hasNext());
      assertEquals("a", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertTrue(iter.hasNext());
      assertEquals("b", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("c", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("d", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("e", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("f", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("g", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("h", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("i", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("j", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("k", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("l", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("m", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("n", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("o", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("p", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("q", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("r", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("s", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("t", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("u", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("v", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("w", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("x", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("y", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("z", w1.s1);
      assertFalse(iter.hasNext());

      iter = list.iterator();
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("a", w1.s1);

      iter.remove();

      assertEquals(25, list.size());

      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("b", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("c", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("d", w1.s1);

      iter.remove();

      assertEquals(24, list.size());

      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("c", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("e", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("f", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("g", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("h", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("i", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("j", w1.s1);

      iter.remove();

      assertEquals(23, list.size());

      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("i", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("k", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("l", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("m", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("n", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("o", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("p", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("q", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("r", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("s", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("t", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("u", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("v", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("w", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("x", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("y", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("z", w1.s1);
      iter.remove();

      iter = list.iterator();
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("b", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("c", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("e", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("f", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("g", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("h", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("i", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("k", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("l", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("m", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("n", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("o", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("p", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("q", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("r", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("s", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("t", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("u", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("v", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("w", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("x", w1.s1);
      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("y", w1.s1);

      assertFalse(iter.hasNext());
      assertFalse(iter.hasNext());

      // Test the elements added after iter created are seen

      list.addTail(a, 4);
      list.addTail(b, 4);

      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("a", w1.s1);

      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("b", w1.s1);

      assertFalse(iter.hasNext());

      list.addTail(c, 4);
      list.addTail(d, 4);

      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("c", w1.s1);

      assertTrue(iter.hasNext());
      w1 = iter.next();
      assertEquals("d", w1.s1);

      assertFalse(iter.hasNext());

   }

   @Test
   public void testIteratorPicksUpHigherPriorities() {
      list.addTail(a, 4);
      list.addTail(b, 4);
      list.addTail(c, 4);

      LinkedListIterator<Wibble> iter = list.iterator();

      assertTrue(iter.hasNext());
      assertEquals(a, iter.next());

      assertTrue(iter.hasNext());
      assertEquals(b, iter.next());

      list.addTail(d, 5);
      list.addTail(e, 5);

      assertTrue(iter.hasNext());
      assertEquals(d, iter.next());

      assertTrue(iter.hasNext());
      assertEquals(e, iter.next());

      assertTrue(iter.hasNext());
      assertEquals(c, iter.next());

      list.addTail(f, 1);
      list.addTail(g, 9);

      assertTrue(iter.hasNext());
      assertEquals(g, iter.next());

      assertTrue(iter.hasNext());
      assertEquals(f, iter.next());
   }

   @Test
   public void testClear() {
      list.addTail(a, 0);
      list.addTail(b, 3);
      list.addTail(c, 3);
      list.addTail(d, 3);
      list.addTail(e, 6);
      list.addTail(f, 6);
      list.addTail(g, 6);
      list.addTail(h, 9);
      list.addTail(i, 9);
      list.addTail(j, 9);

      list.clear();

      assertNull(list.poll());
   }

   @Test
   public void testMixupIterator() {
      list.addTail(c, 5);
      list.addTail(a, 4);
      list.addTail(b, 4);

      LinkedListIterator<Wibble> iter = list.iterator();

      assertTrue(iter.hasNext());
      assertEquals(c, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(a, iter.next());
      assertTrue(iter.hasNext());
      assertEquals(b, iter.next());
      list.addTail(d, 5);
      assertTrue(iter.hasNext());
      assertEquals(d, iter.next());
   }

   @Test
   public void testMixupIterator2() {
      list.addTail(c, 5);

      list.addTail(k, 0);

      list.addTail(a, 2);
      list.addTail(b, 2);

      LinkedListIterator<Wibble> iter = list.iterator();

      assertTrue(iter.hasNext());
      assertEquals(c, iter.next());
      iter.remove();

      assertTrue(iter.hasNext());
      assertEquals(a, iter.next());
      iter.remove();

      assertTrue(iter.hasNext());
      assertEquals(b, iter.next());
      iter.remove();

      assertTrue(iter.hasNext());
      assertEquals(k, iter.next());
      iter.remove();

      list.addTail(d, 2);

      assertTrue(iter.hasNext());
      assertEquals(d, iter.next());
      iter.remove();
   }

   @Test
   public void testMixupIterator3() {
      list.addTail(b, 4);
      list.addTail(c, 9);
      LinkedListIterator<Wibble> iter = list.iterator();
      assertTrue(iter.hasNext());
      assertEquals(c, iter.next());
      iter.remove();
      list.addTail(a, 0);
      assertTrue(iter.hasNext());
      assertEquals(b, iter.next());
   }

   @Test
   public void testPeek() {
      assertNull(list.peek());

      list.addTail(c, 5);
      assertEquals(c, list.peek());

      list.addTail(k, 0);
      assertEquals(k, list.peek());

      list.addHead(a, 0);
      assertEquals(a, list.peek());
   }


   @Test
   public void testRemoveWithID() {

      for (int i = 1; i <= 3000; i++) {
         list.addHead(new Wibble("" + i, i), i % 10);
      }

      list.setNodeStore(WibbleNodeStore::new);

      // remove every 3rd
      for (int i = 3; i <= 3000; i += 3) {
         assertEquals(new Wibble("" + i, i), list.removeWithID("", i));
      }

      assertEquals(2000, list.size());

      Iterator<Wibble> iterator = list.iterator();

      Set<String> values = new HashSet<>();
      while (iterator.hasNext()) {
         values.add(iterator.next().s1);
      }

      assertEquals(2000, values.size());


      for (int i = 1; i <= 3000; i += 3) {
         if (i % 3 == 0) {
            assertFalse(values.contains("" + i));
         } else {
            assertTrue(values.contains("" + i));
         }
      }


   }

   @Test
   public void testRemoveWithRandomOrderID() {

      list.setNodeStore(WibbleNodeStore::new);

      List<Integer> usedIds = new ArrayList<>();

      int elements = 50;

      for (int i = 1; i <= elements; i++) {
         int level = RandomUtil.randomInterval(0, 2);
         list.addTail(new Wibble("" + i, i, level), level);
         usedIds.add(i);
      }

      Map<Integer, Wibble> hashMapOutput = new HashMap<>(elements);

      while (!usedIds.isEmpty()) {
         Integer idToRemove = usedIds.remove(RandomUtil.randomInterval(0, usedIds.size() - 1));
         Wibble wibble = list.removeWithID("", idToRemove);
         assertNotNull(wibble);
         assertEquals(idToRemove.intValue(), wibble.id);
         assertSame(wibble, lastRemovedWibble);
         // removing from the wrong could create a mess in the linked list nodes
         assertEquals(wibble.level, lastRemovedLevel);
         hashMapOutput.put(idToRemove, wibble);
      }

      assertEquals(elements, hashMapOutput.size());

      for (int i = 1; i <= elements; i++) {
         Wibble wibble = hashMapOutput.remove(i);
         assertNotNull(wibble);
         assertEquals(i, wibble.id);
      }

      assertEquals(0, hashMapOutput.size());
   }

   static class Wibble {

      String s1;
      long id;

      int level;

      Wibble(final String s, long id) {
         this(s, id, 4);
      }

      Wibble(final String s, long id, int level) {
         this.s1 = s;
         this.id = id;
         this.level = level;
      }

      @Override
      public String toString() {
         return "s = " + s1 + ", id = " + id + ", level = " + level;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         }
         if (!(obj instanceof Wibble other)) {
            return false;
         }

         return Objects.equals(s1, other.s1) &&
                Objects.equals(id, other.id) &&
                Objects.equals(level, other.level);
      }

      @Override
      public int hashCode() {
         return Objects.hashCode(s1);
      }
   }

   class WibbleNodeStore implements NodeStore<Wibble> {
      LongObjectHashMap<LinkedListImpl.Node<Wibble>> list = new LongObjectHashMap<>();

      @Override
      public void storeNode(Wibble element, LinkedListImpl.Node<Wibble> node) {
         list.put(element.id, node);
      }

      @Override
      public LinkedListImpl.Node<Wibble> getNode(String listID, long id) {
         return list.get(id);
      }

      @Override
      public void removeNode(Wibble element, LinkedListImpl.Node<Wibble> node) {
         list.remove(element.id);
      }

      @Override
      public void clear() {
         list.clear();
      }

      @Override
      public int size() {
         return list.size();
      }
   }


}
