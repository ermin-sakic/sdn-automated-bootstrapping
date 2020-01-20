package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.experimental.stuff;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.SwitchBootstrappingAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;

import javax.annotation.Nonnull;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DummyStateListener implements ClusteredDataTreeChangeListener<Node> {

    private static final Logger LOG = LoggerFactory.getLogger(DummyStateListener.class);
    private DataBroker dataBroker = null;

    public DummyStateListener(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Node>> changes) {
        synchronized (this) {
            for (DataTreeModification<Node> change : changes) {
                Node dataBefore = change.getRootNode().getDataBefore();
                Node dataAfter = change.getRootNode().getDataAfter();

                String nodeId = change.getRootNode().getDataBefore().getKey().getId().getValue();
                String stateBefore = dataBefore.getAugmentation(SwitchBootstrappingAugmentation.class).
                        getSwitchBootsrappingState().getState().getName();
                String stateAfter = dataAfter.getAugmentation(SwitchBootstrappingAugmentation.class).
                        getSwitchBootsrappingState().getState().getName();
                String IP = dataBefore.getAugmentation(FlowCapableNode.class).getIpAddress().getIpv4Address().getValue();
                LOG.info("DUMMY-> ID: " + nodeId + " IP: " + IP + " Before: " + stateBefore + " After: " + stateAfter);

                /*
                InstanceIdentifier<Node> dataBeforeId = InstanceIdentifier.builder(Nodes.class)
                        .child(Node.class, new NodeKey(dataBefore.getKey())).build();
                InstanceIdentifier<Node> dataAfterId = InstanceIdentifier.builder(Nodes.class)
                        .child(Node.class, new NodeKey(dataAfter.getKey())).build();

                InstanceIdentifier<SwitchBootsrappingState> stateBeforeId = dataBeforeId.builder()
                        .augmentation(SwitchBootstrappingAugmentation.class)
                        .child(SwitchBootsrappingState.class).build();
                InstanceIdentifier<SwitchBootsrappingState> stateAfterId = dataAfterId.builder()
                        .augmentation(SwitchBootstrappingAugmentation.class)
                        .child(SwitchBootsrappingState.class).build();

                ReadOnlyTransaction readTransaction = dataBroker.newReadOnlyTransaction();
                SwitchBootsrappingState stateBefore = null;
                try {
                    Optional optionalData = readTransaction.read(LogicalDatastoreType.OPERATIONAL, stateBeforeId).get();
                    if (optionalData.isPresent()) {
                        stateBefore = (SwitchBootsrappingState) optionalData.get();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    readTransaction.close();
                }
                LOG.info("DUMMY-> State Before: " + stateBefore.getState().getIntValue());

                SwitchBootsrappingState stateAfter = null;
                try {
                    Optional optionalData = readTransaction.read(LogicalDatastoreType.OPERATIONAL, stateAfterId).get();
                    if (optionalData.isPresent()) {
                        stateAfter = (SwitchBootsrappingState) optionalData.get();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    readTransaction.close();
                }
                LOG.info("DUMMY-> State After: " + stateAfter.getState().getIntValue());
                */

                //LOG.info("DUMMY-> Modified Root Path Identifier: " + change.getRootPath().getRootIdentifier().firstKeyOf(Node.class));
                //LOG.info("DUMMY-> Modified Root Node Identifier: " + change.getRootNode().getIdentifier());
                //LOG.info("DUMMY-> Modified Root Node DataAfter: " + change.getRootNode().getDataAfter().toString());


            }
        }
    }
}
