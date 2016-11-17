package org.springframework.cloud.deployer.spi.deployer.socket.service.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.spi.zmq.sockets.domain.Owner;
import org.springframework.stereotype.Service;
import org.zeromq.ZMQ;

/**
 * Created by joriscaloud on 10/11/16.
 */
@Service
public class SocketServiceSubscriber extends Subscriber {

    @Autowired
    private SocketServiceSubscribeController controller;

    @Override
    public void run() {
        controller.setOwner(new Owner() {
            @Override
            public String getAddress() {
                return "tcp://localhost:5563";
            }
        });
        ZMQ.Socket subscriber = controller.getSocket();
        while (!Thread.currentThread().isInterrupted()) {
            // Wait for next request from the client
            String request = subscriber.recvStr();
            System.out.println("Received Scaling Request");

            // Do some checking on if the same type of request has already been processed recently
            processRequest(request);
        }
        subscriber.close();
    }

    public void processRequest(String request) {    }
}
