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

package org.apache.artemis.jsonschema.ir;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable representation of a property path in the schema IR.
 *
 * <p>This is the identity key that links a property across all pipeline phases: IR building,
 * enrichment storage, and emission. Using a typed class instead of raw strings prevents malformed
 * paths from typos and makes the path-building API self-documenting.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code Location.root()} → ""
 *   <li>{@code Location.root().child("acceptorConfigurations")} → "acceptorConfigurations"
 *   <li>{@code location.wildcard()} → "acceptorConfigurations.*"
 *   <li>{@code location.child("params").child("host")} → "acceptorConfigurations.*.params.host"
 * </ul>
 */
public final class Location {

   private static final Location ROOT = new Location(Collections.emptyList());

   private final List<String> segments;

   private Location(List<String> segments) {
      this.segments = segments;
   }

   /**
    * The root location (empty path, represents ConfigurationImpl itself).
    *
    * @return the singleton root location
    */
   public static Location root() {
      return ROOT;
   }

   /**
    * Parse a dotted string into a Location. Used at boundaries with extractor-produced paths.
    *
    * @param dottedPath dot-separated path string, or null/empty for root
    * @return parsed location, or root if the input is null or empty
    */
   public static Location of(String dottedPath) {
      if (dottedPath == null || dottedPath.isEmpty()) {
         return ROOT;
      }
      return new Location(Arrays.asList(dottedPath.split("\\.")));
   }

   /**
    * Derive a child location by appending a property name.
    *
    * @param name property name segment to append
    * @return new location with the segment appended
    */
   public Location child(String name) {
      List<String> newSegments = new ArrayList<>(segments.size() + 1);
      newSegments.addAll(segments);
      newSegments.add(name);
      return new Location(newSegments);
   }

   /**
    * Derive a child location from a PropertyNode's name.
    *
    * @param prop property node whose name becomes the new segment
    * @return new location with the property name appended
    */
   public Location child(SchemaIR.PropertyNode prop) {
      return child(prop.getName());
   }

   /**
    * Append a wildcard segment (represents user-defined map/collection keys).
    *
    * @return new location with a {@code *} segment appended
    */
   public Location wildcard() {
      return child("*");
   }

   /**
    * The last segment of the path (property name at this level).
    *
    * @return leaf segment, or empty string if this is the root location
    */
   public String leafName() {
      return segments.isEmpty() ? "" : segments.get(segments.size() - 1);
   }

   /**
    * True if this is the root location (empty path).
    *
    * @return {@code true} if this location has no segments
    */
   public boolean isEmpty() {
      return segments.isEmpty();
   }

   /**
    * Convert to the dot-separated string used as enrichment key and log output.
    *
    * @return dot-joined path string, or empty string for root
    */
   public String toDotted() {
      return String.join(".", segments);
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Location location = (Location) o;
      return segments.equals(location.segments);
   }

   @Override
   public int hashCode() {
      return Objects.hash(segments);
   }

   @Override
   public String toString() {
      return toDotted();
   }
}
