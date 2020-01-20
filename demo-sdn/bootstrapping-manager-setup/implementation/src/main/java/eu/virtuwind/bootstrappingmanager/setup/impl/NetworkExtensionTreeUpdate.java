package eu.virtuwind.bootstrappingmanager.setup.impl;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class handles tree and normal topology updates for NetworkExtensionManager when a new switch is added
 * to the network after the bootstrapping has been finished.
 *
 * TODO: EXHAUSTIVE TESTING!!!
 *
 */
public class NetworkExtensionTreeUpdate implements ClusteredDataTreeChangeListener<Link> {

    private static final Logger LOG = LoggerFactory.getLogger(NetworkExtensionTreeUpdate.class);

    // changed by NetworkExtensionManager
    public  static  String nodeConnectorToCheck = new String();

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Link>> changes) {

        synchronized (this) {
            for (DataTreeModification<Link> change : changes) {

                DataObjectModification<Link> mod = change.getRootNode();

                if (mod.getModificationType() == DataObjectModification.ModificationType.WRITE) {
                    
                    if (!nodeConnectorToCheck.isEmpty()) {
                        Link newLink = mod.getDataAfter();
                        
                        if (newLink.getSource().getSourceTp().getValue().equals(nodeConnectorToCheck)) {

                            LOG.info("Link that contains {} node NodeConnector has been added to the topology: {}", newLink.getLinkId().getValue());

                            // update the networkGraph and currentTreeGraph
                            List<Link> linksForAddition = new ArrayList<>();
                            linksForAddition.add(newLink);
                            NetworkExtensionManager.addLinksIntoNetworkGraph(linksForAddition);
                            NetworkExtensionManager.addLinksIntoCurrentTreeGraph(linksForAddition);
                            NetworkExtensionManager.writeCurrentTreeInDS();
                            NetworkExtensionManager.computeAlternativeTrees();
                            NetworkExtensionManager.writeAlternativeTreesInDS();

                        } else if (newLink.getDestination().getDestNode().getValue().equals(nodeConnectorToCheck)) {

                            LOG.info("Link that contains {} node NodeConnector has been added to the topology: {}", newLink.getLinkId().getValue());

                            // update the networkGraph and currentTreeGraph
                            List<Link> linksForAddition = new ArrayList<>();
                            linksForAddition.add(newLink);
                            NetworkExtensionManager.addLinksIntoNetworkGraph(linksForAddition);
                            NetworkExtensionManager.addLinksIntoCurrentTreeGraph(linksForAddition);
                            NetworkExtensionManager.writeCurrentTreeInDS();
                            NetworkExtensionManager.computeAlternativeTrees();
                            NetworkExtensionManager.writeAlternativeTreesInDS();

                        }
                    }
                
                }
            } 
        } 
    }
}

