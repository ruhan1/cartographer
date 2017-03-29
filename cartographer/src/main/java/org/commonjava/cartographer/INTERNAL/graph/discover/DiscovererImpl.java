/**
 * Copyright (C) 2013 Red Hat, Inc. (jdcasey@commonjava.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.cartographer.INTERNAL.graph.discover;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.commonjava.cartographer.CartoDataException;
import org.commonjava.cartographer.graph.discover.DiscoveryConfig;
import org.commonjava.cartographer.graph.discover.DiscoveryResult;
import org.commonjava.cartographer.graph.discover.meta.MetadataScannerSupport;
import org.commonjava.cartographer.graph.discover.patch.PatcherSupport;
import org.commonjava.cartographer.spi.graph.discover.ProjectRelationshipDiscoverer;
import org.commonjava.cartographer.graph.RelationshipGraph;
import org.commonjava.cartographer.graph.RelationshipGraphException;
import org.commonjava.maven.atlas.graph.model.EProjectDirectRelationships;
import org.commonjava.maven.atlas.graph.rel.ProjectRelationship;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.galley.TransferException;
import org.commonjava.maven.galley.maven.ArtifactManager;
import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.commonjava.maven.galley.maven.model.view.MavenPomView;
import org.commonjava.maven.galley.maven.parse.MavenPomReader;
import org.commonjava.maven.galley.maven.rel.MavenModelProcessor;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.model.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
@Named
@Alternative
public class DiscovererImpl
    implements ProjectRelationshipDiscoverer
{

    @Inject
    private ArtifactManager artifactManager;

    @Inject
    private MavenModelProcessor modelProcessor;

    @Inject
    private PatcherSupport patchers;

    @Inject
    private MetadataScannerSupport metadataScanners;

    @Inject
    private MavenPomReader pomReader;

    @Inject
    private ObjectMapper objectMapper;

    protected DiscovererImpl()
    {
    }

    public DiscovererImpl( final MavenModelProcessor modelProcessor, final MavenPomReader pomReader,
                           final ArtifactManager artifactManager, final PatcherSupport patchers,
                           final MetadataScannerSupport metadataScanners )
    {
        this.modelProcessor = modelProcessor;
        this.pomReader = pomReader;
        this.artifactManager = artifactManager;
        this.patchers = patchers;
        this.metadataScanners = metadataScanners;
    }

    @Override
    public ProjectVersionRef resolveSpecificVersion( final ProjectVersionRef ref, final DiscoveryConfig discoveryConfig )
        throws CartoDataException
    {
        final List<? extends Location> locations = discoveryConfig.getLocations();

        try
        {
            return artifactManager.resolveVariableVersion( locations, ref );
        }
        catch ( final TransferException e )
        {
            throw new CartoDataException( "Failed to graph variable version for: {}. Reason: {}", e, ref,
                                          e.getMessage() );
        }
    }

    @Override
    public DiscoveryResult discoverRelationships( final ProjectVersionRef ref, final RelationshipGraph graph,
                                                  final DiscoveryConfig discoveryConfig )
        throws CartoDataException
    {
        ProjectVersionRef specific = ref;
        if ( !ref.isSpecificVersion() )
        {
            specific = resolveSpecificVersion( ref, discoveryConfig );
        }

        if ( specific == null )
        {
            specific = ref;
        }

        final List<? extends Location> locations = discoveryConfig.getLocations();

        DiscoveryResult result = null;

        Transfer transfer = null;
        try
        {
            transfer = artifactManager.retrieveFirst( locations, specific.asPomArtifact() );
        }
        catch ( TransferException e )
        {
            throw new CartoDataException( "Failed to retrieve POM: {} from: {}. Reason: {}", e, specific, locations,
                                          e.getMessage() );
        }

        if ( transfer != null && transfer.exists() )
        {
            final String REL_SUFFIX = ".rel";

            Transfer relTransfer = transfer.getSibling( REL_SUFFIX );
            if ( relTransfer != null && relTransfer.exists() )
            {
                try (InputStream in = relTransfer.openInputStream())
                {
                    String rel = IOUtils.toString( in );
                    final Logger logger = LoggerFactory.getLogger( getClass() );
                    logger.debug( "Rel: " + rel );
                    EProjectDirectRelationships rels = objectMapper.readValue( rel, EProjectDirectRelationships.class );
                    result = new DiscoveryResult( discoveryConfig.getDiscoverySource(), specific,
                                                  rels.getExactAllRelationships() );
                }
                catch ( IOException e )
                {
                    throw new CartoDataException( "Failed to read Rel: {} from: {}. Reason: {}", e, specific, locations,
                                                  e.getMessage() );
                }
            }
        }

        /*
        Transfer transfer;
        final MavenPomView pomView;
        try
        {
            transfer = artifactManager.retrieveFirst( locations, specific.asPomArtifact() );
            if ( transfer == null )
            {
                return null;
            }

            pomView = pomReader.read( specific, transfer, locations );
        }
        catch ( final TransferException e )
        {
            throw new CartoDataException( "Failed to retrieve POM: {} from: {}. Reason: {}", e, specific, locations,
                                          e.getMessage() );
        }
        catch ( final GalleyMavenException e )
        {
            throw new CartoDataException( "Failed to parse POM: {} from: {}. Reason: {}", e, specific, locations,
                                          e.getMessage() );
        }

        DiscoveryResult result = null;
        if ( pomView != null )
        {
            try
            {
                EProjectDirectRelationships rels =
                        modelProcessor.readRelationships( pomView, discoveryConfig.getDiscoverySource(),
                                                          discoveryConfig.getProcessorConfig() );

                result = new DiscoveryResult( discoveryConfig.getDiscoverySource(), specific, rels.getExactAllRelationships() );
            }
            catch ( GalleyMavenException e )
            {
                throw new CartoDataException( "Failed to read relationships from POM: %s. Reason: %s", e, pomView,
                                              e.getMessage() );
            }
        }

        if ( result != null )
        {
            result = patchers.patch( result, discoveryConfig.getEnabledPatchers(), locations, pomView, transfer );

            final Map<String, String> metadata =
                metadataScanners.scan( result.getSelectedRef(), locations, pomView, transfer );
            result.setMetadata( metadata );

            if ( discoveryConfig.isStoreRelationships() )
            {
                final Set<ProjectRelationship<?, ?>> rejected;
                try
                {
                    rejected = graph.storeRelationships( result.getAcceptedRelationships() );
                    graph.addMetadata( result.getSelectedRef(), metadata );
                }
                catch ( final RelationshipGraphException e )
                {
                    throw new CartoDataException( "Failed to store relationships or metadata for: {}. Reason: {}", e,
                                                  result.getSelectedRef(), e.getMessage() );
                }

                result = new DiscoveryResult( result.getSource(), result, rejected );
            }
        }
        */

        return result;
    }

}
