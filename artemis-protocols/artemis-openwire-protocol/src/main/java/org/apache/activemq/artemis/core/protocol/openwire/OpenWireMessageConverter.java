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
package org.apache.activemq.artemis.core.protocol.openwire;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.InflaterOutputStream;

import org.apache.activemq.ScheduledMessage;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQPropertyConversionException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.protocol.openwire.amq.AMQConsumer;
import org.apache.activemq.artemis.core.protocol.openwire.util.OpenWireUtil;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.activemq.artemis.utils.ByteUtil;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.apache.activemq.command.ActiveMQBytesMessage;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQObjectMessage;
import org.apache.activemq.command.ActiveMQStreamMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.command.BrokerId;
import org.apache.activemq.command.CommandTypes;
import org.apache.activemq.command.DataStructure;
import org.apache.activemq.command.Message;
import org.apache.activemq.command.MessageDispatch;
import org.apache.activemq.command.MessageId;
import org.apache.activemq.command.ProducerId;
import org.apache.activemq.util.ByteArrayInputStream;
import org.apache.activemq.util.ByteSequence;
import org.apache.activemq.util.ByteSequenceData;
import org.apache.activemq.util.MarshallingSupport;
import org.apache.activemq.wireformat.WireFormat;
import org.fusesource.hawtbuf.UTF8Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.api.core.Message.HDR_INGRESS_TIMESTAMP;
import static org.apache.activemq.command.ActiveMQDestination.QUEUE_TYPE;

public final class OpenWireMessageConverter {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public OpenWireMessageConverter() {

   }

   public static org.apache.activemq.artemis.api.core.Message inbound(final Message messageSend,
                                                                      final WireFormat marshaller,
                                                                      final CoreMessageObjectPools coreMessageObjectPools) throws Exception {

      final CoreMessage coreMessage = new CoreMessage(-1, messageSend.getSize(), coreMessageObjectPools);

      final String type = messageSend.getType();
      if (type != null) {
         coreMessage.putStringProperty(OpenWireConstants.JMS_TYPE_PROPERTY, SimpleString.of(type));
      }
      coreMessage.setDurable(messageSend.isPersistent());
      coreMessage.setExpiration(messageSend.getExpiration());
      coreMessage.setPriority(messageSend.getPriority());
      coreMessage.setTimestamp(messageSend.getTimestamp());

      final byte coreType = toCoreType(messageSend.getDataStructureType());
      coreMessage.setType(coreType);

      final ActiveMQBuffer body = coreMessage.getBodyBuffer();

      final ByteSequence contents = messageSend.getContent();
      if (contents == null) {
         if (coreType == org.apache.activemq.artemis.api.core.Message.TEXT_TYPE) {
            body.writeNullableString(null);
         } else if (coreType == org.apache.activemq.artemis.api.core.Message.MAP_TYPE) {
            body.writeByte(DataConstants.NULL);
         }
      } else {
         final boolean messageCompressed = messageSend.isCompressed();
         if (messageCompressed) {
            coreMessage.putBooleanProperty(OpenWireConstants.AMQ_MSG_COMPRESSED, true);
         }

         switch (coreType) {
            case org.apache.activemq.artemis.api.core.Message.TEXT_TYPE:
               writeTextType(contents, messageCompressed, body);
               break;
            case org.apache.activemq.artemis.api.core.Message.MAP_TYPE:
               writeMapType(contents, messageCompressed, body);
               break;
            case org.apache.activemq.artemis.api.core.Message.OBJECT_TYPE:
               writeObjectType(contents, messageCompressed, body);
               break;
            case org.apache.activemq.artemis.api.core.Message.STREAM_TYPE:
               writeStreamType(contents, messageCompressed, body);
               break;
            case org.apache.activemq.artemis.api.core.Message.BYTES_TYPE:
               writeBytesType(contents, messageCompressed, body);
               break;
            default:
               writeDefaultType(contents, messageCompressed, body);
               break;
         }
      }
      //amq specific
      coreMessage.putLongProperty(OpenWireConstants.AMQ_MSG_ARRIVAL, messageSend.getArrival());
      coreMessage.putLongProperty(OpenWireConstants.AMQ_MSG_BROKER_IN_TIME, messageSend.getBrokerInTime());
      final BrokerId[] brokers = messageSend.getBrokerPath();
      if (brokers != null) {
         putMsgBrokerPath(brokers, coreMessage);
      }
      final BrokerId[] cluster = messageSend.getCluster();
      if (cluster != null) {
         putMsgCluster(cluster, coreMessage);
      }

      coreMessage.putIntProperty(OpenWireConstants.AMQ_MSG_COMMAND_ID, messageSend.getCommandId());
      final String corrId = messageSend.getCorrelationId();
      if (corrId != null) {
         coreMessage.setCorrelationID(corrId);
      }
      final DataStructure ds = messageSend.getDataStructure();
      if (ds != null) {
         putMsgDataStructure(ds, marshaller, coreMessage);
      }
      final String groupId = messageSend.getGroupID();
      if (groupId != null) {
         coreMessage.setGroupID(groupId);
      }
      coreMessage.setGroupSequence(messageSend.getGroupSequence());

      final MessageId messageId = messageSend.getMessageId();
      if (messageId != null) {
         coreMessage.putStringProperty(OpenWireConstants.AMQ_MSG_MESSAGE_ID, SimpleString.of(messageId.toString()));
      }

      coreMessage.setUserID(UUIDGenerator.getInstance().generateUUID());

      final ProducerId producerId = messageSend.getProducerId();
      if (producerId != null) {
         coreMessage.putStringProperty(OpenWireConstants.AMQ_MSG_PRODUCER_ID, SimpleString.of(producerId.toString()));
      }

      putMsgProperties(messageSend, coreMessage);

      final ActiveMQDestination replyTo = messageSend.getReplyTo();
      if (replyTo != null) {
         if (replyTo instanceof TemporaryQueue temporaryQueue) {
            MessageUtil.setJMSReplyTo(coreMessage, org.apache.activemq.artemis.jms.client.ActiveMQDestination.TEMP_QUEUE_QUALIFED_PREFIX + (temporaryQueue.getQueueName()));
         } else if (replyTo instanceof TemporaryTopic temporaryTopic) {
            MessageUtil.setJMSReplyTo(coreMessage, org.apache.activemq.artemis.jms.client.ActiveMQDestination.TEMP_TOPIC_QUALIFED_PREFIX + (temporaryTopic.getTopicName()));
         } else if (replyTo instanceof Queue queue) {
            MessageUtil.setJMSReplyTo(coreMessage, org.apache.activemq.artemis.jms.client.ActiveMQDestination.QUEUE_QUALIFIED_PREFIX + (queue.getQueueName()));
         } else if (replyTo instanceof Topic topic) {
            MessageUtil.setJMSReplyTo(coreMessage, org.apache.activemq.artemis.jms.client.ActiveMQDestination.TOPIC_QUALIFIED_PREFIX + (topic.getTopicName()));
         } else {
            // it should not happen
            MessageUtil.setJMSReplyTo(coreMessage, org.apache.activemq.artemis.jms.client.ActiveMQDestination.QUEUE_QUALIFIED_PREFIX + (((Queue) replyTo).getQueueName()));
         }
      }

      final String userId = messageSend.getUserID();
      if (userId != null) {
         coreMessage.putStringProperty(OpenWireConstants.AMQ_MSG_USER_ID, SimpleString.of(userId));
      }
      coreMessage.putBooleanProperty(OpenWireConstants.AMQ_MSG_DROPPABLE, messageSend.isDroppable());

      final ActiveMQDestination origDest = messageSend.getOriginalDestination();
      if (origDest != null) {
         coreMessage.putStringProperty(OpenWireConstants.AMQ_MSG_ORIG_DESTINATION, origDest.getQualifiedName());
      }

      final Object scheduledDelay = messageSend.getProperties().get(ScheduledMessage.AMQ_SCHEDULED_DELAY);
      if (scheduledDelay instanceof Long scheduledDelayMillis) {
         coreMessage.putLongProperty(org.apache.activemq.artemis.api.core.Message.HDR_SCHEDULED_DELIVERY_TIME, System.currentTimeMillis() + scheduledDelayMillis);
         // this property may have already been copied, but we don't need it anymore
         coreMessage.removeProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY);
      }

      return coreMessage;
   }

   private static void writeTextType(final ByteSequence contents,
                                     final boolean messageCompressed,
                                     final ActiveMQBuffer body) throws IOException {
      InputStream tis = new ByteArrayInputStream(contents);
      if (messageCompressed) {
         tis = MarshallingSupport.createInflaterInputStream(Integer.MAX_VALUE, tis);
      }
      DataInputStream tdataIn = new DataInputStream(tis);
      String text = MarshallingSupport.readUTF8(tdataIn);
      tdataIn.close();
      body.writeNullableSimpleString(SimpleString.of(text));
   }

   private static void writeMapType(final ByteSequence contents,
                                    final boolean messageCompressed,
                                    final ActiveMQBuffer body) throws IOException {
      InputStream mis = new ByteArrayInputStream(contents);
      if (messageCompressed) {
         mis = MarshallingSupport.createInflaterInputStream(Integer.MAX_VALUE, mis);
      }
      DataInputStream mdataIn = new DataInputStream(mis);
      Map<String, Object> map = MarshallingSupport.unmarshalPrimitiveMap(mdataIn);
      mdataIn.close();
      TypedProperties props = new TypedProperties();
      loadMapIntoProperties(props, map);
      props.encode(body.byteBuf());
   }

   private static void writeObjectType(ByteSequence contents,
                                       final boolean messageCompressed,
                                       final ActiveMQBuffer body) throws IOException {
      if (messageCompressed) {
         contents = writeCompressedObjectType(contents);
      }
      body.writeInt(contents.length);
      body.writeBytes(contents.data, contents.offset, contents.length);
   }

   private static ByteSequence writeCompressedObjectType(final ByteSequence contents) throws IOException {
      try (InputStream ois = new InflaterInputStream(new ByteArrayInputStream(contents));
           org.apache.activemq.util.ByteArrayOutputStream decompressed = new org.apache.activemq.util.ByteArrayOutputStream()) {
         byte[] buf = new byte[1024];
         int n = ois.read(buf);
         while (n != -1) {
            decompressed.write(buf, 0, n);
            n = ois.read(buf);
         }
         //read done
         return decompressed.toByteSequence();
      }
   }

   private static void writeStreamType(final ByteSequence contents,
                                       final boolean messageCompressed,
                                       final ActiveMQBuffer body) throws IOException {
      InputStream sis = new ByteArrayInputStream(contents);
      if (messageCompressed) {
         sis = MarshallingSupport.createInflaterInputStream(Integer.MAX_VALUE, sis);
      }
      DataInputStream sdis = new DataInputStream(sis);
      int stype = sdis.read();
      while (stype != -1) {
         switch (stype) {
            case MarshallingSupport.BOOLEAN_TYPE:
               body.writeByte(DataConstants.BOOLEAN);
               body.writeBoolean(sdis.readBoolean());
               break;
            case MarshallingSupport.BYTE_TYPE:
               body.writeByte(DataConstants.BYTE);
               body.writeByte(sdis.readByte());
               break;
            case MarshallingSupport.BYTE_ARRAY_TYPE:
               body.writeByte(DataConstants.BYTES);
               int slen = sdis.readInt();
               byte[] sbytes = new byte[slen];
               sdis.read(sbytes);
               body.writeInt(slen);
               body.writeBytes(sbytes);
               break;
            case MarshallingSupport.CHAR_TYPE:
               body.writeByte(DataConstants.CHAR);
               char schar = sdis.readChar();
               body.writeShort((short) schar);
               break;
            case MarshallingSupport.DOUBLE_TYPE:
               body.writeByte(DataConstants.DOUBLE);
               double sdouble = sdis.readDouble();
               body.writeLong(Double.doubleToLongBits(sdouble));
               break;
            case MarshallingSupport.FLOAT_TYPE:
               body.writeByte(DataConstants.FLOAT);
               float sfloat = sdis.readFloat();
               body.writeInt(Float.floatToIntBits(sfloat));
               break;
            case MarshallingSupport.INTEGER_TYPE:
               body.writeByte(DataConstants.INT);
               body.writeInt(sdis.readInt());
               break;
            case MarshallingSupport.LONG_TYPE:
               body.writeByte(DataConstants.LONG);
               body.writeLong(sdis.readLong());
               break;
            case MarshallingSupport.SHORT_TYPE:
               body.writeByte(DataConstants.SHORT);
               body.writeShort(sdis.readShort());
               break;
            case MarshallingSupport.STRING_TYPE:
               body.writeByte(DataConstants.STRING);
               String sstring = sdis.readUTF();
               body.writeNullableString(sstring);
               break;
            case MarshallingSupport.BIG_STRING_TYPE:
               body.writeByte(DataConstants.STRING);
               String sbigString = MarshallingSupport.readUTF8(sdis);
               body.writeNullableString(sbigString);
               break;
            case MarshallingSupport.NULL:
               body.writeByte(DataConstants.STRING);
               body.writeNullableString(null);
               break;
            default:
               //something we don't know, ignore
               break;
         }
         stype = sdis.read();
      }
      sdis.close();
   }

   private static void writeBytesType(ByteSequence contents,
                                      final boolean messageCompressed,
                                      final ActiveMQBuffer body) throws IOException {
      if (messageCompressed) {
         contents = writeCompressedBytesType(contents);
      }
      body.writeBytes(contents.data, contents.offset, contents.length);
   }

   private static ByteSequence writeCompressedBytesType(final ByteSequence contents) throws IOException {
      Inflater inflater = new Inflater();
      try (org.apache.activemq.util.ByteArrayOutputStream decompressed = new org.apache.activemq.util.ByteArrayOutputStream()) {
         int length = ByteSequenceData.readIntBig(contents);
         contents.offset = 0;
         byte[] data = Arrays.copyOfRange(contents.getData(), 4, contents.getLength());

         inflater.setInput(data);
         byte[] buffer = new byte[length];
         int count = inflater.inflate(buffer);
         decompressed.write(buffer, 0, count);
         return decompressed.toByteSequence();
      } catch (Exception e) {
         throw new IOException(e);
      } finally {
         inflater.end();
      }
   }

   private static void writeDefaultType(ByteSequence contents,
                                        final boolean messageCompressed,
                                        final ActiveMQBuffer body) throws IOException {
      if (messageCompressed) {
         contents = writeCompressedDefaultType(contents);
      }
      body.writeBytes(contents.data, contents.offset, contents.length);
   }

   private static ByteSequence writeCompressedDefaultType(final ByteSequence contents) throws IOException {
      try (org.apache.activemq.util.ByteArrayOutputStream decompressed = new org.apache.activemq.util.ByteArrayOutputStream();
           OutputStream os = new InflaterOutputStream(decompressed)) {
         os.write(contents.data, contents.offset, contents.getLength());
         return decompressed.toByteSequence();
      } catch (Exception e) {
         throw new IOException(e);
      }
   }

   private static void putMsgBrokerPath(final BrokerId[] brokers, final CoreMessage coreMessage) {
      final StringBuilder builder = new StringBuilder();
      for (int i = 0, size = brokers.length; i < size; i++) {
         builder.append(brokers[i].getValue());
         if (i != (size - 1)) {
            builder.append(','); //is this separator safe?
         }
      }
      coreMessage.putStringProperty(OpenWireConstants.AMQ_MSG_BROKER_PATH, SimpleString.of(builder.toString()));
   }

   private static void putMsgCluster(final BrokerId[] cluster, final CoreMessage coreMessage) {
      final StringBuilder builder = new StringBuilder();
      for (int i = 0, size = cluster.length; i < size; i++) {
         builder.append(cluster[i].getValue());
         if (i != (size - 1)) {
            builder.append(','); //is this separator safe?
         }
      }
      coreMessage.putStringProperty(OpenWireConstants.AMQ_MSG_CLUSTER, SimpleString.of(builder.toString()));
   }

   private static void putMsgDataStructure(final DataStructure ds,
                                           final WireFormat marshaller,
                                           final CoreMessage coreMessage) throws IOException {
      final ByteSequence dsBytes = marshaller.marshal(ds);
      dsBytes.compact();
      coreMessage.putBytesProperty(OpenWireConstants.AMQ_MSG_DATASTRUCTURE, dsBytes.data);
   }

   private static void putMsgProperties(final Message messageSend,
                                        final CoreMessage coreMessage) throws IOException {
      final Map<String, Object> props = messageSend.getProperties();
      if (!props.isEmpty()) {
         props.forEach((key, value) -> {
            try {
               if (value instanceof UTF8Buffer) {
                  coreMessage.putObjectProperty(key, value.toString());
               } else {
                  coreMessage.putObjectProperty(key, value);
               }
            } catch (ActiveMQPropertyConversionException e) {
               coreMessage.putStringProperty(key, value.toString());
            }
         });
      }
   }

   private static void loadMapIntoProperties(TypedProperties props, Map<String, Object> map) {
      for (Entry<String, Object> entry : map.entrySet()) {
         SimpleString key = SimpleString.of(entry.getKey());
         Object value = entry.getValue();
         if (value instanceof UTF8Buffer) {
            value = value.toString();
         }
         TypedProperties.setObjectProperty(key, value, props);
      }
   }

   public static byte toCoreType(byte amqType) {
      return switch (amqType) {
         case CommandTypes.ACTIVEMQ_BLOB_MESSAGE -> throw new IllegalStateException("We don't support BLOB type yet!");
         case CommandTypes.ACTIVEMQ_BYTES_MESSAGE -> org.apache.activemq.artemis.api.core.Message.BYTES_TYPE;
         case CommandTypes.ACTIVEMQ_MAP_MESSAGE -> org.apache.activemq.artemis.api.core.Message.MAP_TYPE;
         case CommandTypes.ACTIVEMQ_OBJECT_MESSAGE -> org.apache.activemq.artemis.api.core.Message.OBJECT_TYPE;
         case CommandTypes.ACTIVEMQ_STREAM_MESSAGE -> org.apache.activemq.artemis.api.core.Message.STREAM_TYPE;
         case CommandTypes.ACTIVEMQ_TEXT_MESSAGE -> org.apache.activemq.artemis.api.core.Message.TEXT_TYPE;
         case CommandTypes.ACTIVEMQ_MESSAGE -> org.apache.activemq.artemis.api.core.Message.DEFAULT_TYPE;
         default -> throw new IllegalStateException("Unknown ActiveMQ Artemis message type: " + amqType);
      };
   }

   public static MessageDispatch createMessageDispatch(MessageReference reference,
                                                       ICoreMessage message,
                                                       WireFormat marshaller,
                                                       AMQConsumer consumer,
                                                       UUID serverNodeUUID,
                                                       long consumerDeliverySequenceId) throws IOException {
      ActiveMQMessage amqMessage = toAMQMessage(reference, message, marshaller, consumer, serverNodeUUID);

      amqMessage.getMessageId().setBrokerSequenceId(consumerDeliverySequenceId);
      MessageDispatch md = new MessageDispatch();
      md.setConsumerId(consumer.getId());
      md.setRedeliveryCounter(reference.getDeliveryCount() - 1);
      md.setDeliverySequenceId(consumerDeliverySequenceId);
      md.setMessage(amqMessage);
      ActiveMQDestination destination = amqMessage.getDestination();
      md.setDestination(destination);

      return md;
   }

   private static final class EagerActiveMQBytesMessage extends ActiveMQBytesMessage {

      EagerActiveMQBytesMessage(int size) {
         this.bytesOut = new org.apache.activemq.util.ByteArrayOutputStream(size);
         OutputStream os = bytesOut;
         this.dataOut = new DataOutputStream(os);
      }
   }

   private static ActiveMQMessage toAMQMessage(MessageReference reference,
                                               ICoreMessage coreMessage,
                                               WireFormat marshaller,
                                               AMQConsumer consumer, UUID serverNodeUUID) throws IOException {
      final ActiveMQMessage amqMsg;
      final byte coreType = coreMessage.getType();
      final Boolean compressProp = getObjectProperty(coreMessage, Boolean.class, OpenWireConstants.AMQ_MSG_COMPRESSED);
      final boolean isCompressed = compressProp != null && compressProp;
      final ActiveMQBuffer buffer = coreMessage.getDataBuffer();
      buffer.resetReaderIndex();

      final byte[] bytes = switch (coreType) {
         case org.apache.activemq.artemis.api.core.Message.BYTES_TYPE -> {
            amqMsg = new EagerActiveMQBytesMessage(0);
            yield toAMQMessageBytesType(buffer, isCompressed);
         }
         case org.apache.activemq.artemis.api.core.Message.MAP_TYPE -> {
            amqMsg = new ActiveMQMapMessage();
            yield toAMQMessageMapType(buffer, isCompressed);
         }
         case org.apache.activemq.artemis.api.core.Message.OBJECT_TYPE -> {
            amqMsg = new ActiveMQObjectMessage();
            yield toAMQMessageObjectType(buffer, isCompressed);
         }
         case org.apache.activemq.artemis.api.core.Message.STREAM_TYPE -> {
            amqMsg = new ActiveMQStreamMessage();
            yield toAMQMessageStreamType(buffer, isCompressed);
         }
         case org.apache.activemq.artemis.api.core.Message.TEXT_TYPE -> {
            amqMsg = new ActiveMQTextMessage();
            yield toAMQMessageTextType(buffer, isCompressed);
         }
         case org.apache.activemq.artemis.api.core.Message.DEFAULT_TYPE -> {
            amqMsg = new ActiveMQMessage();
            yield toAMQMessageDefaultType(buffer, isCompressed);
         }
         default -> throw new IllegalStateException("Unknown message type: " + coreMessage.getType());
      };

      final String type = getObjectProperty(coreMessage, String.class, OpenWireConstants.JMS_TYPE_PROPERTY);
      if (type != null) {
         amqMsg.setJMSType(type);
      }
      amqMsg.setPersistent(coreMessage.isDurable());
      amqMsg.setExpiration(coreMessage.getExpiration());
      amqMsg.setPriority(coreMessage.getPriority());
      amqMsg.setTimestamp(coreMessage.getTimestamp());

      Long brokerInTime = getObjectProperty(coreMessage, Long.class, OpenWireConstants.AMQ_MSG_BROKER_IN_TIME);
      if (brokerInTime == null) {
         brokerInTime = 0L;
      }
      amqMsg.setBrokerInTime(brokerInTime);

      amqMsg.setCompressed(isCompressed);

      //we need check null because messages may come from other clients
      //and those amq specific attribute may not be set.
      Long arrival = getObjectProperty(coreMessage, Long.class, OpenWireConstants.AMQ_MSG_ARRIVAL);
      if (arrival == null) {
         //messages from other sources (like core client) may not set this prop
         arrival = 0L;
      }
      amqMsg.setArrival(arrival);

      final SimpleString brokerPath = getObjectProperty(coreMessage, SimpleString.class, OpenWireConstants.AMQ_MSG_BROKER_PATH);
      if (brokerPath != null && !brokerPath.isEmpty()) {
         setAMQMsgBrokerPath(amqMsg, brokerPath.toString());
      }

      final SimpleString clusterPath = getObjectProperty(coreMessage, SimpleString.class, OpenWireConstants.AMQ_MSG_CLUSTER);
      if (clusterPath != null && !clusterPath.isEmpty()) {
         setAMQMsgClusterPath(amqMsg, clusterPath.toString());
      }

      Integer commandId = getObjectProperty(coreMessage, Integer.class, OpenWireConstants.AMQ_MSG_COMMAND_ID);
      if (commandId == null) {
         commandId = -1;
      }
      amqMsg.setCommandId(commandId);

      final Object correlationID = coreMessage.getCorrelationID();
      if (correlationID instanceof String || correlationID instanceof SimpleString) {
         amqMsg.setCorrelationId(correlationID.toString());
      } else if (correlationID instanceof byte[] correlationIdBytes) {
         try {
            amqMsg.setCorrelationId(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(correlationIdBytes)).toString());
         } catch (MalformedInputException e) {
            ActiveMQServerLogger.LOGGER.unableToDecodeCorrelationId(e.getMessage());
         }
      }

      final byte[] dsBytes = getObjectProperty(coreMessage, byte[].class, OpenWireConstants.AMQ_MSG_DATASTRUCTURE);
      if (dsBytes != null) {
         setAMQMsgDataStructure(amqMsg, marshaller, dsBytes);
      }
      final ActiveMQDestination actualDestination = consumer.getOpenwireDestination();
      amqMsg.setDestination(OpenWireUtil.toAMQAddress(coreMessage, actualDestination));

      final Object value = coreMessage.getGroupID();
      if (value != null) {
         String groupId = value.toString();
         amqMsg.setGroupID(groupId);
      }

      amqMsg.setGroupSequence(coreMessage.getGroupSequence());

      final Object messageIdValue = getObjectProperty(coreMessage, Object.class, OpenWireConstants.AMQ_MSG_MESSAGE_ID);
      final MessageId messageId;
      if (messageIdValue instanceof SimpleString) {
         messageId = new MessageId(messageIdValue.toString());
      } else if (messageIdValue instanceof byte[] messageIdBytes) {
         ByteSequence midSeq = new ByteSequence(messageIdBytes);
         messageId = (MessageId) marshaller.unmarshal(midSeq);
      } else {
         //  ARTEMIS-3776 due to AMQ-6431 some older clients will not be able to receive messages
         // if using a failover schema due to the messageID overFlowing Integer.MAX_VALUE
         String midd = "ID:" + serverNodeUUID + ":-1:-1:" + (coreMessage.getMessageID() / Integer.MAX_VALUE);
         messageId = new MessageId(midd, coreMessage.getMessageID() % Integer.MAX_VALUE);
      }

      amqMsg.setMessageId(messageId);

      final Object origDestValue = getObjectProperty(coreMessage, Object.class, OpenWireConstants.AMQ_MSG_ORIG_DESTINATION);
      if (origDestValue instanceof SimpleString) {
         amqMsg.setOriginalDestination(ActiveMQDestination.createDestination(origDestValue.toString(), QUEUE_TYPE));
      } else if (origDestValue instanceof byte[] origDestValueBytes) {
         ActiveMQDestination origDest = (ActiveMQDestination) marshaller.unmarshal(new ByteSequence(origDestValueBytes));
         amqMsg.setOriginalDestination(origDest);
      }

      final Object producerIdValue = getObjectProperty(coreMessage, Object.class, OpenWireConstants.AMQ_MSG_PRODUCER_ID);
      if (producerIdValue instanceof SimpleString simpleString && !simpleString.isEmpty()) {
         amqMsg.setProducerId(new ProducerId(producerIdValue.toString()));
      } else if (producerIdValue instanceof byte[] producerIdBytes) {
         ProducerId producerId = (ProducerId) marshaller.unmarshal(new ByteSequence(producerIdBytes));
         amqMsg.setProducerId(producerId);
      }

      amqMsg.setRedeliveryCounter(reference.getDeliveryCount() - 1);

      final Object replyToValue = getObjectProperty(coreMessage, Object.class, OpenWireConstants.AMQ_MSG_REPLY_TO);
      if (replyToValue instanceof SimpleString) {
         amqMsg.setReplyTo(ActiveMQDestination.createDestination(replyToValue.toString(), QUEUE_TYPE));
      } else if (replyToValue instanceof byte[] replyToBytes) {
         ActiveMQDestination replyTo = (ActiveMQDestination) marshaller.unmarshal(new ByteSequence(replyToBytes));
         amqMsg.setReplyTo(replyTo);
      }

      final SimpleString userId = getObjectProperty(coreMessage, SimpleString.class, OpenWireConstants.AMQ_MSG_USER_ID);
      if (userId != null && !userId.isEmpty()) {
         amqMsg.setUserID(userId.toString());
      }

      final Boolean isDroppable = getObjectProperty(coreMessage, Boolean.class, OpenWireConstants.AMQ_MSG_DROPPABLE);
      if (isDroppable != null) {
         amqMsg.setDroppable(isDroppable);
      }

      final SimpleString dlqCause = getObjectProperty(coreMessage, SimpleString.class, OpenWireConstants.AMQ_MSG_DLQ_DELIVERY_FAILURE_CAUSE_PROPERTY);
      if (dlqCause != null) {
         setAMQMsgDlqDeliveryFailureCause(amqMsg, dlqCause);
      }

      final SimpleString lastValueProperty = coreMessage.getLastValueProperty();
      if (lastValueProperty != null) {
         setAMQMsgHdrLastValueName(amqMsg, lastValueProperty);
      }

      final Long ingressTimestamp = getObjectProperty(coreMessage, Long.class, HDR_INGRESS_TIMESTAMP);
      if (ingressTimestamp != null) {
         setAMQMsgHdrIngressTimestamp(amqMsg, ingressTimestamp);
      }

      final Set<SimpleString> props = coreMessage.getPropertyNames();
      if (props != null) {
         setAMQMsgObjectProperties(amqMsg, coreMessage, props);
      }

      if (bytes != null) {
         ByteSequence content = new ByteSequence(bytes);
         amqMsg.setContent(content);
      }
      return amqMsg;
   }

   private static <T> T getObjectProperty(ICoreMessage message, Class<T> type, SimpleString property) {
      if (message.getPropertyNames().contains(property)) {
         try {
            Object value = message.getObjectProperty(property);
            if (type == String.class && value != null) {
               return (T)value.toString();
            } else {
               return type.cast(value);
            }
         } catch (ClassCastException e) {
            ActiveMQServerLogger.LOGGER.failedToDealWithObjectProperty(property, e.getMessage());
         }
      }
      return null;
   }

   private static byte[] toAMQMessageTextType(final ActiveMQBuffer buffer,
                                              final boolean isCompressed) throws IOException {
      byte[] bytes = null;
      SimpleString text = buffer.readNullableSimpleString();
      if (text != null) {
         ByteArrayOutputStream bytesOut = new ByteArrayOutputStream(text.length() + 4);
         OutputStream out = bytesOut;
         if (isCompressed) {
            out = new DeflaterOutputStream(out, true);
         }
         try (DataOutputStream dataOut = new DataOutputStream(out)) {
            MarshallingSupport.writeUTF8(dataOut, text.toString());
            dataOut.flush();
            bytes = bytesOut.toByteArray();
         }
      }
      return bytes;
   }

   private static byte[] toAMQMessageMapType(final ActiveMQBuffer buffer,
                                             final boolean isCompressed) throws IOException {
      byte[] bytes = null;
      //it could be a null map
      if (buffer.readableBytes() > 0) {
         TypedProperties mapData = new TypedProperties();
         mapData.decode(buffer.byteBuf());
         Map<String, Object> map = mapData.getMap();
         ByteArrayOutputStream out = new ByteArrayOutputStream(mapData.getEncodeSize());
         OutputStream os = out;
         if (isCompressed) {
            os = new DeflaterOutputStream(os, true);
         }
         try (DataOutputStream dataOut = new DataOutputStream(os)) {
            MarshallingSupport.marshalPrimitiveMap(map, dataOut);
            dataOut.flush();
         }
         bytes = out.toByteArray();
      }
      return bytes;
   }

   private static byte[] toAMQMessageObjectType(final ActiveMQBuffer buffer,
                                                final boolean isCompressed) throws IOException {
      byte[] bytes = null;
      if (buffer.readableBytes() > 0) {
         int len = buffer.readInt();
         bytes = new byte[len];
         buffer.readBytes(bytes);
         if (isCompressed) {
            ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
            try (DeflaterOutputStream out = new DeflaterOutputStream(bytesOut, true)) {
               out.write(bytes);
               out.flush();
            }
            bytes = bytesOut.toByteArray();
         }
      }
      return bytes;
   }

   private static byte[] toAMQMessageStreamType(final ActiveMQBuffer buffer,
                                                final boolean isCompressed) throws IOException {
      byte[] bytes;
      org.apache.activemq.util.ByteArrayOutputStream bytesOut = new org.apache.activemq.util.ByteArrayOutputStream();
      OutputStream out = bytesOut;
      if (isCompressed) {
         out = new DeflaterOutputStream(bytesOut, true);
      }
      try (DataOutputStream dataOut = new DataOutputStream(out)) {

         boolean stop = false;
         while (!stop && buffer.readable()) {
            byte primitiveType = buffer.readByte();
            switch (primitiveType) {
               case DataConstants.BOOLEAN:
                  MarshallingSupport.marshalBoolean(dataOut, buffer.readBoolean());
                  break;
               case DataConstants.BYTE:
                  MarshallingSupport.marshalByte(dataOut, buffer.readByte());
                  break;
               case DataConstants.BYTES:
                  int len = buffer.readInt();
                  byte[] bytesData = new byte[len];
                  buffer.readBytes(bytesData);
                  MarshallingSupport.marshalByteArray(dataOut, bytesData);
                  break;
               case DataConstants.CHAR:
                  char ch = (char) buffer.readShort();
                  MarshallingSupport.marshalChar(dataOut, ch);
                  break;
               case DataConstants.DOUBLE:
                  double doubleVal = Double.longBitsToDouble(buffer.readLong());
                  MarshallingSupport.marshalDouble(dataOut, doubleVal);
                  break;
               case DataConstants.FLOAT:
                  float floatVal = Float.intBitsToFloat(buffer.readInt());
                  MarshallingSupport.marshalFloat(dataOut, floatVal);
                  break;
               case DataConstants.INT:
                  MarshallingSupport.marshalInt(dataOut, buffer.readInt());
                  break;
               case DataConstants.LONG:
                  MarshallingSupport.marshalLong(dataOut, buffer.readLong());
                  break;
               case DataConstants.SHORT:
                  MarshallingSupport.marshalShort(dataOut, buffer.readShort());
                  break;
               case DataConstants.STRING:
                  String string = buffer.readNullableString();
                  if (string == null) {
                     MarshallingSupport.marshalNull(dataOut);
                  } else {
                     MarshallingSupport.marshalString(dataOut, string);
                  }
                  break;
               default:
                  //now we stop
                  stop = true;
                  break;
            }
            dataOut.flush();
         }
      }
      bytes = bytesOut.toByteArray();
      return bytes;
   }

   private static byte[] toAMQMessageBytesType(final ActiveMQBuffer buffer,
                                               final boolean isCompressed) throws IOException {
      int n = buffer.readableBytes();
      byte[] bytes = new byte[n];
      buffer.readBytes(bytes);
      if (isCompressed) {
         bytes = toAMQMessageCompressedBytesType(bytes);
      }
      return bytes;
   }

   private static byte[] toAMQMessageCompressedBytesType(final byte[] bytes) throws IOException {
      int length = bytes.length;
      Deflater deflater = new Deflater();
      try (org.apache.activemq.util.ByteArrayOutputStream compressed = new org.apache.activemq.util.ByteArrayOutputStream()) {
         compressed.write(new byte[4]);
         deflater.setInput(bytes);
         deflater.finish();
         byte[] bytesBuf = new byte[1024];
         while (!deflater.finished()) {
            int count = deflater.deflate(bytesBuf);
            compressed.write(bytesBuf, 0, count);
         }
         compressed.flush();
         ByteSequence byteSeq = compressed.toByteSequence();
         ByteSequenceData.writeIntBig(byteSeq, length);
         return Arrays.copyOfRange(byteSeq.data, 0, byteSeq.length);
      } finally {
         deflater.end();
      }
   }

   private static byte[] toAMQMessageDefaultType(final ActiveMQBuffer buffer,
                                                 final boolean isCompressed) throws IOException {
      int n = buffer.readableBytes();
      byte[] bytes = new byte[n];
      buffer.readBytes(bytes);
      if (isCompressed) {
         try (ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
              DeflaterOutputStream out = new DeflaterOutputStream(bytesOut, true)) {
            out.write(bytes);
            out.flush();
            bytes = bytesOut.toByteArray();
         }
      }
      return bytes;
   }

   private static void setAMQMsgBrokerPath(final ActiveMQMessage amqMsg, final String brokerPath) {
      String[] brokers = brokerPath.split(",");
      BrokerId[] bids = new BrokerId[brokers.length];
      for (int i = 0; i < bids.length; i++) {
         bids[i] = new BrokerId(brokers[i]);
      }
      amqMsg.setBrokerPath(bids);
   }

   private static void setAMQMsgClusterPath(final ActiveMQMessage amqMsg, final String clusterPath) {
      String[] cluster = clusterPath.split(",");
      BrokerId[] bids = new BrokerId[cluster.length];
      for (int i = 0; i < bids.length; i++) {
         bids[i] = new BrokerId(cluster[i]);
      }
      amqMsg.setCluster(bids);
   }

   private static void setAMQMsgDataStructure(final ActiveMQMessage amqMsg,
                                              final WireFormat marshaller,
                                              final byte[] dsBytes) throws IOException {
      ByteSequence seq = new ByteSequence(dsBytes);
      DataStructure ds = (DataStructure) marshaller.unmarshal(seq);
      amqMsg.setDataStructure(ds);
   }

   private static void setAMQMsgDlqDeliveryFailureCause(final ActiveMQMessage amqMsg,
                                                        final SimpleString dlqCause) throws IOException {
      try {
         amqMsg.setStringProperty(ActiveMQMessage.DLQ_DELIVERY_FAILURE_CAUSE_PROPERTY, dlqCause.toString());
      } catch (JMSException e) {
         throw new IOException("failure to set dlq property " + dlqCause, e);
      }
   }

   private static void setAMQMsgHdrLastValueName(final ActiveMQMessage amqMsg,
                                                 final SimpleString lastValueProperty) throws IOException {
      try {
         amqMsg.setStringProperty(org.apache.activemq.artemis.api.core.Message.HDR_LAST_VALUE_NAME.toString(), lastValueProperty.toString());
      } catch (JMSException e) {
         throw new IOException("failure to set lvq property " + lastValueProperty, e);
      }
   }

   private static void setAMQMsgHdrIngressTimestamp(final ActiveMQMessage amqMsg,
                                                    final Long ingressTimestamp) throws IOException {
      try {
         amqMsg.setLongProperty(HDR_INGRESS_TIMESTAMP.toString(), ingressTimestamp);
      } catch (JMSException e) {
         throw new IOException("failure to set ingress timestamp property " + ingressTimestamp, e);
      }
   }

   private static void setAMQMsgObjectProperties(final ActiveMQMessage amqMsg,
                                                 final ICoreMessage coreMessage,
                                                 final Set<SimpleString> props) throws IOException {
      for (SimpleString s : props) {
         final String keyStr = s.toString();
         if (keyStr.isEmpty()) {
            logger.debug("ignoring property with empty key name");
            continue;
         }
         if (!coreMessage.containsProperty(ManagementHelper.HDR_NOTIFICATION_TYPE) && (keyStr.startsWith("_AMQ") || keyStr.startsWith("__HDR_"))) {
            continue;
         } else if (s.equals(OpenWireConstants.JMS_CORRELATION_ID_PROPERTY)) {
            continue;
         }
         final Object prop = coreMessage.getObjectProperty(s);
         try {
            if (prop instanceof SimpleString) {
               amqMsg.setObjectProperty(keyStr, prop.toString());
            } else if (prop instanceof byte[] propBytes) {
               amqMsg.setObjectProperty(keyStr, ByteUtil.bytesToHex(propBytes));
            } else if (keyStr.equals(MessageUtil.JMSXDELIVERYCOUNT) && prop instanceof Long longValue) {
               amqMsg.setObjectProperty(keyStr, longValue.intValue());
            } else {
               amqMsg.setObjectProperty(keyStr, prop);
            }
         } catch (JMSException e) {
            ActiveMQServerLogger.LOGGER.failedToDealWithObjectProperty(s, e.getMessage());
         }
      }
   }
}
