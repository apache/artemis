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
package org.apache.activemq.artemis.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RepositoryTest extends ServerTestBase {

   HierarchicalRepository<Set<Role>> securityRepository;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      securityRepository = new HierarchicalObjectRepository<>();
   }

   @Test
   public void testDefault() {
      securityRepository.setDefault(new HashSet<>());
      Set<Role> roles = securityRepository.getMatch("queues.something");

      assertEquals(0, roles.size());
   }

   @Test
   public void testMatchingDocs() throws Throwable {
      HierarchicalObjectRepository<String> repo = new HierarchicalObjectRepository<>();

      repo.addMatch("a.b.#", "ab#");
      repo.addMatch("a.b.d.#", "abd#");
      repo.addMatch("#", "root");

      assertEquals("ab#", repo.getMatch("a.b"));
      assertEquals("ab#", repo.getMatch("a.b.c"));
      assertEquals("abd#", repo.getMatch("a.b.d.lll"));
      assertEquals("root", repo.getMatch("z.z.z.z.z"));
      assertEquals("root", repo.getMatch("a.babc"));
      assertEquals("ab#", repo.getMatch("a.b.dabc"));
      assertEquals("abd#", repo.getMatch("a.b.d"));
   }

   /**
    * A "literal" match is one which uses wild-cards but should not be applied to other matches "below" it in the
    * hierarchy.
    */
   @Test
   public void testLiteral() {
      HierarchicalObjectRepository<DummyMergeable> repo = new HierarchicalObjectRepository<>(null, new HierarchicalObjectRepository.MatchModifier() { }, "()");

      repo.addMatch("#", new DummyMergeable(0));
      repo.addMatch("(a.#)", new DummyMergeable(1));
      repo.addMatch("a.#", new DummyMergeable(2));
      repo.addMatch("a.b", new DummyMergeable(3));
      repo.addMatch("a.*", new DummyMergeable(4));

      DummyMergeable abDummyMatch = repo.getMatch("a.b");
      assertEquals(3, abDummyMatch.getMergedItems().size());
      assertEquals(3, abDummyMatch.getId());
      assertEquals(4, abDummyMatch.getMergedItems().get(0).getId());
      assertEquals(2, abDummyMatch.getMergedItems().get(1).getId());
      assertEquals(0, abDummyMatch.getMergedItems().get(2).getId());

      DummyMergeable aDummyMatch = repo.getMatch("a.#");
      assertEquals(3, aDummyMatch.getMergedItems().size());
      assertEquals(1, aDummyMatch.getId());
      assertEquals(4, aDummyMatch.getMergedItems().get(0).getId());
      assertEquals(2, aDummyMatch.getMergedItems().get(1).getId());
      assertEquals(0, aDummyMatch.getMergedItems().get(2).getId());
   }

   @Test
   public void testCacheWithWildcards() throws Throwable {
      HierarchicalObjectRepository<String> repo = new HierarchicalObjectRepository<>();

      repo.addMatch("#", "root");
      assertEquals("root", repo.getMatch("b"));

      repo.addMatch("b", "leaf");
      assertEquals("leaf", repo.getMatch("b"));
   }

   @Test
   public void testMultipleMatchesHasRightOrder() {
      HierarchicalRepository<String> repository = new HierarchicalObjectRepository<>();
      repository.addMatch("a.b.c.d.e.f", "a.b.c.d.e.f");//1
      repository.addMatch("a.b.c.d.e.*", "a.b.c.d.e.*");//2
      repository.addMatch("a.*.*.*.*.*", "a.*.*.*.*.*");//3
      repository.addMatch("*.b.c.d.*.f", "*.b.c.d.*.f");//4
      repository.addMatch("*.b.*.d.*.f", "*.b.*.d.*.f");//5
      repository.addMatch("a.b.c.d.e.#", "a.b.c.d.e.#");//6

      String val = repository.getMatch("a.b.c.d.e.f");//matches all
      assertEquals("a.b.c.d.e.f", val);
      val = repository.getMatch("a.b.c.d.e.x");//matches 2,3,6
      assertEquals("a.b.c.d.e.*", val);
      val = repository.getMatch("a.b.x.d.x.f");//matches 3,5
      assertEquals("a.*.*.*.*.*", val);
      val = repository.getMatch("x.b.c.d.e.f");//matches 4,5
      assertEquals("*.b.c.d.*.f", val);
      val = repository.getMatch("x.b.x.d.e.f");//matches 5
      assertEquals("*.b.*.d.*.f", val);
      val = repository.getMatch("a.b.c.d.e.f.g");//matches 6
      assertEquals("a.b.c.d.e.#", val);
   }

   @Test
   public void testMatchingDocsCustomUnderscorDelimiter() throws Throwable {
      WildcardConfiguration wildcardConfiguration = new WildcardConfiguration();
      wildcardConfiguration.setDelimiter('_');
      HierarchicalObjectRepository<String> repo = new HierarchicalObjectRepository<>(wildcardConfiguration);

      repo.addMatch("a_b_#", "ab#");
      repo.addMatch("a_b_d_#", "abd#");
      repo.addMatch("#", "root");

      assertEquals("ab#", repo.getMatch("a_b"));
      assertEquals("ab#", repo.getMatch("a_b_c"));
      assertEquals("abd#", repo.getMatch("a_b_d_lll"));
      assertEquals("root", repo.getMatch("z_z_z_z_z"));
      assertEquals("root", repo.getMatch("a_babc"));
      assertEquals("ab#", repo.getMatch("a_b_dabc"));
      assertEquals("abd#", repo.getMatch("a_b_d"));
   }

   @Test
   public void testMatchingDocsCustomForwardSlashDelimiter() throws Throwable {
      WildcardConfiguration wildcardConfiguration = new WildcardConfiguration();
      wildcardConfiguration.setDelimiter('/');
      HierarchicalObjectRepository<String> repo = new HierarchicalObjectRepository<>(wildcardConfiguration);

      repo.addMatch("a/b/#", "ab#");
      repo.addMatch("a/b/d/#", "abd#");
      repo.addMatch("#", "root");

      assertEquals("ab#", repo.getMatch("a/b"));
      assertEquals("ab#", repo.getMatch("a/b/c"));
      assertEquals("abd#", repo.getMatch("a/b/d/lll"));
      assertEquals("root", repo.getMatch("z/z/z/z/z"));
      assertEquals("root", repo.getMatch("a/babc"));
      assertEquals("ab#", repo.getMatch("a/b/dabc"));
      assertEquals("abd#", repo.getMatch("a/b/d"));
   }

   @Test
   public void testSingleMatch() {
      securityRepository.addMatch("queues.*", new HashSet<>());
      Set<Role> hashSet = securityRepository.getMatch("queues.something");
      assertEquals(0, hashSet.size());
   }

   @Test
   public void testSingletwo() {
      securityRepository.addMatch("queues.another.aq.*", new HashSet<>());
      Set<Role> roles = new HashSet<>(2);
      roles.add(new Role("test1", true, true, true, true, true, true, true, true, true, true, false, false));
      roles.add(new Role("test2", true, true, true, true, true, true, true, true, true, true, false, false));
      securityRepository.addMatch("queues.aq", roles);
      Set<Role> roles2 = new HashSet<>(2);
      roles2.add(new Role("test1", true, true, true, true, true, true, true, true, true, true, false, false));
      roles2.add(new Role("test2", true, true, true, true, true, true, true, true, true, true, false, false));
      roles2.add(new Role("test3", true, true, true, true, true, true, true, true, true, true, false, false));
      securityRepository.addMatch("queues.another.andanother", roles2);

      Set<Role> hashSet = securityRepository.getMatch("queues.another.andanother");
      assertEquals(3, hashSet.size());
   }

   @Test
   public void testWithoutWildcard() {
      securityRepository.addMatch("queues.1.*", new HashSet<>());
      Set<Role> roles = new HashSet<>(2);
      roles.add(new Role("test1", true, true, true, true, true, true, true, true, true, true, false, false));
      roles.add(new Role("test2", true, true, true, true, true, true, true, true, true, true, false, false));
      securityRepository.addMatch("queues.2.aq", roles);
      Set<Role> hashSet = securityRepository.getMatch("queues.2.aq");
      assertEquals(2, hashSet.size());
   }

   @Test
   public void testMultipleWildcards() {
      HierarchicalRepository<String> repository = new HierarchicalObjectRepository<>();
      repository.addMatch("#", "#");
      repository.addMatch("a", "a");
      repository.addMatch("a.#", "a.#");
      repository.addMatch("a.*", "a.*");
      repository.addMatch("a.b.c", "a.b.c");
      repository.addMatch("a.*.c", "a.*.c");
      repository.addMatch("a.d.c", "a.d.c");
      repository.addMatch("a.b.#", "a.b.#");
      repository.addMatch("a.b", "a.b");
      repository.addMatch("a.b.c.#", "a.b.c.#");
      repository.addMatch("a.b.c.d", "a.b.c.d");
      repository.addMatch("a.*.*.d", "a.*.*.d");
      repository.addMatch("a.*.d.#", "a.*.d.#");
      String val = repository.getMatch("a");
      assertEquals("a", val);
      val = repository.getMatch("a.b");
      assertEquals("a.b", val);
      val = repository.getMatch("a.x");
      assertEquals("a.*", val);
      val = repository.getMatch("a.b.x");
      assertEquals("a.b.#", val);
      val = repository.getMatch("a.b.c");
      assertEquals("a.b.c", val);
      val = repository.getMatch("a.d.c");
      assertEquals("a.d.c", val);
      val = repository.getMatch("a.x.c");
      assertEquals("a.*.c", val);
      val = repository.getMatch("a.b.c.d");
      assertEquals("a.b.c.d", val);
      val = repository.getMatch("a.x.c.d");
      assertEquals("a.*.*.d", val);
      val = repository.getMatch("a.b.x.d");
      assertEquals("a.*.*.d", val);
      val = repository.getMatch("a.d.x.d");
      assertEquals("a.*.*.d", val);
      val = repository.getMatch("a.d.d.g");
      assertEquals("a.*.d.#", val);
      val = repository.getMatch("zzzz.z.z.z.d.r.g.f.sd.s.fsdfd.fsdfs");
      assertEquals("#", val);
   }

   @Test
   public void testRepositoryMerge() {
      HierarchicalRepository<DummyMergeable> repository = new HierarchicalObjectRepository<>();
      repository.addMatch("#", new DummyMergeable(1));
      repository.addMatch("a.#", new DummyMergeable(2));
      repository.addMatch("b.#", new DummyMergeable(3));
      repository.addMatch("a.b.#", new DummyMergeable(4));
      repository.addMatch("b.c.#", new DummyMergeable(5));
      repository.addMatch("a.b.c.#", new DummyMergeable(6));
      repository.addMatch("a.b.*.d", new DummyMergeable(7));
      repository.addMatch("a.b.c.*", new DummyMergeable(8));
      DummyMergeable abcdDummyMatch = repository.getMatch("a.b.c.d");
      assertEquals(5, abcdDummyMatch.getMergedItems().size());
      assertEquals(8, abcdDummyMatch.getId());
      assertEquals(7, abcdDummyMatch.getMergedItems().get(0).getId());
      assertEquals(6, abcdDummyMatch.getMergedItems().get(1).getId());
      assertEquals(4, abcdDummyMatch.getMergedItems().get(2).getId());
      assertEquals(2, abcdDummyMatch.getMergedItems().get(3).getId());
      assertEquals(1, abcdDummyMatch.getMergedItems().get(4).getId());
      DummyMergeable abcDummyMatch = repository.getMatch("a.b.c");
      assertEquals(3, abcDummyMatch.getMergedItems().size());
      assertEquals(6, abcDummyMatch.getId());
      assertEquals(4, abcDummyMatch.getMergedItems().get(0).getId());
      assertEquals(2, abcDummyMatch.getMergedItems().get(1).getId());
      assertEquals(1, abcDummyMatch.getMergedItems().get(2).getId());
      DummyMergeable zDummyMatch = repository.getMatch("z");
      assertEquals(0, zDummyMatch.getMergedItems().size());
      assertEquals(1, zDummyMatch.getId());
   }

   @Test
   public void testAddListener() {
      HierarchicalRepository<String> repository = new HierarchicalObjectRepository<>();
      repository.addMatch("#", "1");
      repository.addMatch("B", "2");

      final AtomicInteger called = new AtomicInteger(0);
      repository.registerListener(() -> called.incrementAndGet());

      assertEquals(1, called.get());

      repository.disableListeners();

      repository.addMatch("C", "3");

      assertEquals(1, called.get());

      repository.enableListeners();

      assertEquals(2, called.get());

      repository.addMatch("D", "4");

      assertEquals(3, called.get());

      repository.removeMatch("D");

      assertEquals(4, called.get());

      repository.disableListeners();

      repository.removeMatch("C");

      assertEquals(4, called.get());
   }

   @Test
   public void testIllegalMatches() {
      HierarchicalRepository<String> repository = new HierarchicalObjectRepository<>();
      try {
         repository.addMatch("hjhjhjhjh.#.hhh", "test");
         fail("expected exception");
      } catch (IllegalArgumentException e) {
         // pass
      }
      try {
         repository.addMatch(null, "test");
         fail("expected exception");
      } catch (IllegalArgumentException e) {
         // pass
      }
   }

   @Test
   public void testMatchMergeIdempotence() {
      HierarchicalRepository<DummyMergeable> repository = new HierarchicalObjectRepository<>();
      repository.addMatch("foo.*", new DummyMergeable(0, Map.of("s0", "x")));
      repository.addMatch("foo.0.#", new DummyMergeable(1, Map.of("so", "a", "s1", "a")));
      repository.addMatch("foo.1.#", new DummyMergeable(2, Map.of("so", "b", "s1", "b")));

      DummyMergeable fooxMatch = repository.getMatch("foo.x");
      assertEquals(0, fooxMatch.getId());
      assertEquals(0, fooxMatch.getMergedItems().size());
      assertEquals("x", fooxMatch.getSettings().get("s0"));
      assertNull(fooxMatch.getSettings().get("s1"));

      DummyMergeable foo1Match = repository.getMatch("foo.0");
      assertEquals(0, foo1Match.getId());
      assertEquals(1, foo1Match.getMergedItems().size());
      assertEquals("x", fooxMatch.getSettings().get("s0"));
      assertEquals("a", foo1Match.getSettings().get("s1"));

      DummyMergeable foo2Match = repository.getMatch("foo.1");
      assertEquals(0, foo2Match.getId());
      assertEquals(1, foo2Match.getMergedItems().size());
      assertEquals("x", fooxMatch.getSettings().get("s0"));
      assertEquals("b", foo2Match.getSettings().get("s1"));

      DummyMergeable fooxBisMatch = repository.getMatch("foo.x");
      assertEquals(0, fooxBisMatch.getId());
      assertEquals(0, fooxBisMatch.getMergedItems().size());
      assertEquals("x", fooxBisMatch.getSettings().get("s0"));
      assertNull(fooxBisMatch.getSettings().get("s1"));

      DummyMergeable foo1BisMatch = repository.getMatch("foo.0");
      assertEquals(0, foo1BisMatch.getId());
      assertEquals(1, foo1BisMatch.getMergedItems().size());
      assertEquals("x", foo1BisMatch.getSettings().get("s0"));
      assertEquals("a", foo1BisMatch.getSettings().get("s1"));

      DummyMergeable foo2BisMatch = repository.getMatch("foo.1");
      assertEquals(0, foo2BisMatch.getId());
      assertEquals(1, foo2BisMatch.getMergedItems().size());
      assertEquals("x", foo2BisMatch.getSettings().get("s0"));
      assertEquals("b", foo2BisMatch.getSettings().get("s1"));
   }

   @Test
   public void testAddressSettingsMergeIdempotence() {
      AddressSettings foox = new AddressSettings().setMaxRedeliveryDelay(10000);
      AddressSettings foo0 = new AddressSettings().setMaxRedeliveryDelay(20000).setMaxExpiryDelay(20000L);
      AddressSettings foo1 = new AddressSettings().setMaxRedeliveryDelay(30000).setMaxExpiryDelay(30000L);

      HierarchicalRepository<AddressSettings> repository = new HierarchicalObjectRepository<>();
      repository.addMatch("foo.*", foox);
      repository.addMatch("foo.0.#", foo0);
      repository.addMatch("foo.1.#", foo1);

      AddressSettings fooxMatch = repository.getMatch("foo.x");
      assertEquals(10000, fooxMatch.getMaxRedeliveryDelay());
      assertEquals(Long.valueOf(AddressSettings.DEFAULT_MAX_EXPIRY_DELAY), fooxMatch.getMaxExpiryDelay());

      AddressSettings foo0Match = repository.getMatch("foo.0");
      assertEquals(10000, foo0Match.getMaxRedeliveryDelay());
      assertEquals(Long.valueOf(20000), foo0Match.getMaxExpiryDelay());

      AddressSettings foo1Match = repository.getMatch("foo.1");
      assertEquals(10000, foo1Match.getMaxRedeliveryDelay());
      assertEquals(Long.valueOf(30000), foo1Match.getMaxExpiryDelay());

      AddressSettings fooxBisMatch = repository.getMatch("foo.x");
      assertEquals(10000, fooxBisMatch.getMaxRedeliveryDelay());
      assertEquals(Long.valueOf(AddressSettings.DEFAULT_MAX_EXPIRY_DELAY), fooxBisMatch.getMaxExpiryDelay());

      AddressSettings foo0BisMatch = repository.getMatch("foo.0");
      assertEquals(10000, foo0BisMatch.getMaxRedeliveryDelay());
      assertEquals(Long.valueOf(20000), foo0BisMatch.getMaxExpiryDelay());

      AddressSettings foo1BisMatch = repository.getMatch("foo.1");
      assertEquals(10000, foo1BisMatch.getMaxRedeliveryDelay());
      assertEquals(Long.valueOf(30000), foo1BisMatch.getMaxExpiryDelay());
   }

   static class DummyMergeable implements Mergeable<DummyMergeable> {
      private final int id;
      private final Map<String, String> settings;
      private final List<DummyMergeable> mergedItems;

      public int getId() {
         return id;
      }

      public Map<String, String> getSettings() {
         return settings;
      }

      public List<DummyMergeable> getMergedItems() {
         return mergedItems;
      }

      DummyMergeable(final int id) {
         this.id = id;
         this.settings = new HashMap<>();
         this.mergedItems = new ArrayList<>();
      }

      DummyMergeable(final int id, final Map<String, String> settings) {
         this.id = id;
         this.settings = settings;
         this.mergedItems = new ArrayList<>();
      }

      DummyMergeable(DummyMergeable item) {
         this.id = item.id;
         this.settings = new HashMap<>(item.settings);
         this.mergedItems = new ArrayList<>(item.mergedItems);
      }

      @Override
      public void merge(final DummyMergeable merged) {
         for (Map.Entry<String, String> entry : merged.settings.entrySet()) {
            this.settings.putIfAbsent(entry.getKey(), entry.getValue());
         }
         this.mergedItems.add(merged);
      }

      @Override
      public DummyMergeable mergeCopy(DummyMergeable merged) {
         DummyMergeable target = new DummyMergeable(this);
         for (Map.Entry<String, String> entry : merged.settings.entrySet()) {
            target.settings.putIfAbsent(entry.getKey(), entry.getValue());
         }
         target.mergedItems.add(merged);
         return target;
      }
   }
}
