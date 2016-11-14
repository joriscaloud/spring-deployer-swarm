package org.springframework.cloud.deployer.spi.deployer.socket.service.server;

import org.springframework.cloud.deployer.spi.zmq.sockets.domain.Owner;

import javax.annotation.PostConstruct;

/**
 * Created by joriscaloud on 10/11/16.
 */
public abstract class SocketServiceServerEntity implements Owner {

    protected String address;

    public abstract String getAddress();

    @PostConstruct
    public abstract void init();
}