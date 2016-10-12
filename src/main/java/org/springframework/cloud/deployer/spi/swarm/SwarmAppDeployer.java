package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.DefaultDockerClient;
import org.springframework.cloud.deployer.spi.app.AppDeployer;

/**
 * Created by joriscaloud on 12/10/16.
 */
public class SwarmAppDeployer extends AbstractSwarmDeployer implements AppDeployer{

    private static final String SERVER_PORT_KEY = "server.port";

    private SwarmDeployerProperties properties = new SwarmDeployerProperties();

    private final DefaultDockerClient client;

    private final ContainerFactory containerFactory;

    @Autowired
    public SwarmAppDeployer(SwarmDeployerProperties swarmDeployerProperties, DefaultDockerClient client) {

    }


}

