package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.deployer.resource.docker.DockerResourceLoader;
import org.springframework.cloud.deployer.resource.maven.MavenResourceLoader;
import org.springframework.cloud.deployer.resource.support.DelegatingResourceLoader;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.ResourceLoader;

import java.util.HashMap;
import java.util.Map;

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
    public DelegatingResourceLoader delegatingResourceLoader() {
        DockerResourceLoader dockerLoader = new DockerResourceLoader();
        MavenResourceLoader mavenResourceLoader = new MavenResourceLoader(properties.getMavenProperties());
        Map<String, ResourceLoader> loaders = new HashMap<>();
        loaders.put("docker", dockerLoader);
        loaders.put("maven", mavenResourceLoader);
        return new DelegatingResourceLoader(loaders);
    }


    @Bean
    public DockerClient defaultDockerClient() {
        return new DefaultDockerClient(properties.getDockerURI());
    }

    @Bean
    @ConditionalOnMissingBean(AppDeployer.class)
    public AppDeployer appDeployer() {
        return new SwarmAppDeployer();
    }

    @Bean
    @ConditionalOnMissingBean(TaskLauncher.class)
    public TaskLauncher taskLauncher() {
        return new SwarmTaskLauncher();
    }

}
