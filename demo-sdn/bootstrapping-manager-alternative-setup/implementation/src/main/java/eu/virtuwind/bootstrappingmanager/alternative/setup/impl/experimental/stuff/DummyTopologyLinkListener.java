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

public class DummyTopologyLinkListener implements ClusteredDataTreeChangeListener<Link> {

    private static final Logger LOG = LoggerFactory.getLogger(DummyTopologyLinkListener.class);
    private DataBroker dataBroker = null;

    public DummyTopologyLinkListener(DataBroker dataBroker) {
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


                /*
                if (modtype.equals(DataObjectModification.ModificationType.SUBTREE_MODIFIED)) {
                    LOG.info("DUMMY-TOP-LINK: we have a sub-tree modified");
                }


                if (modtype.equals(DataObjectModification.ModificationType.DELETE)) {
                    LOG.info("DUMMY-TOP-LINK: we have a delete");
                }
                */
                    //String linkIdBefore = ""; //No before on write
                    String linkIdAfter = "";
                    /*try {
                        linkIdBefore = change.getRootNode().getDataBefore().getKey().getLinkId().getValue();
                    } catch (NullPointerException e){
                        LOG.info("linkIdBefore NullPointerException");
                    }*/
                    try {
                         linkIdAfter = change.getRootNode().getDataAfter().getKey().getLinkId().getValue();
                    } catch (NullPointerException e) {
                        LOG.info("DUMMY-TOP-LINK: linkIdAfter NullPointerException");
                    }
                    //String sourceNodeBefore = change.getRootNode().getDataBefore().getSource().getSourceNode().getValue();
                    String sourceNodeAfter = change.getRootNode().getDataAfter().getSource().getSourceNode().getValue();

                    //String destNodeBefore = change.getRootNode().getDataBefore().getDestination().getDestNode().getValue();
                    String destNodeAfter = change.getRootNode().getDataAfter().getDestination().getDestNode().getValue();

                    //String sourceTPBefore = change.getRootNode().getDataBefore().getSource().getSourceTp().getValue();
                    String sourceTPAfter = change.getRootNode().getDataAfter().getSource().getSourceTp().getValue();

                    //String destTPBefore = change.getRootNode().getDataBefore().getDestination().getDestTp().getValue();
                    String destTPAfter = change.getRootNode().getDataAfter().getDestination().getDestTp().getValue();

                    //LOG.info("DUMMY-TOP-LINK: Link id before: " + linkIdBefore);
                    LOG.info("DUMMY-TOP-LINK: Link id after: " + linkIdAfter);

                    //LOG.info("DUMMY-TOP-LINK: Source Node before: " + sourceNodeBefore);
                    LOG.info("DUMMY-TOP-LINK: Source Node after: " + sourceNodeAfter);
                    //LOG.info("DUMMY-TOP-LINK: Dest Node before: " + destNodeBefore);
                    LOG.info("DUMMY-TOP-LINK: Dest Node after: " + destNodeAfter);

                    //LOG.info("DUMMY-TOP-LINK: Source TP before: " + sourceTPBefore);
                    LOG.info("DUMMY-TOP-LINK: Source TP after: " + sourceTPAfter);
                    //LOG.info("DUMMY-TOP-LINK: Dest TP before: " + destTPBefore);
                    LOG.info("DUMMY-TOP-LINK: Dest TP after: " + destTPAfter);


                }
            }

        }
    }
}
