package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Created by joriscaloud on 13/10/16.
 */
@Configuration
@EnableConfigurationProperties(SwarmDeployerProperties.class)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
public class SwarmAutoConfiguration {

    @Autowired
    private SwarmDeployerProperties properties;
    @Bean
    public DockerClient defaultDockerClient() {
        return new DefaultDockerClient(properties.getURI());
    }

    @Bean
    public AppDeployer swarmAppDeployer(SwarmDeployerProperties properties, DefaultDockerClient defaultDockeClient) {
        return new SwarmAppDeployer(properties, defaultDockeClient);
    }

}
