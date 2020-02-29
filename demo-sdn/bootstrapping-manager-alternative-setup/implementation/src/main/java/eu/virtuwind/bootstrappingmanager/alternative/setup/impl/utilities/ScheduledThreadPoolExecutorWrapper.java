package eu.virtuwind.bootstrappingmanager.alternative.setup.impl.utilities;

/**
 * @author Mirza Avdic
 * @project bootstrapping-demo
 * @date 09.05.18
 */

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ScheduledThreadPoolExecutorWrapper extends ScheduledThreadPoolExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledThreadPoolExecutorWrapper.class);

    public ScheduledThreadPoolExecutorWrapper(int corePoolSize) {
        super(corePoolSize);
    }

    @Override
    public ScheduledFuture scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return super.scheduleAtFixedRate(wrapRunnable(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return super.scheduleWithFixedDelay(wrapRunnable(command), initialDelay, delay, unit);
    }

    private Runnable wrapRunnable(Runnable command) {
        return new LogOnExceptionRunnable(command);
    }

    private class LogOnExceptionRunnable implements Runnable {
        private Runnable theRunnable;

        public LogOnExceptionRunnable(Runnable theRunnable) {
            super();
            this.theRunnable = theRunnable;
        }

        @Override
        public void run() {
            try {
                theRunnable.run();
            } catch (Exception e) {

                LOG.info("SCHEDULER: Error in executing {}", theRunnable.toString());
                for (String m: ExceptionUtils.getRootCauseStackTrace(e)) {
                    LOG.info("SCHEDULER: Stack root cause trace -> {}", m);
                }
                // and re throw it so that the Executor also gets this error so that it can do what it would
                // usually do
                throw new RuntimeException(e);
            }
        }
    }
}