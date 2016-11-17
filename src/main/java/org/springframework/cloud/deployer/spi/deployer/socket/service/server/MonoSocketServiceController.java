package org.springframework.cloud.deployer.spi.deployer.socket.service.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.deployer.spi.deployer.socket.service.zmq.SocketController;
import org.springframework.cloud.deployer.spi.deployer.socket.service.zmq.SocketFactory;
import org.springframework.cloud.deployer.spi.zmq.sockets.connection.SocketBuilder;
import org.springframework.cloud.deployer.spi.zmq.sockets.domain.Owner;
import org.zeromq.ZMQ;

/**
 * Created by joriscaloud on 09/11/16.
 */
public class MonoSocketServiceController<T> extends SocketController {
    private final Log logger = LogFactory.getLog(getClass());

    private ZMQ.Socket socket;

    protected Owner owner;


    public ZMQ.Socket getSocket() {
        if(socket == null)
            socket = getNewSocket(owner);
        return socket;
    }

    @Override
    public void init() {
        return;
    }

    @Override
    protected ZMQ.Socket getNewSocket(Owner owner) {
        ZMQ.Socket socket = SocketBuilder.build(
                SocketFactory.get(manager),
                getOwner().getAddress());

        logger.info("ZMQ socket of type " + socket.getType() + " built");

        return socket;
    }

    @Override
    public void kill() {
        socket.close();
    }

    protected Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }
}
