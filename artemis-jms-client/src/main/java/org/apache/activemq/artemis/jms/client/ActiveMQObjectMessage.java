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
package org.apache.activemq.artemis.jms.client;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import javax.jms.ObjectMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.utils.ObjectInputStreamWithClassLoader;

/**
 * ActiveMQ Artemis implementation of a JMS ObjectMessage.
 * <br>
 * Don't used ObjectMessage if you want good performance!
 * <p>
 * Serialization is slooooow!
 */
public class ActiveMQObjectMessage extends ActiveMQMessage implements ObjectMessage {


   public static final byte TYPE = Message.OBJECT_TYPE;


   // keep a snapshot of the Serializable Object as a byte[] to provide Object isolation
   private byte[] data;

   private final ConnectionFactoryOptions options;


   protected ActiveMQObjectMessage(final ClientSession session, ConnectionFactoryOptions options) {
      super(ActiveMQObjectMessage.TYPE, session);
      this.options = options;
   }

   protected ActiveMQObjectMessage(final ClientMessage message,
                                   final ClientSession session,
                                   ConnectionFactoryOptions options) {
      super(message, session);
      this.options = options;
   }

   /**
    * A copy constructor for foreign JMS ObjectMessages.
    */
   public ActiveMQObjectMessage(final ObjectMessage foreign,
                                final ClientSession session,
                                ConnectionFactoryOptions options) throws JMSException {
      super(foreign, ActiveMQObjectMessage.TYPE, session);

      setObject(foreign.getObject());
      this.options = options;
   }


   @Override
   public byte getType() {
      return ActiveMQObjectMessage.TYPE;
   }

   @Override
   public void doBeforeSend() throws Exception {
      message.getBodyBuffer().clear();
      if (data != null) {
         message.getBodyBuffer().writeInt(data.length);
         message.getBodyBuffer().writeBytes(data);
      }

      super.doBeforeSend();
   }

   @Override
   public void doBeforeReceive() throws ActiveMQException {
      super.doBeforeReceive();
      try {
         int len = message.getBodyBuffer().readInt();
         data = new byte[len];
         message.getBodyBuffer().readBytes(data);
      } catch (Exception e) {
         data = null;
      }

   }

   // ObjectMessage implementation ----------------------------------

   @Override
   public void setObject(final Serializable object) throws JMSException {
      checkWrite();

      if (object != null) {
         try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);

            ObjectOutputStream oos = new ObjectOutputStream(baos);

            oos.writeObject(object);

            oos.flush();

            data = baos.toByteArray();
         } catch (Exception e) {
            JMSException je = new JMSException("Failed to serialize object");
            je.setLinkedException(e);
            je.initCause(e);
            throw je;
         }
      }
   }

   // lazy deserialize the Object the first time the client requests it
   @Override
   public Serializable getObject() throws JMSException {
      if (data == null || data.length == 0) {
         return null;
      }

      try (ObjectInputStreamWithClassLoader ois = new ObjectInputStreamWithClassLoader(new ByteArrayInputStream(data))) {
         String blackList = getDeserializationBlackList();
         if (blackList != null) {
            ois.setBlackList(blackList);
         }
         String whiteList = getDeserializationWhiteList();
         if (whiteList != null) {
            ois.setWhiteList(whiteList);
         }
         Serializable object = (Serializable) ois.readObject();
         return object;
      } catch (Exception e) {
         JMSException je = new JMSException(e.getMessage());
         je.setStackTrace(e.getStackTrace());
         throw je;
      }
   }

   @Override
   public void clearBody() throws JMSException {
      super.clearBody();

      data = null;
   }

   @Override
   protected <T> T getBodyInternal(Class<T> c) throws MessageFormatException {
      try {
         return (T) getObject();
      } catch (JMSException e) {
         throw new MessageFormatException("Deserialization error on ActiveMQObjectMessage");
      }
   }

   @Override
   public boolean isBodyAssignableTo(Class c) {
      if (data == null) // we have no body
         return true;
      try {
         return Serializable.class == c || Object.class == c || c.isInstance(getObject());
      } catch (JMSException e) {
         return false;
      }
   }

   private String getDeserializationBlackList() {
      if (options == null) {
         return null;
      } else {
         return options.getDeserializationBlackList();
      }
   }

   private String getDeserializationWhiteList() {
      if (options == null) {
         return null;
      } else {
         return options.getDeserializationWhiteList();
      }
   }
}
