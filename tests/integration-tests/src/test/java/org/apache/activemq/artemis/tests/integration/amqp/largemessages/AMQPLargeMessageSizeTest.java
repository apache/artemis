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
package org.apache.activemq.artemis.tests.integration.amqp.largemessages;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.io.buffer.TimedBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeBody;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeServerMessageImpl;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPLargeMessage;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class AMQPLargeMessageSizeTest extends ActiveMQTestBase {

   @Test
   public void testAMQPMockLargeMessageSize() throws Exception {
      AMQPLargeMessage amqpLargeMessage = new AMQPLargeMessage(1, 0, null, null, null, new MockLargeBody(123456));
      assertTrue(amqpLargeMessage.getWholeMessageSize() >= 123456);
   }

   @Test
   public void testCoreMockLargeMessageSize() throws Exception {
      LargeServerMessageImpl largeServerMessage = new LargeServerMessageImpl((byte) 0, 1, new MockSM(123456), null);
      assertTrue(largeServerMessage.getWholeMessageSize() >= 123456);
   }

   private static class MockSM extends NullStorageManager {

      final long size;

      MockSM(long size) {
         this.size = size;
      }

      @Override
      public SequentialFile createFileForLargeMessage(long messageID, boolean durable) {
         return new MockFileSize(size);
      }

      @Override
      public SequentialFile createFileForLargeMessage(long messageID, LargeMessageExtension extension) {
         return new MockFileSize(size);
      }
   }

   private static class MockFileSize implements SequentialFile {

      final long size;

      MockFileSize(long size) {
         this.size = size;
      }

      @Override
      public boolean isOpen() {
         return false;
      }

      @Override
      public boolean exists() {
         return false;
      }

      @Override
      public void open() throws Exception {

      }

      @Override
      public void open(int maxIO, boolean useExecutor) throws Exception {

      }

      @Override
      public ByteBuffer map(int position, long size) throws IOException {
         return null;
      }

      @Override
      public boolean fits(int size) {
         return false;
      }

      @Override
      public int calculateBlockStart(int position) throws Exception {
         return 0;
      }

      @Override
      public String getFileName() {
         return null;
      }

      @Override
      public void fill(int size) throws Exception {

      }

      @Override
      public void delete() throws IOException, InterruptedException, ActiveMQException {

      }

      @Override
      public void write(ActiveMQBuffer bytes, boolean sync, IOCallback callback) throws Exception {

      }

      @Override
      public void write(ActiveMQBuffer bytes, boolean sync) throws Exception {

      }

      @Override
      public void write(EncodingSupport bytes, boolean sync, IOCallback callback) throws Exception {

      }

      @Override
      public void write(EncodingSupport bytes, boolean sync) throws Exception {

      }

      @Override
      public void writeDirect(ByteBuffer bytes, boolean sync, IOCallback callback) {

      }

      @Override
      public void writeDirect(ByteBuffer bytes, boolean sync) throws Exception {

      }

      @Override
      public void blockingWriteDirect(ByteBuffer bytes, boolean sync, boolean releaseBuffer) throws Exception {

      }

      @Override
      public int read(ByteBuffer bytes, IOCallback callback) throws Exception {
         return 0;
      }

      @Override
      public int read(ByteBuffer bytes) throws Exception {
         return 0;
      }

      @Override
      public void position(long pos) throws IOException {

      }

      @Override
      public long position() {
         return 0;
      }

      @Override
      public void close() throws Exception {

      }

      @Override
      public void sync() throws IOException {

      }

      @Override
      public long size() throws Exception {
         return size;
      }

      @Override
      public void renameTo(String newFileName) throws Exception {

      }

      @Override
      public SequentialFile cloneFile() {
         return null;
      }

      @Override
      public void copyTo(SequentialFile newFileName) throws Exception {

      }

      @Override
      public void setTimedBuffer(TimedBuffer buffer) {

      }

      @Override
      public File getJavaFile() {
         return null;
      }
   }

   private static class MockLargeBody extends LargeBody {

      final long size;

      MockLargeBody(long size) {
         super(null, null);
         this.size = size;
      }

      @Override
      public long getBodySize() {
         return size;
      }
   }

}
