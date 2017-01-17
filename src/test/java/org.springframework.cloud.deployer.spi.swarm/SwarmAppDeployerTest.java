package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ServiceCreateResponse;
import com.spotify.docker.client.messages.swarm.Service;
import com.spotify.docker.client.messages.swarm.Swarm;
import com.spotify.docker.client.messages.swarm.Task;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.spotify.docker.client.VersionCompare.compareVersion;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
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
    private DockerClient defaultDockerClient;

    private SwarmAppDeployer swarmAppDeployer;

    private String dockerApiVersion;


    @Autowired
    public void setAppDeployer(AppDeployer appDeployer) {
        this.swarmAppDeployer = (SwarmAppDeployer) appDeployer;
    }

    @Before
    public void setup() throws Exception {
        dockerApiVersion = defaultDockerClient.version().apiVersion();
        swarmAppDeployer.testing = true;
        swarmAppDeployer.withNetwork = false;
    }

    @After
    public void tearDown() throws InterruptedException, DockerException {
        List<Service> services = defaultDockerClient.listServices();

        services.forEach(service -> {
            try {
                defaultDockerClient.removeService(service.id());
            } catch (InterruptedException | DockerException e) {
                e.printStackTrace();
            }

        });
        if (swarmAppDeployer.withNetwork) {
            defaultDockerClient.removeNetwork("swarm-app-deployer-network-test");
        }
        launchTimeout();
    }

    @Test
    public void testDeployUndeploySingleTaskService() throws Exception {
        log.info("Testing {}...", "a simple deployment with the swarm");
        AppDefinition definition = new AppDefinition(randomName(), null);
        Resource resource = firstDockerResource();
        AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);
        log.info("Deploying {}...", request.getDefinition().getName());
        String deploymentId =  swarmAppDeployer.deploy(request);
        Timeout timeout = deploymentTimeout();
        AppStatus appStatus = swarmAppDeployer.status(deploymentId);
        launchTimeout();
        assertThat(appStatus.getState(), eventually(Matchers.<DeploymentState>anyOf(is(DeploymentState.deployed), is(DeploymentState.deploying)), timeout.maxAttempts, timeout.pause));

        log.info("Undeploying {}...", deploymentId);
        launchTimeout();
        swarmAppDeployer.updateReplicasNumber(deploymentId, 0);
        assertTrue(defaultDockerClient.listServices().isEmpty());
    }

    @Test
    public void testDeployUndeployTwoDifferentImages() throws Exception {
        log.info("Testing the  deployment of two different images in the swarm");
        AppDefinition firstDefinition = new AppDefinition(randomName(), null);
        Resource firstResource = firstDockerResource();
        AppDeploymentRequest firstRequest = new AppDeploymentRequest(firstDefinition, firstResource);
        log.info("Deploying {}...", firstRequest.getDefinition().getName());
        String firstDeploymentId =  swarmAppDeployer.deploy(firstRequest);
        Timeout timeout = deploymentTimeout();
        Task firstTask = (Task) swarmAppDeployer.testInformations.get("Task");
        AppStatus firstAppStatus = swarmAppDeployer.status(firstTask.id(), firstTask);
        launchTimeout();
        assertThat(firstAppStatus.getState(), eventually(Matchers.<DeploymentState>anyOf(is(DeploymentState.deployed), is(DeploymentState.deploying)), timeout.maxAttempts, timeout.pause));

        AppDefinition secondDefinition = new AppDefinition(randomName(), null);
        Resource secondResource = secondDockerResource();
        AppDeploymentRequest secondRequest = new AppDeploymentRequest(secondDefinition, secondResource);
        log.info("Deploying {}...", secondRequest.getDefinition().getName());
        String secondDeploymentId =  swarmAppDeployer.deploy(secondRequest);
        launchTimeout();
        Task secondTask = (Task) swarmAppDeployer.testInformations.get("Task");
        AppStatus secondAppStatus = swarmAppDeployer.status(secondTask.id(), secondTask);
        launchTimeout();
        assertThat(secondAppStatus.getState(), eventually(Matchers.<DeploymentState>anyOf(is(DeploymentState.deployed), is(DeploymentState.deploying)), timeout.maxAttempts, timeout.pause));

        log.info("Undeploying {}...", firstDeploymentId, secondDeploymentId);
        launchTimeout();
        swarmAppDeployer.updateReplicasNumber(firstDeploymentId, 0);
        swarmAppDeployer.updateReplicasNumber(secondDeploymentId, 0);
        assertTrue(defaultDockerClient.listServices().isEmpty());
    }

    @Test
    public void testDeployUndeployMultipleTasksService() throws Exception {
        log.info("Testing {}...", "a simple deployment with the swarm");
        AppDefinition definition = new AppDefinition(randomName(), null);
        Resource resource = firstDockerResource();

        int count = 10;
        Map<String, String> properties = new HashMap<>();
        properties.put("spring.cloud.deployer.count", "10");
        properties.put("spring.cloud.deployer.indexed", "true");
        AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, properties);

        log.info("Deploying {}...", request.getDefinition().getName());
        String deploymentId =  swarmAppDeployer.deploy(request);
        Timeout timeout = deploymentTimeout();
        launchTimeout();
        for (int index = 0; index<count; index++) {
            Task task = (Task)swarmAppDeployer.testInformations.get("Task " + index);
            AppStatus appStatus = swarmAppDeployer.status(deploymentId, task);
            assertThat(appStatus.getState(), eventually
                    (Matchers.<DeploymentState>anyOf
                                    (is(DeploymentState.deployed), is(DeploymentState.deploying)),
                            timeout.maxAttempts, timeout.pause));
        }

        log.info("Undeploying {}...", deploymentId);
        launchTimeout();
        swarmAppDeployer.updateReplicasNumber(deploymentId, 0);
        assertTrue(defaultDockerClient.listServices().isEmpty());
    }

    @Test
    public void testAddOneReplicaToService() throws Exception {
        log.info("Testing {}...", "adding a replicas to a swarm service");
        AppDefinition definition = new AppDefinition(randomName(), null);
        Resource resource = firstDockerResource();
        AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);
        log.info("Deploying {}...", request.getDefinition().getName());
        String deploymentId =  swarmAppDeployer.deploy(request);
        Timeout timeout = deploymentTimeout();
        launchTimeout();
        assertThat(defaultDockerClient
                .inspectService(((ServiceCreateResponse)swarmAppDeployer.testInformations.get("Last Response")).id())
                .spec()
                .mode()
                .replicated()
                .replicas(), is(1L));
        log.info("first container :  {}", defaultDockerClient.inspectTask(((Task)swarmAppDeployer.testInformations.get("Task")).id()));
        log.info("Service logging :  {}", defaultDockerClient.inspectService(deploymentId));

        log.info("Adding a replica to  {}...", deploymentId);
        swarmAppDeployer.updateReplicasNumber(deploymentId, 2);
        launchTimeout();
        assertThat(defaultDockerClient
                .inspectService(((ServiceCreateResponse)swarmAppDeployer.testInformations.get("Last Response")).id())
                .spec()
                .mode()
                .replicated()
                .replicas(), is(2L));
        log.info("Two containers :  {}", defaultDockerClient.inspectService(((ServiceCreateResponse)swarmAppDeployer.testInformations.get("Last Response")).id()));
        log.info("Service logging :  {}", defaultDockerClient.inspectService(deploymentId));

        launchTimeout();
        Task task = (Task)swarmAppDeployer.testInformations.get("Task");
        AppStatus appStatus = swarmAppDeployer.status(deploymentId, task);
        launchTimeout();
        assertThat(appStatus.getState(), eventually
                (Matchers.<DeploymentState>
                                anyOf(is(DeploymentState.deployed), is(DeploymentState.deploying)),
                        timeout.maxAttempts, timeout.pause));

    }


    @Test
    public void testFailedLaunchService() throws Exception {
        log.info("Testing {}...", "FailedLaunch");
        Map<String, String> properties = new HashMap<>();
        properties.put("killDelay", "10");
        properties.put("exitCode", "1");
        AppDefinition definition = new AppDefinition(this.randomName(), properties);
        Resource resource = firstDockerResource();
        AppDeploymentRequest request = new AppDeploymentRequest(definition, resource);
        log.info("Launching {}...", request.getDefinition().getName());
        String deploymentId = swarmAppDeployer.deploy(request);
        log.info("Launched {} ", deploymentId);
        Timeout timeout = deploymentTimeout();
        launchTimeout();
        AppStatus appStatus = swarmAppDeployer.status(deploymentId);
        launchTimeout();
        assertThat(appStatus.getState(), eventually
                (Matchers.<DeploymentState>
                                anyOf(is(DeploymentState.deployed), is(DeploymentState.deploying)),
                        timeout.maxAttempts, timeout.pause));
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
        Resource resource = firstDockerResource();
        AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, Collections.emptyMap(),
                Collections.singletonList("--exitCode=0"));
        log.info("Launching {}...", request.getDefinition().getName());
        String deploymentId = swarmAppDeployer.deploy(request);
        log.info("Launched {} ", deploymentId);

        Timeout timeout = launchTimeout();
        launchTimeout();
        Task task = (Task)swarmAppDeployer.testInformations.get("Task");
        AppStatus appStatus = swarmAppDeployer.status(deploymentId);
        launchTimeout();
        assertThat(appStatus.getState(),
                eventually(Matchers.<DeploymentState>
                                anyOf(is(DeploymentState.deployed), is(DeploymentState.deploying)),
                        timeout.maxAttempts, timeout.pause));

    }


    @Test
    public void testCreateServiceWithNetwork() throws Exception {
        requireDockerApiVersionAtLeast("1.24", "swarm support");

        swarmAppDeployer.withNetwork = true;
        log.info("Testing {}...", "a simple deployment with the swarm");
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(AppDeployer.GROUP_PROPERTY_KEY, "swarm-app-deployer-network-test");
        AppDefinition definition = new AppDefinition(randomName(), null);
        Resource resource = firstDockerResource();
        AppDeploymentRequest request = new AppDeploymentRequest(definition, resource, properties);
        log.info("Deploying {}...", request.getDefinition().getName());
        String deploymentId =  swarmAppDeployer.deploy(request);
        launchTimeout();
        assertThat(defaultDockerClient.inspectService(deploymentId).spec().networks().size(), is(1));
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

    protected static class Timeout {

        public final int maxAttempts;

        public final int pause;

        public Timeout(int maxAttempts, int pause) {
            this.maxAttempts = maxAttempts;
            this.pause = pause;
        }
    }

    protected Resource firstDockerResource() {
        return new DockerResource("springcloud/spring-cloud-deployer-spi-test-app:latest");
    }

    protected Resource secondDockerResource() {
        return new DockerResource("hellow-world");
    }

    protected Timeout launchTimeout() {
        return new Timeout(20, 8000);
    }


    protected String randomName() {
        return UUID.randomUUID().toString().substring(0,8);
    }

    /**
     * Return the timeout to use for repeatedly querying app status while it is being deployed.
     * Default value is one minute, being queried every 5 seconds.
     */
    protected Timeout deploymentTimeout() {
        return new Timeout(12, 5000);
    }


}
