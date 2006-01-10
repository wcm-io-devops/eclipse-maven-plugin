package org.apache.maven.plugin.eclipse.writers;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.eclipse.EclipseSourceDir;
import org.apache.maven.plugin.eclipse.EclipseUtils;
import org.apache.maven.plugin.eclipse.Messages;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.XMLWriter;

/**
 * Creates a .settings folder for Eclipse WTP 1.xRCx release and writes out the configuration under it.
 * 
 * @author <a href="mailto:rahul.thakur.xdev@gmail.com">Rahul Thakur</a>
 * @author <a href="mailto:fgiust@apache.org">Fabrizio Giustina</a>
 * @version $Id$
 */
public class EclipseWtpSettingsWriter
    extends AbstractWtpResourceWriter
{

    private static final String FACET_JST_EAR = "jst.ear"; //$NON-NLS-1$

    private static final String ATTR_CONTEXT_ROOT = "context-root"; //$NON-NLS-1$

    private static final String ATTR_VERSION = "version"; //$NON-NLS-1$

    private static final String ELT_INSTALLED = "installed"; //$NON-NLS-1$

    private static final String FACET_JST_EJB = "jst.ejb"; //$NON-NLS-1$

    private static final String FACET_JST_WEB = "jst.web"; //$NON-NLS-1$

    private static final String FACET_JST_JAVA = "jst.java"; //$NON-NLS-1$

    private static final String ATTR_FACET = "facet"; //$NON-NLS-1$

    private static final String ELT_FIXED = "fixed"; //$NON-NLS-1$

    private static final String ELT_FACETED_PROJECT = "faceted-project"; //$NON-NLS-1$

    /**
     * The .settings folder for Web Tools Project 1.x release.
     */
    private static final String DIR_WTP_SETTINGS = ".settings"; //$NON-NLS-1$

    /**
     * File name where the WTP component settings will be stored for our Eclipse Project.
     */
    private static final String FILE_DOT_COMPONENT = ".component"; //$NON-NLS-1$

    /**
     * File name where Eclipse Project's Facet configuration will be stored.
     */
    private static final String FILE_FACET_CORE_XML = "org.eclipse.wst.common.project.facet.core.xml"; //$NON-NLS-1$

    public EclipseWtpSettingsWriter( Log log, File eclipseProjectDir, MavenProject project, Collection artifacts )
    {
        super( log, eclipseProjectDir, project, artifacts );
    }

    public void write( List referencedReactorArtifacts, EclipseSourceDir[] sourceDirs,
                      ArtifactRepository localRepository, File buildOutputDirectory )
        throws MojoExecutionException
    {
        // delete the .settings directory (if exists)
        File settingsDir = new File( getEclipseProjectDirectory(), DIR_WTP_SETTINGS );
        if ( settingsDir.isDirectory() && !settingsDir.delete() )
        {
            // force delete
            try
            {
                FileUtils.forceDelete( settingsDir );
            }
            catch ( IOException e )
            {
                if ( getLog().isErrorEnabled() )
                {
                    getLog().error( "Unable to delete directory " + DIR_WTP_SETTINGS );
                }
            }
        }

        // create a .settings directory
        settingsDir.mkdirs();
        FileWriter w;
        try
        {
            w = new FileWriter( new File( settingsDir, FILE_DOT_COMPONENT ) );
        }
        catch ( IOException ex )
        {
            throw new MojoExecutionException( Messages.getString( "EclipsePlugin.erroropeningfile" ), ex ); //$NON-NLS-1$
        }

        // create a .component file and write out to it
        XMLWriter writer = new PrettyPrintXMLWriter( w );
        String packaging = getProject().getPackaging();
        writeModuleTypeComponent( writer, packaging, buildOutputDirectory, referencedReactorArtifacts, localRepository );
        IOUtil.close( w );

        // Write out facet core xml
        try
        {
            w = new FileWriter( new File( settingsDir, FILE_FACET_CORE_XML ) );
        }
        catch ( IOException ex )
        {
            throw new MojoExecutionException( Messages.getString( "EclipsePlugin.erroropeningfile" ), ex ); //$NON-NLS-1$
        }
        writer = new PrettyPrintXMLWriter( w );
        writeModuleTypeFacetCore( writer, packaging );
        IOUtil.close( w );
    }

    /**
     * Writes out the module type settings for a Web Tools Project to a {@link #FILE_DOT_COMPONENT}.
     * 
     * @param writer
     * @param packaging
     * @param buildOutputDirectory
     * @param referencedReactorArtifacts
     * @param localRepository
     * @throws MojoExecutionException
     */
    private void writeModuleTypeComponent( XMLWriter writer, String packaging, File buildOutputDirectory,
                                          List referencedReactorArtifacts, ArtifactRepository localRepository )
        throws MojoExecutionException
    {
        writer.startElement( ELT_PROJECT_MODULES );
        writer.addAttribute( ATTR_MODULE_ID, "moduleCoreId" ); //$NON-NLS-1$
        writer.startElement( ELT_WB_MODULE );
        writer.addAttribute( ATTR_DEPLOY_NAME, getProject().getArtifactId() );
        if ( "war".equalsIgnoreCase( packaging ) ) //$NON-NLS-1$
        {
            writer.startElement( ELT_WB_RESOURCE );
            writer.addAttribute( ATTR_DEPLOY_PATH, "/WEB-INF/classes" ); //$NON-NLS-1$
            writer.addAttribute( ATTR_SOURCE_PATH, EclipseUtils.toRelativeAndFixSeparator( getProject().getBasedir(),
                                                                                           new File( getProject()
                                                                                               .getBuild()
                                                                                               .getSourceDirectory() ),
                                                                                           false ) );
            writer.endElement();
            String warSourceDirectory = EclipseUtils.getPluginSetting( getProject(), ARTIFACT_MAVEN_WAR_PLUGIN,
                                                                       "warSourceDirectory", "/src/main/webapp" ); //$NON-NLS-1$ //$NON-NLS-2$
            writer.startElement( ELT_WB_RESOURCE );
            writer.addAttribute( ATTR_DEPLOY_PATH, "/" ); //$NON-NLS-1$
            writer.addAttribute( ATTR_SOURCE_PATH, EclipseUtils
                .toRelativeAndFixSeparator( getProject().getBasedir(), new File( getEclipseProjectDirectory(),
                                                                                 warSourceDirectory ), false ) );
            writer.endElement();
        }
        else if ( "ear".equalsIgnoreCase( packaging ) ) //$NON-NLS-1$
        {
            writer.startElement( ELT_WB_RESOURCE );
            writer.addAttribute( ATTR_DEPLOY_PATH, "/ejbmodule" ); //$NON-NLS-1$
            writer.endElement();
        }
        // write out the dependencies.
        writeWarOrEarResources( writer, getProject(), referencedReactorArtifacts, localRepository );

        // fix for WTP 1.0
        copyExternalDependencies( writer, getProject(), referencedReactorArtifacts, localRepository );

        // write out properties.
        writer.startElement( ELT_PROPERTY );
        writer.addAttribute( ATTR_NAME, "java-output-path" ); //$NON-NLS-1$
        writer.addAttribute( ATTR_VALUE, "/" //$NON-NLS-1$
            + EclipseUtils.toRelativeAndFixSeparator( getProject().getBasedir(), buildOutputDirectory, false ) );

        // close elements
        writer.endElement(); // property
        writer.startElement( ELT_PROPERTY );
        writer.addAttribute( ATTR_CONTEXT_ROOT, getProject().getArtifactId() );
        writer.endElement(); // property
        writer.endElement(); // wb-module
        writer.endElement(); // project-modules
    }

    /**
     * Writes out the facet info for a faceted-project based on the packaging.
     * 
     * @param writer
     * @param packaging
     */
    private void writeModuleTypeFacetCore( XMLWriter writer, String packaging )
    {
        writer.startElement( ELT_FACETED_PROJECT );
        // common facet
        writer.startElement( ELT_FIXED );
        writer.addAttribute( ATTR_FACET, FACET_JST_JAVA );
        writer.endElement(); // element fixed
        if ( "war".equalsIgnoreCase( packaging ) ) //$NON-NLS-1$
        {
            writer.startElement( ELT_FIXED );
            writer.addAttribute( ATTR_FACET, FACET_JST_WEB );
            writer.endElement(); // fixed
            writer.startElement( ELT_INSTALLED );
            writer.addAttribute( ATTR_FACET, FACET_JST_WEB );
            writer.addAttribute( ATTR_VERSION, resolveServletVersion() );
            writer.endElement(); // installed
        }
        else if ( "ejb".equalsIgnoreCase( packaging ) ) //$NON-NLS-1$
        {
            writer.startElement( ELT_FIXED );
            writer.addAttribute( ATTR_FACET, FACET_JST_EJB );
            writer.endElement(); // fixed
            writer.startElement( ELT_INSTALLED );
            writer.addAttribute( ATTR_FACET, FACET_JST_EJB );
            writer.addAttribute( ATTR_VERSION, resolveEjbVersion() );
            writer.endElement(); // installed
        }
        else if ( "ear".equalsIgnoreCase( packaging ) ) //$NON-NLS-1$
        {
            writer.startElement( ELT_FIXED );
            writer.addAttribute( ATTR_FACET, FACET_JST_EAR );
            writer.endElement(); // fixed
            writer.startElement( ELT_INSTALLED );
            writer.addAttribute( ATTR_FACET, FACET_JST_EAR );
            writer.addAttribute( ATTR_VERSION, resolveJ2eeVersion() );
            writer.endElement(); // installed
        }

        // common installed element
        writer.startElement( ELT_INSTALLED );
        writer.addAttribute( ATTR_FACET, FACET_JST_JAVA );
        writer.addAttribute( ATTR_VERSION, resolveJavaVersion() );
        writer.endElement(); // installed
        writer.endElement(); // faceted-project
    }

    /**
     * Patch fpr WTP 1.0, external libraries are not copied to deployed app.
     * See <a
     * href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=116783">https://bugs.eclipse.org/bugs/show_bug.cgi?id=116783</a>
     */
    protected void copyExternalDependencies( XMLWriter writer, MavenProject project, List referencedReactorArtifacts,
                                            ArtifactRepository localRepository )
        throws MojoExecutionException
    {
        ScopeArtifactFilter scopeFilter = new ScopeArtifactFilter( Artifact.SCOPE_RUNTIME );
        String warSourceDirectory = EclipseUtils.getPluginSetting( getProject(), ARTIFACT_MAVEN_WAR_PLUGIN,
                                                                   "warSourceDirectory", "/src/main/webapp/" ); //$NON-NLS-1$ //$NON-NLS-2$

        File webInfLibDir = new File( getEclipseProjectDirectory() + "/" + warSourceDirectory + "/WEB-INF/lib" ); //$NON-NLS-1$ //$NON-NLS-2$
        String webInfLibDirAsString = EclipseUtils.toRelativeAndFixSeparator( getProject().getBasedir(), webInfLibDir,
                                                                              false );

        if ( getLog().isWarnEnabled() )
        {
            getLog().warn( "----------------------------------------------------------------------------" );
            getLog().warn( "Copying over dependencies for WTP1.0 Project to directory: " + webInfLibDirAsString );
            getLog()
                .warn(
                       "Please NOTE that this is a patch to allow publishing external dependencies for a WTP1.0 project." );
            getLog().warn( "----------------------------------------------------------------------------" );
        }

        // dependencies
        for ( Iterator it = getDependencies().iterator(); it.hasNext(); )
        {
            Artifact artifact = (Artifact) it.next();
            String type = artifact.getType();

            if ( !referencedReactorArtifacts.contains( artifact )
                && ( scopeFilter.include( artifact ) || Artifact.SCOPE_SYSTEM.equals( artifact.getScope() ) )
                && ( "jar".equals( type ) || "ejb".equals( type ) || "ejb-client".equals( type ) || "war".equals( type ) ) ) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            {
                // we want this bit container independent, so copy over everything to /WEB-INF/lib under our eclipse
                // warSourceDirectory and add a deploy-path so that resources get published.
                try
                {
                    getLog().info( "Copying dependency: " + artifact.getFile().getName() + "..." );
                    FileUtils.copyFileToDirectory( artifact.getFile(), webInfLibDir );
                }
                catch ( IOException e )
                {
                    // we log the error and still go ahead with the wtp project creation.

                    getLog().error(
                                    "Unable to copy dependency: " + artifact.getFile().getAbsolutePath()
                                        + " over to web app lib directory : " + webInfLibDirAsString );
                }
            }
        }
        if ( getLog().isWarnEnabled() )
        {
            getLog().warn( "----------------------------------------------------------------------------" );
            getLog().warn( "WTP1.0 Project dependencies copied!" );
            getLog().warn( "----------------------------------------------------------------------------" );
        }
        writer.startElement( ELT_WB_RESOURCE );
        writer.addAttribute( ATTR_DEPLOY_PATH, "/WEB-INF/lib" ); //$NON-NLS-1$
        writer.addAttribute( ATTR_SOURCE_PATH, webInfLibDirAsString );
        writer.endElement();
    }

}