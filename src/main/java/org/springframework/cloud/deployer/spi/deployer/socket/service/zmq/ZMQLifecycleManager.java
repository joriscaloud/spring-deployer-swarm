package org.springframework.cloud.deployer.spi.deployer.socket.service.zmq;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

/**
 * Created by A626200 on 13/10/2016.
 */

public abstract class ZMQLifecycleManager<T extends Controller> implements ZMQShutdownListener {

    protected Log logger = LogFactory.getLog(this.getClass());

    private final Object socketThreadMonitor = new Object();

    private ZMQContextManager contextManager;

    private Integer socketType;

    protected abstract void checkOnInit();

    @Autowired
    protected T controller;

    @PostConstruct
    protected void init() {
        doInit();
        assert controller != null;
        getContextManager().addShutdownListener(this);
        synchronized (socketThreadMonitor) {
            try {
                this.getContextManager().start();
                checkOnInit();
            } finally {
                socketThreadMonitor.notify();
            }
        }
    }

    public abstract void doStop();

    public  void onZmqContextTerm() {
        controller.kill();
        doStop();
    }

    public ZMQContextManager getContextManager() {
        if (contextManager == null) {
            contextManager = new ZMQContextManager(1);
        }
        return contextManager;
    }

    protected Integer getSocketType() {
        return socketType;
    }

    protected void setSocketType(Integer socketType) {
        this.socketType = socketType;
    }

    protected abstract void doInit();

}
