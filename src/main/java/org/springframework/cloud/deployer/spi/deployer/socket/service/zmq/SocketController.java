package org.springframework.cloud.deployer.spi.deployer.socket.service.zmq;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.spi.zmq.sockets.connection.SocketBuilder;
import org.springframework.cloud.deployer.spi.zmq.sockets.domain.Owner;
import org.zeromq.ZMQ;

import javax.annotation.PostConstruct;

/**
 * Created by A626200 on 19/10/2016.
 */
public abstract class SocketController<T extends ZMQLifecycleManager> implements Controller {

    protected Owner owner;

    @Autowired
    protected T manager;

    @PostConstruct
    public abstract void init();

    protected ZMQ.Socket getNewSocket(Owner owner) {
        return SocketBuilder.build(
                SocketFactory.get(manager),
                getOwner().getAddress());
    }

    protected Owner getOwner() {
        return owner;
    }

    public abstract void setOwner(Owner owner);

}
