package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.swarm.TaskStatus;
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
        Timeout timeout = deploymentTimeout();

        long t= System.currentTimeMillis();
        long end = t+15000;
        while(System.currentTimeMillis() < end) {
            if (swarmAppDeployer.getCreatedTask().status().state().equals(TaskStatus.TASK_STATE_RUNNING)) {
                AppStatus appStatus = swarmAppDeployer.status(deploymentId, swarmAppDeployer.getCreatedTask());
                assertThat(appStatus.getState(), eventually(is(Matchers.<DeploymentState>is(DeploymentState.deployed)), timeout.maxAttempts, timeout.pause));
            }
            else log.info("was not deployed or assert did not work");

        }

        log.info("Undeploying {}...", deploymentId);
        timeout = undeploymentTimeout();
        swarmAppDeployer.undeployService(deploymentId);
        while(System.currentTimeMillis() < end) {
            if (swarmAppDeployer.getCreatedTask().status().state().equals(TaskStatus.TASK_STATE_SHUTDOWN)) {
                AppStatus appStatus = swarmAppDeployer.status(deploymentId, swarmAppDeployer.getCreatedTask());
                assertThat(appStatus.getState(), eventually(is(Matchers.<DeploymentState>is(DeploymentState.undeployed)), timeout.maxAttempts, timeout.pause));
            }
            else log.info("was not undeployed or assert did not work");

        }
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
