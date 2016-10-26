package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.DockerClient;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.test.AbstractAppDeployerIntegrationTests;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.cloud.deployer.spi.test.EventuallyMatcher.eventually;

/**
 * Created by joriscaloud on 13/10/16.
 */
@ContextConfiguration(classes = SwarmAutoConfiguration.class)
public class SwarmAppDeployerTest extends AbstractAppDeployerIntegrationTests {

    @Autowired
    private SwarmDeployerProperties properties;

    @Autowired
    private SwarmAppDeployer swarmAppDeployer;

    @Autowired
    private DockerClient defaultDockerClient;

    @Test
    public void deployment() throws Exception {
        log.info("Testing {}...", "a simple deployment with the swarm");
        AppDefinition definition = new AppDefinition(randomName(), null);
        Resource resource = integrationTestProcessor();
        AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);
        log.info("Deploying {}...", request.getDefinition().getName());
        String deploymentId =  swarmAppDeployer.deploy(request);
        AppStatus appStatus = swarmAppDeployer.getAppStatus();
        String containerId = swarmAppDeployer.getResponse().id();
        Timeout timeout = deploymentTimeout();

        assertThat(appStatus.getState(), eventually(is(Matchers.<DeploymentState>is(DeploymentState.deployed)), timeout.maxAttempts, timeout.pause));

        log.info("Undeploying {}...", deploymentId);
        timeout = undeploymentTimeout();
        swarmAppDeployer.undeploy(deploymentId);
        assertThat(appStatus.getState(), eventually(is(
                Matchers.<DeploymentState>is(DeploymentState.undeployed)), timeout.maxAttempts, timeout.pause));
    }

    @Override
    protected AppDeployer appDeployer() {
        return swarmAppDeployer;
    }

    @Autowired
    protected Resource integrationTestProcessor() {
        return new DockerResource("springcloud/spring-cloud-deployer-spi-test-app:latest");
    }

}
