package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
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
    public AppDeployer appDeployer(DefaultDockerClient defaultDockeClient) {
        return new SwarmAppDeployer(properties, defaultDockeClient);
    }

    @Bean
    public TaskLauncher taskDeployer(DockerClient DockerClient) {
        return new SwarmTaskLauncher(properties, DockerClient);
    }

    @Bean
    public DockerClient dockerClient() {
        return new DefaultDockerClient(properties.getURI());
    }

}
