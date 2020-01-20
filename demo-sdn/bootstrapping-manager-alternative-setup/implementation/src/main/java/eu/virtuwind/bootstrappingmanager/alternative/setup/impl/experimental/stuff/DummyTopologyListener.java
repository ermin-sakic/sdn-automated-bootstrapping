package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.experimental.stuff;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;

public class DummyTopologyListener implements ClusteredDataTreeChangeListener<Topology> {

    private static final Logger LOG = LoggerFactory.getLogger(DummyTopologyListener.class);
    private DataBroker dataBroker = null;

    public DummyTopologyListener(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Topology>> changes) {

        synchronized (this) {
            for (DataTreeModification<Topology> change : changes) {

                DataObjectModification.ModificationType modtype = change.getRootNode().getModificationType();

                if(modtype.equals(DataObjectModification.ModificationType.WRITE)){
                    LOG.info("DUMMY-TOP: we have a write"); // only once beacuse the topology is created at the beginning only
                }

                String before = change.getRootNode().getDataBefore().getTopologyId().getValue();
                String after = change.getRootNode().getDataAfter().getTopologyId().getValue();
                String rootIdentifier = change.getRootPath().getRootIdentifier().toString();
                List<Node> newNodes = change.getRootNode().getDataAfter().getNode();
                for(Node newNode: newNodes) {
                    LOG.info("DUMMY-TOP: " + newNode.getKey().getNodeId().getValue() + " Before: " + before + " After: " + after);
                }
                List<Link> newLinks = change.getRootNode().getDataAfter().getLink();
                for(Link newLink: newLinks) {
                    LOG.info("DUMMY-TOP: " + newLink.getKey().getLinkId().getValue()
                            + " Source TP: " + newLink.getSource().getSourceTp().getValue()
                            + " Dest TP: " +  newLink.getDestination().getDestTp().getValue());
                }
            }


        }

    }
}
