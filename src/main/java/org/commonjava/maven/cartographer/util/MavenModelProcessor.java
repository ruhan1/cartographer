/*******************************************************************************
 * Copyright 2011 John Casey
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.commonjava.maven.cartographer.util;

import java.net.URI;
import java.util.List;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.commonjava.maven.atlas.graph.model.EProjectDirectRelationships;
import org.commonjava.maven.atlas.graph.model.EProjectDirectRelationships.Builder;
import org.commonjava.maven.atlas.graph.rel.DependencyRelationship;
import org.commonjava.maven.atlas.graph.rel.ExtensionRelationship;
import org.commonjava.maven.atlas.graph.rel.ParentRelationship;
import org.commonjava.maven.atlas.graph.rel.PluginRelationship;
import org.commonjava.maven.atlas.graph.rel.ProjectRelationship;
import org.commonjava.maven.atlas.graph.util.RelationshipUtils;
import org.commonjava.maven.atlas.ident.ref.ArtifactRef;
import org.commonjava.maven.atlas.ident.ref.InvalidRefException;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.version.InvalidVersionSpecificationException;
import org.commonjava.maven.cartographer.data.CartoDataException;
import org.commonjava.maven.cartographer.data.CartoDataManager;
import org.commonjava.maven.cartographer.discover.DiscoveryResult;
import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.commonjava.maven.galley.maven.model.view.DependencyView;
import org.commonjava.maven.galley.maven.model.view.ExtensionView;
import org.commonjava.maven.galley.maven.model.view.MavenPomView;
import org.commonjava.maven.galley.maven.model.view.ParentView;
import org.commonjava.maven.galley.maven.model.view.PluginView;
import org.commonjava.util.logging.Logger;

@ApplicationScoped
public class MavenModelProcessor
{

    private final Logger logger = new Logger( MavenModelProcessor.class );

    @Inject
    private CartoDataManager dataManager;

    private final boolean processManagedInfo = false;

    private final boolean processBuildInfo = true;

    protected MavenModelProcessor()
    {
    }

    public MavenModelProcessor( final CartoDataManager dataManager )
    {
        this.dataManager = dataManager;
    }

    public DiscoveryResult storeModelRelationships( final MavenPomView pomView, final URI source )
        throws CartoDataException
    {
        final DiscoveryResult fromRead = readRelationships( pomView, source );
        final ProjectVersionRef projectRef = fromRead.getSelectedRef();
        dataManager.clearErrors( projectRef );
        final Set<ProjectRelationship<?>> skipped = dataManager.storeRelationships( fromRead.getAllDiscoveredRelationships() );

        return new DiscoveryResult( source, fromRead, skipped );

    }

    public DiscoveryResult readRelationships( final MavenPomView pomView, final URI source )
        throws CartoDataException
    {
        logger.info( "Reading relationships for: %s\n  (from: %s)", pomView.getRef(), source );

        try
        {
            final ProjectVersionRef projectRef = pomView.getRef();

            final EProjectDirectRelationships.Builder builder = new EProjectDirectRelationships.Builder( source, projectRef );

            addParentRelationship( source, builder, pomView, projectRef );

            addDependencyRelationships( source, builder, pomView, projectRef );

            if ( processBuildInfo )
            {
                addExtensionUsages( source, builder, pomView, projectRef );
                addPluginUsages( source, builder, pomView, projectRef );
            }

            final EProjectDirectRelationships rels = builder.build();
            return new DiscoveryResult( source, projectRef, rels.getAllRelationships() );
        }
        catch ( final InvalidVersionSpecificationException e )
        {
            throw new CartoDataException( "Failed to parse version for model: %s. Reason: %s", e, pomView, e.getMessage() );
        }
        catch ( final IllegalArgumentException e )
        {
            throw new CartoDataException( "Failed to parse relationships for model: %s. Reason: %s", e, pomView, e.getMessage() );
        }
    }

    private void addExtensionUsages( final URI source, final Builder builder, final MavenPomView pomView, final ProjectVersionRef projectRef )
        throws CartoDataException
    {
        List<ExtensionView> extensions = null;
        try
        {
            extensions = pomView.getBuildExtensions();
        }
        catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
        {
            logger.error( "Cannot retrieve build extensions: %s", e, e.getMessage() );
        }

        for ( final ExtensionView ext : extensions )
        {
            if ( ext == null )
            {
                continue;
            }

            try
            {
                final ProjectVersionRef ref = ext.asProjectVersionRef();

                builder.withExtensions( new ExtensionRelationship( source, projectRef, ref, builder.getNextExtensionIndex() ) );
            }
            catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
            {
                logger.error( "Build extension is invalid! Reason: %s. Skipping:\n\n%s\n\n", e, e.getMessage(), ext.toXML() );
            }
        }
    }

    private void addPluginUsages( final URI source, final Builder builder, final MavenPomView pomView, final ProjectVersionRef projectRef )
        throws CartoDataException
    {
        addBuildPluginUsages( source, builder, pomView, projectRef );
        addReportPluginUsages( source, builder, pomView, projectRef );
        addSiteReportPluginUsages( source, builder, pomView, projectRef );
    }

    private void addSiteReportPluginUsages( final URI source, final Builder builder, final MavenPomView pomView, final ProjectVersionRef projectRef )
        throws CartoDataException
    {
        //        final List<ProjectVersionRefView> refs = pomView.getProjectVersionRefs( "//plugin[artifactId/text()=\"maven-site-plugin\"]//reportPlugin" );

        List<PluginView> plugins = null;
        try
        {
            plugins = pomView.getAllPluginsMatching( "//plugin[artifactId/text()=\"maven-site-plugin\"]//reportPlugin" );
        }
        catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
        {
            logger.error( "Cannot retrieve site-plugin nested reporting plugins: %s", e, e.getMessage() );
        }

        if ( plugins != null )
        {
            for ( final PluginView plugin : plugins )
            {
                try
                {
                    final ProjectVersionRef ref = plugin.asProjectVersionRef();
                    final String profileId = plugin.getProfileId();
                    final URI location = RelationshipUtils.profileLocation( profileId );

                    builder.withPlugins( new PluginRelationship( source, location, projectRef, ref, builder.getNextPluginIndex( false ), false ) );
                }
                catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
                {
                    logger.error( "Site-plugin nested reporting plugin is invalid! Reason: %s. Skipping:\n\n%s\n\n", e, e.getMessage(),
                                  plugin.toXML() );
                }
            }
        }
    }

    public void addReportPluginUsages( final URI source, final Builder builder, final MavenPomView pomView, final ProjectVersionRef projectRef )
        throws CartoDataException
    {
        List<PluginView> plugins = null;
        try
        {
            plugins = pomView.getAllPluginsMatching( "//reporting/plugins/plugin" );
        }
        catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
        {
            logger.error( "Cannot retrieve reporting plugins: %s", e, e.getMessage() );
        }

        if ( plugins != null )
        {
            for ( final PluginView plugin : plugins )
            {
                try
                {
                    final ProjectVersionRef ref = plugin.asProjectVersionRef();
                    final String profileId = plugin.getProfileId();
                    final URI location = RelationshipUtils.profileLocation( profileId );

                    builder.withPlugins( new PluginRelationship( source, location, projectRef, ref, builder.getNextPluginDependencyIndex( projectRef,
                                                                                                                                          false ),
                                                                 false ) );
                }
                catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
                {
                    logger.error( "Reporting plugin is invalid! Reason: %s. Skipping:\n\n%s\n\n", e, e.getMessage(), plugin.toXML() );
                }
            }
        }
    }

    public void addBuildPluginUsages( final URI source, final Builder builder, final MavenPomView pomView, final ProjectVersionRef projectRef )
        throws CartoDataException
    {
        if ( processManagedInfo )
        {
            List<PluginView> plugins = null;
            try
            {
                plugins = pomView.getAllManagedBuildPlugins();
            }
            catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
            {
                logger.error( "Cannot retrieve managed plugins: %s", e, e.getMessage() );
            }

            if ( plugins != null )
            {
                for ( final PluginView plugin : plugins )
                {
                    try
                    {
                        final ProjectVersionRef ref = plugin.asProjectVersionRef();

                        final String profileId = plugin.getProfileId();
                        final URI location = RelationshipUtils.profileLocation( profileId );

                        builder.withPlugins( new PluginRelationship( source, location, projectRef, ref,
                                                                     builder.getNextPluginDependencyIndex( projectRef, true ), true ) );
                    }
                    catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
                    {
                        logger.error( "Managed plugin is invalid! Reason: %s. Skipping:\n\n%s\n\n", e, e.getMessage(), plugin.toXML() );
                    }
                }
            }
        }

        List<PluginView> plugins = null;
        try
        {
            plugins = pomView.getAllBuildPlugins();
        }
        catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
        {
            logger.error( "Cannot retrieve build plugins: %s", e, e.getMessage() );
        }

        if ( plugins != null )
        {
            for ( final PluginView plugin : plugins )
            {
                try
                {
                    final ProjectVersionRef ref = plugin.asProjectVersionRef();
                    final String profileId = plugin.getProfileId();
                    final URI location = RelationshipUtils.profileLocation( profileId );

                    builder.withPlugins( new PluginRelationship( source, location, projectRef, ref, builder.getNextPluginDependencyIndex( projectRef,
                                                                                                                                          false ),
                                                                 false ) );
                }
                catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
                {
                    logger.error( "Build plugin is invalid! Reason: %s. Skipping:\n\n%s\n\n", e, e.getMessage(), plugin.toXML() );
                }
            }
        }
    }

    protected void addDependencyRelationships( final URI source, final Builder builder, final MavenPomView pomView, final ProjectVersionRef projectRef )
    {
        if ( processManagedInfo )
        {
            List<DependencyView> deps = null;
            try
            {
                deps = pomView.getAllManagedDependencies();
            }
            catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
            {
                logger.error( "Failed to retrieve managed dependencies: %s. Skipping", e, e.getMessage() );
            }

            if ( deps != null )
            {
                for ( final DependencyView dep : deps )
                {
                    try
                    {
                        final ProjectVersionRef ref = dep.asProjectVersionRef();

                        final String profileId = dep.getProfileId();
                        final URI location = RelationshipUtils.profileLocation( profileId );

                        final ArtifactRef artifactRef = new ArtifactRef( ref, dep.getType(), dep.getClassifier(), dep.isOptional() );

                        builder.withDependencies( new DependencyRelationship( source, location, projectRef, artifactRef, dep.getScope(),
                                                                              builder.getNextDependencyIndex( true ), true ) );
                    }
                    catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
                    {
                        logger.error( "Managed dependency is invalid! Reason: %s. Skipping:\n\n%s\n\n", e, e.getMessage(), dep.toXML() );
                    }
                }
            }
        }

        // regardless of whether we're processing managed info, this is STRUCTURAL, so always grab it!
        List<DependencyView> boms = null;
        try
        {
            boms = pomView.getAllBOMs();
        }
        catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
        {
            logger.error( "Failed to retrieve BOM declarations: %s. Skipping", e, e.getMessage() );
        }

        if ( boms != null )
        {
            for ( final DependencyView bom : boms )
            {
                try
                {
                    final ProjectVersionRef ref = bom.asProjectVersionRef();
                    final String profileId = bom.getProfileId();
                    final URI location = RelationshipUtils.profileLocation( profileId );

                    final ArtifactRef artifactRef = new ArtifactRef( ref, bom.getType(), bom.getClassifier(), bom.isOptional() );

                    builder.withDependencies( new DependencyRelationship( source, location, projectRef, artifactRef, bom.getScope(),
                                                                          builder.getNextDependencyIndex( true ), true ) );
                }
                catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
                {
                    logger.error( "BOM declaration is invalid! Reason: %s. Skipping:\n\n%s\n\n", e, e.getMessage(), bom.toXML() );
                }
            }
        }

        List<DependencyView> deps = null;
        try
        {
            deps = pomView.getAllDirectDependencies();
        }
        catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
        {
            logger.error( "Failed to retrieve direct dependencies: %s. Skipping", e, e.getMessage() );
        }

        if ( deps != null )
        {
            for ( final DependencyView dep : deps )
            {
                try
                {
                    final ProjectVersionRef ref = dep.asProjectVersionRef();
                    final String profileId = dep.getProfileId();
                    final URI location = RelationshipUtils.profileLocation( profileId );

                    final ArtifactRef artifactRef = new ArtifactRef( ref, dep.getType(), dep.getClassifier(), dep.isOptional() );

                    builder.withDependencies( new DependencyRelationship( source, location, projectRef, artifactRef, dep.getScope(),
                                                                          builder.getNextDependencyIndex( false ), false ) );
                }
                catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
                {
                    logger.error( "Direct dependency is invalid! Reason: %s. Skipping:\n\n%s\n\n", e, e.getMessage(), dep.toXML() );
                }
            }
        }
    }

    protected void addParentRelationship( final URI source, final Builder builder, final MavenPomView pomView, final ProjectVersionRef projectRef )
    {
        try
        {
            final ParentView parent = pomView.getParent();
            if ( parent != null )
            {
                final ProjectVersionRef ref = parent.asProjectVersionRef();
                builder.withParent( new ParentRelationship( source, builder.getProjectRef(), ref ) );
            }
            else
            {
                builder.withParent( new ParentRelationship( source, builder.getProjectRef() ) );
            }
        }
        catch ( final GalleyMavenException | InvalidVersionSpecificationException | InvalidRefException e )
        {
            logger.error( "Parent refernce is invalid! Reason: %s. Skipping.", e, e.getMessage() );
        }
    }

}
