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
package org.apache.activemq.artemis.jms.bridge;

/**
 * <h2>Quality of server (QoS) levels</h2>
 *
 * <h3>QOS_AT_MOST_ONCE</h3>
 * <p>
 * With this QoS mode messages will reach the destination from the source at most once. The messages are consumed from
 * the source and acknowledged before sending to the destination. Therefore there is a possibility that if failure
 * occurs between removing them from the source and them arriving at the destination they could be lost. Hence delivery
 * will occur at most once. This mode is available for both persistent and non persistent messages.
 *
 * <h3>QOS_DUPLICATES_OK</h3>
 * <p>
 * With this QoS mode, the messages are consumed from the source and then acknowledged after they have been successfully
 * sent to the destination. Therefore there is a possibility that if failure occurs after sending to the destination but
 * before acknowledging them, they could be sent again when the system recovers. I.e. the destination might receive
 * duplicates after a failure. This mode is available for both persistent and non persistent messages.
 *
 * <h3>QOS_ONCE_AND_ONLY_ONCE</h3>
 * <p>
 * This QoS mode ensures messages will reach the destination from the source once and only once. (Sometimes this mode is
 * known as "exactly once"). If both the source and the destination are on the same Apache Artemis server instance
 * then this can be achieved by sending and acknowledging the messages in the same local transaction. If the source and
 * destination are on different servers this is achieved by enlisting the sending and consuming sessions in a JTA
 * transaction. The JTA transaction is controlled by JBoss Transactions JTA implementation which is a fully recovering
 * transaction manager, thus providing a very high degree of durability. If JTA is required then both supplied
 * connection factories need to be XAConnectionFactory implementations. This mode is only available for persistent
 * messages. This is likely to be the slowest mode since it requires extra persistence for the transaction logging.
 * <p>
 * Note: For a specific application it may possible to provide once and only once semantics without using the
 * QOS_ONCE_AND_ONLY_ONCE QoS level. This can be done by using the QOS_DUPLICATES_OK mode and then checking for
 * duplicates at the destination and discarding them. Some JMS servers provide automatic duplicate message detection
 * functionality, or this may be possible to implement on the application level by maintaining a cache of received
 * message ids on disk and comparing received messages to them. The cache would only be valid for a certain period of
 * time so this approach is not as watertight as using QOS_ONCE_AND_ONLY_ONCE but may be a good choice depending on your
 * specific application.
 */
public enum QualityOfServiceMode {
   AT_MOST_ONCE(0), DUPLICATES_OK(1), ONCE_AND_ONLY_ONCE(2);

   private final int value;

   QualityOfServiceMode(final int value) {
      this.value = value;
   }

   public int intValue() {
      return value;
   }

   public static QualityOfServiceMode valueOf(final int value) {
      if (value == AT_MOST_ONCE.value) {
         return AT_MOST_ONCE;
      }
      if (value == DUPLICATES_OK.value) {
         return DUPLICATES_OK;
      }
      if (value == ONCE_AND_ONLY_ONCE.value) {
         return ONCE_AND_ONLY_ONCE;
      }
      throw new IllegalArgumentException("invalid QualityOfServiceMode value: " + value);
   }

}
