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
package org.apache.activemq.artemis.common.testjndi;

import javax.naming.Binding;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.LinkRef;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NotContextException;
import javax.naming.OperationNotSupportedException;
import javax.naming.Reference;
import javax.naming.spi.NamingManager;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import org.apache.activemq.artemis.jndi.NameParserImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A read-only Context
 * <p>
 * This version assumes it and all its subcontext are read-only and any attempt to modify (e.g. through bind) will
 * result in an OperationNotSupportedException. Each Context in the tree builds a cache of the entries in all
 * sub-contexts to optimise the performance of lookup.
 * <p>
 * This implementation is intended to optimise the performance of lookup(String) to about the level of a HashMap get. It
 * has been observed that the scheme resolution phase performed by the JVM takes considerably longer, so for optimum
 * performance lookups should be coded like:
 * <pre>{@code
 * Context componentContext = (Context)new InitialContext().lookup("java:comp"); String envEntry = (String)
 * componentContext.lookup("env/myEntry"); String envEntry2 = (String) componentContext.lookup("env/myEntry2");
 * }</pre>
 */
@SuppressWarnings("unchecked")
public class TestContext implements Context, Serializable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String SEPARATOR = "/";
   protected static final NameParser NAME_PARSER = new NameParserImpl();
   private static final long serialVersionUID = -5754338187296859149L;

   protected final Hashtable<String, Object> environment; // environment for this context
   protected final Map<String, Object> bindings; // bindings at my level
   protected final Map<String, Object> treeBindings; // all bindings under me

   private boolean frozen;
   private String nameInNamespace = "";

   public TestContext() {
      environment = new Hashtable<>();
      bindings = new HashMap<>();
      treeBindings = new HashMap<>();
   }

   public TestContext(Map env) {
      if (env == null) {
         this.environment = new Hashtable<>();
      } else {
         this.environment = new Hashtable<>(env);
      }
      this.bindings = Collections.emptyMap();
      this.treeBindings = Collections.emptyMap();
   }

   public TestContext(Map environment, Map<String, Object> bindings) {
      if (environment == null) {
         this.environment = new Hashtable<>();
      } else {
         this.environment = new Hashtable<>(environment);
      }
      this.bindings = new HashMap<>();
      treeBindings = new HashMap<>();
      if (bindings != null) {
         for (Map.Entry<String, Object> binding : bindings.entrySet()) {
            try {
               internalBind(binding.getKey(), binding.getValue());
            } catch (Throwable e) {
               logger.error("Failed to bind {}={}", binding.getKey(), binding.getValue(), e);
            }
         }
      }
      frozen = true;
   }

   public TestContext(Hashtable environment, Map<String, Object> bindings, String nameInNamespace) {
      this(environment, bindings);
      this.nameInNamespace = nameInNamespace;
   }

   protected TestContext(TestContext clone, Hashtable env) {
      this.bindings = clone.bindings;
      this.treeBindings = clone.treeBindings;
      this.environment = new Hashtable<>(env);
   }

   protected TestContext(TestContext clone, Hashtable<String, Object> env, String nameInNamespace) {
      this(clone, env);
      this.nameInNamespace = nameInNamespace;
   }

   public void freeze() {
      frozen = true;
   }

   boolean isFrozen() {
      return frozen;
   }

   /**
    * internalBind is intended for use only during setup or possibly by suitably synchronized superclasses. It binds
    * every possible lookup into a map in each context. To do this, each context strips off one name segment and if
    * necessary creates a new context for it. Then it asks that context to bind the remaining name. It returns a map
    * containing all the bindings from the next context, plus the context it just created (if it in fact created it).
    * (the names are suitably extended by the segment originally lopped off).
    */
   protected Map<String, Object> internalBind(String name, Object value) throws NamingException {
      assert name != null && !name.isEmpty();

      Map<String, Object> newBindings = new HashMap<>();
      int pos = name.indexOf('/');
      if (pos == -1) {
         if (treeBindings.put(name, value) != null) {
            throw new NamingException("Something already bound at " + name);
         }
         bindings.put(name, value);
         newBindings.put(name, value);
      } else {
         String segment = name.substring(0, pos);
         assert segment != null;
         assert !segment.isEmpty();
         Object o = treeBindings.get(segment);
         if (o == null) {
            o = newContext();
            treeBindings.put(segment, o);
            bindings.put(segment, o);
            newBindings.put(segment, o);
         } else if (!(o instanceof TestContext)) {
            throw new NamingException("Something already bound where a subcontext should go");
         }
         TestContext readOnlyContext = (TestContext) o;
         String remainder = name.substring(pos + 1);
         Map<String, Object> subBindings = readOnlyContext.internalBind(remainder, value);
         for (Map.Entry<String, Object> entry : subBindings.entrySet()) {
            String subName = segment + "/" + entry.getKey();
            Object bound = entry.getValue();
            treeBindings.put(subName, bound);
            newBindings.put(subName, bound);
         }
      }
      return newBindings;
   }

   protected TestContext newContext() {
      return new TestContext();
   }

   @Override
   public Object addToEnvironment(String propName, Object propVal) throws NamingException {
      return environment.put(propName, propVal);
   }

   @Override
   public Hashtable<String, Object> getEnvironment() throws NamingException {
      return (Hashtable<String, Object>) environment.clone();
   }

   @Override
   public Object removeFromEnvironment(String propName) throws NamingException {
      return environment.remove(propName);
   }

   @Override
   public Object lookup(String name) throws NamingException {
      if (name.isEmpty()) {
         return this;
      }
      Object result = treeBindings.get(name);
      if (result == null) {
         result = bindings.get(name);
      }
      if (result == null) {
         int pos = name.indexOf(':');
         if (pos > 0) {
            String scheme = name.substring(0, pos);
            Context ctx = NamingManager.getURLContext(scheme, environment);
            if (ctx == null) {
               throw new NamingException("scheme " + scheme + " not recognized");
            }
            return ctx.lookup(name);
         } else {
            // Split out the first name of the path
            // and look for it in the bindings map.
            CompositeName path = new CompositeName(name);

            if (path.isEmpty()) {
               return this;
            } else {
               String first = path.get(0);
               Object obj = bindings.get(first);
               if (obj == null) {
                  throw new NameNotFoundException(name);
               } else if (obj instanceof Context subContext && path.size() > 1) {
                  obj = subContext.lookup(path.getSuffix(1));
               }
               return obj;
            }
         }
      }
      if (result instanceof LinkRef ref) {
         result = lookup(ref.getLinkName());
      }
      if (result instanceof Reference) {
         try {
            result = NamingManager.getObjectInstance(result, null, null, this.environment);
         } catch (NamingException e) {
            throw e;
         } catch (Exception e) {
            throw (NamingException) new NamingException("could not look up : " + name).initCause(e);
         }
      }
      if (result instanceof TestContext context) {
         String prefix = getNameInNamespace();
         if (!prefix.isEmpty()) {
            prefix = prefix + SEPARATOR;
         }
         result = new TestContext(context, environment, prefix + name);
      }
      return result;
   }

   @Override
   public Object lookup(Name name) throws NamingException {
      return lookup(name.toString());
   }

   @Override
   public Object lookupLink(String name) throws NamingException {
      return lookup(name);
   }

   @Override
   public Name composeName(Name name, Name prefix) throws NamingException {
      Name result = (Name) prefix.clone();
      result.addAll(name);
      return result;
   }

   @Override
   public String composeName(String name, String prefix) throws NamingException {
      CompositeName result = new CompositeName(prefix);
      result.addAll(new CompositeName(name));
      return result.toString();
   }

   @Override
   public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
      Object o = lookup(name);
      if (o == this) {
         return new ListEnumeration();
      } else if (o instanceof Context context) {
         return context.list("");
      } else {
         throw new NotContextException();
      }
   }

   @Override
   public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
      Object o = lookup(name);
      if (o == this) {
         return new ListBindingEnumeration();
      } else if (o instanceof Context context) {
         return context.listBindings("");
      } else {
         throw new NotContextException();
      }
   }

   @Override
   public Object lookupLink(Name name) throws NamingException {
      return lookupLink(name.toString());
   }

   @Override
   public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
      return list(name.toString());
   }

   @Override
   public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
      return listBindings(name.toString());
   }

   @Override
   public void bind(Name name, Object obj) throws NamingException {
      internalBind(name.toString(), obj);
   }

   @Override
   public void bind(String name, Object obj) throws NamingException {
      internalBind(name, obj);
   }

   @Override
   public void close() throws NamingException {
      // ignore
   }

   @Override
   public Context createSubcontext(Name name) throws NamingException {
      throw new OperationNotSupportedException();
   }

   @Override
   public Context createSubcontext(String name) throws NamingException {
      throw new OperationNotSupportedException();
   }

   @Override
   public void destroySubcontext(Name name) throws NamingException {
      throw new OperationNotSupportedException();
   }

   @Override
   public void destroySubcontext(String name) throws NamingException {
      throw new OperationNotSupportedException();
   }

   @Override
   public String getNameInNamespace() throws NamingException {
      return nameInNamespace;
   }

   @Override
   public NameParser getNameParser(Name name) throws NamingException {
      return NAME_PARSER;
   }

   @Override
   public NameParser getNameParser(String name) throws NamingException {
      return NAME_PARSER;
   }

   @Override
   public void rebind(Name name, Object obj) throws NamingException {
      throw new OperationNotSupportedException();
   }

   @Override
   public void rebind(String name, Object obj) throws NamingException {
      throw new OperationNotSupportedException();
   }

   @Override
   public void rename(Name oldName, Name newName) throws NamingException {
      throw new OperationNotSupportedException();
   }

   @Override
   public void rename(String oldName, String newName) throws NamingException {
      throw new OperationNotSupportedException();
   }

   @Override
   public void unbind(Name name) throws NamingException {
      treeBindings.remove(name.toString());
   }

   @Override
   public void unbind(String name) throws NamingException {
      treeBindings.remove(name);
   }

   private abstract class LocalNamingEnumeration implements NamingEnumeration {

      private final Iterator<Map.Entry<String, Object>> i = bindings.entrySet().iterator();

      @Override
      public boolean hasMore() throws NamingException {
         return i.hasNext();
      }

      @Override
      public boolean hasMoreElements() {
         return i.hasNext();
      }

      protected Map.Entry<String, Object> getNext() {
         return i.next();
      }

      @Override
      public void close() throws NamingException {
      }
   }

   private class ListEnumeration extends LocalNamingEnumeration {

      ListEnumeration() {
      }

      @Override
      public Object next() throws NamingException {
         return nextElement();
      }

      @Override
      public Object nextElement() {
         Map.Entry<String, Object> entry = getNext();
         return new NameClassPair(entry.getKey(), entry.getValue().getClass().getName());
      }
   }

   private class ListBindingEnumeration extends LocalNamingEnumeration {

      ListBindingEnumeration() {
      }

      @Override
      public Object next() throws NamingException {
         return nextElement();
      }

      @Override
      public Object nextElement() {
         Map.Entry<String, Object> entry = getNext();
         return new Binding(entry.getKey(), entry.getValue());
      }
   }
}
