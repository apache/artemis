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
package org.apache.activemq.artemis.utils;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.OtherName;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.utils.CertificateUtil.UPN_OID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CertificateUtilTest {

   private static final String TEST_UPN = "user@domain.com";

   @Test
   void testExtractUpnPositive() throws Exception {
      String extractedUpn = CertificateUtil.getUserPrincipalName(new X509Certificate[] {generateCertificateWithUPN(TEST_UPN)});
      assertEquals(TEST_UPN, extractedUpn, "Returned UPN should match the one embedded in the cert.");
   }

   @Test
   void testExtractUpnNegative() throws Exception {
      String extractedUpn = CertificateUtil.getUserPrincipalName(new X509Certificate[] {generateCertificateWithUPN(null)});
      assertNull(extractedUpn, "Should return null when no UPN is present.");
   }

   /**
    * Helper method to generate a self-signed v3 certificate. If upnValue is provided, it embeds it as an 'otherName' in
    * the SAN extension.
    */
   public static X509Certificate generateCertificateWithUPN(String upnValue) throws Exception {
      KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
      keyGen.initialize(2048);
      KeyPair keyPair = keyGen.generateKeyPair();

      long now = System.currentTimeMillis();
      JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
         new X500Name("CN=Mock Issuer"),
         BigInteger.valueOf(now),
         new Date(now - 86400000L),
         new Date(now + 86400000L),
         new X500Name("CN=Mock Subject"),
         keyPair.getPublic()
      );

      // inject the UPN into the Subject Alternative Name extension if provided
      if (upnValue != null) {
         OtherName otherName = new OtherName(new ASN1ObjectIdentifier(UPN_OID), new DERUTF8String(upnValue));
         GeneralNames subjectAltNames = new GeneralNames(new GeneralName(GeneralName.otherName, otherName));
         certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);
      }

      // sign the certificate
      ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());

      // convert BouncyCastle builder format to standard java.security.cert.X509Certificate
      return new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));
   }

   @Test
   void testParseOtherNameForUpnSingleWrappedUtf8String() throws Exception {
      byte[] derEncoded = createUpnDer(TEST_UPN, UPN_OID, DerStringOption.UTF8, false);
      String extractedUpn = CertificateUtil.parseOtherNameForUpn(derEncoded);
      assertEquals(TEST_UPN, extractedUpn, "Should extract UPN from single-wrapped UTF8String");
   }

   @Test
   void testParseOtherNameForUpnSingleWrappedIa5String() throws Exception {
      byte[] derEncoded = createUpnDer(TEST_UPN, UPN_OID, DerStringOption.IA5, false);
      String extractedUpn = CertificateUtil.parseOtherNameForUpn(derEncoded);
      assertEquals(TEST_UPN, extractedUpn, "Should extract UPN from single-wrapped IA5String");
   }

   @Test
   void testParseOtherNameForUpnDoubleWrappedUtf8String() throws Exception {
      byte[] derEncoded = createUpnDer(TEST_UPN, UPN_OID, DerStringOption.UTF8, true);
      String extractedUpn = CertificateUtil.parseOtherNameForUpn(derEncoded);
      assertEquals(TEST_UPN, extractedUpn, "Should extract UPN from double-wrapped UTF8String");
   }

   @Test
   void testParseOtherNameForUpnDoubleWrappedIa5String() throws Exception {
      byte[] derEncoded = createUpnDer(TEST_UPN, UPN_OID, DerStringOption.IA5, true);
      String extractedUpn = CertificateUtil.parseOtherNameForUpn(derEncoded);
      assertEquals(TEST_UPN, extractedUpn, "Should extract UPN from double-wrapped IA5String");
   }

   @Test
   void testParseOtherNameForUpnInvalidOid() throws Exception {
      byte[] derEncoded = createUpnDer(TEST_UPN, "2.5.4.3", DerStringOption.UTF8, false);
      String extractedUpn = CertificateUtil.parseOtherNameForUpn(derEncoded);
      assertNull(extractedUpn, "Should return null when OID doesn't match UPN_OID");
   }

   /**
    * A PrintableString is a restricted character string type in the ASN.1 notation. It is used to describe data that
    * consists only of a specific printable subset of the ASCII character set. See more at
    * https://en.wikipedia.org/wiki/PrintableString.
    * <p>
    * In the context of UPN encoding, PrintableString is not typically used because UPNs can contain non-printable
    * characters. Therefore, encountering a PrintableString in a UPN context is considered invalid.
    */
   @Test
   void testParseOtherNameForUpnInvalidStringTag() throws Exception {
      byte[] derEncoded = createUpnDer(TEST_UPN, UPN_OID, DerStringOption.PRINTABLE, false);
      String extractedUpn = CertificateUtil.parseOtherNameForUpn(derEncoded);
      assertNull(extractedUpn, "Should return null when string tag is not UTF8String or IA5String");
   }

   /**
    * Tests the behavior when the DER-encoded byte sequence is missing the outer sequence tag.
    */
   @Test
   void testParseOtherNameForUpnMissingSequence() throws Exception {
      byte[] derEncoded = createUpnDer(TEST_UPN, UPN_OID, DerStringOption.UTF8, false);
      byte[] derEncodedSlice = new byte[derEncoded.length - 2];
      System.arraycopy(derEncoded, 2, derEncodedSlice, 0, derEncodedSlice.length);
      String extractedUpn = CertificateUtil.parseOtherNameForUpn(derEncodedSlice);
      assertNull(extractedUpn, "Should return null when outer sequence tag is missing");
   }

   /**
    * Creates a DER-encoded byte array representing a User Principal Name (UPN) entry.
    *
    * @param upnValue The UPN value to encode as a string.
    * @param oid The object identifier (OID) to use for the entry.
    * @param derStringOption The string type (e.g., UTF8, IA5, PRINTABLE) to encode the UPN value.
    * @param doubleWrap Indicates whether the string value should be wrapped in an additional tag structure.
    * @return A DER-encoded byte array representing the UPN entry.
    * @throws IOException If an error occurs during encoding.
    */
   private static byte[] createUpnDer(String upnValue, String oid, DerStringOption derStringOption, boolean doubleWrap) throws IOException {
      ASN1EncodableVector sequence = new ASN1EncodableVector();
      sequence.add(new ASN1ObjectIdentifier(oid));
      ASN1Encodable stringValue = switch (derStringOption) {
         case UTF8 -> new DERUTF8String(upnValue);
         case IA5 -> new DERIA5String(upnValue);
         case PRINTABLE -> new DERPrintableString(upnValue);
         default -> throw new IllegalArgumentException("Unsupported DER string option: " + derStringOption);
      };

      DERTaggedObject taggedString;
      DERTaggedObject intermediateTaggedString = new DERTaggedObject(true, 0, stringValue);
      if (doubleWrap) {
         taggedString = new DERTaggedObject(true, 0, intermediateTaggedString);
      } else {
         taggedString = intermediateTaggedString;
      }
      sequence.add(taggedString);

      return new DERSequence(sequence).getEncoded();
   }

   private enum DerStringOption {
      UTF8, IA5, PRINTABLE
   }
}
