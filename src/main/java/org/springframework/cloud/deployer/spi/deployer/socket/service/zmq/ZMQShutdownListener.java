package org.springframework.cloud.deployer.spi.deployer.socket.service.zmq;

/**
 * @author Titouan CHARY [a626200]
 */
public interface ZMQShutdownListener {
    void onZmqContextTerm();
}
