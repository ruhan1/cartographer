package org.commonjava.cartographer.request.build;

import org.commonjava.cartographer.request.GraphDescription;
import org.commonjava.cartographer.request.MetadataExtractionRequest;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.galley.model.Location;

import java.util.*;
import java.util.function.Consumer;

public class MetadataExtractionRequestBuilder<T extends MetadataExtractionRequestBuilder<T, R>, R extends MetadataExtractionRequest>
    extends ProjectGraphRequestBuilder<T, R>
{

    public static final class StandaloneMeta
        extends MetadataExtractionRequestBuilder<StandaloneMeta, MetadataExtractionRequest>
    {
    }

    public static StandaloneMeta newMetadataRecipeBuilder()
    {
        return new StandaloneMeta();
    }

    private Set<String> keys;

    public T withKeys( Collection<String> keys )
    {
        this.keys = new HashSet<>( keys );
        return self;
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public R build()
    {
        final R recipe = (R) new MetadataExtractionRequest();
        configure( recipe );

        return recipe;
    }

    @Override
    public T withProjectVersionRef( ProjectVersionRef ref )
    {
        return super.withProjectVersionRef( ref );
    }

    @Override
    public T withGraph( GraphDescription graph )
    {
        return super.withGraph( graph );
    }

    @Override
    public T withSource( String source )
    {
        return super.withSource( source );
    }

    @Override
    public T withWorkspaceId( String workspaceId )
    {
        return super.withWorkspaceId( workspaceId );
    }

    @Override
    public T withSourceLocation( Location source )
    {
        return super.withSourceLocation( source );
    }

    @Override
    public T withTimeoutSecs( Integer timeoutSecs )
    {
        return super.withTimeoutSecs( timeoutSecs );
    }

    @Override
    public T withPatcherIds( Collection<String> patcherIds )
    {
        return super.withPatcherIds( patcherIds );
    }

    @Override
    public T withResolve( boolean resolve )
    {
        return super.withResolve( resolve );
    }

    @Override
    public T withInjectedBOMs( List<ProjectVersionRef> injectedBOMs )
    {
        return super.withInjectedBOMs( injectedBOMs );
    }

    @Override
    public T withExcludedSubgraphs( Collection<ProjectVersionRef> excludedSubgraphs )
    {
        return super.withExcludedSubgraphs( excludedSubgraphs );
    }

    @Override
    public T withVersionSelections( Map<ProjectRef, ProjectVersionRef> versionSelections )
    {
        return super.withVersionSelections( versionSelections );
    }
}