package eu.virtuwind.registryhandler.impl;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
// commented out to compile, before applying relevant changes to align with the updated yang model. HINT: path-segment-offer-list was changed to grouping instead of container
//import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.orchestrator.rev161017.PathSegmentOfferList;
//import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.orchestrator.rev161017.PathSegmentOfferListBuilder;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.rev161017.*;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.rev161017.examplary.path.manager.store.EdgeCostPair;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.rev161017.examplary.path.manager.store.EdgeCostPairBuilder;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.rev161017.examplary.path.manager.store.EdgeCostPairKey;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.rev161017.generic.kv.store.KvPair;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.rev161017.generic.kv.store.KvPairBuilder;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.rev161017.generic.kv.store.KvPairKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

/**
 * A simple implementation of a key-value store and testing methods for evaluation of data-store performance.
 */
public class RegistryHandlerImpl implements RegistryHandlerService {
    private static final Logger logger = LoggerFactory.getLogger(RegistryHandlerImpl.class);
    private  DataBroker dataBroker;
    private static RegistryHandlerImpl instance = null;

    /**
     * Sets the RegistryHandler data-store broker and automatically initializes the data-tree.
     */
    void setDb(DataBroker db) {
        dataBroker = db;
        initializeDataTree();
    }

    /**
     * Default constructor
     */
    private RegistryHandlerImpl(){}

    /**
     * Return an instance of the RegistryHandlerImpl.
     * @return Singleton instance
     */
    public static RegistryHandlerImpl getInstance()
    {
        if(instance == null)
            instance = new RegistryHandlerImpl();

        return instance;
    }

    /**
     * An exemplary population of a path-manager specific yang structure.
     */
    private void edgeCostPopulator()
    {
        EdgeCostPair newEdgeCostPair = new EdgeCostPairBuilder()
                .setEdgeId("OpenFlow1:E12")
                .setBacklog(2343)
                .setBurstRate(2321)
                .setFlowRate(232323)
                .setNumberFlows(12)
                .build();

        genericWriteToStore(newEdgeCostPair);
    }

    /**
     * Initializes the data-tree for storage of simple key-value pairs in the MD-SAL modeled data-store.
     */
    private void initializeDataTree() {
        logger.info("Preparing to initialize the Register Handler's Datatree");

        // Initialize the generic KV store
        WriteTransaction transactionKVRegistry = dataBroker.newWriteOnlyTransaction();
        InstanceIdentifier<GenericKvStore> KVregistryIId = InstanceIdentifier.create(GenericKvStore.class);
        GenericKvStore initializeKVStoreRegistry = new GenericKvStoreBuilder().build();

        transactionKVRegistry.put(LogicalDatastoreType.OPERATIONAL, KVregistryIId, initializeKVStoreRegistry);
        CheckedFuture<Void, TransactionCommitFailedException> future = transactionKVRegistry.submit();

        Futures.addCallback(future, new FutureCallback<Void>() {
                    public void onSuccess(Void v) {
                        logger.info("KV Datatree Initialized");
                    }
                    public void onFailure(Throwable thrown) {
                        logger.info("KV Datatree Initialization Failed.");
                    }
                }
        );

        // Initialize the exemplary path manager store
        WriteTransaction transactionEdgeCostStore = dataBroker.newWriteOnlyTransaction();
        InstanceIdentifier<ExamplaryPathManagerStore> examplaryPathManagerStoreIId = InstanceIdentifier.create(ExamplaryPathManagerStore.class);
        ExamplaryPathManagerStore initializeEdgeCostStore = new ExamplaryPathManagerStoreBuilder().build();

        transactionEdgeCostStore.put(LogicalDatastoreType.OPERATIONAL, examplaryPathManagerStoreIId, initializeEdgeCostStore);
        CheckedFuture<Void, TransactionCommitFailedException> futureExemplaryStore = transactionEdgeCostStore.submit();

        Futures.addCallback(future, new FutureCallback<Void>() {
                    public void onSuccess(Void v) {
                        logger.info("Edge-Cost Datatree Initialized");
                    }
                    public void onFailure(Throwable thrown) {
                        logger.info("Edge-Cost Datatree Initialization Failed.");
                    }
                }
        );
    }

    /**
     * Write accessor to YANG modeled key-value store.
     * @param key Unique key for the key-value pair that is to be written into the distributed data-store.
     * @param val Value mapped to the key.
     * @return Boolean that indicates success or failure of data-store transaction.
     */
    public boolean writeKVPairToDataStore(Object key, Object val) {
        logger.info("Identifying the object instance...)");

        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();

        if(key instanceof String && val instanceof String)
        {
            KvPair newKVPair = new KvPairBuilder()
                    .setKvKey(key.toString())
                    .setKvValue(val.toString())
                    .build();

            InstanceIdentifier<KvPair> newKVPairIId = kvStorePairIID(key.toString());
            transaction.put(LogicalDatastoreType.OPERATIONAL, newKVPairIId, newKVPair);
        }
        else return false;

        CheckedFuture<Void, TransactionCommitFailedException> future = transaction.submit();

        Futures.addCallback(future, new FutureCallback<Void>() {
            public void onSuccess(Void v) {
                logger.info("New KV entry successfully added to registry.");
            }
            public void onFailure(Throwable thrown) {
                logger.warn("Adding new KV entry failed.");
            }
        });

        // wait to finish writing
        while(!future.isDone())
        {
        }

        return !future.isCancelled() && future.isDone();
    }

    /**
     * Removes a key-value pair from the MD-SAL distributed data-store based on a given key input.
     * @param key Unique key for the key-value pair that is to be written into the distributed data-store.
     * @return Boolean that indicates success of transaction.
     */
    public boolean removeKVPairFromDataStore(Object key) {
        logger.info("Identifying the object instance...)");

        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();

        if(key instanceof String)
        {
            InstanceIdentifier<KvPair> newKVPairIId = kvStorePairIID(key.toString());
            transaction.delete(LogicalDatastoreType.OPERATIONAL, newKVPairIId);
        }
        else return false; // TODO

        CheckedFuture<Void, TransactionCommitFailedException> future = transaction.submit();

        Futures.addCallback(future, new FutureCallback<Void>() {
            public void onSuccess(Void v) {
                logger.info("New KV entry successfully removed from registry.");
            }
            public void onFailure(Throwable thrown) {
                logger.warn("Removing the KV entry failed.");
            }
        });

        // wait to finish writing
        while(!future.isDone())
        {
        }

        return !future.isCancelled() && future.isDone();
    }

    /**
     * Updates an existing key-value pair from distributed MD-SAL data-store based on given key input.
     * @param key Unique key for the key-value pair that is to be written into the VirtuWind registry.
     * @param val Value to be written into the distributed data-store.
     * @return Boolean that indicates the success or failure of the data-store transaction.
     */
    public boolean updateKVPairInDataStore(Object key, Object val) {
        return removeKVPairFromDataStore(key) && writeKVPairToDataStore(key, val);
    }

    /**
     * Write accessor for a generic structure. Used in order to store an arbitrary Object in the data-store.
     * Objects requiring the storage require the appropriate extension of the method implementation.
     * @param structure Object to be written into the distributed data-store.
     * @return Boolean that indicates the succcess or failure of the data-store transaction.
     */
    public boolean genericWriteToStore(Object structure) {
        logger.info("Identifying the object instance...)");

        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();

        // Cover all types of objects that may be entered as
        // Keys and Values here, and store to appropriate registry
        // by specifying the correct instance identifier to right container
        if(structure instanceof EdgeCostPair)
        {
            InstanceIdentifier<EdgeCostPair> newEdgeCostPairPairIId = exemplaryPathManagerStoreNewPairIID(structure.toString());
            transaction.put(LogicalDatastoreType.OPERATIONAL, newEdgeCostPairPairIId, (EdgeCostPair) structure);
        }
        else return false;

        CheckedFuture<Void, TransactionCommitFailedException> future = transaction.submit();

        Futures.addCallback(future, new FutureCallback<Void>() {
            public void onSuccess(Void v) {
                logger.info("New entry successfully added to registry.");
            }
            public void onFailure(Throwable thrown) {
                logger.warn("Adding new entry failed.");
            }
        });

        // wait to finish writing
        while(!future.isDone())
        {
        }

        return !future.isCancelled() && future.isDone();
    }

    /**
     * Retrieves the InstanceIdentifier for a KvPair structure based on a given key identifier.
     * @param kvPairKey Unique key for the key-value pair.
     * @return InstanceIdentifier that holds the unique instance path to the key-value pair structure.
     */
    private InstanceIdentifier<KvPair> kvStorePairIID (String kvPairKey) {
        return InstanceIdentifier.create(GenericKvStore.class)
                .child(KvPair.class, new KvPairKey(kvPairKey));
    }

    /**
     * Retrieves the InstanceIdentifier for a EdgeCostPair structure based on a given key identifier.
     * @param edgeID Unique key for the EdgeCostPair structure.
     * @return InstanceIdentifier that holds the unique instance path to the EdgeCostPair structure.
     */
    private InstanceIdentifier<EdgeCostPair> exemplaryPathManagerStoreNewPairIID (String edgeID) {
        return InstanceIdentifier.create(ExamplaryPathManagerStore.class)
                .child(EdgeCostPair.class, new EdgeCostPairKey(edgeID));
    }

    /**
     * RPC implementation for storing a generic key-value input to the MD-SAL data-store.
     * @param input The input object.
     * @return The Future object indicating the success or failure of transaction.
     */
    @Override
    public Future<RpcResult<TestGenericKvInputOutput>> testGenericKvInput(TestGenericKvInputInput input) {
        String key = input.getKey();
        String value = input.getValue();

        // Test the embedding of a KV Pair
        boolean writeSuccess = writeKVPairToDataStore(key, value);
        String status = writeSuccess ? "Success!" : "Failure!";

        TestGenericKvInputOutputBuilder output = new TestGenericKvInputOutputBuilder().setResponse(status);
        return RpcResultBuilder.success(output).buildFuture();
    }

    /**
     * RPC implementation for removing a generic key-value input from the MD-SAL data-store.
     * @param input The input object.
     * @return The Future object indicating the success or failure of transaction.
     */
    @Override
    public Future<RpcResult<TestGenericKvRemovalOutput>> testGenericKvRemoval(TestGenericKvRemovalInput input) {
        String key = input.getKey();

        boolean removeSuccess = removeKVPairFromDataStore(key);
        String status = removeSuccess ? "Success!" : "Failure!";

        TestGenericKvRemovalOutputBuilder output = new TestGenericKvRemovalOutputBuilder().setResponse(status);
        return RpcResultBuilder.success(output).buildFuture();
    }

    /**
     * RPC implementation for modification of a generic key-value input in the MD-SAL data-store.
     * @param input The input object.
     * @return The Future object indicating the success or failure of transaction.
     */
    @Override
    public Future<RpcResult<TestGenericKvModifyOutput>> testGenericKvModify(TestGenericKvModifyInput input) {
        String key = input.getKey();
        String value = input.getValue();

        boolean updateSuccess = updateKVPairInDataStore(key, value);
        String status = updateSuccess ? "Success!" : "Failure!";

        TestGenericKvModifyOutputBuilder output = new TestGenericKvModifyOutputBuilder().setResponse(status);
        return RpcResultBuilder.success(output).buildFuture();    }
}
