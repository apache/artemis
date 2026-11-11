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

package org.apache.activemq.artemis.tests.compatibility.base;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;

/**
 * The sole purpose of this class is to be a tag for eventually looking into MemoryDumps. When tests are failing, I need
 * to identify the classloaders created by the testsuite. And this class would make it simpler for that purpose.
 */
public class TestClassLoader extends URLClassLoader {

   public TestClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, parent);
   }

   public TestClassLoader(URL[] urls) {
      super(urls);
   }

   public TestClassLoader(URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory) {
      super(urls, parent, factory);
   }

   public TestClassLoader(String name, URL[] urls, ClassLoader parent) {
      super(name, urls, parent);
   }

   public TestClassLoader(String name, URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory) {
      super(name, urls, parent, factory);
   }
}
