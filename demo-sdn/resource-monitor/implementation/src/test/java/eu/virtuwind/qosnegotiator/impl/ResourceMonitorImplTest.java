package eu.virtuwind.qosnegotiator.impl;

import eu.virtuwind.resourcemonitor.impl.ResourceMonitor;
import org.junit.*;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;

public class ResourceMonitorImplTest {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceMonitorImplTest.class);


    @Test
    public void testGetAllNodesIsEmpty() {
        assertEquals ( new ArrayList<Object>(), ResourceMonitor.getAllNodes(null));
    }

    @Test
    public void testGetAllLinksIsEmpty() {
        assertEquals ( new ArrayList<Object>(), ResourceMonitor.getAllLinks(null));
    }

    @Test
    public void testGetAllNodesWithNewDataBroker() {
        assertEquals ( new ArrayList<Object>(), ResourceMonitor.getAllNodes(new DataBroker() {
            @Override
            public ReadOnlyTransaction newReadOnlyTransaction() {
                return null;
            }

            @Override
            public ReadWriteTransaction newReadWriteTransaction() {
                return null;
            }

            @Override
            public WriteTransaction newWriteOnlyTransaction() {
                return null;
            }

            @Override
            public ListenerRegistration<DataChangeListener> registerDataChangeListener(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<?> instanceIdentifier, DataChangeListener dataChangeListener, DataChangeScope dataChangeScope) {
                return null;
            }

            @Override
            public BindingTransactionChain createTransactionChain(TransactionChainListener transactionChainListener) {
                return null;
            }

            @Nonnull
            @Override
            public <T extends DataObject, L extends DataTreeChangeListener<T>> ListenerRegistration<L> registerDataTreeChangeListener(@Nonnull DataTreeIdentifier<T> dataTreeIdentifier, @Nonnull L l) {
                return null;
            }
        }));
    }

    @Test
    public void testGetAllLinksWithNewDataBroker() {
        assertEquals ( new ArrayList<Object>(), ResourceMonitor.getAllLinks(new DataBroker() {
            @Override
            public ReadOnlyTransaction newReadOnlyTransaction() {
                return null;
            }

            @Override
            public ReadWriteTransaction newReadWriteTransaction() {
                return null;
            }

            @Override
            public WriteTransaction newWriteOnlyTransaction() {
                return null;
            }

            @Override
            public ListenerRegistration<DataChangeListener> registerDataChangeListener(LogicalDatastoreType logicalDatastoreType, InstanceIdentifier<?> instanceIdentifier, DataChangeListener dataChangeListener, DataChangeScope dataChangeScope) {
                return null;
            }

            @Override
            public BindingTransactionChain createTransactionChain(TransactionChainListener transactionChainListener) {
                return null;
            }

            @Nonnull
            @Override
            public <T extends DataObject, L extends DataTreeChangeListener<T>> ListenerRegistration<L> registerDataTreeChangeListener(@Nonnull DataTreeIdentifier<T> dataTreeIdentifier, @Nonnull L l) {
                return null;
            }
        }));
    }

}
