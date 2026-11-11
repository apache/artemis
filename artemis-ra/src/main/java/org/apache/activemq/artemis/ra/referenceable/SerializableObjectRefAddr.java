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
package org.apache.activemq.artemis.ra.referenceable;

import javax.naming.NamingException;
import javax.naming.RefAddr;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * A SerializableObjectRefAddr.
 * <p>
 * A RefAddr that can be used for any serializable object.
 * <p>
 * Basically the address is the serialized form of the object as a byte[]
 */
public class SerializableObjectRefAddr extends RefAddr {

   private static final long serialVersionUID = 9158134548376171898L;

   private final byte[] bytes;

   public SerializableObjectRefAddr(final String type, final Object content) throws NamingException {
      super(type);

      try {
         // Serialize the object
         ByteArrayOutputStream bos = new ByteArrayOutputStream();

         ObjectOutputStream oos = new ObjectOutputStream(bos);

         oos.writeObject(content);

         oos.flush();

         bytes = bos.toByteArray();
      } catch (IOException e) {
         NamingException ne = new NamingException("Failed to serialize object: " + content);
         ne.initCause(e);
         throw ne;
      }
   }

   @Override
   public Object getContent() {
      return bytes;
   }

   public static Object deserialize(final byte[] bytes) throws IOException, ClassNotFoundException {
      ByteArrayInputStream bis = new ByteArrayInputStream(bytes);

      ObjectInputStream ois = new ObjectInputStream(bis);

      return ois.readObject();
   }
}
