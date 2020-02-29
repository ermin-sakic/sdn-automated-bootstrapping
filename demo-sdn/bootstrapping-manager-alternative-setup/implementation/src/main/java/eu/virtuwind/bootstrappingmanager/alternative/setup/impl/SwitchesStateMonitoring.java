package eu.virtuwind.bootstrappingmanager.alternative.setup.impl;

import eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities.InitialFlowUtils;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.SwitchBootstrappingAugmentation;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;

/**
 * The class that tracks current states of the switches. Its purpose is just to track state transitions and to facilitate
 * debugging if something eventually goes wrong.
 */
public class SwitchesStateMonitoring implements ClusteredDataTreeChangeListener<SwitchBootsrappingState> {

    private static final Logger LOG = LoggerFactory.getLogger(SwitchesStateMonitoring.class);
    private DataBroker dataBroker = null;

    public SwitchesStateMonitoring(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<SwitchBootsrappingState>> changes) {
        // TODO: execute only for a leader
        synchronized (this) {
            List<Node> nodes = InitialFlowUtils.getAllRealNodes(dataBroker);
            LOG.info("#################################################################################");
            LOG.info("Current state of the discovered nodes:");

            for (Node node : nodes) {
                SwitchBootstrappingAugmentation switchBootstrappingAugmentation = node.getAugmentation(SwitchBootstrappingAugmentation.class);
                try {
                    String swState = switchBootstrappingAugmentation.getSwitchBootsrappingState().getState().getName();
                    LOG.info("Node: {} -> State: {}", node.getId().getValue(), swState);
                } catch (NullPointerException e) {
                    LOG.info("Node: {} -> State: Not available", node.getId().getValue());

                }

            }

            LOG.info("#################################################################################");

            //TODO: reload this part from the datastore when a leader fails

            LOG.info("--------------------------------------------------------------------------------------");
            LOG.info("Switch states log:");
            LOG.info("{}:{}", SwitchBootsrappingState.State.OFSESSIONESTABLISHED.getName(), InitialFlowWriter.initialOFSessionEstablishedDoneNodes.toString());
            LOG.info("{}:{}", SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYRULEINSTALLED.getName(), ControllerSelfDiscovery.initialSelfDiscoveryRuleInstalledDoneNodes.toString());
            LOG.info("{}:{}", SwitchBootsrappingState.State.CONTROLLERSELFDISCOVERYDONE.getName(), ControllerSelfDiscovery.initialSelfDiscoveryDoneNodes.toString());
            LOG.info("{}:{}", SwitchBootsrappingState.State.INITIALOFRULESPHASEIDONE.getName(), InitialOFRulesPhaseI.initialOFPhaseIDoneNodes.toString());
            LOG.info("{}:{}", SwitchBootsrappingState.State.INITIALOFRULESPHASEIIDONE.getName(), InitialOFRulesPhaseII.initialOFPhaseIIDoneNodes.toString());
            LOG.info("{}:{}", SwitchBootsrappingState.State.INTERMEDIATERESILIENCEINSTALLED.getName(), FlowReconfiguratorForResilienceStateListener.intermediateResilienceDoneNodes.toString());
            LOG.info("--------------------------------------------------------------------------------------");

        }
    }

    public static class SBuilderWithNewLine {
        private StringBuilder sb = new StringBuilder();

        public SBuilderWithNewLine add(String stateName, String stateTracker) {

                sb.append(stateName).append(":").append(stateTracker).append(System.lineSeparator());

            return this;
        }

        public String build() {
            return sb.toString();
        }
    }
}
