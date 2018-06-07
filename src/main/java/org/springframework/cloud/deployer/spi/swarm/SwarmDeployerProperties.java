package org.springframework.cloud.deployer.spi.swarm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;

import java.net.URI;

/**
 * Created by joriscaloud on 12/10/16.
 * Properties for the containers
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "spring.cloud.deployer.swarm")
public class SwarmDeployerProperties {

    private MavenProperties mavenProperties = new MavenProperties();

    private URI dockerURI = URI.create("unix:///var/run/docker.sock");

    /**
     * Memory to allocate for a service
     */
    private Long memory = 0L;

    /**
     * CPU to allocate for a service
     */
    private Long cpu = 0L;
}