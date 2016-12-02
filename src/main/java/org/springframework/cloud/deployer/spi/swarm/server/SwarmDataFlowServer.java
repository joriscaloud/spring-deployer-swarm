package org.springframework.cloud.deployer.spi.swarm.server;

import org.springframework.boot.SpringApplication;
import org.springframework.cloud.dataflow.server.EnableDataFlowServer;

/**
 * Created by joriscaloud on 25/11/16.
 */
@EnableDataFlowServer
public class SwarmDataFlowServer {

    public static void main(String[] args) {
        SpringApplication.run(SwarmDataFlowServer.class, args);
    }
}
