package eu.virtuwind.pathmanager.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PathManagerProvider implements BindingAwareProvider, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(PathManagerProvider.class);
    //Members related to MD-SAL operations
    protected DataBroker dataBroker;
    protected SalFlowService salFlowService;


    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("PathManager session initiated");
    }

    @Override
    public void close() throws Exception {
        LOG.info("PathManager closed");
    }
}
