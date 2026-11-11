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
package org.apache.activemq.artemis.core.security.jaas;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Set;
import javax.security.auth.x500.X500Principal;

public class StubX509Certificate extends X509Certificate {

   private static final KeyPair KEY_PAIR;
   static {
      try {
         KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
         kpg.initialize(1024);
         KEY_PAIR = kpg.generateKeyPair();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   private static final byte[] SHA256_WITH_RSA = {
      0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
      (byte) 0xf7, 0x0d, 0x01, 0x01, 0x0b, 0x05, 0x00
   };

   private final Principal id;
   private byte[] encoded;

   public StubX509Certificate(Principal id) {
      this.id = id;
   }

   private byte[] buildEncoded() {
      try {
         X500Principal principal = new X500Principal(id.getName());
         byte[] dnBytes = principal.getEncoded();
         byte[] spki = KEY_PAIR.getPublic().getEncoded();

         byte[] tbsCertificate = derSequence(
            derInteger(1),
            SHA256_WITH_RSA,
            dnBytes,
            derSequence(
               derUtcTime("200101000000Z"),
               derUtcTime("300101000000Z")
            ),
            dnBytes,
            spki
         );

         Signature sig = Signature.getInstance("SHA256withRSA");
         sig.initSign(KEY_PAIR.getPrivate());
         sig.update(tbsCertificate);

         return derSequence(tbsCertificate, SHA256_WITH_RSA, derBitString(sig.sign()));
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   private static byte[] derTag(int tag, byte[] content) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      out.write(tag);
      if (content.length < 128) {
         out.write(content.length);
      } else if (content.length < 256) {
         out.write(0x81);
         out.write(content.length);
      } else {
         out.write(0x82);
         out.write((content.length >> 8) & 0xFF);
         out.write(content.length & 0xFF);
      }
      out.write(content, 0, content.length);
      return out.toByteArray();
   }

   private static byte[] derSequence(byte[]... items) {
      ByteArrayOutputStream content = new ByteArrayOutputStream();
      for (byte[] item : items) {
         content.write(item, 0, item.length);
      }
      return derTag(0x30, content.toByteArray());
   }

   private static byte[] derInteger(long value) {
      return derTag(0x02, new byte[]{(byte) value});
   }

   private static byte[] derBitString(byte[] data) {
      byte[] content = new byte[data.length + 1];
      content[0] = 0;
      System.arraycopy(data, 0, content, 1, data.length);
      return derTag(0x03, content);
   }

   private static byte[] derUtcTime(String time) {
      return derTag(0x17, time.getBytes(StandardCharsets.US_ASCII));
   }

   @Override
   public Principal getSubjectDN() {
      return this.id;
   }

   // --- Stubbed Methods ---
   @Override
   public void checkValidity() {
   }

   @Override
   public void checkValidity(Date arg0) {
   }

   @Override
   public int getVersion() {
      return 0;
   }

   @Override
   public BigInteger getSerialNumber() {
      return null;
   }

   @Override
   public Principal getIssuerDN() {
      return null;
   }

   @Override
   public Date getNotBefore() {
      return null;
   }

   @Override
   public Date getNotAfter() {
      return null;
   }

   @Override
   public byte[] getTBSCertificate() {
      return null;
   }

   @Override
   public byte[] getSignature() {
      return null;
   }

   @Override
   public String getSigAlgName() {
      return null;
   }

   @Override
   public String getSigAlgOID() {
      return null;
   }

   @Override
   public byte[] getSigAlgParams() {
      return null;
   }

   @Override
   public boolean[] getIssuerUniqueID() {
      return null;
   }

   @Override
   public boolean[] getSubjectUniqueID() {
      return null;
   }

   @Override
   public boolean[] getKeyUsage() {
      return null;
   }

   @Override
   public int getBasicConstraints() {
      return 0;
   }

   @Override
   public byte[] getEncoded() {
      if (null == this.encoded) {
         this.encoded = buildEncoded();
      }
      return encoded.clone();
   }

   @Override
   public void verify(PublicKey arg0) {
   }

   @Override
   public void verify(PublicKey arg0, String arg1) {
   }

   @Override
   public String toString() {
      return null;
   }

   @Override
   public PublicKey getPublicKey() {
      return null;
   }

   @Override
   public boolean hasUnsupportedCriticalExtension() {
      return false;
   }

   @SuppressWarnings("rawtypes")
   @Override
   public Set getCriticalExtensionOIDs() {
      return null;
   }

   @SuppressWarnings("rawtypes")
   @Override
   public Set getNonCriticalExtensionOIDs() {
      return null;
   }

   @Override
   public byte[] getExtensionValue(String arg0) {
      return null;
   }

}
