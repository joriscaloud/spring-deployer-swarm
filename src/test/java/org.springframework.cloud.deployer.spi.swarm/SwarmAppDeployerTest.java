package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.DefaultDockerClient;
import org.hamcrest.Matchers;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.test.AbstractAppDeployerIntegrationTests;
import org.springframework.core.io.Resource;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.deployed;
import static org.springframework.cloud.deployer.spi.app.DeploymentState.unknown;
import static org.springframework.cloud.deployer.spi.test.EventuallyMatcher.eventually;

/**
 * Created by joriscaloud on 13/10/16.
 */
@SpringApplicationConfiguration(classes = {SwarmAutoConfiguration.class})
public class SwarmAppDeployerTest extends AbstractAppDeployerIntegrationTests {

    @ClassRule
    public static SwarmTestSupport swarmAvailable = new SwarmTestSupport();

    @Autowired
    private AppDeployer appDeployer;

    @Autowired
    DefaultDockerClient sut;

    @Override
    protected AppDeployer appDeployer() {
        return appDeployer;
    }

    @Test
    public void deployment() throws Exception{
        log.info("Testing {}...", "a simple deployment with the swarm");
        SwarmDeployerProperties swarmProperties = new SwarmDeployerProperties();
        SwarmAppDeployer swarmAppDeployer = new SwarmAppDeployer(swarmProperties, sut);
        AppDefinition definition = new AppDefinition(randomName(), null);
        Resource resource = integrationTestProcessor();
        AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);

        log.info("Deploying {}...", request.getDefinition().getName());
        String deploymentId = swarmAppDeployer.deploy(request);
        Timeout timeout = deploymentTimeout();
        assertThat(deploymentId, eventually(hasStatusThat(
                Matchers.<AppStatus>hasProperty("state", is(deployed))), timeout.maxAttempts, timeout.pause));

        log.info("Undeploying {}...", deploymentId);
        timeout = undeploymentTimeout();
        swarmAppDeployer.undeploy(deploymentId);
        assertThat(deploymentId, eventually(hasStatusThat(
                Matchers.<AppStatus>hasProperty("state", is(unknown))), timeout.maxAttempts, timeout.pause));
    }

    protected Resource integrationTestProcessor() {
        return new DockerResource("springcloud/spring-cloud-deployer-spi-test-app:latest");
    }

}
