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

package org.apache.activemq.artemis.tests.smoke.lockmanager;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinikubeSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static void setupRBAC() {
      try {
         // Create Role with lease permissions
         ProcessBuilder roleBuilder = new ProcessBuilder("kubectl", "apply", "-f", "-");
         roleBuilder.redirectErrorStream(true);
         Process roleProcess = roleBuilder.start();

         String roleYaml = """
            apiVersion: rbac.authorization.k8s.io/v1
            kind: Role
            metadata:
              name: lease-manager
              namespace: default
            rules:
            - apiGroups: ["coordination.k8s.io"]
              resources: ["leases"]
              verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
            - apiGroups: [""]
              resources: ["configmaps"]
              verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
            """;

         roleProcess.getOutputStream().write(roleYaml.getBytes());
         roleProcess.getOutputStream().close();

         String roleOutput;
         try (java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(roleProcess.getInputStream()))) {
            roleOutput = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
         }

         int roleExitCode = roleProcess.waitFor();
         if (roleExitCode != 0) {
            logger.warn("Failed to create Role: {}", roleOutput);
         } else {
            logger.debug("Created Role: {}", roleOutput);
         }

         // Create RoleBinding
         ProcessBuilder bindingBuilder = new ProcessBuilder("kubectl", "apply", "-f", "-");
         bindingBuilder.redirectErrorStream(true);
         Process bindingProcess = bindingBuilder.start();

         String bindingYaml = """
            apiVersion: rbac.authorization.k8s.io/v1
            kind: RoleBinding
            metadata:
              name: lease-manager-binding
              namespace: default
            roleRef:
              apiGroup: rbac.authorization.k8s.io
              kind: Role
              name: lease-manager
            subjects:
            - kind: ServiceAccount
              name: default
              namespace: default
            """;

         bindingProcess.getOutputStream().write(bindingYaml.getBytes());
         bindingProcess.getOutputStream().close();

         String bindingOutput;
         try (java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(bindingProcess.getInputStream()))) {
            bindingOutput = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
         }

         int bindingExitCode = bindingProcess.waitFor();
         if (bindingExitCode != 0) {
            logger.warn("Failed to create RoleBinding: {}", bindingOutput);
         } else {
            logger.debug("Created RoleBinding: {}", bindingOutput);
         }
      } catch (Exception e) {
         logger.warn("Failed to set up Kubernetes RBAC", e);
      }
   }

   public static void cleanupRBAC() {
      try {
         // Delete RoleBinding
         ProcessBuilder deleteBinding = new ProcessBuilder("kubectl", "delete", "rolebinding", "lease-manager-binding", "-n", "default", "--ignore-not-found");
         deleteBinding.start().waitFor();

         // Delete Role
         ProcessBuilder deleteRole = new ProcessBuilder("kubectl", "delete", "role", "lease-manager", "-n", "default", "--ignore-not-found");
         deleteRole.start().waitFor();

         logger.debug("Cleaned up Kubernetes RBAC resources");
      } catch (Exception e) {
         logger.warn("Failed to clean up Kubernetes RBAC", e);
      }
   }


   public static String getKubeconfigValue(String key) {
      try {
         String kubeconfigPath = System.getenv("KUBECONFIG");
         if (kubeconfigPath == null) {
            kubeconfigPath = System.getProperty("user.home") + "/.kube/config";
         }
         Path path = Path.of(kubeconfigPath);
         if (!Files.exists(path)) {
            return null;
         }

         String content = Files.readString(path);
         String searchKey = key + ":";
         for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(searchKey)) {
               String value = trimmed.substring(searchKey.length()).trim();
               logger.debug("Found {} in kubeconfig: {}", key, value);
               return value;
            }
         }
      } catch (IOException e) {
         logger.debug("Could not read kubeconfig", e);
      }
      return null;
   }

   public static String getKubeconfigServer() {
      return getKubeconfigValue("server");
   }


   public static boolean supported() {
      return getKubeconfigServer() != null;
   }

   public static String extractCACertificate() {
      // First try reading certificate-authority-data (base64 encoded) from kubeconfig
      String base64Cert = getKubeconfigValue("certificate-authority-data");

      if (base64Cert != null) {
         try {
            byte[] decoded = java.util.Base64.getDecoder().decode(base64Cert);
            logger.debug("Successfully decoded certificate-authority-data from kubeconfig");
            return new String(decoded);
         } catch (IllegalArgumentException e) {
            logger.debug("Could not decode certificate-authority-data", e);
         }
      }

      // Fall back to certificate-authority (file path)
      String certPath = getKubeconfigValue("certificate-authority");
      if (certPath != null) {
         try {
            logger.debug("Reading CA certificate from file: {}", certPath);
            return Files.readString(Path.of(certPath));
         } catch (IOException e) {
            logger.debug("Could not read certificate from path: {}", certPath, e);
         }
      }

      throw new IllegalStateException("Cannot find the CA certificate in kubeconfig");
   }

   public static String generateKubectlToken() {
      try {
         ProcessBuilder pb = new ProcessBuilder("kubectl", "create", "token", "default");
         pb.redirectErrorStream(true);
         Process process = pb.start();

         String output;
         try (java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(process.getInputStream()))) {
            output = reader.lines().collect(java.util.stream.Collectors.joining("\n")).trim();
         }

         int exitCode = process.waitFor();
         if (exitCode == 0 && !output.isEmpty()) {
            logger.debug("Successfully generated token using kubectl");
            return output;
         } else {
            logger.debug("kubectl create token failed with exit code {}: {}", exitCode, output);
         }
      } catch (Exception e) {
         logger.debug("Could not generate token using kubectl", e);
      }
      return null;
   }



}
