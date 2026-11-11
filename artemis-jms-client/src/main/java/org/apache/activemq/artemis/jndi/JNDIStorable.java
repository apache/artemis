/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.jndi;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Properties;

/**
 * Facilitates objects to be stored in JNDI as properties
 */
public abstract class JNDIStorable implements Referenceable {

   /**
    * Set the properties that will represent the instance in JNDI
    *
    * @param props The properties to use when building the new isntance.
    */
   protected abstract void buildFromProperties(Properties props);

   /**
    * Initialize the instance from properties stored in JNDI
    *
    * @param props The properties to use when initializing the new instance.
    */
   protected abstract void populateProperties(Properties props);

   /**
    * set the properties for this instance as retrieved from JNDI
    *
    * @param props The properties to apply to this instance.
    * @return a new, unmodifiable, map containing any unused properties, or empty if none were
    */
   synchronized void setProperties(Properties props) {
      buildFromProperties(props);
   }

   /**
    * Get the properties from this instance for storing in JNDI
    *
    * @return the properties
    */
   synchronized Properties getProperties() {
      Properties properties = new Properties();
      populateProperties(properties);
      return properties;
   }

   /**
    * Retrieve a Reference for this instance to store in JNDI
    *
    * @return the built Reference
    * @throws NamingException if error on building Reference
    */
   @Override
   public Reference getReference() throws NamingException {
      return JNDIReferenceFactory.createReference(this.getClass().getName(), this);
   }

   /**
    * Method for class's implementing externalizable to delegate to if not custom implementing.
    *
    * @see java.io.Externalizable#readExternal(java.io.ObjectInput)
    */
   public void readObject(ObjectInput in) throws IOException, ClassNotFoundException {
      Properties props = (Properties)in.readObject();
      if (props != null) {
         setProperties(props);
      }
   }

   /**
    * Method for class's implementing externalizable to delegate to if not custom implementing.
    *
    * @see java.io.Externalizable#writeExternal(java.io.ObjectOutput)
    */
   public void writeObject(ObjectOutput out) throws IOException {
      out.writeObject(getProperties());
   }

}