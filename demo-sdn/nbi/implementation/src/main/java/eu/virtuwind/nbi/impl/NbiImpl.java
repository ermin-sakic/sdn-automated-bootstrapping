package eu.virtuwind.nbi.impl;

import org.opendaylight.yang.gen.v1.urn.eu.virtuwind.nbi.model.rev160704.*;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

public class NbiImpl implements NbiService {
	private static final Logger LOG = LoggerFactory
			.getLogger(NbiImpl.class);

	public NbiImpl() {
	}

	@Override
	public Future<RpcResult<CsAddCommServOutput>> csAddCommServ(
			CsAddCommServInput input) {
		LOG.info("Received request {}", input);

		//TODO: Call the path finding stuff here.

		LOG.info("Sending response {}");

		return RpcResultBuilder.success(new CsAddCommServOutputBuilder().setReason("TODO").build()).buildFuture();
	}

	@Override
	public Future<RpcResult<CsDelCommServOutput>> csDelCommServ(
			CsDelCommServInput input) {
		LOG.info("Received request {}", input);

		//TODO: Call the path finding stuff here.

		LOG.info("Sending response {}");

		return RpcResultBuilder.success(new CsDelCommServOutputBuilder().setReason("TODO").build()).buildFuture();
	}

	@Override
	public Future<RpcResult<CsGetCommServOutput>> csGetCommServ(
			CsGetCommServInput input) {
		LOG.info("Received request {}", input);

		//TODO: Call the path finding stuff here.

		LOG.info("Sending response {}");

		return RpcResultBuilder.success(new CsGetCommServOutputBuilder().build()).buildFuture();
	}
}
