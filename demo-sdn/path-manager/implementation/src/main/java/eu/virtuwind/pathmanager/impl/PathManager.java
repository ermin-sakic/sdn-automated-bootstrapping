package eu.virtuwind.pathmanager.impl;

import com.google.common.util.concurrent.Futures;
import de.tum.ei.lkn.eces.core.Controller;
import de.tum.ei.lkn.eces.core.Entity;
import de.tum.ei.lkn.eces.core.MapperSpace;
import de.tum.ei.lkn.eces.dnm.NCProxy;
import de.tum.ei.lkn.eces.dnm.NCSystem;
import de.tum.ei.lkn.eces.dnm.color.*;
import de.tum.ei.lkn.eces.dnm.components.GraphConfig;
import de.tum.ei.lkn.eces.dnm.components.NCRequestData;
import de.tum.ei.lkn.eces.dnm.mappers.GraphConfigMapper;
import de.tum.ei.lkn.eces.dnm.mappers.NCRequestDataMapper;
import de.tum.ei.lkn.eces.dnm.modes.ACModel;
import de.tum.ei.lkn.eces.dnm.modes.BurstIncrease;
import de.tum.ei.lkn.eces.dnm.modes.costmodels.functions.Division;
import de.tum.ei.lkn.eces.dnm.modes.costmodels.functions.LowerLimit;
import de.tum.ei.lkn.eces.dnm.modes.costmodels.functions.Summation;
import de.tum.ei.lkn.eces.dnm.modes.costmodels.functions.UpperLimit;
import de.tum.ei.lkn.eces.dnm.modes.costmodels.values.Constant;
import de.tum.ei.lkn.eces.dnm.modes.costmodels.values.QueuePriority;
import de.tum.ei.lkn.eces.dnm.networkcalculus.arrivalcurve.TokenBucket;
import de.tum.ei.lkn.eces.dnm.networkcalculus.servicecurve.ResidualMode;
import de.tum.ei.lkn.eces.graph.Edge;
import de.tum.ei.lkn.eces.graph.Node;
import de.tum.ei.lkn.eces.network.*;
import de.tum.ei.lkn.eces.network.Link;
import de.tum.ei.lkn.eces.network.Queue;
import de.tum.ei.lkn.eces.network.color.DelayColoring;
import de.tum.ei.lkn.eces.network.color.QueueColoring;
import de.tum.ei.lkn.eces.network.color.RateColoring;
import de.tum.ei.lkn.eces.network.mappers.LinkMapper;
import de.tum.ei.lkn.eces.network.mappers.QueueMapper;
import de.tum.ei.lkn.eces.network.mappers.ToNetworkMapper;
import de.tum.ei.lkn.eces.routing.RoutingSystem;
import de.tum.ei.lkn.eces.routing.SelectedRoutingAlgorithm;
import de.tum.ei.lkn.eces.routing.algorithms.csp.CSPAlgorithm;
import de.tum.ei.lkn.eces.routing.algorithms.csp.unicast.larac.LARACAlgorithm;
import de.tum.ei.lkn.eces.routing.algorithms.agnostic.disjoint.simplepartial.SPDAlgorithm;
import de.tum.ei.lkn.eces.routing.algorithms.agnostic.resilience.partial.SPRAlgorithm;
import de.tum.ei.lkn.eces.routing.algorithms.agnostic.resilience.simple.SRAlgorithm;
import de.tum.ei.lkn.eces.routing.algorithms.agnostic.resilience.simple.SRAlgorithm;
import de.tum.ei.lkn.eces.routing.algorithms.sp.SPAlgorithm;
import de.tum.ei.lkn.eces.routing.algorithms.sp.unicast.dijkstra.DijkstraAlgorithm;
import de.tum.ei.lkn.eces.routing.mappers.*;
import de.tum.ei.lkn.eces.routing.pathlist.LastEmbeddingColoring;
import de.tum.ei.lkn.eces.routing.pathlist.PathListColoring;
import de.tum.ei.lkn.eces.routing.pathlist.PathListSystem;
import de.tum.ei.lkn.eces.routing.proxies.PathProxy;
import de.tum.ei.lkn.eces.routing.proxies.ShortestPathProxy;
import de.tum.ei.lkn.eces.routing.requests.DisjointRequest;
import de.tum.ei.lkn.eces.routing.requests.ResilientRequest;
import de.tum.ei.lkn.eces.routing.requests.UnicastRequest;
import de.tum.ei.lkn.eces.routing.responses.DisjointPaths;
import de.tum.ei.lkn.eces.routing.responses.Path;
import de.tum.ei.lkn.eces.routing.responses.ResilientPath;
import de.tum.ei.lkn.eces.routing.responses.Response;
import de.tum.ei.lkn.eces.webgraphgui.WebGraphGuiSystem;
import de.tum.ei.lkn.eces.webgraphgui.color.ColoringSystem;
import eu.virtuwind.resourcemanager.impl.PathConfigurator;
import eu.virtuwind.resourcemonitor.impl.ResourceMonitor;
import org.javatuples.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.common.util.Rpcs;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.pathmanager.rev161017.*;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.pathmanager.rev161017.embed.disjoint.best.effort.input.DestinationConnectors;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.pathmanager.rev161017.embed.disjoint.best.effort.input.Destinations;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.pathmanager.rev161017.embed.real.time.input.Delays;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.pathmanager.rev161017.embed.real.time.input.IntermediateNodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.Match;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Future;

public class PathManager extends PathManagerProvider implements PathManagerService {
    private static final Logger LOG = LoggerFactory.getLogger(PathManager.class);

    /**
     * Port on which the web GUI should run.
     */
    private static final int GUI_PORT = 18888;

    /**
     * The current instance.
     */
    private static PathManager instance;

    /**
     * Controller of the ECES System.
     */
    private Controller controller;

    /**
     * Networking system for modifying the network.
     */
    private NetworkingSystem networkingSystem;

    /**
     * Routing system.
     */
    private RoutingSystem routingSystem;

    /**
     * Network.
     */
    private Network network;

    /**
     * Algorithm for solving SP problem.
     */
    private SPAlgorithm spAlgorithm;

    /**
     * Algorithm for solving CSP problem.
     */
    private CSPAlgorithm cspAlgorithm;

    /**
     * Algorithm for solving resilient CSP problem.
     */
    private SRAlgorithm resilienceAlgorithm;

    /**
     * Algorithm for solving resilient SP problem.
     */
    private SPRAlgorithm resilienceSPAlgorithm;

    /**
     * Algorithm for solving disjoint CSP problem.
     */
    private SPDAlgorithm disjointAlgorithm;

    /**
     * Proxy used for CSP routing.
     */
    private PathProxy cspProxy;

    /**
     * NC Configuration.
     */
    private GraphConfig networkCalculusConfig;

    /**
     * Maps Node IDs to NetworkNodes of the Network.
     */
    private Map<String, NetworkNode> nodesODLtoECES;

    /**
     * Maps Link IDs to Links of the Network.
     */
    private Map<String, Link> linksODLtoECES;

    /**
     * Maps Links of the Network to their ODL equivalent.
     */
    private Map<Link, org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link> linksECEStoODL;

    /**
     * Object programming the switches.
     */
    private PathConfigurator resourceManager;

    /**
     * Mappers.
     */
    private LinkMapper linkMapper;
    private ToNetworkMapper toNetworkMapper;
    private UnicastRequestMapper unicastRequestMapper;
    private NCRequestDataMapper ncRequestDataMapper;
    private PathMapper pathMapper;
    private ResilientPathMapper resilientPathMapper;
    private DisjointPathsMapper disjointPathsMapper;
    private QueueMapper queueMapper;
    private GraphConfigMapper graphConfigMapper;
    private SelectedRoutingAlgorithmMapper selectedRoutingAlgorithmMapper;

    /**
     * Gets the active instance of the module.
     * @return Path Manager object.
     */
    public static PathManager getInstance() {
        if(instance == null)
            throw new IllegalStateException("You cannot get an instance of the path manager if it has never been instantiated before");
        else
            return instance;
    }

    /**
     * Package private: classes from the backage will trigger the creation of the
     * Path Manager if it does not exist.
     * @param dataBroker
     * @param salFlowService
     * @return the current instance (newly created or existing one)
     */
    static PathManager getInstance(DataBroker dataBroker, SalFlowService salFlowService) {
        if(instance == null)
            return new PathManager(dataBroker, salFlowService);
        else
            return instance;
    }

    private PathManager(DataBroker dataBroker, SalFlowService salFlowService) {
        LOG.info("Initialization of the Path Manager...");
        this.dataBroker = dataBroker;
        this.salFlowService = salFlowService;
        this.resourceManager = new PathConfigurator(salFlowService);
        PathManager.instance = this;

        // ECES Initializations.
        controller = new Controller();
        linkMapper = new LinkMapper(controller);
        toNetworkMapper = new ToNetworkMapper(controller);
        unicastRequestMapper = new UnicastRequestMapper(controller);
        ncRequestDataMapper = new NCRequestDataMapper(controller);
        pathMapper = new PathMapper(controller);
        resilientPathMapper = new ResilientPathMapper(controller);
        disjointPathsMapper = new DisjointPathsMapper(controller);
        queueMapper = new QueueMapper(controller);
        graphConfigMapper = new GraphConfigMapper(controller);
        selectedRoutingAlgorithmMapper = new SelectedRoutingAlgorithmMapper(controller);

        networkCalculusConfig = new GraphConfig(ACModel.MultiHopModel,
                ResidualMode.LEAST_LATENCY,
                BurstIncrease.WORST_CASE_BURST_REAL_RESERVATION,
                false,
                new LowerLimit(new UpperLimit(
                        new Summation(new Division(
                                new Constant(),
                                new Summation(
                                        new Constant(),
                                        new QueuePriority())),
                                new Constant()),
                        2),1));

        // NC system and proxy
        new NCSystem(controller, networkCalculusConfig);
        cspProxy = new NCProxy(controller, networkCalculusConfig);

        // Routing algorithms
        routingSystem = new RoutingSystem(controller);
        routingSystem.disableRestoration();
        networkingSystem = new NetworkingSystem(controller);

        // For delay-constrained (real-time) paths
        cspAlgorithm = new LARACAlgorithm(controller);
        cspAlgorithm.setProxy(cspProxy);
        routingSystem.registerRoutingAlgorithm(cspAlgorithm);

        // For best-effort (shortest path) paths
        spAlgorithm = new DijkstraAlgorithm(controller);
        spAlgorithm.setProxy(new ShortestPathProxy());
        routingSystem.registerRoutingAlgorithm(spAlgorithm);

        // Resilient paths
        resilienceAlgorithm = new SRAlgorithm(controller, new LARACAlgorithm(controller));
        resilienceAlgorithm.setProxy(cspProxy);
        routingSystem.registerRoutingAlgorithm(resilienceAlgorithm);

        resilienceSPAlgorithm = new SPRAlgorithm(controller, new DijkstraAlgorithm(controller));
        resilienceSPAlgorithm.setProxy(new ShortestPathProxy());
        routingSystem.registerRoutingAlgorithm(resilienceSPAlgorithm);

        // Disjoint paths to different destinations
        disjointAlgorithm = new SPDAlgorithm(controller, new DijkstraAlgorithm(controller));
        disjointAlgorithm.setProxy(new ShortestPathProxy());
        routingSystem.registerRoutingAlgorithm(disjointAlgorithm);

        // GUI
        ColoringSystem myColoringSys = new ColoringSystem(controller);
        myColoringSys.addColoringScheme(new DelayColoring(controller), "Delay");
        myColoringSys.addColoringScheme(new QueueColoring(controller), "Queue sizes");
        myColoringSys.addColoringScheme(new RateColoring(controller), "Link rate");
        myColoringSys.addColoringScheme(new RemainingRateColoring(controller), "Remaining rate");
        myColoringSys.addColoringScheme(new AssignedRateColoring(controller), "Assigned rate");
        myColoringSys.addColoringScheme(new RemainingBufferColoring(controller), "Remaining buffer space");
        myColoringSys.addColoringScheme(new RemainingDelayColoring(controller), "Remaining delay");
        myColoringSys.addColoringScheme(new AssignedBufferColoring(controller), "Assigned buffer space");
        myColoringSys.addColoringScheme(new LastEmbeddingColoring(new PathListSystem(controller)), "Last embedded flow");
        myColoringSys.addColoringScheme(new PathListColoring(controller), "Amount of paths");
        new WebGraphGuiSystem(controller, myColoringSys, GUI_PORT);
        LOG.info("Web GUI of the Path Manager started on port " + GUI_PORT);

        LOG.info("Initialization of the Path Manager done!");
    }

    /**
     * Fetches the topology from the Resource Monitor and populates the ECES data structures.
     */
    private void fetchTopology() {
        LOG.info("Fetching topology...");
        if(network != null) {
            LOG.info("Topology already known.");
            return; // Topology known already.
        }

        linksODLtoECES = new HashMap<>();
        linksECEStoODL = new HashMap<>();
        nodesODLtoECES = new HashMap<>();

        network = networkingSystem.createNetwork();
        graphConfigMapper.attachComponent(network.getQueueGraph(), networkCalculusConfig);

        // Getting links of topology.
        List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link> links = ResourceMonitor.getAllLinks(dataBroker);
        LOG.info("Links returned by Resource Monitor: " + links);
        if(links != null) {
            for (org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link link : links) {
                String dstId = link.getDestination().getDestNode().getValue();
                String srcId = link.getSource().getSourceNode().getValue();
                this.newNode(dstId);
                this.newNode(srcId);

                // Hiding the fourth queue as it is for best-effort.
                this.newLink(link, 125000000.0, new double[]{300000, 300000, 300000});
            }
        }

        LOG.info("Topology fetched!");
        LOG.info("Physical Topology GML: " + network.getLinkGraph().toGML());
        LOG.info("Queue-Link Topology GML: " + network.getQueueGraph().toGML());
    }

    /**
     * Fetches the current topology from the Resource Monitor and populates the ECES data structures.
     */
    private void fetchCurrentTopology() {
        LOG.info("Fetching topology...");

        linksODLtoECES = new HashMap<>();
        linksECEStoODL = new HashMap<>();
        nodesODLtoECES = new HashMap<>();

        network = networkingSystem.createNetwork();
        graphConfigMapper.attachComponent(network.getQueueGraph(), networkCalculusConfig);

        // Getting links of topology.
        List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link> links = ResourceMonitor.getAllLinks(dataBroker);
        LOG.info("Links returned by Resource Monitor: " + links);
        if(links != null) {
            for (org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link link : links) {
                String dstId = link.getDestination().getDestNode().getValue();
                String srcId = link.getSource().getSourceNode().getValue();
                this.newNode(dstId);
                this.newNode(srcId);

                // Hiding the fourth queue as it is for best-effort.
                this.newLink(link, 125000000.0, new double[]{300000, 300000, 300000});
            }
        }

        LOG.info("Topology fetched!");
        LOG.info("Physical Topology GML: " + network.getLinkGraph().toGML());
        LOG.info("Queue-Link Topology GML: " + network.getQueueGraph().toGML());
    }

    /**
     * Embeds a flow with real-time requirements.
     *
     * @param srcNodeId               Source switch of the flow.
     * @param srcNodePort             Connector of the source end host on the source switch.
     *                                (null for switch-to-switch flows)
     * @param dstNodeId               Destination switch of the flow.
     * @param dstNodePort             Connector of the destination end host on the destination switch.
     *                                (null for switch-to-switch flows).
     * @param matchingStructure       Matching structure.
     * @param rate                    Sustainable rate of the flow in B/s.
     * @param burst                   Maximum burstiness of the flow in bytes.
     * @param maxPacketSize           Maximum packet size of the flow in bytes.
     *                                Note that this *should* include the interframe
     *                                gap between Ethernet frames.
     *                                If unknown, specify 1542, i.e. the maximum
     *                                Ethernet frame size.
     * @param maxDelay                Maximum end-to-end delay packets of the flow should
     *                                experience (in seconds).
     * @param resilience              true if the flow has to be resilient to a single
     *                                component (link/node) failure. If false, a single
     *                                component failure might disrupt the connection.
     *                                Resilience to failures requires more resources and
     *                                setting true hence leads to higher rejection rate.
     * @param intermediateNodes       Ordered list of functions through which the flow has to
     *                                be routed. Each element of the list is an array of
     *                                candidate Nodes. One Node of each list element will be
     *                                chosen. Increasing the amount of candidate Nodes for
     *                                the different functions increases the probability of
     *                                acceptance of the flow.
     * @param intermediateNodesDelays List following the same structure as 'intermediateNodes'
     *                                and defining the worst-case processing delays (in seconds)
     *                                of the Nodes specified in 'intermediateNodes'.
     * @param meterId                 Id of the flow meter that the resource manager should
     *                                update.
     * @return an embedding ID.
     *         -1 if no path was found/if an error occurred (e.g., unknown nodes).
     */
    public long embedRealTimePath(NodeId srcNodeId,
                                  NodeConnectorId srcNodePort,
                                  NodeId dstNodeId,
                                  NodeConnectorId dstNodePort,
                                  Match matchingStructure,
                                  double rate,
                                  double burst,
                                  double maxPacketSize,
                                  double maxDelay,
                                  boolean resilience,
                                  List<NodeId[]> intermediateNodes,
                                  List<Double[]> intermediateNodesDelays,
                                  Long meterId) {
        List<List<Pair<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link, Long>>> result = findRealTimePath(srcNodeId, dstNodeId, rate, burst, maxPacketSize, maxDelay, resilience, intermediateNodes, intermediateNodesDelays);
        if(result == null)
            return -1;
        else {
            LOG.info("Embedding the path between " + srcNodeId + " (port: " + ((srcNodePort == null) ? "null" : srcNodePort) + ") and " + dstNodeId + " (port: " + ((dstNodePort == null) ? "null" : dstNodePort) + ") with following matching.");
            LOG.info(((matchingStructure == null) ? "none" : matchingStructure.toString()));

            long embeddingId;
            if(resilience) {
                embeddingId = 0; /* return resourceManager.createDisjointFlows(matchingStructure,
                        links,
                        queues,
                        meterId,
                        srcNodeId,
                        dstNodeId,
                        srcNodePort,
                        dstNodePort);*/
            }
            else {
                embeddingId = 0; /* resourceManager.createAndSendFlow(
                        matchingStructure,
                        links,
                        queues,
                        meterId,
                        srcNodeId,
                        dstNodeId,
                        srcNodePort,
                        dstNodePort); */
            }

            LOG.info("Embedding request successfully sent to resource manager.");
            return embeddingId;
        }
    }

    // Same as previous method but without embedding on the switches (but registers in Path Manager data structure)
    public List<List<Pair<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link, Long>>>
    findRealTimePath(NodeId srcNodeId,
                     NodeId dstNodeId,
                     double rate,
                     double burst,
                     double maxPacketSize,
                     double maxDelay,
                     boolean resilience,
                     List<NodeId[]> intermediateNodes,
                     List<Double[]> intermediateNodesDelays) {
        // Checking intermediate nodes parameters.
        if((intermediateNodes == null && intermediateNodesDelays != null)
                || (intermediateNodes != null && intermediateNodesDelays == null)
                || (intermediateNodes != null && intermediateNodesDelays != null && intermediateNodes.size() != intermediateNodesDelays.size()))
            throw new RuntimeException("The 'intermediateNodes' and 'intermediateNodesDelays' parameters don't have the same size!");

        // If size ok, check that internal sizes also.
        if(intermediateNodes != null && intermediateNodesDelays != null) {
            for(int i = 0; i < intermediateNodes.size(); i++) {
                NodeId[] candidatesNodes = intermediateNodes.get(i);
                Double[] delaysNodes = intermediateNodesDelays.get(i);
                if(candidatesNodes == null || delaysNodes == null)
                    throw new RuntimeException("The 'intermediateNodes' and 'intermediateNodesDelays' parameters contain null elements!");

                if(candidatesNodes.length != delaysNodes.length)
                    throw new RuntimeException("The 'intermediateNodes' and 'intermediateNodesDelays' parameters contain elements of different sizes!");
            }
        }

        LOG.info("Computing a real-time path between " + srcNodeId + " and " + dstNodeId + " with following parameters.");
        LOG.info("  QoS parameters:");
        LOG.info("    Max delay: " + maxDelay + " seconds");
        LOG.info("    Rate: " + rate + " bytes/s");
        LOG.info("    Burst: " + burst + " bytes");
        LOG.info("    Max packet size: " + maxPacketSize + " bytes");
        LOG.info("    Resilience: " + resilience);
        if(intermediateNodes == null)
            LOG.info("    Intermediate nodes: none");
        else {
            LOG.info("    Intermediate nodes: ");
            for (int i = 0; i < intermediateNodes.size(); i++) {
                StringBuilder candidatesLine = new StringBuilder("      " + (i + 1) + ": ");
                for(int j = 0; j < intermediateNodes.get(i).length; j++)
                    candidatesLine.append(intermediateNodes.get(i)[j]).append("(").append(intermediateNodesDelays.get(i)[j]).append(" s)");
                LOG.info(candidatesLine.toString());
            }
        }

        // Get topology.
        fetchTopology();

        // Checking that source and destination nodes are known.
        NetworkNode source = nodesODLtoECES.get(srcNodeId.getValue());
        NetworkNode destination = nodesODLtoECES.get(dstNodeId.getValue());
        if(source == null || destination == null) {
            LOG.error("One (or more) of the specified nodes (\"" + srcNodeId + ", " + dstNodeId + "\") is not known (known nodes: " + nodesODLtoECES.keySet() + ")");
            return null;
        }

        // TODO intermediate nodes

        // Set proxy with constraint to SP Algorithm so that it is not chosen for solving the problem
        spAlgorithm.setProxy(cspProxy);
        // Other solution: temporarily deregister it.

        /* Create entity to which we attach the request to be solved.
         * This is done in a Mapper Space so that, when we exit it, all the
         * listeners (that is including the listener routing the request) have
         * been executed. We can do this because we know that our current code
         * will not be wrapped in another Mapper Space. */
        Entity entity = controller.createEntity();
        try (MapperSpace ms = controller.startMapperSpace()) {
            if(resilience) {
                unicastRequestMapper.attachComponent(entity, new ResilientRequest(source.getQueueNode(), destination.getQueueNode()));
                selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(resilienceAlgorithm));
            }
            else {
                unicastRequestMapper.attachComponent(entity, new UnicastRequest(source.getQueueNode(), destination.getQueueNode()));
                selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(cspAlgorithm));
            }

            ncRequestDataMapper.attachComponent(entity, new NCRequestData(new TokenBucket(rate, burst + maxPacketSize), maxDelay));
        }

        Response result;
        if(resilience)
            result = resilientPathMapper.get(entity);
        else
            result = pathMapper.get(entity);
        if (result == null) {
            LOG.warn("No path found!");
            return null;
        } else {
            List<List<Pair<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link, Long>>> out = new LinkedList<>();

            if(resilience) {
                LOG.info("Found paths:");
                LOG.info("1: " + ((ResilientPath) result).getPath1() + " with cost " + ((ResilientPath) result).getPath1().getCost() + " and delay " + ((ResilientPath) result).getPath1().getConstraintsValues()[0]);
                List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link> links1 = this.buildListOfLinks(((ResilientPath) result).getPath1());
                List<Long> queues1 = this.buildQueueIDs(((ResilientPath) result).getPath1());
                LOG.info("Corresponding ODL links: " + links1);
                LOG.info("Corresponding ODL queues: " + queues1);
                LOG.info("2: " + ((ResilientPath) result).getPath2() + " with cost " + ((ResilientPath) result).getPath2().getCost() + " and delay " + ((ResilientPath) result).getPath2().getConstraintsValues()[0]);
                List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link> links2 = this.buildListOfLinks(((ResilientPath) result).getPath2());
                List<Long> queues2 = this.buildQueueIDs(((ResilientPath) result).getPath2());
                LOG.info("Corresponding ODL links: " + links2);
                LOG.info("Corresponding ODL queues: " + queues2);
                LOG.warn("No embedding, requires new resource manager interface");

                // Add first resilient path
                out.add(new LinkedList<>());
                for(int i = 0; i < links1.size(); i++) {
                    out.get(0).add(new Pair<>(links1.get(i), queues1.get(i)));
                }

                // Add second path
                out.add(new LinkedList<>());
                for(int i = 0; i < links2.size(); i++) {
                    out.get(1).add(new Pair<>(links2.get(i), queues2.get(i)));
                }
            }
            else {
                LOG.info("Found path: " + result + " with cost " + ((Path) result).getCost() + " and delay " + ((Path) result).getConstraintsValues()[0]);
                List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link> links = this.buildListOfLinks(((Path) result));
                List<Long> queues = this.buildQueueIDs(((Path) result));
                LOG.info("Corresponding ODL links: " + links);
                LOG.info("Corresponding ODL queues: " + queues);
                out.add(new LinkedList<>());
                for(int i = 0; i < links.size(); i++) {
                    out.get(0).add(new Pair<>(links.get(i), queues.get(i)));
                }
            }

            return out;
        }
    }

    /**
     * Embeds a best-effort flow.
     *
     * @param srcNodeId               Source switch of the flow.
     * @param srcNodePort             Connector of the source end host on the source switch.
     *                                (null for switch-to-switch flows)
     * @param dstNodeId               Destination switch of the flow.
     * @param dstNodePort             Connector of the destination end host on the destination switch.
     *                                (null for switch-to-switch flows).
     * @param matchingStructure       Matching structure.
     * @param meterId                 Id of the flow meter that the resource manager should
     *                                update.
     * @return an embedding ID.
     *         -1 if no path was found or if an error occurred (e.g., unknown nodes).
     */
    public long embedBestEffortPath(NodeId srcNodeId,
                                    NodeConnectorId srcNodePort,
                                    NodeId dstNodeId,
                                    NodeConnectorId dstNodePort,
                                    Match matchingStructure,
                                    Long meterId) {
        List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link> links = findBestEffortPath(srcNodeId, dstNodeId);
        if(links == null)
            return -1;
        else {
            LOG.info("Embedding the path between " + srcNodeId + " (port: " + ((srcNodePort == null) ? "null" : srcNodePort) + ") and " + dstNodeId + " (port: " + ((dstNodePort == null) ? "null" : dstNodePort) + ") with following matching.");
            LOG.info(((matchingStructure == null) ? "none" : matchingStructure.toString()));
            long embeddingId = 0; /* resourceManager.bestembededflow(
                    matchingStructure,
                    links,
                    3000,
                    3000,
                    meterId,
                    srcNodeId,
                    dstNodeId,
                    srcNodePort,
                    dstNodePort);*/
            LOG.info("Embedding request successfully sent to resource manager.");
            return embeddingId;
        }
    }

    // Same as previous method but without embedding on the switches (but registers in Path Manager data structure)
    public List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link> findBestEffortPath(NodeId srcNodeId, NodeId dstNodeId) {
        LOG.info("Computing a best-effort path between " + srcNodeId + " and " + dstNodeId + ".");

        // Get topology.
        fetchTopology();

        // Checking that source and destination nodes are known.
        NetworkNode source = nodesODLtoECES.get(srcNodeId.getValue());
        NetworkNode destination = nodesODLtoECES.get(dstNodeId.getValue());
        if(source == null || destination == null) {
            LOG.error("One (or more) of the specified nodes (\"" + srcNodeId + ", " + dstNodeId + "\") is not known (known nodes: " + nodesODLtoECES.keySet() + ")");
            return null;
        }

        // Shortest path for best-effort.
        spAlgorithm.setProxy(new ShortestPathProxy());

        /* Create entity to which we attach the request to be solved.
         * This is done in a Mapper Space so that, when we exit it, all the
         * listeners (that is including the listener routing the request) have
         * been executed. We can do this because we know that our current code
         * will not be wrapped in another Mapper Space. */
        Entity entity = controller.createEntity();
        try (MapperSpace ms = controller.startMapperSpace()) {
            unicastRequestMapper.attachComponent(entity, new UnicastRequest(source.getLinkNode(), destination.getLinkNode()));
            selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(spAlgorithm));
        }

        Path result = pathMapper.get(entity);
        if (result == null) {
            LOG.warn("No path found!");
            return null;
        } else {
            LOG.info("Found path: " + result + " with cost " + result.getCost());
            List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link> links = this.buildListOfLinks(result);
            LOG.info("Corresponding ODL links: " + links);
            return links;
        }
    }

    // Same as previous method but disjoint paths
    public List<List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link>> findResilientBestEffortPath(NodeId srcNodeId, NodeId dstNodeId) {
        LOG.info("Computing a resilient best-effort path between " + srcNodeId + " and " + dstNodeId + ".");

        // Get topology.
        fetchTopology();

        // Checking that source and destination nodes are known.
        NetworkNode source = nodesODLtoECES.get(srcNodeId.getValue());
        NetworkNode destination = nodesODLtoECES.get(dstNodeId.getValue());
        if(source == null || destination == null) {
            LOG.error("One (or more) of the specified nodes (\"" + srcNodeId + ", " + dstNodeId + "\") is not known (known nodes: " + nodesODLtoECES.keySet() + ")");
            return null;
        }

        /* Create entity to which we attach the request to be solved.
         * This is done in a Mapper Space so that, when we exit it, all the
         * listeners (that is including the listener routing the request) have
         * been executed. We can do this because we know that our current code
         * will not be wrapped in another Mapper Space. */
        Entity entity = controller.createEntity();
        try (MapperSpace ms = controller.startMapperSpace()) {
            unicastRequestMapper.attachComponent(entity, new ResilientRequest(source.getLinkNode(), destination.getLinkNode()));
            selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(resilienceSPAlgorithm));
        }

        ResilientPath result = resilientPathMapper.get(entity);
        if (result == null) {
            LOG.warn("No path found!");
            return null;
        } else {
            LOG.info("Found paths: " + result);
            List<List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link>> out = new LinkedList<>();
            out.add(this.buildListOfLinks(result.getPath1()));
            out.add(this.buildListOfLinks(result.getPath2()));
            LOG.info("Corresponding ODL links: " + out);
            return out;
        }
    }

    // Same as previous method but the topology is always refreshed
    public List<List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link>> findResilientBestEffortPathWithTopologyRefreshed(NodeId srcNodeId, NodeId dstNodeId) {
        LOG.info("Computing a resilient best-effort path between " + srcNodeId + " and " + dstNodeId + ".");

        // Get topology.
        fetchCurrentTopology();

        // Checking that source and destination nodes are known.
        NetworkNode source = nodesODLtoECES.get(srcNodeId.getValue());
        NetworkNode destination = nodesODLtoECES.get(dstNodeId.getValue());
        if(source == null || destination == null) {
            LOG.error("One (or more) of the specified nodes (\"" + srcNodeId + ", " + dstNodeId + "\") is not known (known nodes: " + nodesODLtoECES.keySet() + ")");
            return null;
        }

        /* Create entity to which we attach the request to be solved.
         * This is done in a Mapper Space so that, when we exit it, all the
         * listeners (that is including the listener routing the request) have
         * been executed. We can do this because we know that our current code
         * will not be wrapped in another Mapper Space. */
        Entity entity = controller.createEntity();
        try (MapperSpace ms = controller.startMapperSpace()) {
            unicastRequestMapper.attachComponent(entity, new ResilientRequest(source.getLinkNode(), destination.getLinkNode()));
            selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(resilienceSPAlgorithm));
        }

        ResilientPath result = resilientPathMapper.get(entity);
        if (result == null) {
            LOG.warn("No path found!");
            return null;
        } else {
            LOG.info("Found paths: " + result);
            List<List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link>> out = new LinkedList<>();
            out.add(this.buildListOfLinks(result.getPath1()));
            out.add(this.buildListOfLinks(result.getPath2()));
            LOG.info("Corresponding ODL links: " + out);
            return out;
        }
    }

    /**
     * Embeds disjoint best-effort flows from one source to different
     * destinations.
     *
     * @param srcNodeId               Source switch of the flow.
     * @param srcNodePort             Connector of the source end host on the source switch.
     *                                (null for switch-to-switch flows)
     * @param dstNodeIds              Destination switches.
     * @param dstNodePorts            Connector of the destination end hosts on
     *                                the destination switches.
     *                                (null for switch-to-switch flows).
     * @param matchingStructure       Matching structure.
     * @param meterId                 Id of the flow meter that the resource manager should
     *                                update.
     * @return an embedding ID.
     *         -1 if no path was found/if an error occurred (e.g., unknown nodes).
     */
    public long embedBestEffortDisjointPaths(NodeId srcNodeId,
                                             NodeConnectorId srcNodePort,
                                             List<NodeId> dstNodeIds,
                                             List<NodeConnectorId> dstNodePorts,
                                             Match matchingStructure,
                                             Long meterId) {
        List<List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link>> result = findBestEffortDisjointPaths(srcNodeId, dstNodeIds);
        if(result == null)
            return -1;
        else {
            LOG.info("Embedding the paths between " + srcNodeId + " (port: " + ((srcNodePort == null) ? "null" : srcNodePort) + ") and " + dstNodeIds + " (port: " + ((dstNodePorts == null) ? "null" : dstNodePorts) + ") with following matching.");
            LOG.info(((matchingStructure == null) ? "none" : matchingStructure.toString()));
            LOG.warn("No embedding because of missing resource manager interface");
            return 0;
        }
    }

    // Same as previous method but without embedding on the switches (but registers in Path Manager data structure)
    public List<List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link>> findBestEffortDisjointPaths(NodeId srcNodeId, List<NodeId> dstNodeIds) {
        LOG.info("Computing best-effort disjoint paths between " + srcNodeId + " and " + dstNodeIds + ".");

        // Get topology.
        fetchTopology();

        // Checking that source and destination nodes are known.
        NetworkNode source = nodesODLtoECES.get(srcNodeId.getValue());
        List<NetworkNode> destinations = new LinkedList<>();
        for(NodeId id : dstNodeIds)
            destinations.add(nodesODLtoECES.get(id.getValue()));

        if(source == null || destinations.contains(null)) {
            LOG.error("One (or more) of the specified nodes (\"" + srcNodeId + ", " + destinations + "\") is not known (known nodes: " + nodesODLtoECES.keySet() + ")");
            return null;
        }

        // Shortest path for best-effort.
        disjointAlgorithm.setProxy(new ShortestPathProxy());

        /* Create entity to which we attach the request to be solved.
         * This is done in a Mapper Space so that, when we exit it, all the
         * listeners (that is including the listener routing the request) have
         * been executed. We can do this because we know that our current code
         * will not be wrapped in another Mapper Space. */
        Entity entity = controller.createEntity();
        List<Node> destNodes = new LinkedList<>();
        for(NetworkNode dst : destinations)
            destNodes.add(dst.getLinkNode());

        try (MapperSpace ms = controller.startMapperSpace()) {
            unicastRequestMapper.attachComponent(entity, new DisjointRequest(source.getLinkNode(), destNodes));
            selectedRoutingAlgorithmMapper.attachComponent(entity, new SelectedRoutingAlgorithm(disjointAlgorithm));
        }

        DisjointPaths result = disjointPathsMapper.get(entity);
        if (result == null) {
            LOG.warn("No paths found!");
            return null;
        } else {
            LOG.info("Found paths: " + result);
            List<List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link>> out = new LinkedList<>();
            for(Path path : result.getPaths()) {
                List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link> links = this.buildListOfLinks(path);
                LOG.info("  Corresponding ODL links: " + links);
                out.add(links);
            }

            return out;
        }
    }

    /**
     * Removes a previously embedded flow (both best-effort and real-time).
     *
     * @param embeddingId ID of the previously embedded path.
     * @return true/false based on success failure.
     */
    public boolean removePath(long embeddingId) {
        return true;//return resourceManager.removePath(embeddingId);
    }

    /**
     * Builds a list of ODL links from a Path object.
     *
     * @param path Path object.
     * @return List of ODL Links.
     */
    private List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link> buildListOfLinks(Path path) {
        List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link> result = new LinkedList<>();
        for (Edge edge : path.getPath()) {
            Link link = linkMapper.get(toNetworkMapper.get(edge.getEntity()).getNetworkEntity());
            result.add(linksECEStoODL.get(link));
        }

        return result;
    }

    /**
     * Builds the list of queues IDs from a path object.
     *
     * @param path Path object.
     * @return Queue IDs.
     */
    private List<Long> buildQueueIDs(Path path) {
        List<Long> queues = new LinkedList<>();
        for (int i = 0; i < path.getPath().length; i++) {
            Edge edge = path.getPath()[i];
            Queue queue = queueMapper.get(edge.getEntity());
            Scheduler scheduler = queue.getScheduler();
            long queueID = -1;
            for (int j = 0; j < scheduler.getQueues().length; j++) {
                if (queue == scheduler.getQueues()[j]) {
                    queueID = j;
                    break;
                }
            }

            queues.add(queueID);
        }

        return queues;
    }

    /**
     * Adds a node to the topology of ECES.
     *
     * @param nodeId Node ID to add.
     */
    private void newNode(String nodeId) {
        if (nodesODLtoECES.containsKey(nodeId)) {
            LOG.warn("Trying to create a Node which already exists (" + nodeId + ")... ignored!");
            return;
        }

        LOG.info("Creating new node '" + nodeId + "'");
        NetworkNode newNode = networkingSystem.createNode(network, nodeId);
        nodesODLtoECES.put(nodeId, newNode);
    }

    /**
     * Adds a link to the topology of ECES.
     *
     * @param link        ODL link to add.
     * @param rate        Rate (in bytes/s) of the link.
     * @param bufferSizes Size of the buffers (in bytes) of the link.
     */
    private void newLink(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link link,
                         double rate,
                         double[] bufferSizes) {
        if (linksODLtoECES.containsKey(link.getLinkId().getValue())) {
            LOG.warn("Trying to create a Link with an ID which already exists (" + link + ")... ignored!");
            return;
        }

        String srcNodeId = link.getSource().getSourceNode().getValue();
        String dstNodeId = link.getDestination().getDestNode().getValue();
        if(!nodesODLtoECES.containsKey(srcNodeId) || !nodesODLtoECES.containsKey(dstNodeId)) {
            LOG.warn("Trying to create a Link between two nodes (" + srcNodeId + " and " + dstNodeId + ") that do not exist (known nodes: " + nodesODLtoECES.values() + ")... ignored! NOTE: THIS IS A PROBLEM OF THE PATH MANAGER");
            return;
        }

        LOG.info("Creating new link " + link);
        Link ecesLink = networkingSystem.createLinkWithPriorityScheduling(nodesODLtoECES.get(srcNodeId), nodesODLtoECES.get(dstNodeId), rate, 0, bufferSizes);
        linksODLtoECES.put(link.getLinkId().getValue(), ecesLink);
        linksECEStoODL.put(ecesLink, link);
    }

    /**
     * Removes a link from the topology of ECES.
     *
     * @param link ODL link to remove.
     */
    private void removeLink(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link link) {
        String linkId = link.getLinkId().getValue();
        Link ecesLink = linksODLtoECES.get(linkId);
        if (ecesLink == null) {
            LOG.warn("Tried to remove " + link + " but it does not exist (known links: " + linksODLtoECES.values() + ")... ignored!");
            return;
        }

        LOG.info("Removing link " + link);
        networkingSystem.deleteLink(ecesLink);
        linksODLtoECES.remove(linkId);
        linksECEStoODL.remove(ecesLink);
    }

    @Override
    public Future<RpcResult<EmbedDisjointBestEffortOutput>> embedDisjointBestEffort(EmbedDisjointBestEffortInput input) {
        // Create lists
        List<NodeId> destinations = new LinkedList<>();
        for(Destinations dst : input.getDestinations())
            destinations.add(dst.getDestinationNode());
        List<NodeConnectorId> connectors = new LinkedList<>();
        for(DestinationConnectors dst : input.getDestinationConnectors())
            connectors.add(dst.getDestinationNodeConnectorId());

        long embeddingId = this.embedBestEffortDisjointPaths(
                input.getSourceNodeId(),
                input.getSourceNodeConnectorId(),
                destinations,
                connectors,
                input.getMatching(),
                input.getMeterId());
        return RpcResultBuilder.success(new EmbedDisjointBestEffortOutputBuilder().setEmbeddingId(embeddingId).build()).buildFuture();
    }

    @Override
    public Future<RpcResult<FindResilientBestEffortOutput>> findResilientBestEffort(FindResilientBestEffortInput input) {
        List<List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link>> result = findResilientBestEffortPath(input.getSourceNodeId(), input.getDestinationNodeId());
        return RpcResultBuilder.success(new FindResilientBestEffortOutputBuilder().setPaths(result.toString())).buildFuture();
    }

    @Override
    public Future<RpcResult<EmbedBestEffortOutput>> embedBestEffort(EmbedBestEffortInput input) {
        long embeddingId = this.embedBestEffortPath(
                input.getSourceNodeId(),
                input.getSourceNodeConnectorId(),
                input.getDestinationNodeId(),
                input.getDestinationNodeConnectorId(),
                input.getMatching(),
                input.getMeterId());
        return RpcResultBuilder.success(new EmbedBestEffortOutputBuilder().setEmbeddingId(embeddingId).build()).buildFuture();
    }

    @Override
    public Future<RpcResult<FindDisjointBestEffortOutput>> findDisjointBestEffort(FindDisjointBestEffortInput input) {
        // Create list
        List<NodeId> destinations = new LinkedList<>();
        for(org.opendaylight.yang.gen.v1.urn.eu.virtuwind.pathmanager.rev161017.find.disjoint.best.effort.input.Destinations dst : input.getDestinations())
            destinations.add(dst.getDestinationNode());
        List<List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link>> result = findBestEffortDisjointPaths(input.getSourceNodeId(), destinations);
        return RpcResultBuilder.success(new FindDisjointBestEffortOutputBuilder().setPaths(result.toString())).buildFuture();
    }

    @Override
    public Future<RpcResult<RemoveFlowOutput>> removeFlow(RemoveFlowInput input) {
        boolean success = this.removePath(input.getEmbeddingId());

        // TODO: remove from ECES data structures

        return RpcResultBuilder.success(new RemoveFlowOutputBuilder().setSuccess(success).build()).buildFuture();
    }

    @Override
    public Future<RpcResult<EmbedRealTimeOutput>> embedRealTime(EmbedRealTimeInput input) {
        // Transform lists.
        List<NodeId[]> intermediateNodes = new LinkedList<>();
        List<Double[]> intermediateNodesDelays = new LinkedList<>();

        List<IntermediateNodes> iN = input.getIntermediateNodes();
        if(iN == null)
            intermediateNodes = null;
        else {
            for (IntermediateNodes i : iN) {
                int size = i.getCandidates().size();
                NodeId[] candidates = new NodeId[size];
                for (int j = 0; j < candidates.length; j++)
                    candidates[j] = i.getCandidates().get(j).getNode();
                intermediateNodes.add(candidates);
            }
        }

        List<Delays> iD = input.getDelays();
        if(iD == null)
            intermediateNodesDelays = null;
        else {
            for (Delays i : iD) {
                int size = i.getCandidates().size();
                Double[] candidates = new Double[size];
                for (int j = 0; j < candidates.length; j++)
                    candidates[j] = i.getCandidates().get(j).getDelay().doubleValue();
                intermediateNodesDelays.add(candidates);
            }
        }

        long embeddingId = this.embedRealTimePath(
                input.getSourceNodeId(),
                input.getSourceNodeConnectorId(),
                input.getDestinationNodeId(),
                input.getDestinationNodeConnectorId(),
                input.getMatching(),
                input.getRate().doubleValue(),
                input.getBurst().doubleValue(),
                input.getMaxPacketSize().doubleValue(),
                input.getMaxDelay().doubleValue(),
                input.isResilience(),
                intermediateNodes,
                intermediateNodesDelays,
                input.getMeterId());
        return RpcResultBuilder.success(new EmbedRealTimeOutputBuilder().setEmbeddingId(embeddingId).build()).buildFuture();
    }

    @Override
    public Future<RpcResult<FindRealTimeOutput>> findRealTime(FindRealTimeInput input) {
        // Transform lists.
        List<NodeId[]> intermediateNodes = new LinkedList<>();
        List<Double[]> intermediateNodesDelays = new LinkedList<>();

        List<org.opendaylight.yang.gen.v1.urn.eu.virtuwind.pathmanager.rev161017.find.real.time.input.IntermediateNodes> iN = input.getIntermediateNodes();
        if(iN == null)
            intermediateNodes = null;
        else {
            for (org.opendaylight.yang.gen.v1.urn.eu.virtuwind.pathmanager.rev161017.find.real.time.input.IntermediateNodes i : iN) {
                int size = i.getCandidates().size();
                NodeId[] candidates = new NodeId[size];
                for (int j = 0; j < candidates.length; j++)
                    candidates[j] = i.getCandidates().get(j).getNode();
                intermediateNodes.add(candidates);
            }
        }

        List<org.opendaylight.yang.gen.v1.urn.eu.virtuwind.pathmanager.rev161017.find.real.time.input.Delays> iD = input.getDelays();
        if(iD == null)
            intermediateNodesDelays = null;
        else {
            for (org.opendaylight.yang.gen.v1.urn.eu.virtuwind.pathmanager.rev161017.find.real.time.input.Delays i : iD) {
                int size = i.getCandidates().size();
                Double[] candidates = new Double[size];
                for (int j = 0; j < candidates.length; j++)
                    candidates[j] = i.getCandidates().get(j).getDelay().doubleValue();
                intermediateNodesDelays.add(candidates);
            }
        }

        List<List<Pair<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link, Long>>> result = this.findRealTimePath(
                input.getSourceNodeId(),
                input.getDestinationNodeId(),
                input.getRate().doubleValue(),
                input.getBurst().doubleValue(),
                input.getMaxPacketSize().doubleValue(),
                input.getMaxDelay().doubleValue(),
                input.isResilience(),
                intermediateNodes,
                intermediateNodesDelays);
        return RpcResultBuilder.success(new FindRealTimeOutputBuilder().setResult(result.toString()).build()).buildFuture();
    }

    @Override
    public Future<RpcResult<FindBestEffortOutput>> findBestEffort(FindBestEffortInput input) {
        List<org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link> result = this.findBestEffortPath(input.getSourceNodeId(), input.getDestinationNodeId());
        return RpcResultBuilder.success(new FindBestEffortOutputBuilder().setPath(result.toString()).build()).buildFuture();
    }
}
