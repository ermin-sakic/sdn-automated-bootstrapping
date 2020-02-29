package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.experimental.stuff;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnector;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collection;

public class DummyNodeConnectorListener implements ClusteredDataTreeChangeListener<FlowCapableNodeConnector> {

    private static final Logger LOG = LoggerFactory.getLogger(DummyNodeConnectorListener.class);
    private DataBroker dataBroker = null;

    public DummyNodeConnectorListener(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<FlowCapableNodeConnector>> changes) {

        synchronized (this) {
            for (DataTreeModification<FlowCapableNodeConnector> change : changes) {

                DataObjectModification.ModificationType modtype = change.getRootNode().getModificationType();

                if(modtype.equals(DataObjectModification.ModificationType.WRITE)){
                    LOG.info("DUMMY-CONNECTOR: we have a write");
                    String nodeConnectorId = change.getRootPath().getRootIdentifier().firstKeyOf(NodeConnector.class).getId().getValue();
                    if (nodeConnectorId == null)
                        nodeConnectorId = "";
                    LOG.info("DUMMY-CONNECTOR: NodeConnectorId-> {}", nodeConnectorId);
                    //FlowCapableNodeConnector flowCapableNodeConnector = change.getRootNode()
                    //        .getDataAfter().getAugmentation(FlowCapableNodeConnector.class);
                    FlowCapableNodeConnector flowConnector = (FlowCapableNodeConnector) change.getRootNode()
                            .getDataAfter();
                    /*
                    LOG.info("DUMMY-CONNECTOR: PortNumber-> {}", flowCapableNodeConnector.getPortNumber().getUint32());
                    LOG.info("DUMMY-CONNECTOR: PortName-> {}", flowCapableNodeConnector.getName());
                    LOG.info("DUMMY-CONNECTOR: Port MAC-> {}", flowCapableNodeConnector.getHardwareAddress().toString());
                    */
                    LOG.info("DUMMY-CONNECTOR: PortNumber-> {}", flowConnector.getPortNumber().getUint32());
                    LOG.info("DUMMY-CONNECTOR: PortName-> {}", flowConnector.getName());

                } else if (modtype.equals(DataObjectModification.ModificationType.SUBTREE_MODIFIED)) {
                    LOG.info("DUMMY-CONNECTOR: we have a modification");
                    //FlowCapableNodeConnector flowCapableNodeConnector = change.getRootNode()
                    //        .getDataAfter().getAugmentation(FlowCapableNodeConnector.class);
                    FlowCapableNodeConnector flowConnector = (FlowCapableNodeConnector) change.getRootNode()
                            .getDataAfter();
                    /*
                    LOG.info("DUMMY-CONNECTOR: PortNumber-> {}", flowCapableNodeConnector.getPortNumber().getUint32());
                    LOG.info("DUMMY-CONNECTOR: PortName-> {}", flowCapableNodeConnector.getName());
                    LOG.info("DUMMY-CONNECTOR: Port MAC-> {}", flowCapableNodeConnector.getHardwareAddress().toString());
                    */
                    LOG.info("DUMMY-CONNECTOR: PortNumber-> {}", flowConnector.getPortNumber().getUint32());
                    LOG.info("DUMMY-CONNECTOR: PortName-> {}", flowConnector.getName());
                }
            }
        }
    }
}
