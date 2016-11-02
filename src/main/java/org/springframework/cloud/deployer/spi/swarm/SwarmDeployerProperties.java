package org.springframework.cloud.deployer.spi.swarm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * Created by joriscaloud on 12/10/16.
 */
@ConfigurationProperties(prefix = "spring.cloud.deployer.swarm")
public class SwarmDeployerProperties {


    public URI getURI(){
        return (URI.create("http://127.0.0.1:2375"));
            }

    private String[] environmentVariables = new String[]{};


    public String[] getEnvironmentVariables() {
        return environmentVariables;
    }

    public void setEnvironmentVariables(String[] environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

}
