package org.apache.maven.plugin.eclipse;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import aQute.lib.osgi.Analyzer;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.eclipse.osgiplugin.EclipseOsgiPlugin;
import org.apache.maven.plugin.eclipse.osgiplugin.ExplodedPlugin;
import org.apache.maven.plugin.eclipse.osgiplugin.PackagedPlugin;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.apache.maven.shared.utils.WriterFactory;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.components.interactivity.InputHandler;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>Add Eclipse artifacts from an Eclipse installation or a local P2 repo to the local Maven repo.</p>
 * 
 * <p>This mojo automatically analyzes the local P2 repo, copies jars from the plugins directory to the local maven
 * repo, and generates appropriate poms.</p>
 * 
 * <p>Typical usage for a local Eclipse installation:</p>
 * <p><code>mvn eclipse:to-maven 
 * -DdeployTo=maven.org::default::scpexe://repo1.maven.org/home/maven/repository-staging/to-ibiblio/eclipse-staging
 * -DeclipseDir=/opt/eclipse</code></p>
 * 
 * <p>Or to download and convert a P2 repository:</p>
 * <p><code>/opt/eclipse/eclipse -application org.eclipse.equinox.p2.artifact.repository.mirrorApplication
 * -writeMode clean -verbose -raw -ignoreErrors -source http://download.eclipse.org/releases/oxygen
 * -destination file:/tmp/p2-oxygen<br>
 * mvn eclipse:to-maven -DbundleNameAsArtifactId=true -DgroupIdTokens=3 -DattachSourcePlugins
 * -DresolveVersionRanges -DdeployTo=oxygen::default::file:/tmp/repo-2018-12 -DeclipseDir=/tmp/oxygen</code></p>
 * 
 * <p>Note: The size of such an update site (P2 repository) is 3 to 4 GB and the download can take several hours.</p>
 * 
 * @author Fabrizio Giustina
 * @author <a href="mailto:carlos@apache.org">Carlos Sanchez</a>
 * @version $Id$
 */
@Mojo( name = "to-maven", requiresProject = false )
public class EclipseToMavenMojo
    extends AbstractMojo
    implements Contextualizable
{

    /**
     * A pattern the <code>deployTo</code> param should match.
     */
    private static final Pattern DEPLOYTO_PATTERN = Pattern.compile( "(.+)::(.+)::(.+)" );

    /**
     * A pattern for a 4 digit OSGi version number.
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile( "(([0-9]+\\.)+[0-9]+)" );

    /**
     * A pattern for an artifact id generated from a source plugin.
     */
    private static final Pattern SOURCE_PATTERN = Pattern.compile( ".+:.+\\.source:.+" );

    /**
     * The version range used for an arbitrary version.
     */
    private static final String ANY_VERSION = "[0,)";

    /**
     * Plexus container, needed to manually lookup components for deploy of artifacts.
     */
    private PlexusContainer container;

    /**
     * Local maven repository.
     */
    @Parameter( property = "localRepository", required = true, readonly = true )
    private ArtifactRepository localRepository;

    /**
     * ArtifactRepositoryFactory component.
     */
    @Component
    private ArtifactRepositoryFactory artifactRepositoryFactory;

    /**
     * ArtifactFactory component.
     */
    @Component
    private ArtifactFactory artifactFactory;

    /**
     * ArtifactInstaller component.
     */
    @Component
    protected ArtifactInstaller installer;

    /**
     * ArtifactDeployer component.
     */
    @Component
    private ArtifactDeployer deployer;

    /**
     * Eclipse installation dir. If not set, a value for this parameter will be asked on the command line.
     */
    @Parameter( property = "eclipseDir" )
    private File eclipseDir;

    /**
     * Input handler, needed for command line handling.
     */
    @Component
    protected InputHandler inputHandler;

    /**
     * Strip qualifier (fourth token) from the plugin version. Qualifiers are for eclipse plugin the equivalent of
     * timestamped snapshot versions for Maven, but the date is maintained also for released version (e.g. a jar for the
     * release <code>3.2</code> can be named <code>org.eclipse.core.filesystem_1.0.0.v20060603.jar</code>. It's usually
     * handy to not to include this qualifier when generating maven artifacts for major releases, while it's needed when
     * working with eclipse integration/nightly builds.
     */
    @Parameter( property = "stripQualifier", defaultValue = "false" )
    private boolean stripQualifier;

    /**
     * Specifies a remote repository to which generated artifacts should be deployed to. If this property is specified,
     * artifacts are also deployed to the remote repo. The format for this parameter is <code>id::layout::url</code>
     */
    @Parameter( property = "deployTo" )
    private String deployTo;

    /**
     * Number of tokens from the bundle name used to build the group id. A value of -1 will use all tokens, but the
     * last.
     */
    @Parameter( property = "groupIdTokens" , defaultValue = "-1" )
    private int groupIdTokens;

    /**
     * Flag to use the bundle name directly as artifactId.
     */
    @Parameter( property = "bundleNameAsArtifactId" , defaultValue = "false" )
    private boolean useBundleNameAsArtifactId;

    /**
     * Flag to turn source plugins to attached artifacts.
     */
    @Parameter( property = "attachSourcePlugins" , defaultValue = "false" )
    private boolean attachSourcePlugins;

    /**
     * Flag to turn resolve version ranges of dependencies with available artifacts of the P2 repository.
     */
    @Parameter( property = "resolveVersionRanges" , defaultValue = "false" )
    private boolean resolveVersionRanges;

    /**
     * Flag to turn resolve recommended versions of dependencies with available artifacts of the P2 repository.
     */
    @Parameter( property = "resolveRecommendedVersions" , defaultValue = "false" )
    private boolean resolveRecommendedVersions;

    /**
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( eclipseDir == null )
        {
            getLog().info( Messages.getString( "EclipseToMavenMojo.eclipseDirectoryPrompt" ) );

            String eclipseDirString;
            try
            {
                eclipseDirString = inputHandler.readLine();
            }
            catch ( IOException e )
            {
                throw new MojoFailureException( 
                                        Messages.getString( "EclipseToMavenMojo.errorreadingfromstandardinput" ) );
            }
            eclipseDir = new File( eclipseDirString );
        }

        if ( !eclipseDir.isDirectory() )
        {
            throw new MojoFailureException( Messages.getString( "EclipseToMavenMojo.directoydoesnotexist",
                                                                eclipseDir.getAbsolutePath() ) );
        }

        File pluginDir = new File( eclipseDir, "plugins" );

        if ( !pluginDir.isDirectory() )
        {
            throw new MojoFailureException( Messages.getString( "EclipseToMavenMojo.plugindirectorydoesnotexist",
                                                                pluginDir.getAbsolutePath() ) );
        }

        File[] files = pluginDir.listFiles();

        ArtifactRepository remoteRepo = resolveRemoteRepo();

        if ( remoteRepo != null )
        {
            getLog().info( Messages.getString( "EclipseToMavenMojo.remoterepositorydeployto", deployTo ) );
        }

        Map<String, EclipseOsgiPlugin> plugins = new HashMap<String, EclipseOsgiPlugin>();
        Map<String, Model> models = new HashMap<String, Model>();

        getLog().info( Messages.getString( "EclipseToMavenMojo.searchingplugins", pluginDir.getAbsolutePath() ) );

        for ( File file : files )
        {
            getLog().debug( Messages.getString( "EclipseToMavenMojo.processingfile", file.getAbsolutePath() ) );

            processFile( file, plugins, models );
        }

        getLog().info( Messages.getString( "EclipseToMavenMojo.pluginsfound", 
            new Object[] { pluginDir.getAbsolutePath(), plugins.size() } ) );

        if ( attachSourcePlugins )
        {
            Set<String> sourceKeys = new HashSet<String>();
            for ( String key : plugins.keySet() )
            {
                if ( SOURCE_PATTERN.matcher( key ).matches() )
                {
                    sourceKeys.add( key );
                }
            }

            models.keySet().removeAll( sourceKeys );

            getLog().info( Messages.getString( "EclipseToMavenMojo.attachedsourceplugins", sourceKeys.size() ) );
        }

        resolveVersions( models );

        getLog().info( Messages.getString( "EclipseToMavenMojo.deploymainartifacts", models.size() ) );

        int i = 1;
        try
        {
            for ( String key : models.keySet() )
            {
                getLog().debug( Messages.getString( "EclipseToMavenMojo.processingplugin", new Object[] { i++,
                                   models.keySet().size() } ) );
                Model model = (Model) models.get( key );
                writeArtifact( model, plugins, remoteRepo );
            }
        }
        finally
        {
            getLog().info( Messages.getString( "EclipseToMavenMojo.deployedmainartifacts", i ) );
        }
    }

    protected void processFile( File file, Map<String, EclipseOsgiPlugin> plugins, Map<String, Model> models )
        throws MojoExecutionException, MojoFailureException
    {
        EclipseOsgiPlugin plugin = getEclipsePlugin( file );

        if ( plugin == null )
        {
            getLog().warn( Messages.getString( "EclipseToMavenMojo.skippingfile", file.getAbsolutePath() ) );
            return;
        }

        Model model = createModel( plugin );

        if ( model == null )
        {
            return;
        }

        processPlugin( plugin, model, plugins, models );
    }

    protected void processPlugin(
        EclipseOsgiPlugin plugin, Model model, Map<String, EclipseOsgiPlugin> plugins, Map<String, Model> models )
        throws MojoExecutionException, MojoFailureException
    {
        plugins.put( getKey( model ), plugin );
        models.put( getKey( model ), model );
    }

    protected String getKey( Model model )
    {
        return model.getGroupId() + ":" + model.getArtifactId() + ":" + model.getVersion();
    }

    protected String getSourceKey( Model model )
    {
        return model.getGroupId() + ":" + model.getArtifactId() + ".source" + ":" + model.getVersion();
    }

    private String getModuleKey( Model model )
    {
        return model.getGroupId() + ":" + model.getArtifactId();
    }

    private String getModuleKey( Dependency dep )
    {
        return dep.getGroupId() + ":" + dep.getArtifactId();
    }

    /**
     * Resolve dependency versions in the models, that did not declare a version at all.
     *
     * @param models
     * @throws MojoFailureException
     */
    protected void resolveVersions( Map<String, Model> models )
        throws MojoFailureException
    {
        Map<String, SortedSet<ArtifactVersion>> allVersions = new HashMap<String, SortedSet<ArtifactVersion>>();
        for ( Model model : models.values() )
        {
            String key = getModuleKey( model );
            SortedSet<ArtifactVersion> versions = allVersions.get( key );
            if ( versions == null )
            {
                versions = new TreeSet<ArtifactVersion>( Collections.reverseOrder() );
                allVersions.put( key, versions );
            }
            versions.add( new DefaultArtifactVersion( model.getVersion() ) );
        }

        for ( Model model : models.values() )
        {
            for ( Dependency dep : model.getDependencies() )
            {
                try
                {
                    String versionRange = dep.getVersion();
                    if ( ANY_VERSION.equals( versionRange ) )
                    {
                        String key = getModuleKey( dep );
                        Set<ArtifactVersion> versions = allVersions.get( key );
                        if ( versions != null )
                        {
                            dep.setVersion( versions.iterator().next().toString() );
                        }
                    }
                    else if ( "[(".indexOf( versionRange.charAt( 0 ) ) == 0 )
                    {
                        if ( resolveVersionRanges )
                        {
                            String key = getModuleKey( dep );
                            Set<ArtifactVersion> versions = allVersions.get( key );
                            if ( versions != null )
                            {
                                selectVersion( dep, versionRange, versions );
                            }
                        }
                    }
                    else if ( resolveRecommendedVersions )
                    {
                        String key = getModuleKey( dep );
                        Set<ArtifactVersion> versions = allVersions.get( key );
                        ArtifactVersion version = new DefaultArtifactVersion( versionRange );
                        if ( versions != null )
                        {
                            if ( !versions.contains( version ) )
                            {
                                String lowerBound = String.format( "[%d.%d.%d,",
                                    version.getMajorVersion(),
                                    version.getMinorVersion(),
                                    version.getIncrementalVersion() );
                                for ( int i = 3; i > 0; --i )
                                {
                                    String upperBound = null;
                                    switch ( i )
                                    {
                                        case 3:
                                            upperBound = String.format( "%d.%d.%d)",
                                                version.getMajorVersion(),
                                                version.getMinorVersion(),
                                                version.getIncrementalVersion() + 1 );
                                            break;

                                        case 2:
                                            upperBound = String.format( "%d.%d)",
                                                version.getMajorVersion(),
                                                version.getMinorVersion() + 1 );
                                            break;

                                        case 1:
                                            upperBound = String.format( "%d)", version.getMajorVersion() + 1 );
                                            break;

                                        default: break; // satisfy checkstyle
                                    }

                                    if ( selectVersion( dep, lowerBound + upperBound, versions ) )
                                    {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                catch ( InvalidVersionSpecificationException e )
                {
                    throw new MojoFailureException(
                            Messages.getString( "EclipseToMavenMojo.invalidversionrange",
                                new Object[] { dep, getKey( model ) } ) );
                }
            }
        }
    }

    private boolean selectVersion( Dependency dependency, String versionRange, Set<ArtifactVersion> versions )
        throws InvalidVersionSpecificationException
    {
        VersionRange range = VersionRange.createFromVersionSpec( versionRange );
        for ( ArtifactVersion version : versions )
        {
            if ( range.containsVersion( version ) )
            {
                dependency.setVersion( version.toString() );
                return true;
            }
        }
        return false;
    }

    /**
     * Get a {@link EclipseOsgiPlugin} object from a plugin jar/dir found in the target dir.
     *
     * @param file plugin jar or dir
     * @throws MojoExecutionException if anything bad happens while parsing files
     */
    private EclipseOsgiPlugin getEclipsePlugin( File file )
        throws MojoExecutionException
    {
        if ( file.isDirectory() )
        {
            return new ExplodedPlugin( file );
        }
        else if ( file.getName().endsWith( ".jar" ) ) 
        {
            try
            {
                return new PackagedPlugin( file );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( Messages.getString( "EclipseToMavenMojo.unabletoaccessjar",
                                                                      file.getAbsolutePath() ), e );
            }
        }

        return null;
    }

    /**
     * Create the {@link Model} from a plugin manifest
     *
     * @param plugin Eclipse plugin jar or dir
     * @throws MojoExecutionException if anything bad happens while parsing files
     */
    private Model createModel( EclipseOsgiPlugin plugin )
        throws MojoExecutionException
    {

        String name, bundleName, version, groupId, artifactId, requireBundle;

        try
        {
            if ( !plugin.hasManifest() )
            {
                getLog().warn( Messages.getString( "EclipseToMavenMojo.plugindoesnothavemanifest", plugin ) ); 
                return null;
            }

            Analyzer analyzer = new Analyzer();

            Map bundleSymbolicNameHeader =
                analyzer.parseHeader( plugin.getManifestAttribute( Analyzer.BUNDLE_SYMBOLICNAME ) );
            bundleName = (String) bundleSymbolicNameHeader.keySet().iterator().next();
            version = plugin.getManifestAttribute( Analyzer.BUNDLE_VERSION );

            if ( bundleName == null || version == null )
            {
                getLog().error( Messages.getString( "EclipseToMavenMojo.unabletoreadbundlefrommanifest" ) ); 
                return null;
            }

            version = osgiVersionToMavenVersion( version );

            name = plugin.getManifestAttribute( Analyzer.BUNDLE_NAME );

            requireBundle = plugin.getManifestAttribute( Analyzer.REQUIRE_BUNDLE );

        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( Messages.getString( "EclipseToMavenMojo.errorprocessingplugin", plugin ),
                                              e ); //$NON-NLS-1$
        }

        Dependency[] deps = parseDependencies( requireBundle );

        groupId = createGroupId( bundleName );
        artifactId = createArtifactId( bundleName );

        Model model = new Model();
        model.setModelVersion( "4.0.0" ); 
        model.setGroupId( groupId );
        model.setArtifactId( artifactId );
        model.setName( name );
        model.setVersion( version );

        model.setProperties( plugin.getPomProperties() );

        if ( groupId.startsWith( "org.eclipse" ) ) 
        {
            // why do we need a parent?

            // Parent parent = new Parent();
            // parent.setGroupId( "org.eclipse" );
            // parent.setArtifactId( "eclipse" );
            // parent.setVersion( "1" );
            // model.setParent( parent );

            // infer license for know projects, everything at eclipse is licensed under EPL
            // maybe too simplistic, but better than nothing
            License license = new License();
            license.setName( "Eclipse Public License - v 1.0" ); 
            license.setUrl( "http://www.eclipse.org/org/documents/epl-v10.html" ); 
            model.addLicense( license );
        }

        if ( deps.length > 0 )
        {
            for ( Dependency dep : deps )
            {
                model.getDependencies().add( dep );
            }

        }

        return model;
    }

    /**
     * Writes the artifact to the repo
     *
     * @param model
     * @param remoteRepo remote repository (if set)
     * @throws MojoExecutionException
     */
    private void writeArtifact( Model model, Map<String, EclipseOsgiPlugin> plugins, ArtifactRepository remoteRepo )
        throws MojoExecutionException
    {
        Writer fw = null;
        ArtifactMetadata metadata;
        File pomFile = null;
        Artifact pomArtifact =
            artifactFactory.createArtifact( model.getGroupId(), model.getArtifactId(), model.getVersion(), null, 
                                            "pom" );
        Artifact artifact =
            artifactFactory.createArtifact( model.getGroupId(), model.getArtifactId(), model.getVersion(), null,
                                            Constants.PROJECT_PACKAGING_JAR );
        Artifact sourcesArtifact = !attachSourcePlugins
            ? null
            :  artifactFactory.createArtifactWithClassifier( model.getGroupId(), model.getArtifactId(),
                model.getVersion(), Constants.PROJECT_PACKAGING_JAR, "sources" );
        try
        {
            pomFile = File.createTempFile( "pom-", ".xml" ); 

            fw = WriterFactory.newWriter( pomFile, "UTF-8" );
            model.setModelEncoding( "UTF-8" ); // to be removed when encoding is detected instead of forced to UTF-8 
            pomFile.deleteOnExit();
            new MavenXpp3Writer().write( fw, model );
            metadata = new ProjectArtifactMetadata( pomArtifact, pomFile );
            pomArtifact.addMetadata( metadata );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( Messages.getString( "EclipseToMavenMojo.errorwritingtemporarypom",
                                                                  e.getMessage() ), e );
        }
        finally
        {
            IOUtil.close( fw );
        }

        EclipseOsgiPlugin plugin = plugins.get( getKey( model ) );
        EclipseOsgiPlugin sourcePlugin = attachSourcePlugins ? plugins.get( getSourceKey( model ) ) : null;
        File jarFile = null;
        File jarFileSource = null;

        try
        {
            jarFile = plugin.getJarFile();
            jarFileSource = sourcePlugin != null ? sourcePlugin.getJarFile() : null;

            if ( remoteRepo != null )
            {
                deployer.deploy( pomFile, pomArtifact, remoteRepo, localRepository );
                deployer.deploy( jarFile, artifact, remoteRepo, localRepository );
                if ( sourcePlugin != null )
                {
                    deployer.deploy( jarFileSource, sourcesArtifact, remoteRepo, localRepository );
                }
            }
            else
            {
                installer.install( pomFile, pomArtifact, localRepository );
                installer.install( jarFile, artifact, localRepository );
                if ( sourcePlugin != null )
                {
                    installer.install( jarFileSource, sourcesArtifact, localRepository );
                }
            }
        }
        catch ( ArtifactDeploymentException e )
        {
            throw new MojoExecutionException( 
                                  Messages.getString( "EclipseToMavenMojo.errordeployartifacttorepository" ), e );
        }
        catch ( ArtifactInstallationException e )
        {
            throw new MojoExecutionException( 
                                  Messages.getString( "EclipseToMavenMojo.errorinstallartifacttorepository" ), e );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( 
                                  Messages.getString( "EclipseToMavenMojo.errorgettingjarfileforplugin",
                                      jarFile != null ? sourcePlugin :  plugin ), e );
        }
        finally
        {
            pomFile.delete();
        }

    }

    protected String osgiVersionToMavenVersion( String version )
    {
        return osgiVersionToMavenVersion( version, null, stripQualifier );
    }

    /**
     * The 4th (build) token MUST be separated with "-" and not with "." in maven. A version with 4 dots is not parsed,
     * and the whole string is considered a qualifier. See tests in DefaultArtifactVersion for reference.
     *
     * @param version initial version
     * @param forcedQualifier build number
     * @param stripQualifier always remove 4th token in version
     * @return converted version
     */
    protected String osgiVersionToMavenVersion( String version, String forcedQualifier, boolean stripQualifier )
    {
        if ( stripQualifier && StringUtils.countMatches( version, "." ) > 2 )
        {
            version = StringUtils.substring( version, 0, version.lastIndexOf( '.' ) );
        }
        else if ( StringUtils.countMatches( version, "." ) > 2 )
        {
            int lastDot = version.lastIndexOf( '.' );
            if ( StringUtils.isNotEmpty( forcedQualifier ) )
            {
                version = StringUtils.substring( version, 0, lastDot ) + "-" + forcedQualifier;
            }
            else
            {
                version = StringUtils.substring( version, 0, lastDot ) + "-" 
                    + StringUtils.substring( version, lastDot + 1, version.length() );
            }
        }
        return new DefaultArtifactVersion( version ).toString();
    }

    /**
     * Resolves the deploy<code>deployTo</code> parameter to an <code>ArtifactRepository</code> instance (if set).
     *
     * @return ArtifactRepository instance of null if <code>deployTo</code> is not set.
     * @throws MojoFailureException
     * @throws MojoExecutionException
     */
    private ArtifactRepository resolveRemoteRepo()
        throws MojoFailureException, MojoExecutionException
    {
        if ( deployTo != null )
        {
            Matcher matcher = DEPLOYTO_PATTERN.matcher( deployTo );

            if ( !matcher.matches() )
            {
                throw new MojoFailureException( deployTo,
                                            Messages.getString( "EclipseToMavenMojo.invalidsyntaxforrepository" ),
                                            Messages.getString( "EclipseToMavenMojo.invalidremoterepositorysyntax" ) );
            }
            else
            {
                String id = matcher.group( 1 ).trim();
                String layout = matcher.group( 2 ).trim();
                String url = matcher.group( 3 ).trim();

                ArtifactRepositoryLayout repoLayout;
                try
                {
                    repoLayout = (ArtifactRepositoryLayout) container.lookup( ArtifactRepositoryLayout.ROLE, layout );
                }
                catch ( ComponentLookupException e )
                {
                    throw new MojoExecutionException(
                                              Messages.getString( "EclipseToMavenMojo.cannotfindrepositorylayout",
                                                                          layout ), e ); 
                }

                return artifactRepositoryFactory.createDeploymentArtifactRepository( id, url, repoLayout, true );
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public void contextualize( Context context )
        throws ContextException
    {
        this.container = (PlexusContainer) context.get( PlexusConstants.PLEXUS_KEY );
    }

    /**
     * Get the group id as the tokens until last dot e.g. <code>org.eclipse.jdt</code> -> <code>org.eclipse</code>
     *
     * @param bundleName bundle name
     * @return group id
     */
    protected String createGroupId( String bundleName )
    {
        int i = 0;
        int t = groupIdTokens;
        if ( t < 0 )
        {
            i = bundleName.lastIndexOf( '.' ); //$NON-NLS-1$
        }
        else if ( t > 0 )
        {
            do
            {
                i = bundleName.indexOf( '.', i + 1 ); //$NON-NLS-1$
            } while ( --t > 0 && i > 0 );
        }
        if ( i > 0 )
        {
            return bundleName.substring( 0, i );
        }
        else
        {
            return bundleName;
        }
    }

    /**
     * Get the artifact id as the tokens after last dot e.g. <code>org.eclipse.jdt</code> -> <code>jdt</code>
     *
     * @param bundleName bundle name
     * @return artifact id
     */
    protected String createArtifactId( String bundleName )
    {
        if ( !useBundleNameAsArtifactId )
        {
            String groupId = createGroupId( bundleName );
            if ( bundleName.startsWith( groupId + '.' ) )  //$NON-NLS-1$
            {
                return bundleName.substring( groupId.length() + 1 );
            }
        }
        return bundleName;
    }

    /**
     * Parses the "Require-Bundle" and convert it to a list of dependencies.
     *
     * @param requireBundle "Require-Bundle" entry
     * @return an array of <code>Dependency</code>
     */
    protected Dependency[] parseDependencies( String requireBundle )
    {
        if ( requireBundle == null )
        {
            return new Dependency[0];
        }

        List<Dependency> dependencies = new ArrayList<Dependency>();

        Analyzer analyzer = new Analyzer();

        @SuppressWarnings( "unchecked" )
        Map<String, Map<String, String>> requireBundleHeader = analyzer.parseHeader( requireBundle );

        // now iterates on bundles and extract dependencies
        for ( Map.Entry<String, Map<String, String>> entry : requireBundleHeader.entrySet() )
        {
            String bundleName = entry.getKey();
            Map<String, String> attributes = entry.getValue();

            String version = attributes.get( Analyzer.BUNDLE_VERSION.toLowerCase() );
            boolean optional = "optional".equals( attributes.get( "resolution:" ) );

            if ( version == null )
            {
                getLog().debug( Messages.getString( "EclipseToMavenMojo.missingversionforbundle", bundleName ) );
                version = ANY_VERSION;
            }

            version = fixBuildNumberSeparator( version );

            Dependency dep = new Dependency();
            dep.setGroupId( createGroupId( bundleName ) );
            dep.setArtifactId( createArtifactId( bundleName ) );
            dep.setVersion( version );
            dep.setOptional( optional );

            dependencies.add( dep );

        }

        return dependencies.toArray( new Dependency[dependencies.size()] );
    }

    /**
     * Fix the separator for the 4th token in a versions. In maven this must be "-", in OSGI it's "."
     *
     * @param versionRange input range
     * @return modified version range
     */
    protected String fixBuildNumberSeparator( String versionRange )
    {
        // should not be called with a null versionRange, but a check doesn't hurt...
        if ( versionRange == null )
        {
            return null;
        }

        StringBuffer newVersionRange = new StringBuffer();

        Matcher matcher = VERSION_PATTERN.matcher( versionRange );

        while ( matcher.find() )
        {
            String group = matcher.group();

            if ( StringUtils.countMatches( group, "." ) > 2 ) //$NON-NLS-1$
            {
                // build number found, fix it
                int lastDot = group.lastIndexOf( '.' ); //$NON-NLS-1$
                group = StringUtils.substring( group, 0, lastDot ) + "-" //$NON-NLS-1$
                    + StringUtils.substring( group, lastDot + 1, group.length() );
            }
            matcher.appendReplacement( newVersionRange, group );
        }

        matcher.appendTail( newVersionRange );

        return newVersionRange.toString();
    }

}
