package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.messages.ServiceCreateOptions;
import com.spotify.docker.client.messages.swarm.ContainerSpec;
import com.spotify.docker.client.messages.swarm.Driver;
import com.spotify.docker.client.messages.swarm.EndpointSpec;
import com.spotify.docker.client.messages.swarm.PortConfig;
import com.spotify.docker.client.messages.swarm.ResourceRequirements;
import com.spotify.docker.client.messages.swarm.RestartPolicy;
import com.spotify.docker.client.messages.swarm.ServiceMode;
import com.spotify.docker.client.messages.swarm.ServiceSpec;
import com.spotify.docker.client.messages.swarm.Swarm;
import com.spotify.docker.client.messages.swarm.TaskSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by joriscaloud on 13/10/16.
 */
public abstract class SwarmTaskLauncher extends AbstractSwarmDeployer implements TaskLauncher {

    private SwarmDeployerProperties properties = new SwarmDeployerProperties();

    private final Swarm swarm;

    private DefaultDockerClient dockerClient;
    private final Map<String, Object> running = new ConcurrentHashMap<>();

    @Autowired
    public SwarmTaskLauncher(SwarmDeployerProperties properties, Swarm swarm) {
        this.properties = properties;
        this.swarm = swarm;
    }
/*
    @Override
    public String launch(AppDeploymentRequest request) {
        String appId = createDeploymentId(request);
        TaskStatus status = status(appId);
        ContainerSpec containerSpec;
        if (!status.getState().equals(LaunchState.unknown)) {
            if (status.getState().equals(LaunchState.launching) || status.getState().equals(LaunchState.running)) {
                throw new IllegalStateException("Task " + appId + " is already active with a state of " + status);
            }
            deleteSwarmContainer(appId);
        }
        Map<String, String> idMap = createIdMap(appId, request, null);

        logger.debug("Creating task, service and containeur for : {}", appId);
        try {
            containerSpec = createSwarmContainerSpec(appId, request, null);
            createSwarmService(createSwarmServiceSpec(containerSpec()));
            return appId;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void cancel(String id) {
        logger.debug("Cancelling job: {}", id);
        //ToDo: what does cancel mean? Kubernetes doesn't have stop - just delete
        delete(id);
    }

    //	@Override //TODO: should be part of interface
    public void delete(String id) {
        logger.debug("Deleting job: {}", id);
        deleteSwarmContainer(id);
    }
    */

    /*
    @Override
    public TaskStatus status(String id) {
        Map<String, String> selector = new HashMap<>();
        selector.put(SPRING_APP_KEY, id);
        ContainerInfo.Node node = new ContainerInfo.Node();

        TaskStatus status = buildTaskStatus(properties, id);
        logger.debug("Status for task: {} is {}", id, status);

        return status;
    }
    */

    public void createSwarmService(ServiceSpec serviceSpec) throws Exception {
        dockerClient.createService(serviceSpec, new ServiceCreateOptions());
    }


    public ServiceSpec createSwarmServiceSpec(String serviceName, int replicas,
                                              ContainerSpec containerSpec,
                                              String driverName, HashMap<String, String> driverOptions,
                                              long memoryLimits, String condition,
                                              int delay, int maxAttempts, int port) {

        final ServiceMode serviceMode = ServiceMode.withReplicas(replicas);
        EndpointSpec endpointSpec = addEndPointSpec(port);
        TaskSpec taskSpec = createTaskSpec(containerSpec, driverName, driverOptions, memoryLimits, condition, delay, maxAttempts);
        final ServiceSpec spec = ServiceSpec.builder().withName(serviceName).withTaskTemplate(taskSpec)
                .withServiceMode(serviceMode)
                .withEndpointSpec(endpointSpec)
                .build();
        return spec;
    }

    public TaskSpec createTaskSpec(ContainerSpec containerSpec, String driverName, HashMap<String, String> driverOptions,
                                   long memoryLimits, String condition, int delay, int maxAttempts) {
        TaskSpec taskSpec = new TaskSpec();
        addContainerSpec(taskSpec, containerSpec);
        addLogDriver(taskSpec, driverName, driverOptions);
        addResources(taskSpec, memoryLimits);
        addRestartPolicy(taskSpec, condition, delay, maxAttempts);
        return taskSpec;
    }

    public TaskSpec addContainerSpec(TaskSpec taskSpec, ContainerSpec containerSpec) {
        return taskSpec = TaskSpec.builder()
                            .withContainerSpec(containerSpec)
                        .build();

    }

    public TaskSpec addLogDriver(TaskSpec taskSpec, String driverName, HashMap<String, String> driverOptions) {
        return taskSpec = TaskSpec.builder()
                            .withLogDriver(
                                Driver.builder()
                                .withName(driverName)
                            .build())
                        .build();
    }

    public TaskSpec addResources(TaskSpec taskSpec, long memoryLimits) {
        return taskSpec = TaskSpec.builder()
                            .withResources(
                                    ResourceRequirements.builder()
                                        .withLimits(com.spotify.docker.client.messages.swarm.Resources.builder()
                                        .withMemoryBytes(memoryLimits)
                                        .build())
                                    .build())
                        .build();
    }

    public TaskSpec addRestartPolicy(TaskSpec taskSpec, String condition, int delay, int maxAttempts) {
        return taskSpec = TaskSpec.builder()
                            .withRestartPolicy(
                                    RestartPolicy.builder()
                                        .withCondition(condition)
                                        .withDelay(delay)
                                        .withMaxAttempts(maxAttempts)
                                    .build())
                            .build();
    }

    private EndpointSpec addEndPointSpec(int port) {
        return EndpointSpec.builder()
                                .withPorts(new PortConfig[]{createSwarmContainerPortConfig(port)})
                        .build();

    }



    public ContainerSpec createSwarmContainerSpec(String appId,  AppDeploymentRequest request, Integer instanceIndex)
            throws Exception {
        String image = null;
        final String containerName = appId;
        String appInstanceId = instanceIndex == null ? appId : appId + "-" + instanceIndex;

        try {
            image = request.getResource().getURI().getSchemeSpecificPart();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to get URI for " + request.getResource(), e);
        }

        logger.info("Using Docker image: " + image);
        List<String> envVars = new ArrayList<>();
        for (String envVar : properties.getEnvironmentVariables()) {
            envVars.add(envVar);
        }
        ContainerSpec containerSpec = ContainerSpec.builder()
                .withEnv(properties.getEnvironmentVariables())
                .withImage(image)
                .withCommands(createCommandArgs(request)).build();

        return containerSpec;
    }

    public PortConfig createSwarmContainerPortConfig(Integer port) {
        PortConfig portConfig = new PortConfig();
        if (port != null) {
            portConfig = PortConfig.builder()
                    .withPublishedPort(port)
                    .withTargetPort(port)
                    .withProtocol("tcp")
                    .build();
        }
        return portConfig;
    }

    /**
     * Create command arguments
     */
    protected List<String> createCommandArgs(AppDeploymentRequest request) {
        List<String> cmdArgs = new LinkedList<String>();
        // add properties from deployment request
        Map<String, String> args = request.getDefinition().getProperties();
        for (Map.Entry<String, String> entry : args.entrySet()) {
            cmdArgs.add(String.format("--%s=%s", entry.getKey(), entry.getValue()));
        }
        // add provided command line args
        cmdArgs.addAll(request.getCommandlineArguments());
        logger.debug("Using command args: " + cmdArgs);
        return cmdArgs;
    }

}
