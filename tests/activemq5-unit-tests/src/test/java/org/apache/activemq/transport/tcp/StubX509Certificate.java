/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.transport.tcp;

import java.math.BigInteger;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Set;

public class StubX509Certificate extends X509Certificate {

   private final Principal id;

   public StubX509Certificate(Principal id) {
      this.id = id;
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
      return null;
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

   @Override
   @SuppressWarnings({"unchecked", "rawtypes"})
   public Set getCriticalExtensionOIDs() {
      return null;
   }

   @Override
   @SuppressWarnings({"unchecked", "rawtypes"})
   public Set getNonCriticalExtensionOIDs() {
      return null;
   }

   @Override
   public byte[] getExtensionValue(String arg0) {
      return null;
   }

}
