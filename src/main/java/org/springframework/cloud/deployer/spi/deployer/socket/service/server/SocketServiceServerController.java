package org.springframework.cloud.deployer.spi.deployer.socket.service.server;

import org.springframework.cloud.deployer.spi.deployer.socket.service.zmq.ZMQLifecycleServerManager;
import org.springframework.stereotype.Service;

/**
 * Created by A626200 on 20/10/2016.
 */
@Service
public class SocketServiceServerController extends SocketServiceController<ZMQLifecycleServerManager> {
    @Override
    public void kill() {
        this.getSocket().send("Error on Server");
        super.kill();
    }
}
