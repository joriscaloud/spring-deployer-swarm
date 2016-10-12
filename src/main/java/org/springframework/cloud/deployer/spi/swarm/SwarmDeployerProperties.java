package org.springframework.cloud.deployer.spi.swarm;

/**
 * Created by joriscaloud on 12/10/16.
 */
@ConfigurationProperties(prefix = "spring.cloud.deployer.swarm")
public class SwarmDeployerProperties {
    private static String SWARM_NAMESPACE = System.getenv("SWARM_NAMESPACE")
            != null ? System.getenv("SWARM_NAMESPACE") : "default";


}
