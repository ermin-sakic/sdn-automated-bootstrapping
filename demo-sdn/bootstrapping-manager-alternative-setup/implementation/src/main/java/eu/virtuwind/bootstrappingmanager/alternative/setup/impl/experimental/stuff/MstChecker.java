package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.experimental.stuff;

import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.graphutilities.TopologyLinkDataChangeHandler;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;

public class MstChecker implements ClusteredDataTreeChangeListener<SwitchBootsrappingState> {

    private static final Logger LOG = LoggerFactory.getLogger(MstChecker.class);
    private DataBroker dataBroker = null;

    public MstChecker(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<SwitchBootsrappingState>> changes) {
        synchronized (this) {

            for (DataTreeModification<SwitchBootsrappingState> change : changes) {

                String after = change.getRootNode().getDataAfter().getState().getName();

                if (after.equals(SwitchBootsrappingState.State.INITIALOFRULESPHASEIDONE.getName())) {
                    NodeId switchId = change.getRootPath().getRootIdentifier().firstKeyOf(Node.class).getId();
                    InstanceIdentifier<FlowCapableNode> nodeInstanceIdentifier = InstanceIdentifier.builder(Nodes.class)
                            .child(Node.class, new NodeKey(switchId)).augmentation(FlowCapableNode.class).build();

                    // TODO: maybe sleep a little bit to get all links
                    try {
                        Thread.sleep(5000); // Worst case for LLDP packet in traffic
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    List<Link> mstLinks = TopologyLinkDataChangeHandler.mstLinks;
                    List<Link> allLinks = TopologyLinkDataChangeHandler.allLinks;
                    /*
                    LOG.info("-------------------------------------------------------------------------------");
                    LOG.info("TESTING MST SERVICE");
                    LOG.info("-------------------------------------------------------------------------------");
                    // ti be sure perform 5 times
                    for (int i = 0; i < 5; i++) {
                        LOG.info("Iteration number {}", i);
                        if (!mstLinks.isEmpty()){
                            for(Link mstLink: mstLinks) {
                                LOG.info("MST Link: " + mstLink.getKey().getLinkId().getValue());
                            }
                        } else {
                            LOG.info("MST Link list empty");
                        }

                        if (!allLinks.isEmpty()){
                            for(Link allLink: allLinks) {
                                LOG.info("ALL Link: " + allLink.getKey().getLinkId().getValue());
                            }
                        } else {
                            LOG.info("ALL Link list empty");
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    LOG.info("-------------------------------------------------------------------------------");
                    */


                }

            }


        }
    }
}


