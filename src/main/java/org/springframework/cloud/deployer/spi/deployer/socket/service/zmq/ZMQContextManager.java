package org.springframework.cloud.deployer.spi.deployer.socket.service.zmq;

import org.springframework.context.Lifecycle;
import org.zeromq.ZContext;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Titouan CHARY [a626200]
 */
public class ZMQContextManager extends ZContext implements Lifecycle {

    private volatile boolean running = false;

    private Set<ZMQShutdownListener> shutdownListeners = new HashSet<ZMQShutdownListener>();

    public ZMQContextManager(int ioThreads) {
        super(ioThreads);
    }

    public void start() {
        running = true;
    }

    public void stop() {
        running = false;
        shutdownListeners.forEach(ZMQShutdownListener::onZmqContextTerm);
        this.getContext().term();
    }

    public boolean isRunning() {
        return running;
    }

    public void addShutdownListener(ZMQShutdownListener listener) {
        shutdownListeners.add(listener);
    }
}
