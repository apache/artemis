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
package org.apache.activemq.artemis.tests.integration.stomp.util;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.transport.netty.NettyTransport;
import org.apache.activemq.transport.netty.NettyTransportFactory;
import org.apache.activemq.transport.netty.NettyTransportListener;

public abstract class AbstractStompClientConnection implements StompClientConnection {

   protected Pinger pinger;
   protected String version;
   protected String host;
   protected int port;
   protected String username;
   protected String passcode;
   protected StompFrameFactory factory;
   protected NettyTransport transport;
   protected ByteBuffer readBuffer;
   protected List<Byte> receiveList;
   protected BlockingQueue<ClientStompFrame> frameQueue = new LinkedBlockingQueue<>();
   protected boolean connected = false;
   protected int serverPingCounter;
   //protected ReaderThread readerThread;
   protected String scheme;

   private static final Set<StompClientConnection> connections = ConcurrentHashMap.newKeySet();


   public static final void tearDownConnections() {
      for (StompClientConnection connection: connections) {
         try {
            connection.closeTransport();
         } catch (Throwable e) {
            e.printStackTrace();
         }
      }

      connections.clear();
   }

   @Deprecated
   public AbstractStompClientConnection(String version, String host, int port) throws IOException {
      this.version = version;
      this.host = host;
      this.port = port;
      this.scheme = "tcp";

      this.factory = StompFrameFactoryFactory.getFactory(version);
      connections.add(this);
   }

   public AbstractStompClientConnection(URI uri) throws Exception {
      parseURI(uri);
      this.factory = StompFrameFactoryFactory.getFactory(version);

      readBuffer = ByteBuffer.allocateDirect(10240);
      receiveList = new ArrayList<>(10240);

      transport = NettyTransportFactory.createTransport(uri);
      transport.setTransportListener(new StompTransportListener());
      transport.connect();

      if (!transport.isConnected()) {
         throw new RuntimeException("Could not connect transport");
      }
      connections.add(this);
   }

   public AbstractStompClientConnection(URI uri, boolean autoConnect) throws Exception {
      parseURI(uri);
      this.factory = StompFrameFactoryFactory.getFactory(version);

      readBuffer = ByteBuffer.allocateDirect(10240);
      receiveList = new ArrayList<>(10240);

      transport = NettyTransportFactory.createTransport(uri);
      transport.setTransportListener(new StompTransportListener());

      if (autoConnect) {
         transport.connect();

         if (!transport.isConnected()) {
            throw new RuntimeException("Could not connect transport");
         }
      }
      connections.add(this);
   }

   private void parseURI(URI uri) {
      scheme = Objects.requireNonNullElse(uri.getScheme(), "tcp");
      host = uri.getHost();
      port = uri.getPort();
      this.version = StompClientConnectionFactory.getStompVersionFromURI(uri);
   }

   private ClientStompFrame sendFrameInternal(ClientStompFrame frame, boolean wicked) throws IOException, InterruptedException {
      ClientStompFrame response = null;
      ByteBuffer buffer;
      if (wicked) {
         buffer = frame.toByteBufferWithExtra("\n");
      } else {
         if (frame.isPing()) {
            buffer = ByteBuffer.allocateDirect(1);
            buffer.put((byte) 10);
            buffer.rewind();
         } else {
            buffer = frame.toByteBuffer();
         }
      }

      ByteBuf buf = Unpooled.copiedBuffer(buffer);

      try {
         buf.retain();
         ChannelFuture future = transport.send(buf);
         if (future != null) {
            future.awaitUninterruptibly();
         }
      } finally {
         buf.release();
      }

      //now response
      if (frame.needsReply()) {
         response = receiveFrame();

         //filter out server ping
         while (response != null) {
            if (response.getCommand().equals(Stomp.Commands.STOMP)) {
               response = receiveFrame();
            } else {
               break;
            }
         }
      }

      return response;
   }

   @Override
   public ClientStompFrame sendFrame(ClientStompFrame frame) throws IOException, InterruptedException {
      return sendFrameInternal(frame, false);
   }

   @Override
   public ClientStompFrame sendWickedFrame(ClientStompFrame frame) throws IOException, InterruptedException {
      return sendFrameInternal(frame, true);
   }

   @Override
   public ClientStompFrame receiveFrame() throws InterruptedException {
      return frameQueue.poll(10, TimeUnit.SECONDS);
   }

   @Override
   public ClientStompFrame receiveFrame(long timeout) throws InterruptedException {
      return frameQueue.poll(timeout, TimeUnit.MILLISECONDS);
   }

   //put bytes to byte array.
   private void receiveBytes(int n) {
      readBuffer.rewind();
      for (int i = 0; i < n; i++) {
         byte b = readBuffer.get();
         if (b == 0) {
            //a new frame got.
            int sz = receiveList.size();
            if (sz > 0) {
               byte[] frameBytes = new byte[sz];
               for (int j = 0; j < sz; j++) {
                  frameBytes[j] = receiveList.get(j);
               }
               ClientStompFrame frame = factory.createFrame(new String(frameBytes, StandardCharsets.UTF_8));

               if (validateFrame(frame)) {
                  frameQueue.offer(frame);
                  receiveList.clear();
               } else {
                  receiveList.add(b);
               }
            }
         } else {
            if (b == 10 && receiveList.isEmpty()) {
               //may be a ping
               incrementServerPing();
            } else {
               receiveList.add(b);
            }
         }
      }
      //clear readbuffer
      readBuffer.rewind();
   }

   protected void incrementServerPing() {
      serverPingCounter++;
   }

   private boolean validateFrame(ClientStompFrame f) {
      String h = f.getHeader(Stomp.Headers.CONTENT_LENGTH);
      if (h != null) {
         int len = Integer.parseInt(h);
         if (f.getBody().getBytes(StandardCharsets.UTF_8).length < len) {
            return false;
         }
      }
      return true;
   }

   protected void close() throws IOException {
      transport.close();
   }

   private class StompTransportListener implements NettyTransportListener {

      /**
       * Called when new incoming data has become available.
       *
       * @param incoming the next incoming packet of data.
       */
      @Override
      public void onData(ByteBuf incoming) {
         while (incoming.readableBytes() > 0) {
            int bytes = incoming.readableBytes();
            if (incoming.readableBytes() < readBuffer.remaining()) {
               ByteBuffer byteBuffer = ByteBuffer.allocate(incoming.readableBytes());
               incoming.readBytes(byteBuffer);
               byteBuffer.rewind();
               readBuffer.put(byteBuffer);
               receiveBytes(bytes);
            } else {
               incoming.readBytes(readBuffer);
               receiveBytes(bytes - incoming.readableBytes());
            }
         }
      }

      /**
       * Called if the connection state becomes closed.
       */
      @Override
      public void onTransportClosed() {
      }

      /**
       * Called when an error occurs during normal Transport operations.
       *
       * @param cause the error that triggered this event.
       */
      @Override
      public void onTransportError(Throwable cause) {
         throw new RuntimeException(cause);
      }
   }

//   private class ReaderThread extends Thread {
//
//      @Override
//      public void run() {
//         try {
//            transport.setTransportListener();
//            int n = Z..read(readBuffer);
//
//            while (n >= 0) {
//               if (n > 0) {
//                  receiveBytes(n);
//               }
//               n = socketChannel.read(readBuffer);
//            }
//            //peer closed
//            close();
//
//         } catch (IOException e) {
//            try {
//               close();
//            } catch (IOException e1) {
//               //ignore
//            }
//         }
//      }
//   }

   @Override
   public ClientStompFrame connect() throws Exception {
      return connect(null, null);
   }

   @Override
   public void destroy() {
      try {
         close();
      } catch (IOException e) {
      } finally {
         this.connected = false;
      }
   }

   @Override
   public ClientStompFrame connect(String username, String password) throws Exception {
      throw new RuntimeException("connect method not implemented!");
   }

   @Override
   public boolean isConnected() {
      return connected && transport.isConnected();
   }

   @Override
   public String getVersion() {
      return version;
   }

   @Override
   public int getFrameQueueSize() {
      return this.frameQueue.size();
   }

   @Override
   public void closeTransport() throws IOException {
      if (transport != null) {
         transport.close();
      }
   }

   @Override
   public NettyTransport getTransport() {
      return transport;
   }

   protected class Pinger extends Thread {

      long pingInterval;
      ClientStompFrame pingFrame;
      volatile boolean stop = false;

      Pinger(long interval) {
         this.pingInterval = interval;
         pingFrame = createFrame(Stomp.Commands.STOMP);
         pingFrame.setForceOneway();
         pingFrame.setPing(true);
      }

      public void startPing() {
         start();
      }

      public synchronized void stopPing() {
         stop = true;
         this.notify();
      }

      @Override
      public void run() {
         synchronized (this) {
            while (!stop) {
               try {
                  sendFrame(pingFrame);

                  this.wait(pingInterval);
               } catch (Exception e) {
                  stop = true;
                  e.printStackTrace();
               }
            }
         }
      }
   }
}
