package org.springframework.cloud.deployer.spi.swarm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * Created by joriscaloud on 12/10/16.
 * Properties for the containers
 */
@ConfigurationProperties(prefix = "spring.cloud.deployer.swarm")
public class SwarmDeployerProperties {
    
    public URI getURI(){
        return (URI.create("http://127.0.0.1:2375"));
            }
    
    /**
     * Memory to allocate for a service
     */
    private Long memory = 512L;

    /**
     * CPU to allocate for a service
     */
    private Long cpu = 500L;

    public void setCpu(Long cpu) {
        this.cpu = cpu;
    }

    public Long getCpu() {
        return cpu;
    }

    public Long getMemory() {
        return memory;
    }

    public void setMemory(Long memory) {
        this.memory = memory;
    }

}
