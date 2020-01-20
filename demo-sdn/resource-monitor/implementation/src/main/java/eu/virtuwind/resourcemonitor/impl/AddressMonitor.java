/*
 * Copyright © 2015 Intracom Telecom and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package eu.virtuwind.resourcemonitor.impl;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.address.tracker.rev140617.AddressCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.address.tracker.rev140617.address.node.connector.Addresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class AddressMonitor {

	private static final Logger LOG = LoggerFactory
			.getLogger(AddressMonitor.class);

	public DataBroker db;

	public AddressMonitor(DataBroker db) {
		this.db = db;
	}

	/**
	 * Returns the node from a given ip address
	 */
	public Node getNodeFromIpAddress(String ipAddress) {
		LOG.info("Finding node with IP address {}. ", ipAddress);
		try {
			List<Node> nodeList = new ArrayList<>();
			InstanceIdentifier<Nodes> nodesIid = InstanceIdentifier.builder(
					Nodes.class).build();
			ReadOnlyTransaction nodesTransaction = db.newReadOnlyTransaction();
			CheckedFuture<Optional<Nodes>, ReadFailedException> nodesFuture = nodesTransaction
					.read(LogicalDatastoreType.OPERATIONAL, nodesIid);
			Optional<Nodes> nodesOptional = Optional.absent();
			nodesOptional = nodesFuture.checkedGet();

			if (nodesOptional != null && nodesOptional.isPresent()) {
				nodeList = nodesOptional.get().getNode();
			}

			if (nodeList != null) {
				for (Node n : nodeList) {
					List<NodeConnector> nodeConnectors = n.getNodeConnector();
					for (NodeConnector nc : nodeConnectors) {
						AddressCapableNodeConnector acnc = nc
								.getAugmentation(AddressCapableNodeConnector.class);
						if (acnc != null && acnc.getAddresses() != null) {
							// get address list from augmentation.
							List<Addresses> addresses = acnc.getAddresses();
							for (Addresses address : addresses) {

								/*LOG.info(
										"Checking address {} for connector {}",
										address.getIp().getIpv4Address()
												.getValue(), nc.getId()
												.getValue()); */
								if (address.getIp().getIpv4Address().getValue()
										.equals(ipAddress))
									return n;
							}
						}
					}
				}
			}
		} catch (Exception e) {
			LOG.info("IP address reading failed");
		}
		return null;
	}

}
