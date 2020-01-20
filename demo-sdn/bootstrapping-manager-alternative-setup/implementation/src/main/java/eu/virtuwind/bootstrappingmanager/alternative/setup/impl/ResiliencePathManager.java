package eu.virtuwind.bootstrappingmanager.alternative.setup.impl;

import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import sun.awt.image.ImageWatched;

import java.util.List;

/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 10.07.18
 */
public interface ResiliencePathManager<T1, T2> {

    /**
     * Returns 2 disjoint paths between node1 and node2 if available;
     * If there is only one path available, returns a duplicate;
     * An interface implementation should always return a List of size 2
     * according to the previous spec
     *
     * @param node1
     * @param node2
     * @return
     */
    public List<List<Link>> get2DisjointPathsBetweenNodes(NodeId node1, NodeId node2);

    /**
     * Updates the internal cache path state of the manager for S2C pair
     * depending on the implementation and informs a caller with the
     * appropriate generic type T1 regarding the decisions taken
     *
     * @param node1
     * @param node2
     * @return
     */
    public T1 updateResilientPathsBetweenS2CNodes(NodeId node1, NodeId node2);

    /**
     * Tries to embed disjoint paths for a S2C pair and informs a caller
     * about the decisions taken with the implementation-dependent generic type T2
     *
     * @param node1
     * @param node2
     */
    public T2 embedResilientPathsBetweenS2CNodes(NodeId node1, NodeId node2);

    /**
     * Updates the internal cache path state of the manager for C2C pair
     * depending on the implementation and informs a caller with the
     * appropriate generic type T1 regarding the decisions taken
     *
     * @param node1
     * @param node2
     * @return
     */
    public T1 updateResilientPathsBetweenC2CNodes(NodeId node1, NodeId node2);

    /**
     * Tries to embed disjoint paths for a C2C pair and informs a caller
     * about the decisions taken with the implementation-dependent generic type T2
     *
     * @param node1
     * @param node2
     */
    public T2 embedResilientPathsBetweenC2CNodes(NodeId node1, NodeId node2);
}
