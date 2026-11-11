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
package org.apache.activemq.artemis.tests.unit.core.postoffice.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.Address;
import org.apache.activemq.artemis.core.postoffice.impl.AddressImpl;
import org.apache.activemq.artemis.core.postoffice.impl.AddressMap;
import org.apache.activemq.artemis.core.postoffice.impl.AddressMapVisitor;
import org.junit.jupiter.api.Test;

public class AddressMapUnitTest {

   AddressMap<SimpleString> underTest = new AddressMap<>("#", "*", '.');

   @Test
   public void testAddGetRemove() throws Exception {

      SimpleString a = SimpleString.of("a.b.c");

      assertTrue(isEmpty(a));

      underTest.put(a, a);

      assertFalse(isEmpty(a));

      assertEquals(1, countMatchingWildcards(a));

      underTest.remove(a, a);

      assertTrue(isEmpty(a));
   }

   private boolean isEmpty(SimpleString match) throws Exception {
      return countMatchingWildcards(match) == 0;
   }

   @Test
   public void testWildcardAddGet() throws Exception {

      SimpleString a = SimpleString.of("a.*.c");

      assertTrue(isEmpty(a));

      underTest.put(a, a);

      assertFalse(isEmpty(a));

      assertEquals(1, countMatchingWildcards(a));

      underTest.remove(a, a);

      assertTrue(isEmpty(a));
   }

   @Test
   public void testWildcardAllAddGet() throws Exception {

      SimpleString a = SimpleString.of("a.b.#");

      assertTrue(isEmpty(a));

      underTest.put(a, a);

      assertFalse(isEmpty(a));

      assertEquals(1, countMatchingWildcards(a));

      underTest.remove(a, a);

      assertTrue(isEmpty(a));
   }

   @Test
   public void testNoDots() throws Exception {
      SimpleString s1 = SimpleString.of("abcde");
      SimpleString s2 = SimpleString.of("abcde");

      underTest.put(s1, s1);
      assertEquals(1, countMatchingWildcards(s2));
   }

   @Test
   public void testDotsSameLength2() throws Exception {
      SimpleString s1 = SimpleString.of("a.b");
      SimpleString s2 = SimpleString.of("a.b");

      underTest.put(s1, s1);
      assertEquals(1, countMatchingWildcards(s2));
   }

   @Test
   public void testA() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c");
      SimpleString s2 = SimpleString.of("a.b.c.d.e.f.g.h.i.j.k.l.m.n.*");

      underTest.put(s1, s1);
      assertEquals(0, countMatchingWildcards(s2));
   }

   @Test
   public void testB() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s2 = SimpleString.of("a.b.x.e");
      SimpleString s3 = SimpleString.of("a.b.c.*");

      underTest.put(s1, s1);
      underTest.put(s2, s2);

      assertEquals(1, countNonWildcardMatching(s3));
   }

   @Test
   public void testC() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s2 = SimpleString.of("a.b.c.x");
      SimpleString s3 = SimpleString.of("a.b.*.d");

      underTest.put(s1, s1);
      underTest.put(s2, s2);

      assertEquals(1, countNonWildcardMatching(s3));
   }

   @Test
   public void testD() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d.e");
      SimpleString s2 = SimpleString.of("a.b.c.x.e");
      SimpleString s3 = SimpleString.of("a.b.*.d.*");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));

      underTest.put(s1, s1);
      underTest.put(s2, s2);

      assertEquals(1, countNonWildcardMatching(s3));
   }

   @Test
   public void testE() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.x.e.f");
      SimpleString s3 = SimpleString.of("a.b.*.d.*.f");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));

      underTest.put(s1, s1);
      underTest.put(s2, s2);

      assertEquals(1, countNonWildcardMatching(s3));

   }

   @Test
   public void testF() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.x.e.f");
      SimpleString s3 = SimpleString.of("#");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertTrue(a2.matches(w));

      underTest.put(s1, s1);
      underTest.put(s2, s2);

      assertEquals(2, countNonWildcardMatching(s3));

   }

   @Test
   public void testG() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.x.e.f");
      SimpleString s3 = SimpleString.of("a.#");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertTrue(a2.matches(w));

      underTest.put(s1, s1);
      underTest.put(s2, s2);

      assertEquals(2, countNonWildcardMatching(s3));

   }

   @Test
   public void testH() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.x.e.f");
      SimpleString s3 = SimpleString.of("#.b.#");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertTrue(a2.matches(w));

      underTest.put(s1, s1);
      underTest.put(s2, s2);

      assertEquals(2, countNonWildcardMatching(s3));
   }

   @Test
   public void testI() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.x.e.f");
      SimpleString s3 = SimpleString.of("a.#.b.#");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertTrue(a2.matches(w));

      underTest.put(s1, s1);
      underTest.put(s2, s2);

      assertEquals(2, countNonWildcardMatching(s3));

   }

   @Test
   public void testJ() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.x.e.f");
      SimpleString s3 = SimpleString.of("a.#.c.d.e.f");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));

      underTest.put(s1, s1);
      underTest.put(s2, s2);

      assertEquals(1, countNonWildcardMatching(s3));

   }

   @Test
   public void testK() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.d.e.x");
      SimpleString s3 = SimpleString.of("a.#.c.d.e.*");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertTrue(a2.matches(w));

      underTest.put(s1, s1);
      underTest.put(s2, s2);

      assertEquals(2, countNonWildcardMatching(s3));

   }

   @Test
   public void testL() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d.e.f");
      SimpleString s2 = SimpleString.of("a.b.c.d.e.x");
      SimpleString s3 = SimpleString.of("a.#.c.d.*.f");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));

      underTest.put(s1, s1);
      underTest.put(s2, s2);

      assertEquals(1, countNonWildcardMatching(s3));

   }

   @Test
   public void testM() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c");
      SimpleString s2 = SimpleString.of("a.b.x.e");
      SimpleString s3 = SimpleString.of("a.b.c.#");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));

      underTest.put(s1, s1);
      underTest.put(s2, s2);

      assertEquals(1, countNonWildcardMatching(s3));

   }

   @Test
   public void testN() throws Exception {
      SimpleString s1 = SimpleString.of("usd.stock");
      SimpleString s2 = SimpleString.of("a.b.x.e");
      SimpleString s3 = SimpleString.of("*.stock.#");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));

      underTest.put(s1, s1);
      underTest.put(s2, s2);

      assertEquals(1, countNonWildcardMatching(s3));
   }

   @Test
   public void testO() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s2 = SimpleString.of("a.b.x.e");
      SimpleString s3 = SimpleString.of("a.b.c.*");
      Address a1 = new AddressImpl(s1);
      Address a2 = new AddressImpl(s2);
      Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));
      assertFalse(a2.matches(w));

      underTest.put(s1, s1);
      underTest.put(s2, s2);

      assertEquals(1, countNonWildcardMatching(s3));

   }

   @Test
   public void testP() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s3 = SimpleString.of("a.b.c#");
      Address a1 = new AddressImpl(s1);
      Address w = new AddressImpl(s3);
      assertFalse(a1.matches(w));

      underTest.put(s1, s1);

      assertEquals(0, countNonWildcardMatching(s3));

   }

   @Test
   public void testQ() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s3 = SimpleString.of("#a.b.c");
      Address a1 = new AddressImpl(s1);
      Address w = new AddressImpl(s3);
      assertFalse(a1.matches(w));

      underTest.put(s1, s1);
      assertEquals(0, countNonWildcardMatching(s3));

   }

   @Test
   public void testR() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s3 = SimpleString.of("#*a.b.c");
      Address a1 = new AddressImpl(s1);
      Address w = new AddressImpl(s3);
      assertFalse(a1.matches(w));

      underTest.put(s1, s1);
      assertEquals(0, countNonWildcardMatching(s3));

   }

   @Test
   public void testS() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s3 = SimpleString.of("a.b.c*");
      Address a1 = new AddressImpl(s1);
      Address w = new AddressImpl(s3);
      assertFalse(a1.matches(w));

      underTest.put(s1, s1);
      assertEquals(0, countNonWildcardMatching(s3));

   }

   @Test
   public void testT() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s3 = SimpleString.of("*a.b.c");
      Address a1 = new AddressImpl(s1);
      Address w = new AddressImpl(s3);
      assertFalse(a1.matches(w));

      underTest.put(s1, s1);
      assertEquals(0, countNonWildcardMatching(s3));

   }

   @Test
   public void testU() throws Exception {
      SimpleString s1 = SimpleString.of("a.b.c.d");
      SimpleString s3 = SimpleString.of("*a.b.c");
      Address a1 = new AddressImpl(s1);
      Address w = new AddressImpl(s3);
      assertFalse(a1.matches(w));

      underTest.put(s1, s1);
      assertEquals(0, countNonWildcardMatching(s3));

   }

   @Test
   public void testV() throws Exception {
      final SimpleString s1 = SimpleString.of("a.b.d");
      final SimpleString s3 = SimpleString.of("a.b.#.d");
      final Address a1 = new AddressImpl(s1);
      final Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));

      underTest.put(s1, s1);
      assertEquals(1, countNonWildcardMatching(s3));

      final SimpleString s2 = SimpleString.of("a.b.b.b.b.d");
      underTest.put(s2, s2);
      assertEquals(2, countNonWildcardMatching(s3));
   }

   @Test
   public void testVReverse() throws Exception {
      final SimpleString s1 = SimpleString.of("a.b.d");
      final SimpleString s3 = SimpleString.of("a.b.#.d");
      final Address a1 = new AddressImpl(s1);
      final Address w = new AddressImpl(s3);
      assertTrue(a1.matches(w));

      underTest.put(s3, s3);
      assertEquals(1, countMatchingWildcards(s1));

   }

   @Test
   public void testHashNMatch() throws Exception {

      SimpleString addressABCF = SimpleString.of("a.b.c.f");
      SimpleString addressACF = SimpleString.of("a.c.f");
      SimpleString match = SimpleString.of("a.#.f");

      underTest.put(addressABCF, addressABCF);
      underTest.put(addressACF, addressACF);

      assertEquals(2, countNonWildcardMatching(match));
   }

   @Test
   public void testEndHash() throws Exception {

      SimpleString addressAB = SimpleString.of("a.b");
      SimpleString addressACF = SimpleString.of("a.c.f");
      SimpleString addressABC = SimpleString.of("a.b.c");
      SimpleString match = SimpleString.of("a.b.#");

      underTest.put(addressAB, addressAB);
      underTest.put(addressACF, addressACF);

      assertEquals(1, countNonWildcardMatching(match));

      underTest.put(addressABC, addressABC);
      assertEquals(2, countNonWildcardMatching(match));
   }

   @Test
   public void testHashEndInMap() throws Exception {

      SimpleString addressABHash = SimpleString.of("a.b.#");
      SimpleString addressABC = SimpleString.of("a.b.c");
      SimpleString match = SimpleString.of("a.b");

      underTest.put(addressABHash, addressABHash);
      underTest.put(addressABC, addressABC);

      assertEquals(1, countMatchingWildcards(match));
   }

   private int countMatchingWildcards(SimpleString plainAddress) throws Exception {

      final AtomicInteger count = new AtomicInteger();
      underTest.visitMatchingWildcards(plainAddress, value -> {
         count.incrementAndGet();
      });

      return count.get();
   }

   private int countNonWildcardMatching(SimpleString canBeWildcardAddress) throws Exception {

      final AtomicInteger count = new AtomicInteger();
      underTest.visitMatching(canBeWildcardAddress, value -> {
         count.incrementAndGet();
      });

      return count.get();
   }

   @Test
   public void testHashEndMatchMap() throws Exception {

      SimpleString match = SimpleString.of("a.b.#");
      SimpleString addressABC = SimpleString.of("a.b.c");
      SimpleString addressAB = SimpleString.of("a.b");

      underTest.put(addressAB, addressAB);
      underTest.put(addressABC, addressABC);

      assertEquals(0, countMatchingWildcards(match));
      assertEquals(2, countNonWildcardMatching(match));

      underTest.put(match, match);
      assertEquals(1, countMatchingWildcards(match));

   }

   @Test
   public void testHashAGet() throws Exception {

      SimpleString hashA = SimpleString.of("#.a");
      underTest.put(hashA, hashA);

      SimpleString matchA = SimpleString.of("a");
      SimpleString matchAB = SimpleString.of("a.b");

      assertEquals(1, countMatchingWildcards(matchA));
      assertEquals(0, countMatchingWildcards(matchAB));

      AddressImpl aStar = new AddressImpl(hashA);
      AddressImpl aA = new AddressImpl(matchA);
      assertTrue(aA.matches(aStar));

      AddressImpl aAB = new AddressImpl(matchAB);
      assertFalse(aAB.matches(aStar));
   }

   @Test
   public void testStarOne() throws Exception {

      SimpleString star = SimpleString.of("*");
      underTest.put(star, star);

      SimpleString matchA = SimpleString.of("a");
      SimpleString matchAB = SimpleString.of("a.b");

      final AtomicInteger count = new AtomicInteger();
      underTest.visitMatchingWildcards(matchA, value -> count.incrementAndGet());
      assertEquals(1, count.get());

      count.set(0);

      underTest.visitMatchingWildcards(matchAB, value -> count.incrementAndGet());

      assertEquals(0, count.get());
   }

   @Test
   public void testHashOne() throws Exception {

      SimpleString hash = SimpleString.of("#");
      underTest.put(hash, hash);

      SimpleString matchA = SimpleString.of("a");
      SimpleString matchAB = SimpleString.of("a.b");
      SimpleString matchABC = SimpleString.of("a.b.c");

      final AtomicInteger count = new AtomicInteger();
      AddressMapVisitor<SimpleString> countCollector = value -> count.incrementAndGet();

      count.set(0);
      underTest.visitMatchingWildcards(matchA, countCollector);
      assertEquals(1, count.get());

      count.set(0);
      underTest.visitMatchingWildcards(matchAB, countCollector);
      assertEquals(1, count.get());

      count.set(0);
      underTest.visitMatchingWildcards(matchABC, countCollector);
      assertEquals(1, count.get());
   }

   @Test
   public void testHashAMatch() throws Exception {

      SimpleString a = SimpleString.of("a");
      underTest.put(a, a);

      assertEquals(1, countNonWildcardMatching(SimpleString.of("#.a")));

      assertEquals(1, countMatchingWildcards(SimpleString.of("a")));
   }

   @Test
   public void testHashA() throws Exception {

      SimpleString hashA = SimpleString.of("#.a");
      underTest.put(hashA, hashA);

      assertEquals(1, countMatchingWildcards(SimpleString.of("a")));

      assertEquals(1, countMatchingWildcards(SimpleString.of("d.f.c.a")));

      // has to end in 'a', and not being with 'a'
      SimpleString abcaS = SimpleString.of("a.b.c.a");
      AddressImpl aHashA = new AddressImpl(hashA);
      AddressImpl aABCA = new AddressImpl(abcaS);
      assertFalse(aABCA.matches(aHashA));
      assertFalse(aHashA.matches(aABCA));

      assertEquals(1, countMatchingWildcards(abcaS));

      assertEquals(0, countMatchingWildcards(SimpleString.of("a.b")));

      assertEquals(0, countMatchingWildcards(SimpleString.of("a.b.c")));

      assertEquals(0, countMatchingWildcards(SimpleString.of("a.b.c.a.d")));

      // will match a.....a
      SimpleString AHashA = SimpleString.of("a.#.a");
      underTest.put(AHashA, AHashA);

      assertEquals(2, countMatchingWildcards(SimpleString.of("a.b.c.a")));

      assertEquals(0, countNonWildcardMatching(SimpleString.of("a.b.c.a")));

      // only now remove the #.a
      underTest.remove(hashA, hashA);

      assertEquals(1, countMatchingWildcards(SimpleString.of("a.b.c.a")));

      assertEquals(1, countMatchingWildcards(SimpleString.of("a.a")));

   }

   @Test
   public void testAHashA() throws Exception {

      final AtomicInteger count = new AtomicInteger();
      AddressMapVisitor<SimpleString> countCollector = value -> count.incrementAndGet();

      // will match a.....a
      SimpleString AHashA = SimpleString.of("a.#.a");
      underTest.put(AHashA, AHashA);

      count.set(0);
      underTest.visitMatchingWildcards(SimpleString.of("a.b.c.a"), countCollector);
      assertEquals(1, count.get());

      count.set(0);
      underTest.visitMatchingWildcards(SimpleString.of("a.a"), countCollector);
      assertEquals(1, count.get());

      count.set(0);
      underTest.visitMatchingWildcards(SimpleString.of("a"), countCollector);
      assertEquals(0, count.get());
   }

   @Test
   public void testStar() throws Exception {

      SimpleString star = SimpleString.of("*");
      SimpleString addressA = SimpleString.of("a");
      SimpleString addressAB = SimpleString.of("a.b");

      underTest.put(star, star);
      underTest.put(addressAB, addressAB);

      final AtomicInteger count = new AtomicInteger();
      underTest.visitMatchingWildcards(addressA, value -> count.incrementAndGet());

      assertEquals(1, count.get());
   }

   @Test
   public void testSomeAndAny() throws Exception {

      SimpleString star = SimpleString.of("test.*.some.#");
      underTest.put(star, star);

      assertEquals(0, countNonWildcardMatching(star));
      assertEquals(1, countMatchingWildcards(star));

      SimpleString addressA = SimpleString.of("test.1.some.la");
      underTest.put(addressA, addressA);

      assertEquals(1, countMatchingWildcards(star));
      assertEquals(1, countNonWildcardMatching(star));

      assertEquals(2, countMatchingWildcards(addressA));
      assertEquals(1, countNonWildcardMatching(addressA));

   }

   @Test
   public void testAnyAndSome() throws Exception {

      SimpleString star = SimpleString.of("test.#.some.*");
      underTest.put(star, star);

      assertEquals(1, countMatchingWildcards(star));

      // add another match
      SimpleString addressA = SimpleString.of("test.1.some.la");
      underTest.put(addressA, addressA);

      assertEquals(1, countMatchingWildcards(star));

      assertEquals(1, countNonWildcardMatching(star));

      assertEquals(1, countNonWildcardMatching(addressA));

      assertEquals(2, countMatchingWildcards(addressA));

   }

   @Test
   public void testAnyAndSomeInMap() throws Exception {

      SimpleString hashHash = SimpleString.of("test.#.some.#");
      underTest.put(hashHash, hashHash);

      SimpleString starStar = SimpleString.of("test.*.some.*");
      underTest.put(starStar, starStar);

      SimpleString hashStar = SimpleString.of("test.#.A.*");
      underTest.put(hashStar, hashStar);

      SimpleString oneHashStar = SimpleString.of("test.1.#.T");
      underTest.put(oneHashStar, oneHashStar);

      assertEquals(2, countMatchingWildcards(hashHash));
      assertEquals(0, countNonWildcardMatching(hashHash));

      SimpleString reqular = SimpleString.of("test.a.b.some");
      underTest.put(reqular, reqular);
      assertEquals(1, countNonWildcardMatching(hashHash));

      assertEquals(1, countNonWildcardMatching(reqular));

      assertEquals(2, countMatchingWildcards(reqular));
   }

   @Test
   public void testHashAandHashB() throws Exception {

      SimpleString hashAhash = SimpleString.of("test.#.aaa.#");
      underTest.put(hashAhash, hashAhash);

      SimpleString hashBhash = SimpleString.of("test.#.bbb.#");
      underTest.put(hashBhash, hashBhash);

      assertEquals(2, countMatchingWildcards(SimpleString.of("test.aaa.bbb")));
      assertEquals(2, countMatchingWildcards(SimpleString.of("test.bbb.aaa")));

      assertEquals(2, countMatchingWildcards(SimpleString.of("test.bbb.aaa.ccc")));
      assertEquals(2, countMatchingWildcards(SimpleString.of("test.aaa.bbb.ccc")));
   }

   @Test
   public void testHashNoHashNo() throws Exception {

      SimpleString hashAhash = SimpleString.of("test.#.0.#.168");
      underTest.put(hashAhash, hashAhash);

      assertEquals(1, countMatchingWildcards(SimpleString.of("test.0.168")));
   }

   @Test
   public void testHashNoHashHashNo() throws Exception {

      SimpleString v = SimpleString.of("test.#.0.#.168");
      underTest.put(v, v);

      v = SimpleString.of("test.#.0.#");
      underTest.put(v, v);

      v = SimpleString.of("test.0.#");
      underTest.put(v, v);

      assertEquals(3, countMatchingWildcards(SimpleString.of("test.0.168")));
   }

   @Test
   public void testHashNoHashNoWithNMatch() throws Exception {

      for (String s : new String[] {"t.#.0.#", "t.#.1.#", "t.#.2.#", "t.#.3.#", "t.#.1.2.3", "t.0.1.2.3"}) {
         SimpleString v = SimpleString.of(s);
         underTest.put(v, v);
      }
      assertEquals(6, countMatchingWildcards(SimpleString.of("t.0.1.2.3")));
   }

   @Test
   public void testSomeMoreHashPlacement() throws Exception {

      for (String s : new String[] {"t.#.0.#", "t.0.1.#", "t.0.1.2.#", "t.0.1.#.2.3", "t.*.#.1.2.3"}) {
         SimpleString v = SimpleString.of(s);
         underTest.put(v, v);
      }
      assertEquals(5, countMatchingWildcards(SimpleString.of("t.0.1.2.3")));
      assertEquals(3, countMatchingWildcards(SimpleString.of("t.0.1.2.3.4")));
   }

   @Test
   public void testManyEntries() throws Exception {

      for (int i = 0; i < 10; i++) {
         SimpleString star = SimpleString.of("test." + i);
         underTest.put(star, star);
      }

      assertEquals(10, countNonWildcardMatching(SimpleString.of("test.*")));

      assertEquals(10, countNonWildcardMatching(SimpleString.of("test.#")));

      assertEquals(1, countMatchingWildcards(SimpleString.of("test.0")));

      underTest.put(SimpleString.of("test.#"), SimpleString.of("test.#"));
      underTest.put(SimpleString.of("test.*"), SimpleString.of("test.*"));

      assertEquals(3, countMatchingWildcards(SimpleString.of("test.1")));

      assertEquals(10, countNonWildcardMatching(SimpleString.of("test.#")));

      assertEquals(10, countNonWildcardMatching(SimpleString.of("test.*")));

      for (int i = 0; i < 10; i++) {
         SimpleString star = SimpleString.of("test.a." + i);
         underTest.put(star, star);
      }

      assertEquals(2, countMatchingWildcards(SimpleString.of("test.a.0")));
      assertEquals(20, countNonWildcardMatching(SimpleString.of("test.#")));

      for (int i = 0; i < 10; i++) {
         SimpleString star = SimpleString.of("test.b." + i);
         underTest.put(star, star);
      }

      assertEquals(10, countNonWildcardMatching(SimpleString.of("test.b.*")));
      underTest.remove(SimpleString.of("test.#"), SimpleString.of("test.#"));

      assertEquals(10, countNonWildcardMatching(SimpleString.of("test.b.*")));
      assertEquals(1, countMatchingWildcards(SimpleString.of("test.a.0")));

      for (int i = 0; i < 10; i++) {
         SimpleString star = SimpleString.of("test.c." + i);
         underTest.put(star, star);
      }
      assertEquals(10, countNonWildcardMatching(SimpleString.of("test.c.*")));

      SimpleString testStarStar = SimpleString.of("test.*.*");
      assertEquals(30, countNonWildcardMatching(testStarStar));

      underTest.put(testStarStar, testStarStar);
      assertEquals(30, countNonWildcardMatching(testStarStar));
      assertEquals(1, countMatchingWildcards(testStarStar));

      assertEquals(1, countMatchingWildcards(SimpleString.of("test.b.c")));
   }

   @Test
   public void testReset() throws Exception {
      for (int i = 0; i < 10; i++) {
         SimpleString star = SimpleString.of("test." + i);
         underTest.put(star, star);
      }

      assertEquals(0, countMatchingWildcards(SimpleString.of("test.*")));

      assertEquals(10, countNonWildcardMatching(SimpleString.of("test.*")));
      underTest.reset();
      assertEquals(0, countNonWildcardMatching(SimpleString.of("test.*")));
   }

   @Test
   public void testRemove() throws Exception {
      for (int i = 0; i < 10; i++) {
         SimpleString star = SimpleString.of("test." + i);
         underTest.put(star, star);
      }

      SimpleString test1 = SimpleString.of("test.1");
      assertEquals(1, countMatchingWildcards(test1));

      underTest.remove(test1, test1);
      assertEquals(0, countMatchingWildcards(test1));

      assertEquals(9, countNonWildcardMatching(SimpleString.of("test.*")));

      for (int i = 0; i < 10; i++) {
         SimpleString star = SimpleString.of("test." + i);
         underTest.remove(star, star);
      }

      assertEquals(0, countNonWildcardMatching(SimpleString.of("test.*")));
   }

   @Test
   public void testMax() throws Exception {

      underTest.put(SimpleString.of("test.#.a"), SimpleString.of("test.#.a"));
      underTest.put(SimpleString.of("test.*.a"), SimpleString.of("test.*.a"));
      underTest.put(SimpleString.of("*.a"), SimpleString.of("*.a"));
      underTest.put(SimpleString.of("#.a"), SimpleString.of("#.a"));

      assertEquals(3, countMatchingWildcards(SimpleString.of("test.a")));
      assertEquals(3, countMatchingWildcards(SimpleString.of("test.a.a")));
   }

}