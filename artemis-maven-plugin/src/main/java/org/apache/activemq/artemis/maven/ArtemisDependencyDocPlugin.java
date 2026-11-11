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
package org.apache.activemq.artemis.maven;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * The following substitutions are made for each line
 * <ul>
 * <li>{@code X{group}} with the groupID
 * <li>{@code X{artifact}} with the artifactID
 * <li>{@code X{version}} with the version
 * <li>{@code X{classifier}} with the classifier of the component
 * <li>{@code X{package}} a combination of the maven group:artifact:classifier
 * <li>{@code X{url}} with the url
 * <li>{@code X{file}} with the fileName
 * <li>{@code X{fileMD}} with the fileName on a LINK with MD style
 * <li>{@code X{URI}} with the URI
 * <li>{@code X{detail}} with the detail provided in the config
 * </ul>
 */
@Mojo(name = "dependency-doc", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class ArtemisDependencyDocPlugin extends ArtemisAbstractPlugin {

   @Parameter
   String name;

   @Parameter
   private String[] groupOrder;

   @Parameter(defaultValue = "https://repo.maven.apache.org/maven2")
   private String defaultRepo = "https://repo.maven.apache.org/maven2";

   @Parameter
   private String header;

   @Parameter
   private String lib;

   @Parameter
   private String line;

   @Parameter
   private String footer;

   @Parameter
   private String[] detailKey;

   @Parameter String[] detailValue;

   @Parameter
   private String[] extraRepositories;

   @Parameter
   private String file;

   @Override
   protected boolean isIgnore() {
      return false;
   }


   private String applyFilters(String content, Map<String, String> filters) throws IOException {

      if (filters != null) {
         for (Map.Entry<String, String> entry : filters.entrySet()) {
            try {
               content = replace(content, entry.getKey(), entry.getValue());
            } catch (Throwable e) {
               System.out.println("Error on " + entry.getKey());
               e.printStackTrace();
            }
         }
      }
      return content;
   }


   private String replace(String content, String key, String value) {
      return content.replaceAll(Pattern.quote(key), Matcher.quoteReplacement(value));
   }

   private String getPackageName(org.eclipse.aether.artifact.Artifact artifact) {
      return artifact.getGroupId() + ":" + artifact.getArtifactId() + (artifact.getClassifier() != null && !artifact.getClassifier().isEmpty() ? ":" + artifact.getClassifier() : "");
   }

   private String getGroupOrder(String group) {
      if (groupOrder == null) {
         groupOrder = new String[] {"org.apache.activemq"};
      }

      int i = 0;
      for (; i < groupOrder.length; i++) {
         if (group.equals(groupOrder[i])) {
            return Integer.toString(i);
         }
      }
      return Integer.toString(i);
   }

   @Override
   protected void doExecute() {

      Map<String, String> keys = new HashMap<>();

      if (detailKey != null) {
         if (detailValue == null) {
            throw new IllegalStateException("you need to specify all detail parameters");
         }

         if (detailKey.length != detailValue.length) {
            throw new IllegalStateException("Illegal argument size");
         }

         for (int i = 0; i < detailKey.length; i++) {
            keys.put(detailKey[i], detailValue[i]);
         }
      }

      if (file == null) {
         throw new IllegalStateException("you must specify the file output");
      }

      if (line == null) {
         throw new IllegalStateException("you must specify the line");
      }


      try {
         File javaFile = new File(file);
         javaFile.getParentFile().mkdirs();

         PrintStream stream = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)));

         if (header != null) {
            stream.println(header);
         }

         List<org.eclipse.aether.artifact.Artifact> artifacts = explodeDependencies(newArtifact(lib));

         Collections.sort(artifacts, (o1, o2) -> {
            String pref1 = getGroupOrder(o1.getGroupId());
            String pref2 = getGroupOrder(o2.getGroupId());
            return (pref1 + o1.getGroupId() + o1.getArtifactId()).compareTo(pref2 + o2.getGroupId() + o2.getArtifactId());
         });

         artifacts.forEach((art) -> {
            try {

               String detail = keys.get(art.getGroupId() + ":" + art.getArtifactId());
               if (detail == null) {
                  detail = "";
               }

               ArtifactResult result = resolveArtifact(art);

               Map<String, String> filter = new HashMap<>();
               filter.put("X{detail}", detail);
               filter.put("X{group}", art.getGroupId());
               filter.put("X{artifact}", art.getArtifactId());
               filter.put("X{classifier}", result.getArtifact().getClassifier());
               filter.put("X{package}", getPackageName(result.getArtifact()));
               filter.put("X{file}", result.getArtifact().getFile().getName());
               filter.put("X{version}", result.getArtifact().getVersion());

               String uri = getURI(result);

               filter.put("X{uri}", uri);

               if (uri.isEmpty()) {
                  filter.put("X{fileMD}", result.getArtifact().getFile().getName());
               } else {
                  filter.put("X{fileMD}", "link:" + uri + "[" + result.getArtifact().getFile().getName() + "]");
               }

               String output = applyFilters(line, filter);

               if (getLog().isDebugEnabled()) {
                  filter.forEach((a, b) -> {
                     getLog().debug("filter.put(" + a + ", " + b + ")");
                  });
                  getLog().debug(output);
               }


               stream.println(output);

            } catch (Exception e) {
               throw new RuntimeException(e.getMessage(), e);
            }
         });
         if (footer != null) {
            stream.println(footer);
         }
         stream.close();
      } catch (Throwable e) {
         getLog().error(e.getMessage(), e);
      }
   }

   private String getURI(ArtifactResult result) {
      Artifact art = result.getArtifact();
      String uri = "";
      if (result.getRepository() instanceof RemoteRepository remoteRepository) {
         uri = remoteRepository.getUrl();
      } else {
         uri = defaultRepo;
      }

      return uri + "/" +
            art.getGroupId().replace('.', '/') + "/" +
            art.getArtifactId() + "/" +
            art.getVersion();
   }

}
