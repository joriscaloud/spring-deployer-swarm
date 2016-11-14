package org.springframework.cloud.deployer.spi.zmq.sockets.connection;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;
import org.zeromq.ZMQ;

import java.util.Objects;


/**
 * Created by A626200 on 15/10/2016.
 */
public class SocketBuilder {
    private static final Log logger = LogFactory.getLog("Socket Builder");

    public static ZMQ.Socket build(ZMQ.Socket socket, String address) {
        Assert.notNull(socket, "Socket is null");
        Assert.notNull(address, "Address is empty");
        if(Objects.equals(ZMQ.DEALER, socket.getType())
                || Objects.equals(ZMQ.REQ, socket.getType())
                || Objects.equals(ZMQ.SUB, socket.getType())) {
            logger.info("Socket connected to " + address);
            socket.connect(address);
        }
        else if(Objects.equals(ZMQ.ROUTER, socket.getType())
                || Objects.equals(ZMQ.REP, socket.getType())
                || Objects.equals(ZMQ.PUB, socket.getType())) {
            logger.info("Socket bound to " + address);
            socket.bind(address);
        }
        return socket;
    }
}
