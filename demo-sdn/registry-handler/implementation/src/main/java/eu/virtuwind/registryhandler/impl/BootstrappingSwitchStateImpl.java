package eu.virtuwind.registryhandler.impl;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.SwitchBootstrappingAugmentation;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.SwitchBootstrappingAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingState;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev180417.nodes.node.SwitchBootsrappingStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
//import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev130712.NodeId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutionException;

/**
 * The class containing all methods related to storing/retrieving the state of the switch
 * during the bootstrapping procedure in the distributed MD-SAL data-store.
 */

public class BootstrappingSwitchStateImpl {
    private static final Logger logger = LoggerFactory.getLogger(BootstrappingSwitchStateImpl.class);
    private DataBroker dataBroker;
    private static BootstrappingSwitchStateImpl bootstrappingSwitchStateRegistry = null;

    /**
     * Default constructor
     */
    BootstrappingSwitchStateImpl() {}

    /**
     * Constructor for particular node
     * @param nodeid
     */
    BootstrappingSwitchStateImpl(NodeId nodeid) { initializeBootstrappingSwitchStateRegistryDataTree(nodeid);}


    /**
     * Initializes the BoostrappingManager data-store by creating the bootstrapping registry data-tree.
     */
    private void initializeBootstrappingSwitchStateRegistryDataTree(NodeId nodeid) {
        logger.info("Preparing to initialize the Switch Bootstrapping State DS");

        WriteTransaction switchStateInitialized = dataBroker.newWriteOnlyTransaction();

        InstanceIdentifier<SwitchBootsrappingState> switchBootstrappingStateDatastoreIId =
                InstanceIdentifier.create(Nodes.class)
                .child(Node.class, new NodeKey(nodeid))
                .augmentation(SwitchBootstrappingAugmentation.class)
                .child(SwitchBootsrappingState.class);
        SwitchBootsrappingState initializeSwitchBootstrappingStateDataStore = new SwitchBootsrappingStateBuilder().build();


        switchStateInitialized.put(LogicalDatastoreType.OPERATIONAL,
                switchBootstrappingStateDatastoreIId, initializeSwitchBootstrappingStateDataStore);
        CheckedFuture<Void, TransactionCommitFailedException> futureBootstrappingDatastore =
                switchStateInitialized.submit();


        Futures.addCallback(futureBootstrappingDatastore, new FutureCallback<Void>() {
                    public void onSuccess(Void v) {
                        logger.info("Bootstrapping Switch State Data-Tree Initialized");
                    }
                    public void onFailure(Throwable thrown) {
                        logger.info("Bootstrapping Switch State Datatree Initialization Failed.");
                    }
                }
        );

    }

    /**
     * Return an instance of the BootstrappingSwitchStateImpl.
     * @return BootstrappingRegistryImpl singleton instance.
     */
    public static BootstrappingSwitchStateImpl getInstance()
    {
        if(bootstrappingSwitchStateRegistry == null)
            bootstrappingSwitchStateRegistry = new BootstrappingSwitchStateImpl();

        return bootstrappingSwitchStateRegistry;
    }

    /**
     * DataBroker setter
     *
     * @param db data broker to be used
     */
    void setDb(DataBroker db) {
        this.dataBroker = db;
    }

    /**
     * Reads the current bootstrapping state of the switch.
     *
     * @param nodeId id of the switch from which you want to read the current state
     * @return       current bootstrapping state of the switch
     */

    public int readBootstrappingSwitchState (NodeId nodeId) {
        int state;
        SwitchBootstrappingAugmentation sw = null;
        ReadOnlyTransaction readTransaction = dataBroker.newReadOnlyTransaction();
        InstanceIdentifier<SwitchBootstrappingAugmentation> nodeInstanceIdentifier = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(nodeId)).augmentation(SwitchBootstrappingAugmentation.class).build();
        try {
            Optional optionalData = readTransaction.read(LogicalDatastoreType.OPERATIONAL, nodeInstanceIdentifier).get();
            if (optionalData.isPresent()) {
                sw = (SwitchBootstrappingAugmentation) optionalData.get();
            }
        } catch (ExecutionException | InterruptedException e) {
            readTransaction.close();
        }

        if (sw != null) {

            state = sw.getSwitchBootsrappingState().getState().getIntValue();

            return state;
        } else {
            return -1;
        }
    }

    public String readBootstrappingSwitchStatename (NodeId nodeId) {
        String state;
        SwitchBootstrappingAugmentation sw = null;
        ReadOnlyTransaction readTransaction = dataBroker.newReadOnlyTransaction();
        InstanceIdentifier<SwitchBootstrappingAugmentation> nodeInstanceIdentifier = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(nodeId)).augmentation(SwitchBootstrappingAugmentation.class).build();
        try {
            Optional optionalData = readTransaction.read(LogicalDatastoreType.OPERATIONAL, nodeInstanceIdentifier).get();
            if (optionalData.isPresent()) {
                sw = (SwitchBootstrappingAugmentation) optionalData.get();
            }
        } catch (ExecutionException | InterruptedException e) {
            readTransaction.close();
        }

        if (sw != null) {

            state = sw.getSwitchBootsrappingState().getState().getName();

            return state;
        } else {
            return "";
        }
    }

    /**
     * Reads the current bootstrapping state name of the switch.
     *
     * @param nodeId id of the switch from which you want to read the current state
     * @return       current bootstrapping state of the switch
     */

    public String readBootstrappingSwitchStateName (NodeId nodeId) {
        String state;
        SwitchBootstrappingAugmentation sw = null;
        ReadOnlyTransaction readTransaction = dataBroker.newReadOnlyTransaction();
        InstanceIdentifier<SwitchBootstrappingAugmentation> nodeInstanceIdentifier = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(nodeId)).augmentation(SwitchBootstrappingAugmentation.class).build();
        try {
            Optional optionalData = readTransaction.read(LogicalDatastoreType.OPERATIONAL, nodeInstanceIdentifier).get();
            if (optionalData.isPresent()) {
                sw = (SwitchBootstrappingAugmentation) optionalData.get();
            }
        } catch (ExecutionException | InterruptedException e) {
            readTransaction.close();
        }

        if (sw != null) {

            state = sw.getSwitchBootsrappingState().getState().getName();

            return state;
        } else {
            return "";
        }
    }

    /**
     * Writes the current bootstrapping state to the switch.
     *
     * @param nodeId id of the switch to which you want to write the current state
     */
    public void writeBootstrappingSwitchState (NodeId nodeId, SwitchBootsrappingState state) {

        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        InstanceIdentifier<SwitchBootstrappingAugmentation> nodeInstanceIdentifier = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class, new NodeKey(nodeId)).augmentation(SwitchBootstrappingAugmentation.class).build();
        SwitchBootstrappingAugmentation newState =  new SwitchBootstrappingAugmentationBuilder()
                .setSwitchBootsrappingState(state)
                .build();

        writeTransaction.merge(LogicalDatastoreType.OPERATIONAL, nodeInstanceIdentifier, newState, true);
        CheckedFuture<Void, TransactionCommitFailedException> commitFuture = writeTransaction.submit();
        try {
            commitFuture.checkedGet();
            logger.info("New bootstrapping switch state successfully written");
        } catch (Exception e) {
            writeTransaction.cancel();
            logger.info("New bootstrapping switch state canceled!");
        }


    }

}
