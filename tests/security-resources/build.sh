#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# The various SSL stores and certificates were created with the following commands:
# Requires use of JDK 8+ keytool command.
set -e

KEY_PASS=securepass
STORE_PASS=securepass
CA_VALIDITY=365000
VALIDITY=36500
LOCAL_CLIENT_NAMES="dns:localhost,ip:127.0.0.1"
LOCAL_SERVER_NAMES="dns:localhost,dns:localhost.localdomain,dns:artemis.localtest.me,ip:127.0.0.1"

# Clean up existing files
# -----------------------
rm -f *.crt *.csr openssl-* *.jceks *.jks *.p12 *.pem *.pemcfg

# Create a key and self-signed certificate for the CA, to sign server certificate requests and use for trust:
# ----------------------------------------------------------------------------------------------------
keytool -storetype pkcs12 -keystore server-ca-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -alias server-ca -genkey -keyalg "RSA" -keysize 2048 -dname "CN=ActiveMQ Artemis Server Certification Authority, OU=Artemis, O=ActiveMQ" -validity $CA_VALIDITY -ext bc:c=ca:true
keytool -storetype pkcs12 -keystore server-ca-keystore.p12 -storepass $STORE_PASS -alias server-ca -exportcert -rfc > server-ca.crt
openssl pkcs12 -in server-ca-keystore.p12 -nodes -nocerts -out server-ca.pem -password pass:$STORE_PASS

# Create trust store with the server CA cert:
# -------------------------------------------------------
keytool -storetype pkcs12 -keystore server-ca-truststore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias server-ca -file server-ca.crt -noprompt
keytool -importkeystore -srckeystore server-ca-truststore.p12 -destkeystore server-ca-truststore.jceks -srcstoretype pkcs12 -deststoretype jceks -srcstorepass securepass -deststorepass securepass
keytool -importkeystore -srckeystore server-ca-truststore.p12 -destkeystore server-ca-truststore.jks -srcstoretype pkcs12 -deststoretype jks -srcstorepass securepass -deststorepass securepass

# Create a key pair for the server, and sign it with the CA:
# ----------------------------------------------------------
keytool -storetype pkcs12 -keystore server-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -alias server -genkey -keyalg "RSA" -keysize 2048 -dname "CN=ActiveMQ Artemis Server, OU=Artemis, O=ActiveMQ, L=AMQ, S=AMQ, C=AMQ" -validity $VALIDITY -ext bc=ca:false -ext eku=sA -ext "san=dns:server.artemis.activemq,$LOCAL_SERVER_NAMES"

keytool -storetype pkcs12 -keystore server-keystore.p12 -storepass $STORE_PASS -alias server -certreq -file server.csr
keytool -storetype pkcs12 -keystore server-ca-keystore.p12 -storepass $STORE_PASS -alias server-ca -gencert -rfc -infile server.csr -outfile server.crt -validity $VALIDITY -ext bc=ca:false -ext eku=sA -ext "san=dns:server.artemis.activemq,$LOCAL_SERVER_NAMES"

keytool -storetype pkcs12 -keystore server-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias server-ca -file server-ca.crt -noprompt
keytool -storetype pkcs12 -keystore server-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias server -file server.crt
cp server-keystore.p12 server-keystore-without-ca.p12 && keytool -delete -noprompt -alias server-ca -storetype pkcs12 -keystore server-keystore-without-ca.p12 -storepass $STORE_PASS

keytool -importkeystore -srckeystore server-keystore.p12 -destkeystore server-keystore.jceks -srcstoretype pkcs12 -deststoretype jceks -srcstorepass securepass -deststorepass securepass
keytool -importkeystore -srckeystore server-keystore.p12 -destkeystore server-keystore.jks -srcstoretype pkcs12 -deststoretype jks -srcstorepass securepass -deststorepass securepass

# Create a key pair for the other server, and sign it with the CA:
# ----------------------------------------------------------
keytool -storetype pkcs12 -keystore other-server-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -alias other-server -genkey -keyalg "RSA" -keysize 2048 -dname "CN=ActiveMQ Artemis Other Server, OU=Artemis, O=ActiveMQ, L=AMQ, S=AMQ, C=AMQ" -validity $VALIDITY -ext bc=ca:false -ext eku=sA -ext "san=dns:other-server.artemis.activemq,$LOCAL_SERVER_NAMES"

keytool -storetype pkcs12 -keystore other-server-keystore.p12 -storepass $STORE_PASS -alias other-server -certreq -file other-server.csr
keytool -storetype pkcs12 -keystore server-ca-keystore.p12 -storepass $STORE_PASS -alias server-ca -gencert -rfc -infile other-server.csr -outfile other-server.crt -validity $VALIDITY -ext bc=ca:false -ext eku=sA -ext "san=dns:other-server.artemis.activemq,$LOCAL_SERVER_NAMES"

keytool -storetype pkcs12 -keystore other-server-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias server-ca -file server-ca.crt -noprompt
keytool -storetype pkcs12 -keystore other-server-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias other-server -file other-server.crt

keytool -importkeystore -srckeystore other-server-keystore.p12 -destkeystore other-server-keystore.jceks -srcstoretype pkcs12 -deststoretype jceks -srcstorepass securepass -deststorepass securepass
keytool -importkeystore -srckeystore other-server-keystore.p12 -destkeystore other-server-keystore.jks -srcstoretype pkcs12 -deststoretype jks -srcstorepass securepass -deststorepass securepass

# Create trust store with the other server cert:
# -------------------------------------------------------
keytool -storetype pkcs12 -keystore other-server-truststore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias other-server -file other-server.crt -noprompt
keytool -importkeystore -srckeystore other-server-truststore.p12 -destkeystore other-server-truststore.jceks -srcstoretype pkcs12 -deststoretype jceks -srcstorepass securepass -deststorepass securepass
keytool -importkeystore -srckeystore other-server-truststore.p12 -destkeystore other-server-truststore.jks -srcstoretype pkcs12 -deststoretype jks -srcstorepass securepass -deststorepass securepass

# Create crl with the other server cert:
# -------------------------------------------------------
> openssl-database
echo 00 > openssl-crlnumber
openssl ca -config openssl.conf -revoke other-server.crt -keyfile server-ca.pem -cert server-ca.crt
openssl ca -config openssl.conf -gencrl -keyfile server-ca.pem -cert server-ca.crt -out other-server-crl.pem -crldays $VALIDITY

# Create a key pair for the broker with an unexpected hostname, and sign it with the CA:
# --------------------------------------------------------------------------------------
keytool -storetype pkcs12 -keystore unknown-server-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -alias unknown-server -genkey -keyalg "RSA" -keysize 2048 -dname "CN=ActiveMQ Artemis Unknown Server, OU=Artemis, O=ActiveMQ, L=AMQ, S=AMQ, C=AMQ" -validity $VALIDITY -ext bc=ca:false -ext eku=sA -ext "san=dns:unknown-server.artemis.activemq"

keytool -storetype pkcs12 -keystore unknown-server-keystore.p12 -storepass $STORE_PASS -alias unknown-server -certreq -file unknown-server.csr
keytool -storetype pkcs12 -keystore server-ca-keystore.p12 -storepass $STORE_PASS -alias server-ca -gencert -rfc -infile unknown-server.csr -outfile unknown-server.crt -validity $VALIDITY -ext bc=ca:false -ext eku=sA -ext "san=dns:unknown-server.artemis.activemq"

keytool -storetype pkcs12 -keystore unknown-server-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias server-ca -file server-ca.crt -noprompt
keytool -storetype pkcs12 -keystore unknown-server-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias unknown-server -file unknown-server.crt

keytool -importkeystore -srckeystore unknown-server-keystore.p12 -destkeystore unknown-server-keystore.jceks -srcstoretype pkcs12 -deststoretype jceks -srcstorepass securepass -deststorepass securepass
keytool -importkeystore -srckeystore unknown-server-keystore.p12 -destkeystore unknown-server-keystore.jks -srcstoretype pkcs12 -deststoretype jks -srcstorepass securepass -deststorepass securepass

# Create a key and self-signed certificate for the CA, to sign client certificate requests and use for trust:
# ----------------------------------------------------------------------------------------------------
keytool -storetype pkcs12 -keystore client-ca-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -alias client-ca -genkey -keyalg "RSA" -keysize 2048 -dname "CN=ActiveMQ Artemis Client Certification Authority, OU=Artemis, O=ActiveMQ" -validity $CA_VALIDITY -ext bc:c=ca:true
keytool -storetype pkcs12 -keystore client-ca-keystore.p12 -storepass $STORE_PASS -alias client-ca -exportcert -rfc > client-ca.crt
openssl pkcs12 -in client-ca-keystore.p12 -nodes -nocerts -out client-ca.pem -password pass:$STORE_PASS

# Create trust store with the client CA cert:
# -------------------------------------------------------
keytool -storetype pkcs12 -keystore client-ca-truststore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias client-ca -file client-ca.crt -noprompt
keytool -importkeystore -srckeystore client-ca-truststore.p12 -destkeystore client-ca-truststore.jceks -srcstoretype pkcs12 -deststoretype jceks -srcstorepass securepass -deststorepass securepass
keytool -importkeystore -srckeystore client-ca-truststore.p12 -destkeystore client-ca-truststore.jks -srcstoretype pkcs12 -deststoretype jks -srcstorepass securepass -deststorepass securepass

# Create a key pair for the client, and sign it with the CA:
# ----------------------------------------------------------
keytool -storetype pkcs12 -keystore client-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -alias client -genkey -keyalg "RSA" -keysize 2048 -dname "CN=ActiveMQ Artemis Client, OU=Artemis, O=ActiveMQ, L=AMQ, S=AMQ, C=AMQ" -validity $VALIDITY -ext bc=ca:false -ext eku=cA -ext "san=dns:client.artemis.activemq,$LOCAL_CLIENT_NAMES"

keytool -storetype pkcs12 -keystore client-keystore.p12 -storepass $STORE_PASS -alias client -certreq -file client.csr
keytool -storetype pkcs12 -keystore client-ca-keystore.p12 -storepass $STORE_PASS -alias client-ca -gencert -rfc -infile client.csr -outfile client.crt -validity $VALIDITY -ext bc=ca:false -ext eku=cA -ext "san=dns:client.artemis.activemq,$LOCAL_CLIENT_NAMES"

keytool -storetype pkcs12 -keystore client-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias client-ca -file client-ca.crt -noprompt
keytool -storetype pkcs12 -keystore client-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias client -file client.crt

keytool -importkeystore -srckeystore client-keystore.p12 -destkeystore client-keystore.jceks -srcstoretype pkcs12 -deststoretype jceks -srcstorepass securepass -deststorepass securepass
keytool -importkeystore -srckeystore client-keystore.p12 -destkeystore client-keystore.jks -srcstoretype pkcs12 -deststoretype jks -srcstorepass securepass -deststorepass securepass

# Create a key pair for the other client, and sign it with the CA:
# ----------------------------------------------------------
keytool -storetype pkcs12 -keystore other-client-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -alias other-client -genkey -keyalg "RSA" -keysize 2048 -dname "CN=ActiveMQ Artemis Other Client, OU=Artemis, O=ActiveMQ, L=AMQ, S=AMQ, C=AMQ" -validity $VALIDITY -ext bc=ca:false -ext eku=cA -ext "san=dns:other-client.artemis.activemq,$LOCAL_CLIENT_NAMES"

keytool -storetype pkcs12 -keystore other-client-keystore.p12 -storepass $STORE_PASS -alias other-client -certreq -file other-client.csr
keytool -storetype pkcs12 -keystore client-ca-keystore.p12 -storepass $STORE_PASS -alias client-ca -gencert -rfc -infile other-client.csr -outfile other-client.crt -validity $VALIDITY -ext bc=ca:false -ext eku=cA -ext "san=dns:other-client.artemis.activemq,$LOCAL_CLIENT_NAMES"

keytool -storetype pkcs12 -keystore other-client-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias client-ca -file client-ca.crt -noprompt
keytool -storetype pkcs12 -keystore other-client-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias other-client -file other-client.crt

keytool -importkeystore -srckeystore other-client-keystore.p12 -destkeystore other-client-keystore.jceks -srcstoretype pkcs12 -deststoretype jceks -srcstorepass securepass -deststorepass securepass
keytool -importkeystore -srckeystore other-client-keystore.p12 -destkeystore other-client-keystore.jks -srcstoretype pkcs12 -deststoretype jks -srcstorepass securepass -deststorepass securepass

# Create crl with the other client cert:
# -------------------------------------------------------
> openssl-database
echo 00 > openssl-crlnumber
openssl ca -config openssl.conf -revoke other-client.crt -keyfile client-ca.pem -cert client-ca.crt
openssl ca -config openssl.conf -gencrl -keyfile client-ca.pem -cert client-ca.crt -out other-client-crl.pem -crldays $VALIDITY

# Create a key pair for the client with an unexpected hostname, and sign it with the CA:
# ----------------------------------------------------------
keytool -storetype pkcs12 -keystore unknown-client-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -alias unknown-client -genkey -keyalg "RSA" -keysize 2048 -dname "CN=ActiveMQ Artemis Unknown Client, OU=Artemis, O=ActiveMQ, L=AMQ, S=AMQ, C=AMQ" -validity $VALIDITY -ext bc=ca:false -ext eku=cA -ext "san=dns:unknown-client.artemis.activemq"

keytool -storetype pkcs12 -keystore unknown-client-keystore.p12 -storepass $STORE_PASS -alias unknown-client -certreq -file unknown-client.csr
keytool -storetype pkcs12 -keystore client-ca-keystore.p12 -storepass $STORE_PASS -alias client-ca -gencert -rfc -infile unknown-client.csr -outfile unknown-client.crt -validity $VALIDITY -ext bc=ca:false -ext eku=cA -ext "san=dns:unknown-client.artemis.activemq"

keytool -storetype pkcs12 -keystore unknown-client-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias client-ca -file client-ca.crt -noprompt
keytool -storetype pkcs12 -keystore unknown-client-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias unknown-client -file unknown-client.crt

keytool -importkeystore -srckeystore unknown-client-keystore.p12 -destkeystore unknown-client-keystore.jceks -srcstoretype pkcs12 -deststoretype jceks -srcstorepass securepass -deststorepass securepass
keytool -importkeystore -srckeystore unknown-client-keystore.p12 -destkeystore unknown-client-keystore.jks -srcstoretype pkcs12 -deststoretype jks -srcstorepass securepass -deststorepass securepass

# Create a key pair for a client using UPN for authentication, and sign it with the CA.
# Use OpenSSL to create the cert since keytool has trouble with setting the UPN.
# ----------------------------------------------------------
openssl req -new -x509 -newkey rsa:2048 -nodes -keyout upn-client-keystore.key -out upn-client-keystore.crt -days $VALIDITY -subj "/C=AA/ST=AMQ/L=AMQ/O=ActiveMQ/OU=Artemis/CN=ActiveMQ Artemis UPN Client"
openssl pkcs12 -export -in upn-client-keystore.crt -inkey upn-client-keystore.key -out upn-client-keystore.p12 -name "upn-client" -passout pass:$STORE_PASS

keytool -storetype pkcs12 -keystore upn-client-keystore.p12 -storepass $STORE_PASS -alias upn-client -certreq -file upn-client.csr
openssl x509 -req -in upn-client.csr -CA client-ca.crt -CAkey client-ca.pem -CAcreateserial -out upn-client.crt -days $VALIDITY -sha256 -extfile <(printf "basicConstraints=CA:FALSE\nextendedKeyUsage=clientAuth\nsubjectAltName=otherName:1.3.6.1.4.1.311.20.2.3;UTF8:user@domain.com,DNS:upn-client.artemis.activemq,DNS:localhost,IP:127.0.0.1")

keytool -storetype pkcs12 -keystore upn-client-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias client-ca -file client-ca.crt -noprompt
keytool -storetype pkcs12 -keystore upn-client-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias upn-client -file upn-client.crt

keytool -importkeystore -srckeystore upn-client-keystore.p12 -destkeystore upn-client-keystore.jceks -srcstoretype pkcs12 -deststoretype jceks -srcstorepass $STORE_PASS -deststorepass $STORE_PASS
keytool -importkeystore -srckeystore upn-client-keystore.p12 -destkeystore upn-client-keystore.jks -srcstoretype pkcs12 -deststoretype jks -srcstorepass $STORE_PASS -deststorepass $STORE_PASS

# Create a key pair for an unknown client using UPN for authentication, and sign it with the CA.
# Use OpenSSL to create the cert since keytool has trouble with setting the UPN.
# ----------------------------------------------------------
openssl req -new -x509 -newkey rsa:2048 -nodes -keyout unknown-upn-client-keystore.key -out unknown-upn-client-keystore.crt -days $VALIDITY -subj "/C=AA/ST=AMQ/L=AMQ/O=ActiveMQ/OU=Artemis/CN=ActiveMQ Artemis Unknown UPN Client"
openssl pkcs12 -export -in unknown-upn-client-keystore.crt -inkey unknown-upn-client-keystore.key -out unknown-upn-client-keystore.p12 -name "unknown-upn-client" -passout pass:$STORE_PASS

keytool -storetype pkcs12 -keystore unknown-upn-client-keystore.p12 -storepass $STORE_PASS -alias unknown-upn-client -certreq -file unknown-upn-client.csr
openssl x509 -req -in unknown-upn-client.csr -CA client-ca.crt -CAkey client-ca.pem -CAcreateserial -out unknown-upn-client.crt -days $VALIDITY -sha256 -extfile <(printf "basicConstraints=CA:FALSE\nextendedKeyUsage=clientAuth\nsubjectAltName=otherName:1.3.6.1.4.1.311.20.2.3;UTF8:unknown@domain.com,DNS:unknown-upn-client.artemis.activemq,DNS:localhost,IP:127.0.0.1")

keytool -storetype pkcs12 -keystore unknown-upn-client-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias client-ca -file client-ca.crt -noprompt
keytool -storetype pkcs12 -keystore unknown-upn-client-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -importcert -alias unknown-upn-client -file unknown-upn-client.crt

keytool -importkeystore -srckeystore unknown-upn-client-keystore.p12 -destkeystore unknown-upn-client-keystore.jceks -srcstoretype pkcs12 -deststoretype jceks -srcstorepass $STORE_PASS -deststorepass $STORE_PASS
keytool -importkeystore -srckeystore unknown-upn-client-keystore.p12 -destkeystore unknown-upn-client-keystore.jks -srcstoretype pkcs12 -deststoretype jks -srcstorepass $STORE_PASS -deststorepass $STORE_PASS

# PEM versions
## separate private and public cred pem files combined for the keystore via prop
openssl pkcs12 -in server-keystore.p12 -out server-cert.pem -clcerts -nokeys -password pass:$STORE_PASS
openssl pkcs12 -in server-keystore.p12 -out server-key.pem -nocerts -nodes -password pass:$STORE_PASS
openssl pkcs12 -in other-server-keystore.p12 -out other-server-cert.pem -clcerts -nokeys -password pass:$STORE_PASS
openssl pkcs12 -in other-server-keystore.p12 -out other-server-key.pem -nocerts -nodes -password pass:$STORE_PASS
openssl pkcs12 -in unknown-server-keystore.p12 -out unknown-server-cert.pem -clcerts -nokeys -password pass:$STORE_PASS
openssl pkcs12 -in unknown-server-keystore.p12 -out unknown-server-key.pem -nocerts -nodes -password pass:$STORE_PASS

## PEMCFG properties format
echo source.key=classpath:server-key.pem > server-keystore.pemcfg
echo source.cert=classpath:server-cert.pem >> server-keystore.pemcfg
echo source.key=classpath:other-server-key.pem > other-server-keystore.pemcfg
echo source.cert=classpath:other-server-cert.pem >> other-server-keystore.pemcfg
echo source.key=classpath:unknown-server-key.pem > unknown-server-keystore.pemcfg
echo source.cert=classpath:unknown-server-cert.pem >> unknown-server-keystore.pemcfg

## combined pem file for client
openssl pkcs12 -in client-keystore.p12 -out client-key-cert.pem -nodes -password pass:$STORE_PASS
keytool -storetype pkcs12 -keystore server-ca-keystore.p12 -storepass $STORE_PASS -alias server-ca -exportcert -rfc > server-ca-cert.pem
keytool -storetype pkcs12 -keystore client-ca-keystore.p12 -storepass $STORE_PASS -alias client-ca -exportcert -rfc > client-ca-cert.pem

## Combined ca-certs pem to verify loading of multiple certs
cat client-ca-cert.pem server-ca-cert.pem > client-and-server-ca-certs.pem

## a cert for the ExternalCertificateLoginModule
keytool -storetype pkcs12 -keystore san-keystore.p12 -storepass $STORE_PASS -keypass $KEY_PASS -alias san-roles -genkey -keyalg "RSA" -keysize 2048 -dname "CN=ok" -validity $VALIDITY -ext "SAN=uri:urn:jaas:role:admin,uri:urn:jaas:role:view"

# Create JKS and JCEKS keystores with a different key password (for testing keyPassword parameter):
# -------------------------------------------------------------------------------------------------
DIFFERENT_KEY_PASS=keypass123
keytool -importkeystore -srckeystore server-keystore.p12 -destkeystore server-keystore-keypass.jks -srcstoretype pkcs12 -deststoretype jks -srcstorepass $STORE_PASS -deststorepass $STORE_PASS
keytool -keypasswd -keystore server-keystore-keypass.jks -storepass $STORE_PASS -alias server -keypass $STORE_PASS -new $DIFFERENT_KEY_PASS
keytool -importkeystore -srckeystore server-keystore.p12 -destkeystore server-keystore-keypass.jceks -srcstoretype pkcs12 -deststoretype jceks -srcstorepass $STORE_PASS -deststorepass $STORE_PASS
keytool -keypasswd -keystore server-keystore-keypass.jceks -storepass $STORE_PASS -alias server -keypass $STORE_PASS -new $DIFFERENT_KEY_PASS

# Clean up working files
# -----------------------
rm -f *.crt *.csr openssl-* *.key *.srl
