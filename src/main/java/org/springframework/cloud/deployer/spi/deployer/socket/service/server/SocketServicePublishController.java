package org.springframework.cloud.deployer.spi.deployer.socket.service.server;

import org.springframework.cloud.deployer.spi.deployer.socket.service.zmq.ZMQLifecyclePubManager;
import org.springframework.stereotype.Service;

/**
 * Created by joriscaloud on 14/11/16.
 */
@Service
public class SocketServicePublishController extends MonoSocketServiceController<ZMQLifecyclePubManager> {
    @Override
    public void kill() {
        this.getSocket().send("Error on Server");
        super.kill();
        }
}
