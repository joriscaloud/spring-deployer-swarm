package org.springframework.cloud.deployer.spi.deployer.socket.service.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by joriscaloud on 09/11/16.
 */
@Service
public class SocketServiceServer extends SocketServiceServerEntity {

    @Autowired
    private SocketServiceServerController controller;
 
    @Autowired
    private SocketServiceSubscriber subscriber;
    
    public void init() {
        controller.setOwner(this);
        subscriber.run();
    }

    @Override
    public String getAddress() {
        if(address == null)
            address = "tcp://*:1234";
        return address;
    }
    
}