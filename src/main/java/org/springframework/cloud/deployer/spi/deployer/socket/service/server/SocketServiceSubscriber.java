package org.springframework.cloud.deployer.spi.deployer.socket.service.server;

import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.spi.deployer.socket.service.zmq.ZMQContextManager;
import org.zeromq.ZMQ;

/**
 * Created by joriscaloud on 10/11/16.
 */
public class SocketServiceSubscriber extends Subscriber {

    
    @Autowired
    private SocketServiceServerController controller;
    
    @Autowired
    private SocketServiceSubscriber socket;

    @Autowired
    ZMQContextManager zmqCM;


    public void run() {
        controller.init();
        ZMQ.Socket subscriber = zmqCM.getContext().socket(ZMQ.SUB);
        subscriber.bind("tcp://*:5563");

        while (!Thread.currentThread().isInterrupted()) {
            // Wait for next request from the client
            byte[] request = subscriber.recv(0);
            System.out.println("Received Scaling Request");

            // Do some checking on if the same type of request has already been processed recently
            processRequest(request);
        }
        subscriber.close();
        zmqCM.stop();
    }

    public void processRequest(byte[] request) {
        String request_string = Base64.encodeBase64String(request);
    }
}
