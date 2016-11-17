package org.springframework.cloud.deployer.spi.deployer.socket.service;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.swarm.TaskStatus;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
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
import org.springframework.cloud.deployer.spi.deployer.socket.service.server.SocketServicePublishController;
import org.springframework.cloud.deployer.spi.deployer.socket.service.server.SocketServiceServer;
import org.springframework.cloud.deployer.spi.deployer.socket.service.server.SocketServiceSubscribeController;
import org.springframework.cloud.deployer.spi.deployer.socket.service.zmq.ConverterFactory;
import org.springframework.cloud.deployer.spi.swarm.SwarmAppDeployer;
import org.springframework.cloud.deployer.spi.swarm.SwarmAutoConfiguration;
import org.springframework.cloud.deployer.spi.swarm.SwarmDeployerProperties;
import org.springframework.cloud.deployer.spi.zmq.sockets.domain.Owner;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.cloud.deployer.spi.test.EventuallyMatcher.eventually;

/**
 * Created by joriscaloud on 10/11/16.
 */
@ContextConfiguration(classes = {RequestRouterConfiguration.class, SwarmAutoConfiguration.class})
@RunWith(SpringJUnit4ClassRunner.class)
public class RequestRouterTest extends Thread{

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SocketServiceSubscribeController controllerSub;

    @Autowired
    private SocketServicePublishController controllerPub;

    @Autowired
    private SocketServiceServer socketServiceServer;

    @Autowired
    private SwarmDeployerProperties properties;

    @Autowired
    private SwarmAppDeployer swarmAppDeployer;

    @Autowired
    private DockerClient defaultDockerClient;

    private String dockerApiVersion;
    private String deploymentId;


    @Before
    public void setUp() throws Exception {
        // Prepare controller and publish
        Owner owner = new Owner() {
            @Override
            public String getAddress() {
                return "tcp://localhost:5563";
            }
        };
        controllerPub.setOwner(owner);
        controllerSub.setOwner(owner);
        dockerApiVersion = defaultDockerClient.version().apiVersion();
    }

    @After
    public void tearDown() {
        controllerSub.getSocket().close();
        controllerPub.getSocket().close();

    }

    public void SimplePublish() throws Exception {
        LoadBalancerInfos toSend = new LoadBalancerInfos("springcloud/spring-cloud-deployer-spi-test-app:latest",
                        "450", "load", "313", "512", "2000", null);
        byte[] bytesToSend = ConverterFactory.getDefaultOutboundConverter().convert(toSend);
        // Write messages, each with an envelope and content
        controllerPub.getSocket().sendMore("Byte");
        controllerPub.getSocket().send(bytesToSend);
        Thread.sleep(100);
    }

    @Test
    public void SimpleSubscribeRequest() throws Exception {
        LoadBalancerInfos infoToCompare =
                new LoadBalancerInfos("springcloud/spring-cloud-deployer-spi-test-app:latest",
                "450", "load", "313", "512", "2000", null);

        LoadBalancerInfos infoReceived = null;
        Thread thread = null;
        SimpleSender sender = new SimpleSender();
        controllerPub.getSocket().setIdentity("Byte".getBytes());
        Thread.sleep(5000);
        controllerSub.getSocket().subscribe("Byte".getBytes());
        while (!Thread.currentThread().isInterrupted()) {
            if (thread == null) {
                thread = new Thread(sender);
                thread.start();
                thread.sleep(1000);
            }
            String enveloppe = controllerSub.getSocket().recvStr();
            byte[] bytes_received = controllerSub.getSocket().recv();
            infoReceived = (LoadBalancerInfos) ConverterFactory
                    .getDefaultInboundConverter()
                    .convert(bytes_received);
            log.info(infoReceived.toString() + " PAYLOAD LOG");
            break;
        }
        Assert.assertEquals(infoReceived.toString(),infoToCompare.toString());
    }

    public void publishToRouter() throws Exception {
        String appToDeploy = "springcloud/spring-cloud-deployer-spi-test-app:latest";
        Map<String, String> properties = null;
        LoadBalancerInfos toSend = new LoadBalancerInfos(appToDeploy, properties);
        byte[] bytesToSend = ConverterFactory.getDefaultOutboundConverter().convert(toSend);
        // Write messages, each with an envelope and content
        controllerPub.getSocket().sendMore("Deployment Test");
        controllerPub.getSocket().send(bytesToSend);
        Thread.sleep(100);
    }

    @Test
    public void zmqRequesltAndSwarmDeploy() throws Exception {
        log.info("Testing {}...", "a request deployment from zmq to the swarm");
        LoadBalancerInfos receivedAppName = null;
        log.info("Sending the name of the application to scale through zmq to the router");

        Thread thread = null;
        Sender sender = new Sender();
        controllerPub.getSocket().setIdentity("Deployment Test".getBytes());
        Thread.sleep(5000);
        controllerSub.getSocket().subscribe("Deployment Test".getBytes());

        while (!Thread.currentThread().isInterrupted()) {
            thread = new Thread(sender);
            thread.start();
            thread.sleep(1000);
            String enveloppe = controllerSub.getSocket().recvStr();
            log.info("Received from" + enveloppe);
            byte[] bytes_received = controllerSub.getSocket().recv();
            receivedAppName = (LoadBalancerInfos) ConverterFactory
                    .getDefaultInboundConverter()
                    .convert(bytes_received);
            log.info("App name : " + receivedAppName);
            break;
        }

        if (receivedAppName != null) {

        AppDefinition definition = new AppDefinition(receivedAppName.sink_name, receivedAppName.properties);
        Resource resource = createResourceFromAppName(receivedAppName.sink_name);
        AppDeploymentRequest request_router = new AppDeploymentRequest(definition, resource);

        this.deploymentId =  swarmAppDeployer.deploy(request_router);
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
        }

    }


    public static class LoadBalancerInfos implements Serializable {

        //Name of the app.
        private String sink_name;
        private String exec_speed;
        private String load;
        private String lifetime;
        private String min_ram;
        private String min_cpu;

        private Map<String, String> properties;

        public LoadBalancerInfos(String sink_name, String exec_speed, String load,
                                 String lifetime, String min_ram, String min_cpu, Map<String, String> properties) {
            this.sink_name = sink_name;
            this.exec_speed = exec_speed;
            this.load = load;
            this.lifetime = lifetime;
            this.min_ram = min_ram;
            this.min_cpu = min_cpu;
            this.properties = properties;
        }

        public LoadBalancerInfos(String sink_name, Map<String, String> properties) {
            this.sink_name = sink_name;
            this.properties = properties;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }

        public String getSink_name() {
            return sink_name;
        }

        public void setSink_name(String sink_name) {
            this.sink_name = sink_name;
        }

        public String getExec_speed() {
            return exec_speed;
        }

        public void setExec_speed(String exec_speed) {
            this.exec_speed = exec_speed;
        }

        public String getLoad() {
            return load;
        }

        public void setLoad(String load) {
            this.load = load;
        }

        public String getLifetime() {
            return lifetime;
        }

        public void setLifetime(String lifetime) {
            this.lifetime = lifetime;
        }

        public String getMin_ram() {
            return min_ram;
        }

        public void setMin_ram(String min_ram) {
            this.min_ram = min_ram;
        }

        public String getMin_cpu() {
            return min_cpu;
        }

        public void setMin_cpu(String min_cpu) {
            this.min_cpu = min_cpu;
        }

        @Override
        public String toString() {
            return "LoadBalancerInfos{" +
                    "sink_name='" + sink_name + '\'' +
                    ", exec_speed='" + exec_speed + '\'' +
                    ", load='" + load + '\'' +
                    ", lifetime='" + lifetime + '\'' +
                    ", min_ram='" + min_ram + '\'' +
                    ", min_cpu='" + min_cpu + '\'' +
                    '}';
        }
    }

    public class SimpleSender implements Runnable {

        public void run() {
            try {
                SimplePublish();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public class Sender implements Runnable {

        public void run() {
            try {
                publishToRouter();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


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

    protected Resource createResourceFromAppName(String appName) {
        return new DockerResource(appName);
    }

    protected RequestRouterTest.Timeout launchTimeout() {
        return new RequestRouterTest.Timeout(20, 10000);
    }


    protected String randomName() {
        return UUID.randomUUID().toString();
    }

    /**
     * Return the timeout to use for repeatedly querying app status while it is being deployed.
     * Default value is one minute, being queried every 5 seconds.
     */
    protected RequestRouterTest.Timeout deploymentTimeout() {
        return new RequestRouterTest.Timeout(12, 10000);
    }

    /**
     * Return the timeout to use for repeatedly querying app status while it is being un-deployed.
     * Default value is one minute, being queried every 5 seconds.
     */
    protected RequestRouterTest.Timeout undeploymentTimeout() {
        return new RequestRouterTest.Timeout(20, 5000);
    }

    /**
     * Return the time to wait between reusing deployment requests. This could be necessary to give
     * some platforms time to clean up after undeployment.
     */
    protected int redeploymentPause() {
        return 0;
    }



}
