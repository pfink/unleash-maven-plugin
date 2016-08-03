package com.itemis.maven.plugins.unleash;

import java.util.List;

import javax.inject.Named;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.Deployer;
import org.eclipse.aether.impl.Installer;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

import com.google.common.base.Strings;
import com.itemis.maven.aether.ArtifactCoordinates;
import com.itemis.maven.plugins.cdi.AbstractCDIMojo;
import com.itemis.maven.plugins.cdi.annotations.MojoProduces;
import com.itemis.maven.plugins.cdi.annotations.ProcessingStep;

/**
 * A Maven {@link Mojo} which performs a release of the project it is started on.<br>
 * Release means that the versions of all modules are nailed down to real release versions so that the artifacts are
 * stable and reproducible.
 * Furthermore the whole project is built with these versions, SCM tags will be created and the artifacts will be
 * installed and deployed.<br>
 * <br>
 * Since this mojo depends on the base mojo of the <a href="https://github.com/shillner/maven-cdi-plugin-utils">CDI
 * Plugin Utils</a>
 * project it implements a basic workflow which is fully configurable and extendable by nature.<br>
 * <br>
 * In order to get this plugin to work you will have to add the appropriate SCM Provider implementation as a plugin
 * dependency, such as <a href="https://github.com/shillner/unleash-scm-provider-svn">SVN provider</a> or
 * <a href="https://github.com/shillner/unleash-scm-provider-git">Git provider</a>.<br>
 * You may also add further plugin dependencies that provider some additional {@link ProcessingStep} implementations you
 * want to use in your adapted workflow, f.i. <a href="https://github.com/shillner/maven-cdi-plugin-hooks">CDI Plugin
 * Hooks</a>.
 *
 *
 * @author <a href="mailto:stanley.hillner@itemis.de">Stanley Hillner</a>
 * @since 1.0.0
 */
@Mojo(name = "perform-tycho", aggregator = true, requiresProject = true)
public class UnleashTychoMojo extends AbstractCDIMojo {
  @Component
  @MojoProduces
  private PlexusContainer plexus;

  @Component
  @MojoProduces
  private RepositorySystem repoSystem;

  @Component
  @MojoProduces
  private RemoteRepositoryManager remoteRepositoryManager;

  @Component
  @MojoProduces
  private Deployer deployer;

  @Component
  @MojoProduces
  private Installer installer;

  @Component
  @MojoProduces
  private Prompter prompter;

  @Parameter(property = "session", readonly = true)
  @MojoProduces
  protected MavenSession session;

  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
  @MojoProduces
  private RepositorySystemSession repoSession;

  @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true, required = true)
  @MojoProduces
  @Named("pluginRepositories")
  private List<RemoteRepository> remotePluginRepos;

  @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
  @MojoProduces
  @Named("projectRepositories")
  private List<RemoteRepository> remoteProjectRepos;

  @Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
  @MojoProduces
  @Named("local")
  private ArtifactRepository LocalRepository;

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  @MojoProduces
  private MavenProject project;

  @Parameter(defaultValue = "${reactorProjects}", readonly = true, required = true)
  @MojoProduces
  @Named("reactorProjects")
  private List<MavenProject> reactorProjects;

  @Parameter(defaultValue = "${settings}", readonly = true, required = true)
  @MojoProduces
  private Settings settings;

  ////////////////////////////// configuration parameters //////////////////////////////
  ////////////////////////////// required
  @Parameter(defaultValue = "true", property = "unleash.allowLocalReleaseArtifacts", required = true)
  @MojoProduces
  @Named("allowLocalReleaseArtifacts")
  private boolean allowLocalReleaseArtifacts;

  @Parameter(defaultValue = "false", property = "unleash.commitBeforeTagging", required = true)
  @MojoProduces
  @Named("commitBeforeTagging")
  private boolean commitBeforeTagging;

  @Parameter(defaultValue = "${maven.home}", property = "unleash.mavenHome", required = true)
  @MojoProduces
  @Named("maven.home")
  private String mavenHome;

  @Parameter(defaultValue = "@{project.version}", property = "unleash.tagNamePattern", required = true)
  @MojoProduces
  @Named("tagNamePattern")
  private String tagNamePattern;

  //////////////////////////// optional
  @Parameter(property = "unleash.developmentVersion", required = false)
  @MojoProduces
  @Named("developmentVersion")
  private String developmentVersion;

  @Parameter(property = "unleash.profiles", required = false)
  @MojoProduces
  @Named("profiles")
  private List<String> profiles;

  @Parameter(defaultValue = "", property = "unleash.releaseArgs", required = false)
  @MojoProduces
  @Named("releaseArgs")
  private String releaseArgs;

  @Parameter(property = "unleash.releaseVersion", required = false)
  @MojoProduces
  @Named("releaseVersion")
  private String releaseVersion;

  @Parameter(defaultValue = "[unleash-maven-plugin]", property = "unleash.scmMessagePrefix", required = false)
  private String scmMessagePrefix;

  @MojoProduces
  @Named("scmPassword")
  @Parameter(property = "unleash.scmPassword", required = false)
  private String scmPassword;

  @MojoProduces
  @Named("scmUsername")
  @Parameter(property = "unleash.scmUsername", required = false)
  private String scmUsername;

  @MojoProduces
  @Named("artifactSpyPlugin")
  private ArtifactCoordinates artifactSpyPluginCoordinates = new ArtifactCoordinates("com.itemis.maven.plugins",
      "artifact-spy-plugin", "1.0.3", "maven-plugin");

  @MojoProduces
  private PluginDescriptor getPluginDescriptor() {
    return (PluginDescriptor) getPluginContext().get("pluginDescriptor");
  }

  @MojoProduces
  @Named("scmMessagePrefix")
  private String getScmMessagePrefix() {
    if (this.scmMessagePrefix != null && !this.scmMessagePrefix.endsWith(" ")) {
      this.scmMessagePrefix = this.scmMessagePrefix + " ";
    }
    return Strings.nullToEmpty(this.scmMessagePrefix);
  }
}
