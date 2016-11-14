package org.springframework.cloud.deployer.spi.deployer.socket.service.zmq;

import org.springframework.cloud.deployer.spi.deployer.socket.service.server.SocketServiceServerController;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.zeromq.ZMQ;

/**
 * Created by A626200 on 20/10/2016.
 */
@Service
public class ZMQLifecycleServerManager extends ZMQLifecycleManager<SocketServiceServerController> {
    @Override
    protected void checkOnInit() {
        Assert.notNull(this.getSocketType(), "Server Socket Type is empty");
    }

    @Override
    public void doStop() {
        logger.info("ServerManager stopped");
    }

    @Override
    protected void doInit() {
        this.setSocketType(ZMQ.SUB);
    }
}
