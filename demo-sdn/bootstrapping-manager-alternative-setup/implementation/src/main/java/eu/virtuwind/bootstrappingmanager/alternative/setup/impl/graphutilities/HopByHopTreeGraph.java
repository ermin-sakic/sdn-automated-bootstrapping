/**
  *
  * @filename HopByHopTreeGraph.java
  *
  * @date 27.04.18
  *
  * @author Mirza Avdic
  *
  *
 */
package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseGraph;
import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.HostUtilities;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.host.tracker.rev140624.HostNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/* Class created for purposes of building tree necessary to avoid broadcast storming effects
 *  during the bootstrapping procedure
 * */
public class HopByHopTreeGraph extends UndirectedSparseGraph<NodeId, Link> {

    private static final Logger LOG = LoggerFactory.getLogger(HopByHopTreeGraph.class);
    private static DataBroker dataBroker;
    protected NodeId graphControllerLeader = null;
    protected NodeId graphRootNode = null;
    protected Map<NodeId, Integer> vertexTreeLevel = null;
    private static final String dsTreeTopologyId = "hopbyhoptree";
    private boolean topologyDSCreated = false;

    public static ArrayList<String> ctlIPs = new ArrayList<>();

    public HopByHopTreeGraph() {
        super();
    }

    public HopByHopTreeGraph(DataBroker dataBroker) {
        super();
        this.dataBroker = dataBroker;
    }

    public HopByHopTreeGraph(Graph<NodeId, Link> graph) {
        super();
        for (NodeId nodeId: graph.getVertices()) {
            this.addVertex(nodeId);
        }
        for (Link link: graph.getEdges()){
            NodeId srcNode = link.getSource().getSourceNode();
            NodeId dstNode = link.getDestination().getDestNode();
            this.addEdge(link, srcNode, dstNode);
        }

        // find interface of the controller instance on which this code is being executed
        HostUtilities.InterfaceConfiguration myIfConfig =
                HostUtilities.returnMyIfConfig(ctlIPs);
        if (myIfConfig == null) {
            LOG.warn("Provided controller IP addresses do not found on this machine.");
        }
        String leaderControllerId = "host:" + myIfConfig.getMacAddressString();
        this.setGraphControllerLeader(new NodeId(leaderControllerId));

        // assumption no multihoming
        List<NodeId> leaderControllerNeighbors = new ArrayList<>(this.getNeighbors(getGraphControllerLeader()));
        NodeId rootSwitchNode = leaderControllerNeighbors.get(0);
        this.setGraphRootNode(rootSwitchNode);
        updateTreeLevelsRecursively(rootSwitchNode, 0);
    }

    public static String getDsTreeTopologyId() { return dsTreeTopologyId; }

    public NodeId getGraphRootNode() {
        return graphRootNode;
    }

    public void setGraphRootNode(NodeId graphRootNode) {
        this.graphRootNode = graphRootNode;
    }

    public NodeId getGraphControllerLeader() { return graphControllerLeader; }

    public void setGraphControllerLeader(NodeId graphControllerLeader) { this.graphControllerLeader = graphControllerLeader; }

    public Integer getVertexTreeLevel(NodeId node) {
        return vertexTreeLevel.get(node);
    }

    public Integer setVertexTreeLevel(NodeId node, Integer level) {

        if (vertexTreeLevel == null) {
            vertexTreeLevel = new HashMap<NodeId, Integer>();
        }
        LOG.debug("Adding VertexTreeLevel {} to the node {}.", level, node.toString());
        this.vertexTreeLevel.put(node, level);

        return vertexTreeLevel.get(node);
    }

    public Integer getTreeDepth() {
        if (vertexTreeLevel.isEmpty()) {
            return null;
        }
        Integer treeDepth = null;
        for(Map.Entry e : vertexTreeLevel.entrySet()){
            if (treeDepth == null) {
                treeDepth = (Integer) e.getValue();
            } else {
                if (treeDepth < (Integer) e.getValue()) {
                    treeDepth = (Integer) e.getValue();
                }
            }
        }
        return treeDepth;
    }

    private void updateTreeLevelsRecursively(NodeId nodeId, Integer startingLevel) {
        this.setVertexTreeLevel(nodeId, startingLevel);
        for (NodeId neighborId: getNeighbors(nodeId)){
            if (getVertexTreeLevel(neighborId) == null) {
                updateTreeLevelsRecursively(neighborId, startingLevel + 1);
            }
        }
    }

    public List<NodeId> getAllNodesOfTheLevel(Integer level){
        List<NodeId> sameLevelNodes = new ArrayList<>();

        if (vertexTreeLevel == null) {
            return sameLevelNodes;
        }

        for (Map.Entry<NodeId, Integer> entry: vertexTreeLevel.entrySet()) {
            if (entry.getValue().equals(level)){
                sameLevelNodes.add(entry.getKey());
            }
        }

        return sameLevelNodes;
    }

    public List<NodeId> getSwitchNodesOfTheLevel(Integer level){
        List<NodeId> sameLevelNodes = new ArrayList<>();

        if (vertexTreeLevel == null) {
            return sameLevelNodes;
        }

        for (Map.Entry<NodeId, Integer> entry: vertexTreeLevel.entrySet()) {
            if (entry.getValue().equals(level)){
                if (!entry.getKey().getValue().contains("host"))
                    sameLevelNodes.add(entry.getKey());
            }
        }

        return sameLevelNodes;
    }

    public List<NodeId> getNextLevelNeighbors(NodeId nodeId) {
        List<NodeId> nextLevelNeighbors = new ArrayList<>();
        Integer nextLevel = getVertexTreeLevel(nodeId) + 1;

        for (NodeId vertex: this.getNeighbors(nodeId)) {
            if(this.getVertexTreeLevel(vertex).equals(nextLevel)) {
                nextLevelNeighbors.add(vertex);
            }
        }

        return nextLevelNeighbors;
    }

    public List<NodeId> getPreviousLevelNeighbors(NodeId nodeId) {
        List<NodeId> previousLevelNeighbors = new ArrayList<>();
        Integer previousLevel = getVertexTreeLevel(nodeId) - 1;

        for (NodeId vertex: this.getNeighbors(nodeId)) {
            if(this.getVertexTreeLevel(vertex).equals(previousLevel)) {
                previousLevelNeighbors.add(vertex);
            }
        }

        return previousLevelNeighbors;
    }

    public List<NodeId> getIsolatedVertices() {
        List<NodeId> isolatedVertices = new ArrayList<>();
        // find interface of the controller instance on which this code is being executed
        HostUtilities.InterfaceConfiguration myIfConfig =
                HostUtilities.returnMyIfConfig(ctlIPs);
        if (myIfConfig == null) {
            LOG.warn("Provided controller IP addresses do not found on this machine.");
            return isolatedVertices;
        }
        String leaderControllerId = "host:" + myIfConfig.getMacAddressString();

        // try to find SP between each node in the netwprk and the leader controller
        for (NodeId nodeId: getVertices()){
            if (!nodeId.getValue().equals(leaderControllerId)) {
                DijkstraShortestPath<NodeId, Link> shortestPath = new DijkstraShortestPath<NodeId, Link>(this);
                try {
                    List<Link> sp = shortestPath.getPath(new NodeId(leaderControllerId), nodeId);
                    if (sp.isEmpty()) {
                        // if an sp between the leader and nodeId does not exist in the tree, nodeId is isolated
                        isolatedVertices.add(nodeId);
                        LOG.debug("Isolated node in the tree -> {}", nodeId.getValue());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    LOG.warn("Provided vertices are not present in the tree!!!");
                }
            }
        }

        return isolatedVertices;
    }

    public String getVerticesFormatedNicely() {

        Collection<NodeId> vertices = this.getVertices();
        if (vertices == null || vertices.isEmpty()) {
            return "Vertices not available";
        }

        String formatedVerticesList = "\n";

        for (NodeId vertex: vertices) {
            formatedVerticesList += vertex.getValue() + "\n";
        }

        return formatedVerticesList;

    }

    public String getEdgesFormatedNicely() {

        Collection<Link> edges = this.getEdges();

        if (edges == null || edges.isEmpty()) {
            return "Edges not available";
        }

        String formatedEdgesList = "\n";

        for (Link edge: edges) {
            formatedEdgesList += "Edge-> Source: " + edge.getSource().getSourceNode().getValue() +
                    " Destination: " + edge.getDestination().getDestNode().getValue() + "\n";
        }

        return formatedEdgesList;
    }

    public void writeCurrentTreeInDS() {

        // TODO: Optimize for performance compare if necessary to write
        if (!topologyDSCreated) {
            InstanceIdentifier<Topology> topologyInstanceIdentifier = InstanceIdentifierUtils
                    .generateTopologyInstanceIdentifier(dsTreeTopologyId);
            WriteTransaction createTopologyHopByHopTreeWriteTranscation = dataBroker.newWriteOnlyTransaction();
            Topology treeTopology = new TopologyBuilder().setTopologyId(new TopologyId(dsTreeTopologyId)).build();

            createTopologyHopByHopTreeWriteTranscation.put(LogicalDatastoreType.OPERATIONAL, topologyInstanceIdentifier, treeTopology);

            CheckedFuture<Void, TransactionCommitFailedException> futureTreeTopology =
                    createTopologyHopByHopTreeWriteTranscation.submit();

            Futures.addCallback(futureTreeTopology, new FutureCallback<Void>() {
                        public void onSuccess(Void v) {
                            LOG.info("Hop-By-Hop-Tree Topology Data-Tree Initialized");
                        }
                        public void onFailure(Throwable thrown) {
                            LOG.warn("Hop-By-Hop-Tree Topology Data-Tree Initialization Failed.");
                        }
                    }
            );

            topologyDSCreated = true;
        }

        InstanceIdentifier<Topology> topologyInstanceIdentifier = InstanceIdentifierUtils
                .generateTopologyInstanceIdentifier(dsTreeTopologyId);

        WriteTransaction onlyWriteTransaction = dataBroker.newWriteOnlyTransaction();
        List<Link> treeEdges = new ArrayList<>(getEdges());
        List<NodeId> treeVerticesIds = new ArrayList<>(getVertices());
        List<Node> treeVertices = TreeUtils.getTopologyNodesFromTopologyNodeIds(treeVerticesIds);
        Topology treeTopologyToWrite = new TopologyBuilder().setTopologyId(new TopologyId(dsTreeTopologyId))
                .setLink(treeEdges).setNode(treeVertices).build();

        // merge or put  -> merge preserves old data which leads to inconsistent links in the tree
        onlyWriteTransaction.put(LogicalDatastoreType.OPERATIONAL, topologyInstanceIdentifier, treeTopologyToWrite);
        CheckedFuture<Void, TransactionCommitFailedException> futureTreeTopology =
                onlyWriteTransaction.submit();

        Futures.addCallback(futureTreeTopology, new FutureCallback<Void>() {
                public void onSuccess(Void v) {
                    LOG.info("Hop-By-Hop-Tree Topology Data-Tree Refreshed");
                }
                public void onFailure(Throwable thrown) {
                    LOG.warn("Hop-By-Hop-Tree Topology Data-Tree Refreshing Failed.");
                }
            }
        );
    }

}
