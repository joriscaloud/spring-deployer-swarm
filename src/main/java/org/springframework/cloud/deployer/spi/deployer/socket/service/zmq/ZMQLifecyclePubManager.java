package org.springframework.cloud.deployer.spi.deployer.socket.service.zmq;

import org.springframework.cloud.deployer.spi.deployer.socket.service.server.SocketServicePublishController;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.zeromq.ZMQ;

/**
 * Created by joriscaloud on 14/11/16.
 */
@Service
public class ZMQLifecyclePubManager extends ZMQLifecycleManager<SocketServicePublishController> {
    @Override
    protected void checkOnInit() {
        Assert.notNull(this.getSocketType(), "Client Socket Type is empty");
    }

    @Override
    public void doStop() {
        logger.info("Client Manager stopped");
    }

    @Override
    protected void doInit() {
        this.setSocketType(ZMQ.PUB);
    }
}
