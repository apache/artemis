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
package org.apache.activemq.artemis.core.server.metrics;

import java.util.List;
import java.util.function.Function;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.netty.buffer.PoolArenaMetric;
import io.netty.buffer.PoolChunkListMetric;
import io.netty.buffer.PoolChunkMetric;
import io.netty.buffer.PoolSubpageMetric;
import io.netty.buffer.PooledByteBufAllocatorMetric;

public final class NettyPooledAllocatorMetrics implements MeterBinder {

   private static final String BYTES_UNIT = "bytes";
   private final PooledByteBufAllocatorMetric metric;
   private final Tags commonTags;

   public NettyPooledAllocatorMetrics(final PooledByteBufAllocatorMetric pooledAllocatorMetric, final Tags commonTags) {
      this.metric = pooledAllocatorMetric;
      this.commonTags = commonTags;
   }

   @Override
   public void bindTo(final MeterRegistry registry) {

      Gauge.builder("netty.pooled.used.memory", this.metric, metric -> metric.usedDirectMemory())
         .tags(commonTags)
         .tags("type", "direct")
         .description("The used memory")
         .baseUnit(BYTES_UNIT).register(registry);

      Gauge.builder("netty.pooled.used.memory", this.metric, metric -> metric.usedHeapMemory())
         .tags(commonTags)
         .tags("type", "heap")
         .description("The used memory")
         .baseUnit(BYTES_UNIT).register(registry);

      Gauge.builder("netty.pooled.arenas.num", this.metric, metric -> metric.numDirectArenas())
         .tags(commonTags)
         .tags("type", "direct")
         .description("The number of arenas")
         .register(registry);

      Gauge.builder("netty.pooled.arenas.num", this.metric, metric -> metric.numHeapArenas())
         .tags(commonTags)
         .tags("type", "heap").description("The number or arenas")
         .register(registry);

      Gauge.builder("netty.pooled.cachesize", this.metric, metric -> metric.tinyCacheSize())
         .tags(commonTags)
         .tags("type", "tiny")
         .description("The cachesize used by this netty allocator")
         .register(registry);

      Gauge.builder("netty.pooled.cachesize", this.metric, metric -> metric.smallCacheSize())
         .tags(commonTags)
         .tags("type", "small")
         .description("The cachesize used by this netty allocator")
         .register(registry);

      Gauge.builder("netty.pooled.cachesize", this.metric, metric -> metric.normalCacheSize())
         .tags(commonTags)
         .tags("type", "normal")
         .description("The cachesize used by this netty allocator")
         .register(registry);

      Gauge.builder("netty.pooled.threadlocalcache.num", this.metric, metric -> metric.numThreadLocalCaches())
         .tags(commonTags)
         .description("The number of thread local caches used by this netty allocator")
         .register(registry);

      Gauge.builder("netty.pooled.chunk.size", this.metric, metric -> metric.chunkSize())
         .tags(commonTags)
         .description("The arena chunk size of this netty allocator")
         .baseUnit(BYTES_UNIT)
         .register(registry);

      {
         int i = 0;
         for (final PoolArenaMetric poolArenaMetric : metric.directArenas()) {
            metricsOfPoolArena(registry, poolArenaMetric, i++, "direct");
         }
      }
      {
         int i = 0;
         for (final PoolArenaMetric poolArenaMetric : metric.heapArenas()) {
            metricsOfPoolArena(registry, poolArenaMetric, i++, "heap");
         }
      }
   }

   private void metricsOfPoolArena(final MeterRegistry registry,
                                   final PoolArenaMetric poolArenaMetric,
                                   final int poolArenaIndex,
                                   final String poolArenaType) {
      // the number of thread caches backed by this arena.
      final String poolArenaIndexString = Integer.toString(poolArenaIndex);
      Gauge.builder("netty.pooled.arena.threadcaches.num", poolArenaMetric, metric -> metric.numThreadCaches())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString)
         .description("The number of thread caches backed by this arena")
         .register(registry);

      FunctionCounter.builder("netty.pooled.arena.allocations.num", poolArenaMetric,
         metric -> metric.numAllocations())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "all")
         .description("The number of allocations done via the arena. This includes all sizes")
         .register(registry);

      FunctionCounter.builder("netty.pooled.arena.allocations.num", poolArenaMetric,
         metric -> metric.numTinyAllocations())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "tiny")
         .description("The number of tiny allocations done via the arena")
         .register(registry);

      FunctionCounter.builder("netty.pooled.arena.allocations.num", poolArenaMetric,
         metric -> metric.numSmallAllocations())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "small")
         .description("The number of small allocations done via the arena")
         .register(registry);

      FunctionCounter.builder("netty.pooled.arena.allocations.num", poolArenaMetric,
         metric -> metric.numNormalAllocations())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "normal")
         .description("The number of normal allocations done via the arena")
         .register(registry);

      FunctionCounter.builder("netty.pooled.arena.allocations.num", poolArenaMetric,
         metric -> metric.numHugeAllocations())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "huge")
         .description("The number of huge allocations done via the arena")
         .register(registry);

      FunctionCounter.builder("netty.pooled.arena.deallocations.num", poolArenaMetric,
         metric -> metric.numDeallocations())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "all")
         .description("The number of deallocations done via the arena. This includes all sizes")
         .register(registry);

      FunctionCounter.builder("netty.pooled.arena.deallocations.num", poolArenaMetric,
         metric -> metric.numTinyDeallocations())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "tiny")
         .description("The number of tiny deallocations done via the arena")
         .register(registry);

      FunctionCounter.builder("netty.pooled.arena.deallocations.num", poolArenaMetric,
         metric -> metric.numSmallDeallocations())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "small")
         .description("The number of small deallocations done via the arena")
         .register(registry);

      FunctionCounter.builder("netty.pooled.arena.deallocations.num", poolArenaMetric,
         metric -> metric.numNormalDeallocations())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "normal")
         .description("The number of normal deallocations done via the arena")
         .register(registry);

      FunctionCounter.builder("netty.pooled.arena.deallocations.num", poolArenaMetric,
         metric -> metric.numHugeDeallocations())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "huge")
         .description("The number of huge deallocations done via the arena")
         .register(registry);

      Gauge.builder("netty.pooled.arena.active.allocations.num", poolArenaMetric,
         metric -> metric.numActiveAllocations())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "all")
         .description("The number of currently active allocations")
         .register(registry);

      Gauge.builder("netty.pooled.arena.active.allocations.num", poolArenaMetric,
         metric -> metric.numActiveTinyAllocations())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "tiny")
         .description("The number of currently active tiny allocations")
         .register(registry);

      Gauge.builder("netty.pooled.arena.active.allocations.num", poolArenaMetric,
         metric -> metric.numActiveSmallAllocations())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "small")
         .description("The number of currently active small allocations")
         .register(registry);

      Gauge.builder("netty.pooled.arena.active.allocations.num", poolArenaMetric,
         metric -> metric.numActiveNormalAllocations())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "normal")
         .description("The number of currently active normal allocations")
         .register(registry);

      Gauge.builder("netty.pooled.arena.active.allocations.num", poolArenaMetric,
         metric -> metric.numActiveHugeAllocations())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "huge")
         .description("The number of currently active huge allocations")
         .register(registry);

      Gauge.builder("netty.pooled.arena.active.allocated.num", poolArenaMetric,
         metric -> metric.numActiveBytes())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString)
         .description("The number of active bytes that are currently allocated by the arena")
         .baseUnit(BYTES_UNIT).register(registry);

      Gauge.builder("netty.pooled.arena.chunk.num", poolArenaMetric,
         metric -> metric.numChunkLists())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString)
         .description("The number of chunk lists for the arena")
         .register(registry);

      Gauge.builder("netty.pooled.arena.subpages.num", poolArenaMetric,
         metric -> metric.numTinySubpages())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "tiny")
         .description("The number of tiny sub-pages for the arena")
         .register(registry);

      Gauge.builder("netty.pooled.arena.subpages.num", poolArenaMetric,
         metric -> metric.numSmallSubpages())
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndexString, "size", "small")
         .description("The number of small sub-pages for the arena")
         .register(registry);

      List<PoolChunkListMetric> poolChunkListMetrics = poolArenaMetric.chunkLists();
      assert poolChunkListMetrics.size() == 6;
      for (PoolChunkListMetric poolChunkListMetric : poolChunkListMetrics) {
         final String poolChunkListType = usageTypeOf(poolChunkListMetric);
         metricsOfPoolChunkListMetric(registry, poolChunkListMetric, poolArenaIndexString, poolArenaType, poolChunkListType);
      }
      // smallSubpages metrics
      metricsOfPoolSubpageMetric(registry, poolArenaMetric, PoolArenaMetric::smallSubpages,
                                 poolArenaIndexString, poolArenaType, "small");
      // tinySubpages metrics
      metricsOfPoolSubpageMetric(registry, poolArenaMetric, PoolArenaMetric::tinySubpages,
                                 poolArenaIndexString, poolArenaType, "tiny");



   }

   private void metricsOfPoolSubpageMetric(final MeterRegistry registry,
                                           final PoolArenaMetric poolArenaMetric,
                                           final Function<? super PoolArenaMetric, List<PoolSubpageMetric>> poolSubpageMetricProvider,
                                           final String poolArenaIndex,
                                           final String poolArenaType,
                                           final String size) {
      Gauge.builder("netty.pooled.arena.subpages.count", poolArenaMetric, metric -> {
         long total = 0;
         for (PoolSubpageMetric poolSubpageMetric : poolSubpageMetricProvider.apply(metric)) {
            total++;
         }
         return total;
      })
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndex, "size", size)
         .description("The total count of subpages")
         .register(registry);

      Gauge.builder("netty.pooled.arena.subpages.elementsize", poolArenaMetric, metric -> {
         long total = 0;
         for (PoolSubpageMetric poolSubpageMetric : poolSubpageMetricProvider.apply(metric)) {
            total += poolSubpageMetric.elementSize();
         }
         return total;
      })
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndex, "size", size)
         .description("The total size (in bytes) of the elements that will be allocated")
         .baseUnit(BYTES_UNIT)
         .register(registry);

      Gauge.builder("netty.pooled.arena.subpages.maxnumelements", poolArenaMetric, metric -> {
         long total = 0;
         for (PoolSubpageMetric poolSubpageMetric : poolSubpageMetricProvider.apply(metric)) {
            total += poolSubpageMetric.maxNumElements();
         }
         return total;
      })
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndex, "size", size)
         .description("The total number of maximal elements that can be allocated out of the sub-page.")
         .register(registry);

      Gauge.builder("netty.pooled.arena.subpages.numavailable", poolArenaMetric, metric -> {
         long total = 0;
         for (PoolSubpageMetric poolSubpageMetric : poolSubpageMetricProvider.apply(metric)) {
            total += poolSubpageMetric.numAvailable();
         }
         return total;
      })
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndex, "size", size)
         .description("The total number of available elements to be allocated")
         .register(registry);
      Gauge.builder("netty.pooled.arena.subpages.pagesize", poolArenaMetric, metric -> {
         long total = 0;
         for (PoolSubpageMetric poolSubpageMetric : poolSubpageMetricProvider.apply(metric)) {
            total += poolSubpageMetric.pageSize();
         }
         return total;
      })
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndex, "size", size)
         .description("The total size (in bytes) of the pages")
         .baseUnit(BYTES_UNIT)
         .register(registry);
   }

   private static String usageTypeOf(PoolChunkListMetric metric) {
      // metrics.add(qInit); 0~25%     -> new PoolChunkList<T>(this, q000, Integer.MIN_VALUE, 25, chunkSize);
      // metrics.add(q000); 0~50%      -> new PoolChunkList<T>(this, q025, 1, 50, chunkSize);
      // metrics.add(q025); 25~75%     -> new PoolChunkList<T>(this, q050, 25, 75, chunkSize);
      // metrics.add(q050); 50~100%    -> new PoolChunkList<T>(this, q075, 50, 100, chunkSize);
      // metrics.add(q075); 75~100%    -> new PoolChunkList<T>(this, q100, 75, 100, chunkSize);
      // metrics.add(q100); 100%       -> new PoolChunkList<T>(this, null, 100, Integer.MAX_VALUE, chunkSize);
      final StringBuilder builder = new StringBuilder("75~100%".length());
      int minUsage = metric.minUsage();
      if (minUsage <= 1) {
         minUsage = 0;
      }
      builder.append(minUsage);
      int maxUsage = metric.maxUsage();
      if (maxUsage != minUsage) {
         builder.append('~').append(maxUsage);
      }
      builder.append('%');
      return builder.toString();
   }

   private void metricsOfPoolChunkListMetric(final MeterRegistry registry,
                                             final PoolChunkListMetric poolChunkListMetrics,
                                             final String poolArenaIndex,
                                             final String poolArenaType,
                                             final String poolChunkListType) {
      Gauge.builder("netty.pooled.arena.chunk.list.capacity", poolChunkListMetrics, metric -> {
         long total = 0;
         for (PoolChunkMetric poolChunkMetric : poolChunkListMetrics) {
            total += poolChunkMetric.chunkSize();
         }
         return total;
      })
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndex, "pool_chunk_list_type", poolChunkListType)
         .description("The total capacity in bytes of the chunks in the list")
         .baseUnit(BYTES_UNIT)
         .register(registry);
      Gauge.builder("netty.pooled.arena.chunk.list.free", poolChunkListMetrics, metric -> {
         long total = 0;
         for (PoolChunkMetric poolChunkMetric : poolChunkListMetrics) {
            total += poolChunkMetric.freeBytes();
         }
         return total;
      })
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndex, "pool_chunk_list_type", poolChunkListType)
         .description("The total free bytes of the chunks in the list")
         .baseUnit(BYTES_UNIT)
         .register(registry);

      Gauge.builder("netty.pooled.arena.chunk.list.count", poolChunkListMetrics, metric -> {
         long total = 0;
         for (PoolChunkMetric poolChunkMetric : poolChunkListMetrics) {
            total++;
         }
         return total;
      })
         .tags(commonTags)
         .tags("pool_arena_type", poolArenaType, "pool_arena_index", poolArenaIndex, "pool_chunk_list_type", poolChunkListType)
         .description("The number of chunks in the list")
         .register(registry);
   }

}
