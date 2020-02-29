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
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.pathmanager.rev161223.PathManagerInfo;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.pathmanager.rev161223.PathManagerInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.pathmanager.rev161223.path.manager.info.QueueEdgeInfo;
import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.registryhandler.pathmanager.rev161223.path.manager.info.QueueEdgeInfoKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The class containing all methods related to storing the PathManager
 * configurations in the distributed MD-SAL data-store.
 */
public class PathManagerRegistryImpl {
    private static final Logger logger = LoggerFactory.getLogger(PathManagerRegistryImpl.class);

    private static PathManagerRegistryImpl pathManagerRegistry;
    private DataBroker dataBroker;

    /**
     * Sets the PathManager data-store broker and automatically initializes the data tree.
     * @param db MD-SAL DataBroker structure
     */
    void setDb(DataBroker db) {
        this.dataBroker = db;
        initializePathManagerRegistryDataTree();
    }

    /**
     * Initializes the PathManager data-store by creating the registry data-tree.
     */
    private void initializePathManagerRegistryDataTree() {
        logger.info("Preparing to initialize the Path Manager Registry Datatree");

        WriteTransaction pathManagerConfigListDataStoreTransaction = dataBroker.newWriteOnlyTransaction();
        InstanceIdentifier<PathManagerInfo> pathManagerInfostoreIId = InstanceIdentifier.create(PathManagerInfo.class);
        PathManagerInfo initializePathmanagerStore = new PathManagerInfoBuilder().build();

        pathManagerConfigListDataStoreTransaction.put(LogicalDatastoreType.OPERATIONAL, pathManagerInfostoreIId, initializePathmanagerStore);
        CheckedFuture<Void, TransactionCommitFailedException> futurePathManagerInfostore = pathManagerConfigListDataStoreTransaction.submit();

        Futures.addCallback(futurePathManagerInfostore, new FutureCallback<Void>() {
                    public void onSuccess(Void v) {
                        logger.info("PathManager Datatree Initialized");
                    }
                    public void onFailure(Throwable thrown) {
                        logger.info("PathManager Datatree Initialization Failed.");
                    }
                }
        );
    }

    /**
     * Default constructor.
     */
    private PathManagerRegistryImpl(){}

    /**
     * Returns an instance of the PathManagerRegistryImpl.
     * @return PathManagerRegistryImpl singleton instance.
     */
    public static PathManagerRegistryImpl getInstance()
    {
        if(pathManagerRegistry == null)
            pathManagerRegistry = new PathManagerRegistryImpl();

        return pathManagerRegistry;
    }

    /**
     * Returns an InstanceIdentifier for a new edge entry with ID edgeID.
     * @param edgeID The edge ID.
     * @return The QueueEdgeInfo InstanceIdentifier.
     */
    private InstanceIdentifier<QueueEdgeInfo> getQueueEdgeInfoIId (String edgeID) {
        return InstanceIdentifier.create(PathManagerInfo.class)
                .child(QueueEdgeInfo.class, new QueueEdgeInfoKey(edgeID));
    }

    /**
     * Writes the queue-specific reservation information in the distributed MD-SAL data-store.
     * @param QueueEdgeInfo The structure holding reservation properties of a queue-link edge.
     * @return Boolean flag indicating the success or failure of the transaction.
     */
    public boolean writeQueueEdgeInfoConfiguration(QueueEdgeInfo QueueEdgeInfo) {
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();

        InstanceIdentifier<QueueEdgeInfo> queueEdgeInfoConfigurationIId = getQueueEdgeInfoIId(QueueEdgeInfo.getEdgeId());
        transaction.put(LogicalDatastoreType.OPERATIONAL, queueEdgeInfoConfigurationIId, QueueEdgeInfo);

        CheckedFuture<Void, TransactionCommitFailedException> future = transaction.submit();

        Futures.addCallback(future, new FutureCallback<Void>() {
            public void onSuccess(Void v) {
                logger.info("New queue edge configuration entry  for " + QueueEdgeInfo.getEdgeId() +
                        "successfully added to registry.");
            }

            public void onFailure(Throwable thrown) {
                logger.info("Adding new queue edge configuration entry  for " + QueueEdgeInfo.getEdgeId() +
                        " failed.");
            }
        });

        // wait to finish writing
        while (!future.isDone()) {
        }

        return !future.isCancelled() && future.isDone();
    }

    /**
     * Reads the queue-specific reservation information from the distributed MD-SAL data-store.
     * @param edgeId The queue-link key specified in physLinkId:qPrio format.
     * @return QueueEdgeInfo object holding the information about the edge with ID edgeId.
     */
    @Nullable
    public QueueEdgeInfo readQueueEdgeInfoConfiguration(String edgeId) {
        ReadOnlyTransaction transaction = dataBroker.newReadOnlyTransaction();
        QueueEdgeInfo QueueEdgeInfo = null;

        InstanceIdentifier<QueueEdgeInfo> QueueEdgeInfoConfigurationIId = getQueueEdgeInfoIId(edgeId);
        CheckedFuture<Optional<QueueEdgeInfo>, ReadFailedException> future =
                transaction.read(LogicalDatastoreType.OPERATIONAL, QueueEdgeInfoConfigurationIId);

        if (future.isDone() & future.isCancelled()) {
            logger.info("Reading  the edge-data for {} from Registry FAILED.", edgeId);
            return null;
        }

        Optional<QueueEdgeInfo> optional = Optional.absent();
        try {
            optional = future.checkedGet();
        } catch (ReadFailedException e) {
            logger.info("Reading  the edge-data for {} from Registry FAILED.", edgeId);
            return null;
        }

        if(optional.isPresent()){
            QueueEdgeInfo =  optional.get();
        }else {
            logger.info("Edge-data for {} NOT FOUND in registry.", edgeId);
            return null;
        }

        logger.info("Edge-data for {} retrieved SUCCESSFULLY from the registry.", edgeId);

        return QueueEdgeInfo;
    }

    /**
     * Retrieves a list of the queue-specific reservation objects for all edges
     * from the distributed MD-SAL data-store.
     * @return A List of QueueEdgeInfo objects stored in the database.
     */
    @Nullable
    public List<QueueEdgeInfo> readAllQueueEdgeInfoEntries() {
        ReadOnlyTransaction transaction = dataBroker.newReadOnlyTransaction();

        InstanceIdentifier<PathManagerInfo> PathManagerInfoIId = InstanceIdentifier.create(PathManagerInfo.class);
        PathManagerInfo PathManagerInfo = null;
        List<QueueEdgeInfo> QueueEdgeInfoEntryList = new ArrayList<QueueEdgeInfo>();

        CheckedFuture<Optional<PathManagerInfo>, ReadFailedException> future =
                transaction.read(LogicalDatastoreType.OPERATIONAL, PathManagerInfoIId);

        if (future.isDone() & future.isCancelled()) {
            logger.info("Reading  the PathManager Data from Registry FAILED.");
            return null;
        }

        Optional<PathManagerInfo> optional = Optional.absent();
        try {
            optional = future.checkedGet();
        } catch (ReadFailedException e) {
            logger.info("Reading  the PathManager Data from Registry FAILED.");
            return null;
        }

        if(optional.isPresent()){
            PathManagerInfo =  optional.get();
        }else {
            logger.info("PathManager Data NOT FOUND in registry.");
            return null;
        }

        logger.info("PathManager Data retrieved SUCCESSFULLY from the registry.");

        if(PathManagerInfo.getQueueEdgeInfo() != null)
            QueueEdgeInfoEntryList = PathManagerInfo.getQueueEdgeInfo();

        return QueueEdgeInfoEntryList;
    }

    /**
     * Removes a queue-specific reservation information from the distributed MD-SAL data-store.
     * @param edgeKey The queue-link key specified in physLinkId:qPrio format.
     * @return Boolean indicating the success or failure of data-store transaction.
     */
    public boolean removeQueueEdgeConfiguration(String edgeKey) {
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();

        InstanceIdentifier<QueueEdgeInfo> QueueEdgeInfoConfigurationIId = getQueueEdgeInfoIId(edgeKey);
        transaction.delete(LogicalDatastoreType.OPERATIONAL, QueueEdgeInfoConfigurationIId);

        CheckedFuture<Void, TransactionCommitFailedException> future = transaction.submit();

        Futures.addCallback(future, new FutureCallback<Void>() {
            public void onSuccess(Void v) {
                logger.info("Queue edge configuration entry  for " + edgeKey +
                        "successfully removed from registry.");
            }

            public void onFailure(Throwable thrown) {
                logger.info("Queue edge configuration entry  for " + edgeKey +
                        " could not be removed from registry.");
            }
        });

        // wait to finish writing
        while (!future.isDone()) {
        }

        return !future.isCancelled() && future.isDone();
    }
}
