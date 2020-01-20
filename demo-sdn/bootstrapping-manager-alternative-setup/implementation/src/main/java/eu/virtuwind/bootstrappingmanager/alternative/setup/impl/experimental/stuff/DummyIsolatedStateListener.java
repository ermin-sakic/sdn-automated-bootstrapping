package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.experimental.stuff;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;

import javax.annotation.Nonnull;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DummyIsolatedStateListener implements ClusteredDataTreeChangeListener<SwitchBootsrappingState> {

    private static final Logger LOG = LoggerFactory.getLogger(DummyIsolatedStateListener.class);
    private DataBroker dataBroker = null;

    public DummyIsolatedStateListener(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<SwitchBootsrappingState>> changes) {
        synchronized (this) {
            for (DataTreeModification<SwitchBootsrappingState> change : changes) {

                String before = change.getRootNode().getDataBefore().getState().getName();
                String after = change.getRootNode().getDataAfter().getState().getName();
                String rootIdentifier = change.getRootPath().getRootIdentifier().toString();
                String nodeIdent = change.getRootPath().getRootIdentifier().firstKeyOf(Node.class).getId().getValue();
                LOG.info("ISOLATED-> nodeIdent: " + nodeIdent + " Before: " + before + " After: " + after);

            }


        }
    }
}


