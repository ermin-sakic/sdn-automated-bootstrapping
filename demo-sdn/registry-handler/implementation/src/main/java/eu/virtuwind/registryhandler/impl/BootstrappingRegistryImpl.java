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
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.BootstrappingDatastore;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.BootstrappingDatastoreBuilder;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.BootstrappingStatus;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.bootstrapping.rev161017.BootstrappingStatusBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * The class containing all methods related to storing the BoostrappingManager
 * configuration in the distributed MD-SAL data-store.
 */
public class BootstrappingRegistryImpl {
    private static final Logger logger = LoggerFactory.getLogger(BootstrappingRegistryImpl.class);

    private static BootstrappingRegistryImpl bootstrappingRegistry;
    private DataBroker dataBroker;

    /**
     * Sets the BootstrappingManager data-store data broker and automatically initializes the data-tree.
     */
    void setDb(DataBroker db) {
        this.dataBroker = db;
        initializeBootstrappingRegistryDataTree();
    }

    /**
     * Initializes the BoostrappingManager data-store by creating the bootstrapping registry data-tree.
     */
    private void initializeBootstrappingRegistryDataTree() {
        logger.info("Preparing to initialize the Bootstrapping Registry Datatree");

        WriteTransaction bootstrappingConfigListDataStoreTransaction = dataBroker.newWriteOnlyTransaction();
        WriteTransaction bootstrappingStatusDataStoreTransaction = dataBroker.newWriteOnlyTransaction();

        InstanceIdentifier<BootstrappingDatastore> bootStrappingDatastoreIId =
                InstanceIdentifier.create(BootstrappingDatastore.class);
        BootstrappingDatastore initializeBootstrappingStore = new BootstrappingDatastoreBuilder().build();

        InstanceIdentifier<BootstrappingStatus> bootStrappingStatusIId =
                InstanceIdentifier.create(BootstrappingStatus.class);
        BootstrappingStatus initializeBootstrappingStatus = new BootstrappingStatusBuilder().build();

        bootstrappingConfigListDataStoreTransaction
                .put(LogicalDatastoreType.OPERATIONAL, bootStrappingDatastoreIId, initializeBootstrappingStore);
        CheckedFuture<Void, TransactionCommitFailedException> futureBootstrappingDatastore =
                bootstrappingConfigListDataStoreTransaction.submit();

        bootstrappingStatusDataStoreTransaction
                .put(LogicalDatastoreType.OPERATIONAL, bootStrappingStatusIId, initializeBootstrappingStatus);
        CheckedFuture<Void, TransactionCommitFailedException> futureBootstrappingStatus =
                bootstrappingStatusDataStoreTransaction.submit();

        Futures.addCallback(futureBootstrappingDatastore, new FutureCallback<Void>() {
                    public void onSuccess(Void v) {
                        logger.info("Bootstrapping Data-Tree Initialized");
                    }
                    public void onFailure(Throwable thrown) {
                        logger.info("Bootstrapping Datatree Initialization Failed.");
                    }
                }
        );

        Futures.addCallback(futureBootstrappingStatus, new FutureCallback<Void>() {
                    public void onSuccess(Void v) {
                        logger.info("Bootstrapping Status Initialized");
                    }
                    public void onFailure(Throwable thrown) {
                        logger.info("Bootstrapping Status Initialization Failed.");
                    }
                }
        );
    }

    /**
     * Default constructor for BootstrappingManager registry implementation.
     */
    private BootstrappingRegistryImpl(){}

    /**
     * Return an instance of the BootstrappingRegistryImpl.
     * @return BootstrappingRegistryImpl singleton instance.
     */
    public static BootstrappingRegistryImpl getInstance()
    {
        if(bootstrappingRegistry == null)
            bootstrappingRegistry = new BootstrappingRegistryImpl();

        return bootstrappingRegistry;
    }

    /**
     * Writes a new BootstrappingManager configuration into the distributed data-store.
     * @param datastoreBuilder The structure containing the bootstrapping-related configuration objects.
     * @return Boolean flag indicating the failure or success of the data-store transaction.
     */
    public boolean writeBootstrappingConfigurationToDataStore(BootstrappingDatastoreBuilder datastoreBuilder) {
        BootstrappingDatastore newBootstrappingConfiguration = datastoreBuilder.build();
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        InstanceIdentifier<BootstrappingDatastore> bootstrappingDatastoreConfig =
                InstanceIdentifier.create(BootstrappingDatastore.class);
        transaction.put(LogicalDatastoreType.OPERATIONAL, bootstrappingDatastoreConfig, newBootstrappingConfiguration);

        CheckedFuture<Void, TransactionCommitFailedException> future = transaction.submit();

        Futures.addCallback(future, new FutureCallback<Void>() {
            public void onSuccess(Void v) {
                logger.info("New bootstrapping configuration entry successfully ADDED to registry.");
            }

            public void onFailure(Throwable thrown) {
                logger.warn("Adding new bootstrapping configuration entry FAILED.");
            }
        });

        // wait to finish writing
        while (!future.isDone()) {}

        return !future.isCancelled() && future.isDone();
    }

    /**
     * Writes a new BootstrappingManager status into the distributed data-store.
     * @param statusBuilder The structure containing the bootstrapping-status configuration objects.
     * @return Boolean flag indicating the failure or success of the data-store transaction.
     */
    public boolean writeBootstrappingStatusToDataStore(BootstrappingStatusBuilder statusBuilder) {
        BootstrappingStatus newBootstrappingConfiguration = statusBuilder.build();
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        InstanceIdentifier<BootstrappingStatus> bootstrappingStatusConfig =
                InstanceIdentifier.create(BootstrappingStatus.class);
        transaction.put(LogicalDatastoreType.OPERATIONAL, bootstrappingStatusConfig, newBootstrappingConfiguration);

        CheckedFuture<Void, TransactionCommitFailedException> future = transaction.submit();

        Futures.addCallback(future, new FutureCallback<Void>() {
            public void onSuccess(Void v) {
                logger.info("New bootstrapping status successfully ADDED to registry.");
            }

            public void onFailure(Throwable thrown) {
                logger.warn("Adding new bootstrapping status entry FAILED.");
            }
        });

        // wait to finish writing
        while (!future.isDone()) {}

        return !future.isCancelled() && future.isDone();
    }

    /**
     * Retrieves the current bootstrapping-related configuration from the distributed data-store.
     * @return BootstrappingDatastore object containing the configuration objects.
     */
    @Nullable
    public BootstrappingDatastore readBootstrappingConfigurationFromDataStore() {
        BootstrappingDatastore bootstrappingDatastore = null;
        ReadOnlyTransaction transaction = dataBroker.newReadOnlyTransaction();
        InstanceIdentifier<BootstrappingDatastore> bootstrappingDatastoreConfig =
                InstanceIdentifier.create(BootstrappingDatastore.class);

        CheckedFuture<Optional<BootstrappingDatastore>, ReadFailedException> future =
                transaction.read(LogicalDatastoreType.OPERATIONAL, bootstrappingDatastoreConfig);

        if (future.isDone() & future.isCancelled()) {
            logger.info("Reading  the BootstrappingDatastore from Registry FAILED.");
            return null;
        }
        Optional<BootstrappingDatastore> optional;
        try {
            optional = future.checkedGet();
        } catch (ReadFailedException e) {
            logger.info("Reading the BootstrappingDatastore from Registry FAILED.");
            return null;
        }
        if(optional.isPresent()){
            bootstrappingDatastore =  optional.get();
        }else {
            logger.info("BootstrappingDatastore NOT FOUND in registry.");
            return null;
        }

        logger.info("BootstrappingDatastore retrieved SUCCESSFULLY from the registry.");
        return bootstrappingDatastore;
    }

    /**
     * Writes the modified bootstrapping-related configuration into the distributed data-store.
     * @param datastoreBuilder The structure containing the bootstrapping configuration objects.
     * @return Boolean flag indicating the failure or success of the data-store transaction.
     */
    public boolean writeModifiedBootstrappingConfigurationToDataStore(BootstrappingDatastoreBuilder datastoreBuilder) {
        BootstrappingDatastore newBootstrappingConfiguration = datastoreBuilder.build();
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        InstanceIdentifier<BootstrappingDatastore> bootstrappingDatastoreConfig =
                InstanceIdentifier.create(BootstrappingDatastore.class);
        transaction.merge(LogicalDatastoreType.OPERATIONAL, bootstrappingDatastoreConfig, newBootstrappingConfiguration);

        CheckedFuture<Void, TransactionCommitFailedException> future = transaction.submit();

        Futures.addCallback(future, new FutureCallback<Void>() {
            public void onSuccess(Void v) {
                logger.info("Modified bootstrapping configuration successfully applied in the registry.");
            }
            public void onFailure(Throwable thrown) {
                logger.warn("Modifying the existing bootstrapping configuration entry FAILED.");
            }
        });

        while (!future.isDone()) {}

        return !future.isCancelled() && future.isDone();
    }

    /**
     * Removes the existing bootstrapping-related configuration from the distributed data-store.
     * @return Boolean flag indicating the failure or success of the data-store transaction.
     */
    public boolean removeBootstrappingConfiguration() {
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        InstanceIdentifier<BootstrappingDatastore> bootstrappingDatastoreKey =
                InstanceIdentifier.create(BootstrappingDatastore.class);
        transaction.delete(LogicalDatastoreType.OPERATIONAL, bootstrappingDatastoreKey);

        CheckedFuture<Void, TransactionCommitFailedException> future = transaction.submit();

        Futures.addCallback(future, new FutureCallback<Void>() {
            public void onSuccess(Void v) {
                logger.info("Bootstrapping configuration successfully REMOVED from registry.");
            }

            public void onFailure(Throwable thrown) {
                logger.warn("Removing bootstrapping configuration FAILED.");
            }
        });

        // wait to finish writing
        while (!future.isDone()) {}

        return !future.isCancelled() && future.isDone();
    }
}
