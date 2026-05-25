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

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.cli.commands.tools.LockAbstract;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

public abstract class ArtemisAbstractPlugin extends AbstractMojo {

   @Component
   protected RepositorySystem repositorySystem;

   @Parameter(defaultValue = "${repositorySystemSession}")
   protected RepositorySystemSession repoSession;

   @Parameter(defaultValue = "${project.remoteProjectRepositories}")
   protected List<RemoteRepository> remoteRepos;

   @Parameter(defaultValue = "${localRepository}")
   protected ArtifactRepository localRepository;

   @Parameter
   protected String[] extraRepositories;

   List<RemoteRepository> inUseRepositories;

   @Override
   public void execute() throws MojoExecutionException, MojoFailureException {
      if (isIgnore()) {
         getLog().debug("******************************************************************************************************");
         getLog().debug("Execution of " + getClass().getSimpleName() + " is being ignored as ignore has been set to true");
         getLog().debug("******************************************************************************************************");
      } else {
         doExecute();
         // We could execute the maven plugins over and over on examples
         // For that reason we just unlock the server here
         // Notice this has no implementations if you are using spawn
         LockAbstract.unlock();
      }
   }

   boolean isArtemisHome(Path path) {
      if (path == null) {
         return false;
      }

      Path artemisScript = path.resolve("bin").resolve("artemis");
      return Files.exists(artemisScript, LinkOption.NOFOLLOW_LINKS);
   }

   protected File findArtemisHome(final File home, final File alternateHome) throws MojoExecutionException {
      if (isArtemisHome(home.toPath())) {
         return home;
      }

      if (isArtemisHome(alternateHome.toPath())) {
         return alternateHome;
      }

      getLog().error("********************************************************************************************");
      getLog().error("Could not locate suitable Artemis.home on either " + home + " or " + alternateHome);
      getLog().error("Use the binary distribution or build the distribution before running the examples");
      getLog().error("********************************************************************************************");

      throw new MojoExecutionException("Couldn't find artemis.home");
   }

   protected abstract boolean isIgnore();

   protected Artifact newArtifact(String artifactID) throws MojoFailureException {
      Artifact artifact;
      try {
         artifact = new DefaultArtifact(artifactID);
      } catch (IllegalArgumentException e) {
         throw new MojoFailureException(e.getMessage(), e);
      }
      return artifact;
   }

   protected void doExecute() throws MojoExecutionException, MojoFailureException {
      int repositories = 0;
      if (extraRepositories != null) {
         inUseRepositories = new ArrayList<>();
         for (String strRepo: extraRepositories) {
            RemoteRepository repo = new RemoteRepository.Builder("repo" + (repositories++), "default", strRepo).build();
            inUseRepositories.add(repo);
            remoteRepos.add(repo);
         }
      }
   }

   protected void done() {
      if (inUseRepositories != null) {
         inUseRepositories.forEach(r -> remoteRepos.remove(r));
         inUseRepositories.clear(); // give a help to GC
      }
      inUseRepositories = null;
   }

   protected File resolveArtifactFile(Artifact artifact) throws MojoExecutionException, DependencyCollectionException {
      ArtifactResult result = resolveArtifact(artifact);

      return result.getArtifact().getFile();
   }

   protected ArtifactResult resolveArtifact(Artifact artifact) throws MojoExecutionException {
      ArtifactRequest request = new ArtifactRequest();
      request.setArtifact(artifact);
      request.setRepositories(remoteRepos);

      ArtifactResult result;
      try {
         result = repositorySystem.resolveArtifact(repoSession, request);
      } catch (ArtifactResolutionException e) {
         throw new MojoExecutionException(e.getMessage(), e);
      }
      return result;
   }

   protected List<Artifact> explodeDependencies(Artifact artifact) throws DependencyCollectionException {
      final List<Artifact> dependencies = new LinkedList<>();

      CollectRequest exploreDependenciesRequest = new CollectRequest(new Dependency(artifact, "compile"), remoteRepos);
      CollectResult result = repositorySystem.collectDependencies(repoSession, exploreDependenciesRequest);
      final AtomicInteger level = new AtomicInteger(0);
      DependencyNode node = result.getRoot();

      StringWriter writer = new StringWriter();
      final PrintWriter strPrint = new PrintWriter(writer);

      strPrint.println("Dependencies explored for " + artifact + ":");
      if (node != null) {
         node.accept(new DependencyVisitor() {
            @Override
            public boolean visitEnter(DependencyNode node) {
               for (int i = 0; i < level.get(); i++) {
                  strPrint.print("!...");
               }
               level.incrementAndGet();
               strPrint.println("Dependency:: " + node.getDependency() + " node = " + node.getArtifact());

               dependencies.add(node.getArtifact());
               return true;
            }

            @Override
            public boolean visitLeave(DependencyNode node) {
               level.decrementAndGet();
               return true;
            }
         });
      }
      getLog().debug(writer.toString());
      return dependencies;
   }

   protected Set<File> resolveDependencies(String[] dependencyListParameter,
                                           String[] individualListParameter) throws DependencyCollectionException, MojoFailureException, MojoExecutionException {
      Set<File> filesSet = new HashSet<>();
      if (dependencyListParameter != null) {
         for (String lib : dependencyListParameter) {
            getLog().debug("********************" + lib);

            List<Artifact> artifactsList = explodeDependencies(newArtifact(lib));

            for (Artifact artifact : artifactsList) {
               File artifactFile = resolveArtifactFile(artifact);
               filesSet.add(artifactFile);
            }
         }
      }

      if (individualListParameter != null) {
         for (String lib : individualListParameter) {
            Artifact artifact = newArtifact(lib);
            getLog().debug("Single dpendency resolved::" + artifact);
            File artifactFile = resolveArtifactFile(artifact);
            filesSet.add(artifactFile);
         }
      }
      return filesSet;
   }

}
