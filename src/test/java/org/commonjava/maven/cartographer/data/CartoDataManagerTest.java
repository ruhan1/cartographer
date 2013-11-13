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
package org.commonjava.maven.cartographer.data;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.commonjava.maven.cartographer.agg.DefaultGraphAggregator;
import org.commonjava.maven.cartographer.agg.GraphAggregator;
import org.commonjava.maven.cartographer.discover.DiscovererImpl;
import org.commonjava.maven.cartographer.discover.ProjectRelationshipDiscoverer;
import org.commonjava.maven.cartographer.discover.post.meta.MetadataScannerSupport;
import org.commonjava.maven.cartographer.discover.post.meta.ScmUrlScanner;
import org.commonjava.maven.cartographer.discover.post.patch.PatcherSupport;
import org.commonjava.maven.cartographer.testutil.TestCartoCoreProvider;
import org.commonjava.maven.cartographer.testutil.TestCartoEventManager;
import org.commonjava.maven.cartographer.util.MavenModelProcessor;
import org.commonjava.maven.galley.TransferManager;
import org.commonjava.maven.galley.TransferManagerImpl;
import org.commonjava.maven.galley.cache.FileCacheProvider;
import org.commonjava.maven.galley.internal.xfer.DownloadHandler;
import org.commonjava.maven.galley.internal.xfer.ExistenceHandler;
import org.commonjava.maven.galley.internal.xfer.ListingHandler;
import org.commonjava.maven.galley.internal.xfer.UploadHandler;
import org.commonjava.maven.galley.io.HashedLocationPathGenerator;
import org.commonjava.maven.galley.maven.ArtifactManager;
import org.commonjava.maven.galley.maven.defaults.StandardMaven304PluginDefaults;
import org.commonjava.maven.galley.maven.defaults.StandardMavenPluginImplications;
import org.commonjava.maven.galley.maven.internal.ArtifactManagerImpl;
import org.commonjava.maven.galley.maven.model.view.XPathManager;
import org.commonjava.maven.galley.maven.parse.MavenPomReader;
import org.commonjava.maven.galley.maven.parse.XMLInfrastructure;
import org.commonjava.maven.galley.maven.type.StandardTypeMapper;
import org.commonjava.maven.galley.nfc.MemoryNotFoundCache;
import org.commonjava.maven.galley.spi.cache.CacheProvider;
import org.commonjava.maven.galley.spi.transport.TransportManager;
import org.commonjava.maven.galley.transport.NoOpLocationExpander;
import org.commonjava.maven.galley.transport.TransportManagerImpl;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class CartoDataManagerTest
    extends AbstractCartoDataManagerTest
{

    private CartoDataManager dataManager;

    private DefaultGraphAggregator aggregator;

    private ProjectRelationshipDiscoverer discoverer;

    private TestCartoCoreProvider provider;

    private XMLInfrastructure xml;

    private XPathManager xpath;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private GraphWorkspaceHolder sessionManager;

    private MemoryNotFoundCache nfc;

    @After
    public void teardown()
        throws Exception
    {
        provider.shutdown();
    }

    @Override
    protected void setupComponents()
        throws Exception
    {
        provider = new TestCartoCoreProvider( temp );
        sessionManager = new GraphWorkspaceHolder();

        dataManager = new DefaultCartoDataManager( provider.getGraphs(), sessionManager, new TestCartoEventManager() );

        final MavenModelProcessor processor = new MavenModelProcessor( dataManager );

        // TODO: Do we need to flesh this out??
        final TransportManager transportManager = new TransportManagerImpl();

        nfc = new MemoryNotFoundCache();

        final CacheProvider cacheProvider =
            new FileCacheProvider( temp.newFolder( "cache" ), new HashedLocationPathGenerator(), provider.getFileEventManager(),
                                   provider.getTransferDecorator() );

        final ExecutorService executor = Executors.newFixedThreadPool( 2 );
        final ExecutorService batchExecutor = Executors.newFixedThreadPool( 2 );
        final DownloadHandler dh = new DownloadHandler( nfc, executor );
        final UploadHandler uh = new UploadHandler( nfc, executor );
        final ListingHandler lh = new ListingHandler( nfc );
        final ExistenceHandler eh = new ExistenceHandler( nfc );

        final TransferManager transferManager =
            new TransferManagerImpl( transportManager, cacheProvider, nfc, provider.getFileEventManager(), dh, uh, lh, eh, batchExecutor );

        final ArtifactManager artifacts = new ArtifactManagerImpl( transferManager, new NoOpLocationExpander(), new StandardTypeMapper() );

        xml = new XMLInfrastructure();
        xpath = new XPathManager();

        final MavenPomReader pomReader =
            new MavenPomReader( xml, artifacts, xpath, new StandardMaven304PluginDefaults(), new StandardMavenPluginImplications( xml ) );

        // TODO: Add some scanners.
        final MetadataScannerSupport scannerSupport = new MetadataScannerSupport( new ScmUrlScanner( pomReader ) );

        discoverer = new DiscovererImpl( processor, artifacts, dataManager, new PatcherSupport(), scannerSupport );

        aggregator = new DefaultGraphAggregator( dataManager, discoverer, Executors.newFixedThreadPool( 2 ) );
    }

    @Override
    protected GraphWorkspaceHolder getSessionManager()
    {
        return sessionManager;
    }

    @Override
    protected CartoDataManager getDataManager()
        throws Exception
    {
        return dataManager;
    }

    @Override
    protected GraphAggregator getAggregator()
        throws Exception
    {
        return aggregator;
    }

    public XMLInfrastructure getXML()
    {
        return xml;
    }

    public XPathManager getXPath()
    {
        return xpath;
    }

}
