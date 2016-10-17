package org.springframework.cloud.deployer.spi.swarm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Created by joriscaloud on 12/10/16.
 */
@ConfigurationProperties(prefix = "spring.cloud.deployer.swarm")
public class SwarmDeployerProperties {

    /**
     * namespace to use
     */
    private static String SWARM_NAMESPACE = System.getenv("SWARM_NAMESPACE")
            != null ? System.getenv("SWARM_NAMESPACE") : "default";

    private final String URI  = "unix:///var/run/docker.sock";

    private final Integer timeToWaitFor = 10;


    public String getURI(){
        return this.URI;
    }


}
