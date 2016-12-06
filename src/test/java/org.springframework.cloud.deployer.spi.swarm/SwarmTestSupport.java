package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.stream.test.junit.AbstractExternalResourceTestSupport;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by joriscaloud on 13/10/16.
 */
public class SwarmTestSupport extends AbstractExternalResourceTestSupport<DefaultDockerClient> {


    private ConfigurableApplicationContext context;

    protected SwarmTestSupport() {
        super("SWARM");
    }

    @Override
    protected void cleanupResource() throws Exception {
        context.close();
    }

    @Override
    protected void obtainResource() throws Exception {
        context = new SpringApplicationBuilder(Config.class).web(false).run();
        resource = context.getBean(DefaultDockerClient.class);
    }

    @Configuration
    @EnableConfigurationProperties(SwarmDeployerProperties.class)
    public static class Config {

        @Autowired
        private SwarmDeployerProperties properties;

        @Bean
        public DockerClient defaultDockerClient() {
            return new DefaultDockerClient(properties.getURI());
        }

        @Bean
        public AppDeployer swarmAppDeployer(SwarmDeployerProperties properties, DefaultDockerClient defaultDockeClient) {
            return new SwarmAppDeployer();
        }
    }
}
