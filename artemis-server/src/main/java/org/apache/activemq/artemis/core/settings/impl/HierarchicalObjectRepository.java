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
package org.apache.activemq.artemis.core.settings.impl;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.HierarchicalRepositoryChangeListener;
import org.apache.activemq.artemis.core.settings.Mergeable;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.core.config.WildcardConfiguration.DEFAULT_WILDCARD_CONFIGURATION;

/**
 * allows objects to be mapped against a regex pattern and held in order in a list
 */
public class HierarchicalObjectRepository<T> implements HierarchicalRepository<T> {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private boolean listenersEnabled = true;
   /**
    * The default Match to fall back to
    */
   private T defaultmatch;

   /**
    * the matches; separate wildcard matches from exact matches to reduce the searching necessary with a large number of
    * exact matches
    */
   private final Map<String, Match<T>> wildcardMatches = new HashMap<>();
   private final Map<String, Match<T>> exactMatches = new HashMap<>();
   private final Map<String, Match<T>> literalMatches = new HashMap<>();

   /**
    * Certain values cannot be removed after installed. This is because we read a few records from the main config.
    * JBoss AS deployer may remove them on undeploy, while we don't want to accept that since this could cause issues on
    * shutdown. Notice you can still change these values. You just can't remove them.
    */
   private final Set<String> immutables = new HashSet<>();

   /**
    * a regex comparator
    */
   private final MatchComparator matchComparator;

   private final MatchModifier matchModifier;

   private final WildcardConfiguration wildcardConfiguration;

   private final boolean checkLiteral;

   private final char literalMatchMarkerStart;

   private final char literalMatchMarkerEnd;

   private final Map<String, T> cache = new ConcurrentHashMap<>();

   /**
    * Need a lock instead of using multiple {@link ConcurrentHashMap}s.
    * <p>
    * We could have a race between the state of {@link #wildcardMatches}, {@link #exactMatches}, and {@link #cache}:
    * <ul>
    * <li>Thread1: calls {@link #addMatch(String, T)}: i. cleans cache; ii. adds match to Map.
    * <li>Thread2: could add an (out-dated) entry to the cache between 'i. clean cache' and 'ii. add match to Map'.
    * </ul>
    * The lock is OK with regards to performance because we can search the cache before entering the lock.
    * <p>
    * The lock is required for the 'add match to cache' part.
    */
   private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(false);

   /**
    * any registered listeners, these get fired on changes to the repository
    */
   private final ConcurrentHashSet<HierarchicalRepositoryChangeListener> listeners = new ConcurrentHashSet<>();

   public HierarchicalObjectRepository() {
      this(null);
   }

   public HierarchicalObjectRepository(final WildcardConfiguration wildcardConfiguration) {
      this(wildcardConfiguration, new MatchModifier() { }, null);
   }

   public HierarchicalObjectRepository(final WildcardConfiguration wildcardConfiguration, final MatchModifier matchModifier) {
      this(wildcardConfiguration, matchModifier, null);
   }

   public HierarchicalObjectRepository(final WildcardConfiguration wildcardConfiguration, final MatchModifier matchModifier, final String literalMatchMarkers) {
      this.wildcardConfiguration = Objects.requireNonNullElse(wildcardConfiguration, DEFAULT_WILDCARD_CONFIGURATION);
      this.matchComparator = new MatchComparator(this.wildcardConfiguration);
      this.matchModifier = matchModifier;
      if (literalMatchMarkers != null) {
         this.checkLiteral = true;
         this.literalMatchMarkerStart = literalMatchMarkers.charAt(0);
         this.literalMatchMarkerEnd = literalMatchMarkers.charAt(1);
      } else {
         this.checkLiteral = false;
         this.literalMatchMarkerStart = 0;
         this.literalMatchMarkerEnd = 0;
      }
   }

   @Override
   public void disableListeners() {
      lock.writeLock().lock();
      try {
         this.listenersEnabled = false;
      } finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   public void enableListeners() {
      lock.writeLock().lock();
      try {
         this.listenersEnabled = true;
      } finally {
         lock.writeLock().unlock();
      }
      onChange();
   }

   @Override
   public List<T> values() {
      lock.readLock().lock();
      try {
         List<T> values = new ArrayList<>(wildcardMatches.size() + exactMatches.size() + literalMatches.size());

         for (Match<T> matchValue : wildcardMatches.values()) {
            values.add(matchValue.getValue());
         }

         for (Match<T> matchValue : exactMatches.values()) {
            values.add(matchValue.getValue());
         }

         for (Match<T> matchValue : literalMatches.values()) {
            values.add(matchValue.getValue());
         }

         return values;
      } finally {
         lock.readLock().unlock();
      }
   }

   @Override
   public void addMatch(final String match, final T value) {
      addMatch(match, value, false);
   }

   @Override
   public void addMatch(final String match, final T value, final boolean immutableMatch) {
      addMatch(match, value, immutableMatch, true);
   }

   @Override
   public void addMatch(final String match, final T value, final boolean immutableMatch, final boolean notifyListeners) {
      String modifiedMatch = match;
      boolean literal = false;
      if (checkLiteral) {
         literal = match.charAt(0) == literalMatchMarkerStart && match.charAt(match.length() - 1) == literalMatchMarkerEnd;
         if (literal) {
            modifiedMatch = match.substring(1, match.length() - 1);
         }
      }
      modifiedMatch = matchModifier.modify(modifiedMatch);
      lock.writeLock().lock();
      try {
         // an exact match (i.e. one without wildcards) won't impact any other matches so no need to clear the cache
         if (wildcardConfiguration.isWild(modifiedMatch) && !CompositeAddress.isFullyQualified(modifiedMatch)) {
            clearCache();
         } else if (modifiedMatch != null && cache.containsKey(modifiedMatch)) {
            cache.remove(modifiedMatch);
         }

         if (immutableMatch) {
            immutables.add(modifiedMatch);
         }
         Match.verify(modifiedMatch, wildcardConfiguration);
         Match<T> match1 = new Match<>(modifiedMatch, value, wildcardConfiguration, literal);
         if (literal) {
            literalMatches.put(modifiedMatch, match1);
         } else if (wildcardConfiguration.isWild(modifiedMatch) && !CompositeAddress.isFullyQualified(modifiedMatch)) {
            wildcardMatches.put(modifiedMatch, match1);
         } else {
            exactMatches.put(modifiedMatch, match1);
         }
      } finally {
         lock.writeLock().unlock();
      }

      // Calling the onChange outside of the wrieLock as some listeners may be doing reads on the matches
      if (notifyListeners) {
         onChange();
      }
   }

   @Override
   public int getCacheSize() {
      return cache.size();
   }

   /**
    * {@return the value held against the nearest match}
    * @param match the match to look for
    */
   @Override
   public T getMatch(final String match) {
      String modifiedMatch = matchModifier.modify(match);
      T cacheResult = cache.get(modifiedMatch);
      if (cacheResult != null) {
         return cacheResult;
      }
      lock.readLock().lock();
      try {
         List<Match<T>> matches =
            getMatches(modifiedMatch);
         T actualMatch = merge(matches);
         T value = actualMatch != null ? actualMatch : defaultmatch;
         if (value != null) {
            cache.put(modifiedMatch, value);
         }
         return value;
      } finally {
         lock.readLock().unlock();
      }
   }

   @Override
   public boolean containsExactMatch(String match) {
      return exactMatches.containsKey(match);
   }

   @Override
   public boolean containsExactWildcardMatch(String match) {
      return wildcardMatches.containsKey(match);
   }

   /**
    * merge all the possible matches, if the values implement Mergeable then a full merge is done
    */
   private T merge(final Collection<Match<T>> orderedMatches) {
      Iterator<Match<T>> matchIterator = orderedMatches.iterator();

      T result = null;
      if (matchIterator.hasNext()) {
         Match<T> match = matchIterator.next();
         result = match.getValue();

         while (matchIterator.hasNext() && Mergeable.class.isAssignableFrom(result.getClass())) {
            match = matchIterator.next();
            result = ((Mergeable<T>)result).mergeCopy(match.getValue());
         }
      }

      return result;
   }

   /**
    * remove a match from the repository
    *
    * @param match the match to remove
    */
   @Override
   public void removeMatch(final String match) {
      lock.writeLock().lock();
      try {
         String modMatch = matchModifier.modify(match);
         if (immutables.contains(modMatch)) {
            logger.debug("Cannot remove match {} since it came from a main config", modMatch);
         } else {
            /*
             * Clear the cache before removing the match, but only if the match used wildcards. This will force any
             * thread at {@link #getMatch(String)} to get the lock to recompute.
             */
            if (wildcardConfiguration.isWild(modMatch)) {
               clearCache();
               wildcardMatches.remove(modMatch);
            } else {
               clearCache();
               exactMatches.remove(modMatch);
               literalMatches.remove(modMatch);
            }
            onChange();
         }
      } finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   public void registerListener(final HierarchicalRepositoryChangeListener listener) {
      listeners.add(listener);
      if (listenersEnabled) {
         listener.onChange();
      }
   }

   @Override
   public void unRegisterListener(final HierarchicalRepositoryChangeListener listener) {
      listeners.remove(listener);
   }

   /**
    * set the default value to fallback to if none found
    *
    * @param defaultValue the value
    */
   @Override
   public void setDefault(final T defaultValue) {
      clearCache();
      defaultmatch = defaultValue;
   }

   /**
    * @return the default match for this repo
    */
   @Override
   public T getDefault() {
      return defaultmatch;
   }

   @Override
   public void clear() {
      lock.writeLock().lock();
      try {
         clearCache();
         listeners.clear();
         clearMatches();
      } finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   public void swap(Set<Map.Entry<String, T>> entries) {
      lock.writeLock().lock();
      try {
         clearCache();
         immutables.clear();
         clearMatches();
         for (Map.Entry<String, T> entry : entries) {
            addMatch(entry.getKey(), entry.getValue(), true, false);
         }
      } finally {
         lock.writeLock().unlock();
      }

      onChange();
   }

   @Override
   public void clearListeners() {
      listeners.clear();
   }

   @Override
   public void clearCache() {
      cache.clear();
   }

   private void clearMatches() {
      wildcardMatches.clear();
      exactMatches.clear();
      literalMatches.clear();
   }

   private void onChange() {
      if (listenersEnabled) {
         for (HierarchicalRepositoryChangeListener listener : listeners) {
            try {
               listener.onChange();
            } catch (Throwable e) {
               ActiveMQServerLogger.LOGGER.errorCallingRepoListener(e);
            }
         }
      }
   }

   private List<Match<T>> getMatches(final String match) {
      List<Match<T>> matches = new ArrayList<>();

      Match exactMatch = exactMatches.get(match);
      if (exactMatch != null) {
         matches.add(exactMatch);
      }

      Match literalMatch = literalMatches.get(match);
      if (literalMatch != null) {
         matches.add(literalMatch);
      }

      wildcardMatches.values().stream().
         filter(m -> m.getPattern().matcher(match).matches()).
         sorted((m1, m2) -> matchComparator.compare(m1.getMatch(), m2.getMatch())).
         forEach(m -> matches.add(m));

      return matches;
   }

   /**
    * Modifies the match String for any add or get from the repository
    */
   public interface MatchModifier {
      default String modify(String input) {
         return input;
      }
   }

   /**
    * Compares to matches to see which one is more specific.
    */
   private static final class MatchComparator implements Comparator<String>, Serializable {

      private static final long serialVersionUID = -6182535107518999740L;

      private final String quotedDelimiter;
      private final String anyWords;
      private final String singleWord;

      MatchComparator(final WildcardConfiguration wildcardConfiguration) {
         this.quotedDelimiter = Pattern.quote(wildcardConfiguration.getDelimiterString());
         this.singleWord = wildcardConfiguration.getSingleWordString();
         this.anyWords = wildcardConfiguration.getAnyWordsString();
      }

      @Override
      public int compare(final String o1, final String o2) {
         if (o1.contains(anyWords) && !o2.contains(anyWords)) {
            return +1;
         } else if (!o1.contains(anyWords) && o2.contains(anyWords)) {
            return -1;
         } else if (o1.contains(anyWords) && o2.contains(anyWords)) {
            return o2.length() - o1.length();
         } else if (o1.contains(singleWord) && !o2.contains(singleWord)) {
            return +1;
         } else if (!o1.contains(singleWord) && o2.contains(singleWord)) {
            return -1;
         } else if (o1.contains(singleWord) && o2.contains(singleWord)) {
            String[] leftSplits = o1.split(quotedDelimiter);
            String[] rightSplits = o2.split(quotedDelimiter);
            for (int i = 0; i < leftSplits.length; i++) {
               if (i >= rightSplits.length) {
                  return -1;
               }
               String left = leftSplits[i];
               String right = rightSplits[i];
               if (left.equals(singleWord) && !right.equals(singleWord)) {
                  return +1;
               } else if (!left.equals(singleWord) && right.equals(singleWord)) {
                  return -1;
               }
            }
         }
         return o1.length() - o2.length();
      }
   }
}
