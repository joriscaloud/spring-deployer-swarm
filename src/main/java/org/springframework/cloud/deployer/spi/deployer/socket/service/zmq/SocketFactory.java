package org.springframework.cloud.deployer.spi.deployer.socket.service.zmq;

import org.springframework.util.Assert;
import org.zeromq.ZMQ;

/**
 * Created by A626200 on 15/10/2016.
 */
public class SocketFactory {

    public static ZMQ.Socket get(ZMQLifecycleManager manager) {
        Assert.notNull(manager.getSocketType(), "Socket type is null");
        Assert.notNull(manager.getContextManager(), "Context manager is null");
        return manager.getContextManager().createSocket(manager.getSocketType());
    }
}
