package com.tinkerpop.rexster;

import com.tinkerpop.blueprints.pgm.Graph;
import com.tinkerpop.blueprints.pgm.impls.neo4j.Neo4jGraph;
import com.tinkerpop.blueprints.pgm.impls.orientdb.OrientGraph;
import com.tinkerpop.blueprints.pgm.impls.tg.TinkerGraph;
import com.tinkerpop.blueprints.pgm.parser.GraphMLReader;
import com.tinkerpop.rexster.config.GraphConfigurationContainer;
import com.tinkerpop.rexster.traversals.Traversal;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.io.FileInputStream;
import java.util.*;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class RexsterApplication {

    protected static final Logger logger = Logger.getLogger(RexsterApplication.class);

    private static final String version = Tokens.REXSTER_VERSION;
    private final long startTime = System.currentTimeMillis();
    private ResultObjectCache resultObjectCache;

    private Map<String, RexsterApplicationGraph> graphs = new HashMap<String, RexsterApplicationGraph>();

    static {
        PropertyConfigurator.configure(RexsterApplication.class.getResource("log4j.properties"));
    }

    public RexsterApplication(final String graphName, final Graph graph) {
        RexsterApplicationGraph rag = new RexsterApplicationGraph(graphName, graph);
        this.graphs.put(graphName, rag);
        logger.info("Graph " + rag.getGraph() + " loaded");
        this.resultObjectCache = new ResultObjectCache();
    }

    public RexsterApplication(final XMLConfiguration properties) {

        // get the graph configurations from the XML config file
        List<HierarchicalConfiguration> graphConfigs = properties.configurationsAt(Tokens.REXSTER_GRAPH_PATH);

        GraphConfigurationContainer container = new GraphConfigurationContainer(graphConfigs);
        this.graphs = container.getApplicationGraphs();

        try {
            initTraversals();
            logger.info("Traversal initialization completed.");
        } catch (Exception e) {
            logger.error("Traversal initialization failed.", e);
        }

        this.resultObjectCache = new ResultObjectCache();

    }

    private void initTraversals() {
        // get a list of all traversal implementations.
        ServiceLoader<? extends Traversal> traversalServices = ServiceLoader.load(Traversal.class);

        for (RexsterApplicationGraph rag : this.graphs.values()) {

            if (rag.hasPackages()) {
            	
            	// packages are explicitly configured
                for (Traversal traversalService : traversalServices) {
                    
                	String traversalPackage;
                	
                	// the traversal is defined as a forward-slash separated set of path segments.
                	// the first path segment is the package. if there is no forward slash then
                	// the entire string is the package
                	if (traversalService.getTraversalName().indexOf("/") == -1) {
                        traversalPackage = traversalService.getTraversalName();
                    } else {
                        traversalPackage = traversalService.getTraversalName()
                        	.substring(0, traversalService.getTraversalName().indexOf("/"));
                    }
                	
                	if (rag.getPackageNames().contains(traversalPackage)) {
                        logger.info("loading traversal: /" + rag.getGraphName() + "/" + traversalService.getTraversalName() + " [" + traversalService.getClass().getName() + "]");
                        rag.getLoadedTraversals().put(traversalService.getTraversalName(), traversalService.getClass());
                    }
                }
            }
        }
    }

    public Map<String, Class<? extends Traversal>> getLoadedTraversalServices(String graphName) {
        return this.graphs.get(graphName).getLoadedTraversals();
    }

    public static String getVersion() {
        return version;
    }

    public Graph getGraph(String graphName) {
        return this.graphs.get(graphName).getGraph();
    }

    public RexsterApplicationGraph getApplicationGraph(String graphName) {
        return this.graphs.get(graphName);
    }
    
    public Set<String> getGraphsNames(){
    	return this.graphs.keySet();
    }

    public ResultObjectCache getResultObjectCache() {
        return this.resultObjectCache;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public void stop() throws Exception {

        // need to shutdown all the graphs that were started with the web server
        for (RexsterApplicationGraph rag : this.graphs.values()) {

            Graph graph = rag.getGraph();
            logger.info("Shutting down " + graph);

            // graph may not have been initialized properly if an exception gets tossed in
            // on graph creation
            if (graph != null) {
                graph.shutdown();
            }
        }

    }
}
