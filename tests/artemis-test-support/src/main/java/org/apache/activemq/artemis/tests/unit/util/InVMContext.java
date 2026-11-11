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
package org.apache.activemq.artemis.tests.unit.util;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class InVMContext implements Context, Serializable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


   private static final long serialVersionUID = 385743957345L;


   protected Map<String, Object> map;

   protected NameParser parser = new InVMNameParser();

   private String nameInNamespace = "";


   public InVMContext() {
      map = Collections.synchronizedMap(new HashMap<>());
   }

   public InVMContext(final String nameInNamespace) {
      this();
      this.nameInNamespace = nameInNamespace;
   }

   // Context implementation ----------------------------------------

   @Override
   public Object lookup(final Name name) throws NamingException {
      throw new UnsupportedOperationException();
   }

   @Override
   public Object lookup(String name) throws NamingException {
      name = trimSlashes(name);
      int i = name.indexOf("/");
      String tok = i == -1 ? name : name.substring(0, i);
      Object value = map.get(tok);
      if (value == null) {
         throw new NameNotFoundException("Name not found: " + tok);
      }
      if (value instanceof InVMContext context && i != -1) {
         return context.lookup(name.substring(i));
      }
      if (value instanceof Reference ref) {
         RefAddr refAddr = ref.get("nns");

         // we only deal with references create by NonSerializableFactory
         String key = (String) refAddr.getContent();
         return NonSerializableFactory.lookup(key);
      } else {
         return value;
      }
   }

   @Override
   public void bind(final Name name, final Object obj) throws NamingException {
      throw new UnsupportedOperationException();
   }

   @Override
   public void bind(final String name, final Object obj) throws NamingException {
      internalBind(name, obj, false);
   }

   @Override
   public void rebind(final Name name, final Object obj) throws NamingException {
      throw new UnsupportedOperationException();
   }

   @Override
   public void rebind(final String name, final Object obj) throws NamingException {
      internalBind(name, obj, true);
   }

   @Override
   public void unbind(final Name name) throws NamingException {
      unbind(name.toString());
   }

   @Override
   public void unbind(String name) throws NamingException {
      name = trimSlashes(name);
      int i = name.indexOf("/");
      boolean terminal = i == -1;
      if (terminal) {
         map.remove(name);
      } else {
         String tok = name.substring(0, i);
         InVMContext c = (InVMContext) map.get(tok);
         if (c == null) {
            throw new NameNotFoundException("Context not found: " + tok);
         }
         c.unbind(name.substring(i));
      }
   }

   @Override
   public void rename(final Name oldName, final Name newName) throws NamingException {
      throw new UnsupportedOperationException();
   }

   @Override
   public void rename(final String oldName, final String newName) throws NamingException {
      throw new UnsupportedOperationException();
   }

   @Override
   public NamingEnumeration<NameClassPair> list(final Name name) throws NamingException {
      throw new UnsupportedOperationException();
   }

   @Override
   public NamingEnumeration<NameClassPair> list(final String name) throws NamingException {
      throw new UnsupportedOperationException();
   }

   @Override
   public NamingEnumeration<Binding> listBindings(final Name name) throws NamingException {
      throw new UnsupportedOperationException();
   }

   @Override
   public NamingEnumeration<Binding> listBindings(String contextName) throws NamingException {
      contextName = trimSlashes(contextName);
      if (!"".equals(contextName) && !".".equals(contextName)) {
         try {
            return ((InVMContext) lookup(contextName)).listBindings("");
         } catch (Throwable t) {
            throw new NamingException(t.getMessage());
         }
      }

      List<Binding> l = new ArrayList<>();
      for (Object element : map.keySet()) {
         String name = (String) element;
         Object object = map.get(name);
         l.add(new Binding(name, object));
      }
      return new NamingEnumerationImpl<>(l.iterator());
   }

   @Override
   public void destroySubcontext(final Name name) throws NamingException {
      destroySubcontext(name.toString());
   }

   @Override
   public void destroySubcontext(final String name) throws NamingException {
      map.remove(trimSlashes(name));
   }

   @Override
   public Context createSubcontext(final Name name) throws NamingException {
      throw new UnsupportedOperationException();
   }

   @Override
   public Context createSubcontext(String name) throws NamingException {
      name = trimSlashes(name);
      if (map.get(name) != null) {
         throw new NameAlreadyBoundException(name);
      }
      InVMContext c = new InVMContext(getNameInNamespace());
      map.put(name, c);
      return c;
   }

   @Override
   public Object lookupLink(final Name name) throws NamingException {
      throw new UnsupportedOperationException();
   }

   @Override
   public Object lookupLink(final String name) throws NamingException {
      throw new UnsupportedOperationException();
   }

   @Override
   public NameParser getNameParser(final Name name) throws NamingException {
      return getNameParser(name.toString());
   }

   @Override
   public NameParser getNameParser(final String name) throws NamingException {
      return parser;
   }

   @Override
   public Name composeName(final Name name, final Name prefix) throws NamingException {
      throw new UnsupportedOperationException();
   }

   @Override
   public String composeName(final String name, final String prefix) throws NamingException {
      throw new UnsupportedOperationException();
   }

   @Override
   public Object addToEnvironment(final String propName, final Object propVal) throws NamingException {
      throw new UnsupportedOperationException();
   }

   @Override
   public Object removeFromEnvironment(final String propName) throws NamingException {
      throw new UnsupportedOperationException();
   }

   @Override
   public Hashtable<String, String> getEnvironment() throws NamingException {
      Hashtable<String, String> env = new Hashtable<>();
      env.put("java.naming.factory.initial", "org.apache.activemq.artemis.jms.tests.tools.container.InVMInitialContextFactory");
      return env;
   }

   @Override
   public void close() throws NamingException {
   }

   @Override
   public String getNameInNamespace() throws NamingException {
      return nameInNamespace;
   }





   private String trimSlashes(String s) {
      int i = 0;
      while (true) {
         if (i == s.length() || s.charAt(i) != '/') {
            break;
         }
         i++;
      }
      s = s.substring(i);
      i = s.length() - 1;
      while (true) {
         if (i == -1 || s.charAt(i) != '/') {
            break;
         }
         i--;
      }
      return s.substring(0, i + 1);
   }

   private void internalBind(String name, final Object obj, final boolean rebind) throws NamingException {
      logger.debug("Binding {} obj {} rebind {}", name, obj, rebind);
      name = trimSlashes(name);
      int i = name.lastIndexOf("/");
      InVMContext c = this;
      if (i != -1) {
         String path = name.substring(0, i);
         c = (InVMContext) lookup(path);
      }
      name = name.substring(i + 1);
      if (!rebind && c.map.get(name) != null) {
         throw new NameAlreadyBoundException(name);
      }
      c.map.put(name, obj);
   }


   private class NamingEnumerationImpl<T> implements NamingEnumeration<T> {

      private final Iterator<T> iterator;

      NamingEnumerationImpl(final Iterator<T> bindingIterator) {
         iterator = bindingIterator;
      }

      @Override
      public void close() throws NamingException {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean hasMore() throws NamingException {
         return iterator.hasNext();
      }

      @Override
      public T next() throws NamingException {
         return iterator.next();
      }

      @Override
      public boolean hasMoreElements() {
         return iterator.hasNext();
      }

      @Override
      public T nextElement() {
         return iterator.next();
      }
   }
}
