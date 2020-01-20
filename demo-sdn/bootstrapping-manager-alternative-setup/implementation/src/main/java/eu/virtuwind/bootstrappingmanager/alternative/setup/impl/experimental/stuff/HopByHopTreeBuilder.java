package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.experimental.stuff;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collection;

public class HopByHopTreeBuilder implements ClusteredDataTreeChangeListener<Link> {

    private static final Logger LOG = LoggerFactory.getLogger(HopByHopTreeBuilder.class);
    private DataBroker dataBroker = null;

    public HopByHopTreeBuilder(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Link>> changes) {

        synchronized (this) {
            for (DataTreeModification<Link> change : changes) {

                //DataObjectModification.ModificationType modtype = change.getRootNode().getModificationType();
                DataObjectModification<Link> mod = change.getRootNode();


                if (mod.getModificationType() == DataObjectModification.ModificationType.WRITE) {
                    LOG.info("DUMMY-TOP-LINK: we have a write"); // only once because the topology is created at the beginning only


                }

            }
        }
    }
}
