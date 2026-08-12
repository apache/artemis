package ackManager

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

import org.apache.activemq.artemis.api.core.ActiveMQBuffers
import org.apache.activemq.artemis.core.persistence.impl.journal.codec.AckRetry
import org.apache.activemq.artemis.core.journal.collections.JournalHashMap
import org.apache.activemq.artemis.core.server.impl.AckReason
import org.apache.activemq.artemis.tests.compatibility.GroovyRun

import java.nio.file.Files
import java.nio.file.Paths

file = arg[0]
method = arg[1]

if (method.equals("write")) {
   def persister = AckRetry.getPersister()
   def buffer = ActiveMQBuffers.dynamicBuffer(1024)

   buffer.writeInt(3)

   def ack1 = new AckRetry("test-node-id-1", 12345L, AckReason.NORMAL)
   def rec1 = new JournalHashMap.MapRecord(100L, 1L, ack1, ack1)
   persister.encode(buffer, rec1)

   def ack2 = new AckRetry("test-node-id-2", 67890L, AckReason.EXPIRED)
   def rec2 = new JournalHashMap.MapRecord(200L, 2L, ack2, ack2)
   persister.encode(buffer, rec2)

   def ack3 = new AckRetry(null, 99999L, AckReason.NORMAL)
   def rec3 = new JournalHashMap.MapRecord(300L, 3L, ack3, ack3)
   persister.encode(buffer, rec3)

   byte[] bytes = new byte[buffer.readableBytes()]
   buffer.readBytes(bytes)

   Files.write(Paths.get(file), bytes)
} else {
   byte[] bytes = Files.readAllBytes(Paths.get(file))

   def buffer = ActiveMQBuffers.wrappedBuffer(bytes)

   int count = buffer.readInt()
   GroovyRun.assertEquals(3, count)

   def persister = AckRetry.getPersister()

   def rec1 = persister.decode(buffer, null, null)
   GroovyRun.assertEquals("test-node-id-1", rec1.key.getNodeID())
   GroovyRun.assertEquals(12345L, rec1.key.getMessageID())
   GroovyRun.assertEquals(1L, rec1.id)

   def rec2 = persister.decode(buffer, null, null)
   GroovyRun.assertEquals("test-node-id-2", rec2.key.getNodeID())
   GroovyRun.assertEquals(67890L, rec2.key.getMessageID())
   GroovyRun.assertEquals(2L, rec2.id)

   def rec3 = persister.decode(buffer, null, null)
   GroovyRun.assertNull(rec3.key.getNodeID())
   GroovyRun.assertEquals(99999L, rec3.key.getMessageID())
   GroovyRun.assertEquals(3L, rec3.id)
}
