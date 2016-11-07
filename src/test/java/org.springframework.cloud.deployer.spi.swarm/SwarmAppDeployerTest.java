package org.springframework.cloud.deployer.spi.swarm;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.Ipam;
import com.spotify.docker.client.messages.NetworkConfig;
import com.spotify.docker.client.messages.NetworkCreation;
import com.spotify.docker.client.messages.swarm.NetworkAttachmentConfig;
import com.spotify.docker.client.messages.swarm.Service;
import com.spotify.docker.client.messages.swarm.Swarm;
import com.spotify.docker.client.messages.swarm.TaskStatus;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.spotify.docker.client.VersionCompare.compareVersion;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;
import static org.springframework.cloud.deployer.spi.test.EventuallyMatcher.eventually;

/**
 * Created by joriscaloud on 13/10/16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SwarmAutoConfiguration.class)
public class SwarmAppDeployerTest {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SwarmDeployerProperties properties;

    @Autowired
    private SwarmAppDeployer swarmAppDeployer;

    @Autowired
    private DockerClient defaultDockerClient;

    private String dockerApiVersion;

    private String deploymentId;

    @Before
    public void setup() throws Exception {
        dockerApiVersion = defaultDockerClient.version().apiVersion();
}

    @Test
    public void testDeployUndeplloy() throws Exception {
        log.info("Testing {}...", "a simple deployment with the swarm");
        AppDefinition definition = new AppDefinition(randomName(), null);
        Resource resource = integrationTestProcessor();
        AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);
        log.info("Deploying {}...", request.getDefinition().getName());
        String deploymentId =  swarmAppDeployer.deploy(request);
        Timeout timeout = deploymentTimeout();

        launchTimeout();
        if (swarmAppDeployer.getCreatedTask().status().state().equals(TaskStatus.TASK_STATE_RUNNING)) {
            AppStatus appStatus = swarmAppDeployer.status(deploymentId, swarmAppDeployer.getCreatedTask());
            assertThat(appStatus.getState(), eventually(is(Matchers.<DeploymentState>is(DeploymentState.deployed)), timeout.maxAttempts, timeout.pause));
        }
        else log.info("was not deployed or assert did not work");


        log.info("Undeploying {}...", deploymentId);
        timeout = undeploymentTimeout();
        swarmAppDeployer.updateReplicasNumber(deploymentId, 0);
        if (swarmAppDeployer.getCreatedTask().status().state().equals(TaskStatus.TASK_STATE_SHUTDOWN)) {
            AppStatus appStatus = swarmAppDeployer.status(deploymentId, swarmAppDeployer.getCreatedTask());
            assertThat(appStatus.getState(), eventually(is(Matchers.<DeploymentState>is(DeploymentState.undeployed)), timeout.maxAttempts, timeout.pause));
        }
        else log.info("was not undeployed or assert did not work");
        defaultDockerClient.removeService(deploymentId);
        launchTimeout();
    }

    @Test
    public void testAddOneReplicaToService() throws Exception {
        log.info("Testing {}...", "adding a replicas to a swarm service");
        AppDefinition definition = new AppDefinition(randomName(), null);
        Resource resource = integrationTestProcessor();
        AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);
        log.info("Deploying {}...", request.getDefinition().getName());
        this.deploymentId =  swarmAppDeployer.deploy(request);
        Timeout timeout = deploymentTimeout();
        launchTimeout();
        assertThat(defaultDockerClient
                    .inspectService(swarmAppDeployer.getServiceCreateResponse().id())
                    .spec()
                    .mode()
                    .replicated()
                    .replicas(), is(1L));
        log.info("first container :  {}", defaultDockerClient.inspectTask(swarmAppDeployer.getCreatedTask().id()));
        log.info("Service logging :  {}", defaultDockerClient.inspectService(deploymentId));

        launchTimeout();
        log.info("Adding a replica to  {}...", deploymentId);
        swarmAppDeployer.updateReplicasNumber(deploymentId, 2);
        launchTimeout();
        assertThat(defaultDockerClient
                    .inspectService(swarmAppDeployer.getServiceCreateResponse().id())
                    .spec()
                    .mode()
                    .replicated()
                    .replicas(), is(2L));
        log.info("Two containers :  {}", defaultDockerClient.inspectTask(swarmAppDeployer.getCreatedTask().id()));
        log.info("Service logging :  {}", defaultDockerClient.inspectService(deploymentId));

        launchTimeout();
        if (swarmAppDeployer.getCreatedTask().status().state().equals(TaskStatus.TASK_STATE_RUNNING)) {
            AppStatus appStatus = swarmAppDeployer.status(deploymentId, swarmAppDeployer.getCreatedTask());
            assertThat(appStatus.getState(), eventually(is(Matchers.<DeploymentState>is(DeploymentState.deployed)), timeout.maxAttempts, timeout.pause));
        }
        swarmAppDeployer.updateReplicasNumber(deploymentId, 0);
        defaultDockerClient.removeService(deploymentId);
        launchTimeout();
    }


    @Test
    public void testFailedLaunchService() throws Exception {
        log.info("Testing {}...", "FailedLaunch");
        Map<String, String> properties = new HashMap<>();
        properties.put("killDelay", "10");
        properties.put("exitCode", "1");
        AppDefinition definition = new AppDefinition(this.randomName(), properties);
        Resource resource = integrationTestProcessor();
        AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);
        log.info("Launching {}...", request.getDefinition().getName());
        this.deploymentId = swarmAppDeployer.deploy(request);
        log.info("Launched {} ", deploymentId);
        Timeout timeout = deploymentTimeout();
        launchTimeout();

        if (swarmAppDeployer.getCreatedTask().status().state().equals(TaskStatus.TASK_STATE_RUNNING)) {
            AppStatus appStatus = swarmAppDeployer.status(deploymentId, swarmAppDeployer.getCreatedTask());
            assertThat(appStatus.getState(), eventually(is(Matchers.<DeploymentState>is(DeploymentState.failed)), timeout.maxAttempts, timeout.pause));
        }
        swarmAppDeployer.updateReplicasNumber(deploymentId, 0);
        defaultDockerClient.removeService(deploymentId);
        launchTimeout();
    }

    @Test
    public void testInspectSwarm() throws Exception {
        requireDockerApiVersionAtLeast("1.24", "swarm support");

        final Swarm swarm = defaultDockerClient.inspectSwarm();
        assertThat(swarm.createdAt(), is(notNullValue()));
        assertThat(swarm.updatedAt(), is(notNullValue()));
        assertThat(swarm.id(), is(not(isEmptyOrNullString())));
        assertThat(swarm.joinTokens().worker(), is(not(isEmptyOrNullString())));
        assertThat(swarm.joinTokens().manager(), is(not(isEmptyOrNullString())));
    }


    /**
     * Tests that command line args can be passed in.
     */
    @Test
    public void testCommandLineArgs() throws Exception{
        log.info("Testing {}...", "CommandLineArgs");
        Map<String, String> properties = new HashMap<>();
        properties.put("killDelay", "1000");
        AppDefinition definition = new AppDefinition(this.randomName(), properties);
        Resource resource = integrationTestProcessor();
        AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, Collections.emptyMap(),
                Collections.singletonList("--exitCode=0"));
        log.info("Launching {}...", request.getDefinition().getName());
        this.deploymentId = swarmAppDeployer.deploy(request);
        log.info("Launched {} ", deploymentId);

        Timeout timeout = launchTimeout();
        launchTimeout();
        if (swarmAppDeployer.getCreatedTask().status().state().equals(TaskStatus.TASK_STATE_RUNNING)) {
            AppStatus appStatus = swarmAppDeployer.status(deploymentId, swarmAppDeployer.getCreatedTask());
            assertThat(appStatus.getState(), eventually(is(Matchers.<DeploymentState>is(DeploymentState.deployed)), timeout.maxAttempts, timeout.pause));
        }
        swarmAppDeployer.updateReplicasNumber(deploymentId, 0);
        defaultDockerClient.removeService(deploymentId);
        launchTimeout();
    }


    @Test
    public void testCreateServiceWithNetwork() throws Exception {
        requireDockerApiVersionAtLeast("1.24", "swarm support");

        final String networkName = randomName();
        final String serviceName = randomName();

        final NetworkCreation networkCreation = defaultDockerClient
                .createNetwork(NetworkConfig.builder().driver("overlay")
                        // TODO: workaround for https://github.com/docker/docker/issues/25735
                        .ipam(Ipam.builder().driver("default").build())
                        //
                        .name(networkName).build());

        final String networkId = networkCreation.id();

        assertThat(networkId, is(notNullValue()));

        log.info("Testing {}...", "a simple deployment with the swarm");
        AppDefinition definition = new AppDefinition(randomName(), null);
        Resource resource = integrationTestProcessor();
        AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);
        log.info("Deploying {}...", request.getDefinition().getName());
        this.deploymentId =  swarmAppDeployer.deployWithNetwork(request, networkName);
        Timeout timeout = deploymentTimeout();

        final Service inspectService = defaultDockerClient.inspectService(this.deploymentId);
        assertThat(inspectService.spec().networks().size(), is(1));
        assertThat(Iterables.find(inspectService.spec().networks(),
                new Predicate<NetworkAttachmentConfig>() {

                    @Override
                    public boolean apply(NetworkAttachmentConfig config) {
                        return networkId.equals(config.target());
                    }
                }, null), is(notNullValue()));

        defaultDockerClient.removeService(deploymentId);
        launchTimeout();
    }



    private void requireDockerApiVersionAtLeast(final String required, final String functionality)
            throws Exception {

        final String msg = String.format(
                "Docker API should be at least v%s to support %s but runtime version is %s",
                required, functionality, dockerApiVersion);

        assumeTrue(msg, dockerApiVersionAtLeast(required));
    }

    private boolean dockerApiVersionAtLeast(String expected) throws Exception {
        return compareVersion(dockerApiVersion, expected) >= 0;
    }

    //Timeouts management -> used to prevent tests to fail because the apps didn't deploy quickly enough


    protected static class Timeout {

        public final int maxAttempts;

        public final int pause;

        public Timeout(int maxAttempts, int pause) {
            this.maxAttempts = maxAttempts;
            this.pause = pause;
        }
    }

    @Autowired
    protected Resource integrationTestProcessor() {
        return new DockerResource("springcloud/spring-cloud-deployer-spi-test-app:latest");
    }

    protected Timeout launchTimeout() {
        return new Timeout(20, 5000);
    }


    protected String randomName() {
        return UUID.randomUUID().toString();
    }

    /**
     * Return the timeout to use for repeatedly querying app status while it is being deployed.
     * Default value is one minute, being queried every 5 seconds.
     */
    protected Timeout deploymentTimeout() {
        return new Timeout(12, 5000);
    }

    /**
     * Return the timeout to use for repeatedly querying app status while it is being un-deployed.
     * Default value is one minute, being queried every 5 seconds.
     */
    protected Timeout undeploymentTimeout() {
        return new Timeout(20, 5000);
    }

    /**
     * Return the time to wait between reusing deployment requests. This could be necessary to give
     * some platforms time to clean up after undeployment.
     */
    protected int redeploymentPause() {
        return 0;
    }


}
