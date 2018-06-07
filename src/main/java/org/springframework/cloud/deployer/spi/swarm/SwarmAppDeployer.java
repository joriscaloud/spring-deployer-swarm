package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig.Healthcheck;
import com.spotify.docker.client.messages.Ipam;
import com.spotify.docker.client.messages.NetworkConfig;
import com.spotify.docker.client.messages.NetworkCreation;
import com.spotify.docker.client.messages.ServiceCreateResponse;
import com.spotify.docker.client.messages.swarm.ContainerSpec;
import com.spotify.docker.client.messages.swarm.ContainerSpec.Builder;
import com.spotify.docker.client.messages.swarm.EndpointSpec;
import com.spotify.docker.client.messages.swarm.NetworkAttachmentConfig;
import com.spotify.docker.client.messages.swarm.PortConfig;
import com.spotify.docker.client.messages.swarm.ResourceRequirements;
import com.spotify.docker.client.messages.swarm.Resources;
import com.spotify.docker.client.messages.swarm.RestartPolicy;
import com.spotify.docker.client.messages.swarm.Service;
import com.spotify.docker.client.messages.swarm.ServiceMode;
import com.spotify.docker.client.messages.swarm.ServiceSpec;
import com.spotify.docker.client.messages.swarm.Task;
import com.spotify.docker.client.messages.swarm.TaskSpec;
import com.spotify.docker.client.messages.swarm.TaskStatus;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by joriscaloud on 12/10/16.
 * <p>
 * Swarm Deployer that implements the Spring Cloud AppDeployer
 * Used by the Spring Cloud Data Flow Server
 */
public class SwarmAppDeployer extends AbstractSwarmDeployer implements AppDeployer {

    private static final String SERVER_PORT_KEY = "server.port";
    private static final String SWARM_NETWORKS = AppDeployer.PREFIX + "swarm.networks";
    private static final String RESTART_POLICY = AppDeployer.PREFIX + "swarm.restart";

    private static final String HEALTHCHECK_INTERVAL = AppDeployer.PREFIX + "docker.healthcheck.interval";
    private static final String HEALTHCHECK_RETRIES = AppDeployer.PREFIX + "docker.healthcheck.reties";
    private static final String HEALTHCHECK_START_PERIOD = AppDeployer.PREFIX + "docker.healthcheck.startperiod";
    private static final String HEALTHCHECK_TEST = AppDeployer.PREFIX + "docker.healthcheck.test";
    private static final String HEALTHCHECK_TIMEOUT = AppDeployer.PREFIX + "docker.healthcheck.timeout";

    private static final String TASK_MEMORY = AppDeployer.PREFIX + "docker.memory";
    private static final String TASK_CPU = AppDeployer.PREFIX + "docker.cpu";

    @Autowired
    private SwarmDeployerProperties properties;

    @Autowired
    private DockerClient client;

    /**
     * public variables used for test purposes
     */
    boolean withNetwork = true;

    Map<String, Object> testInformations = new HashMap<>();

    @Data
    private static class PortMap {
        int hostPort;
        int containerPort;
    }

    @Override
    public String deploy(AppDeploymentRequest request) {
        String appId = createDeploymentId(request);
        logger.debug("Deploying app: {}", appId);

        try {
            AppStatus appStatus = status(appId);
            if (appStatus.getState() != DeploymentState.unknown) {
                throw new IllegalStateException(String.format("App '%s' is already deployed", appId));
            }

            // TODO Add port mapping params
            Set<PortMap> portMap = null;
            configureExternalPort(request);

            List<String> swarmNetworks = new ArrayList<>();
            String networks = request.getDeploymentProperties().get(SWARM_NETWORKS);
            if (null != networks) {
                for (String net : networks.split(",")) {
                    swarmNetworks.add(net);
                }
            }

            if (withNetwork) {
                String networkName = request.getDeploymentProperties().get(AppDeployer.GROUP_PROPERTY_KEY);
                try {
                    checkNetworkExistence(networkName);
                } catch (DockerException | InterruptedException e) {
                    throw new RuntimeException(e);
                }

                swarmNetworks.add(networkName);
            }

            String countProperty = request.getDeploymentProperties().get(COUNT_PROPERTY_KEY);
            int count = (countProperty != null) ? Integer.parseInt(countProperty) : 1;

            String indexedProperty = request.getDeploymentProperties().get(INDEXED_PROPERTY_KEY);
            boolean indexed = (indexedProperty != null) && Boolean.valueOf(indexedProperty);

            //can be used if scaling at runtime is implemented in Spring Cloud Data Flow
            if (request.getDeploymentProperties().containsKey("scale")) {
                updateReplicasNumber(appId,
                                     Integer.parseInt(request.getDeploymentProperties().get("scale")),
                                     swarmNetworks);
            }

            //indexed container deployment
            if (indexed) {
                try {
                    String indexedId = appId + "-" + count;
                    Map<String, String> idMap = createIdMap(appId, request, count);
                    logger.debug("Creating service: {} on {} with index count {}", appId, 0, count);
                    TaskSpec taskSpec = createTaskSpec(request);
                    ServiceSpec serviceSpec = createSwarmServiceSpec(appId, taskSpec, idMap, count, portMap, swarmNetworks);
                    ServiceCreateResponse response = client.createService(serviceSpec, null);
                } catch (DockerException | InterruptedException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            } else { //Single container deployment
                try {
                    Map<String, String> idMap = createIdMap(appId, request, null);
                    logger.debug("Creating service: {} on {}", appId); // TODO missing 2nd argument
                    final TaskSpec taskSpec = createTaskSpec(request);
                    ServiceSpec serviceSpec = createSwarmServiceSpec(appId, taskSpec, idMap, count, portMap, swarmNetworks);
                    client.createService(serviceSpec, null);
                } catch (DockerException | InterruptedException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
            return appId;
        } catch (RuntimeException e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void undeploy(String serviceId) {
        logger.debug("Undeploying service: {}", serviceId);
        try {
            client.removeService(serviceId);
        } catch (DockerException | InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void updateReplicasNumber(String appId, int replicas, List<String> networks) {
        logger.debug("Undeploying app: {}", appId);
        try {
            List<Service> services = client.listServices(Service.find().serviceName(appId).build());
            for (Service rc : services) {
                logger.debug("Updating replicas number for : {}", rc.id());
                client.updateService(rc.id(), rc.version().index(), ServiceSpec.builder()
                                                                               .name(rc.spec().name())
                                                                               .taskTemplate(rc.spec().taskTemplate())
                                                                               .mode(ServiceMode.withReplicas(replicas))
                                                                               .networks(networks.stream()
                                                                                                 .map(temp -> NetworkAttachmentConfig.builder().target(temp).build())
                                                                                                 .collect(Collectors.toList()))
                                                                               .endpointSpec(rc.spec().endpointSpec())
                                                                               .updateConfig(rc.spec().updateConfig())
                                                                               .build());
                if (replicas == 0) {
                    client.removeService(rc.id());
                }
            }
        } catch (DockerException | InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }

    //made for tests purposes, services are meant to belong to overlays
    void updateReplicasNumber(String appId, int replicas) {
        logger.debug("Undeploying app: {}", appId);
        try {
            List<Service> services = client.listServices(Service.find().serviceName(appId).build());
            for (Service rc : services) {
                logger.debug("Updating replicas number for : {}", rc.id());
                client.updateService(rc.id(), rc.version().index(), ServiceSpec.builder()
                                                                               .name(rc.spec().name())
                                                                               .taskTemplate(rc.spec().taskTemplate())
                                                                               .mode(ServiceMode.withReplicas(replicas))
                                                                               .endpointSpec(rc.spec().endpointSpec())
                                                                               .updateConfig(rc.spec().updateConfig())
                                                                               .build());
                if (replicas == 0) {
                    client.removeService(rc.id());
                }
            }
        } catch (DockerException | InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void checkNetworkExistence(String networkName) throws DockerException, InterruptedException {
        if (client.listNetworks().stream().noneMatch(n -> n.name().equalsIgnoreCase(networkName))) {
            try {
                createNetwork(networkName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void createNetwork(String networkName) throws Exception {
        final NetworkCreation networkCreation = client
                .createNetwork(NetworkConfig.builder().driver("overlay")
                                            .ipam(Ipam.builder().driver("default").build())
                                            .name(networkName).build());
        testInformations.put("Network", networkCreation);
    }

    private TaskSpec createTaskSpec(AppDeploymentRequest request) {
        String image;

        try {
            image = request.getResource().getURI().getSchemeSpecificPart();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to get URI for " + request.getResource(), e);
        }

        String restartOpts = request.getDeploymentProperties().get(RESTART_POLICY);

        String interval = request.getDeploymentProperties().get(HEALTHCHECK_INTERVAL);
        String retries = request.getDeploymentProperties().get(HEALTHCHECK_RETRIES);
        String startPeriod = request.getDeploymentProperties().get(HEALTHCHECK_START_PERIOD);
        String test = request.getDeploymentProperties().get(HEALTHCHECK_TEST);
        String timeout = request.getDeploymentProperties().get(HEALTHCHECK_TIMEOUT);

        String memory = request.getDeploymentProperties().get(TASK_MEMORY);
        String cpu = request.getDeploymentProperties().get(TASK_CPU);

        Builder containerSpecBuilder = ContainerSpec.builder()
                                                    .image(image)
                                                    .args(request.getDefinition().getProperties().entrySet().stream()
                                                                 .map(e -> "--" + e.getKey() + "=" + e.getValue())
                                                                 .collect(Collectors.toList()));

        if (null != test) {
            List<String> testCommands = new ArrayList<>();
            for (String cmd : test.split(",")) {
                testCommands.add(cmd);
            }

            containerSpecBuilder
                    .healthcheck(Healthcheck.builder()
                                            .interval(null != interval ? Long.parseLong(interval) : 600_000L)
                                            .retries(null != retries ? Integer.parseInt(retries) : 10)
                                            .startPeriod(null != startPeriod ? Long.parseLong(startPeriod) : 1200_00L)
                                            .test(testCommands)
                                            .timeout(null != timeout ? Long.parseLong(timeout) : 60_000L)
                                            .build());
        }

        final TaskSpec taskSpec = TaskSpec.builder()
                                          .containerSpec(containerSpecBuilder
                                                                 .build())
                                          .restartPolicy(RestartPolicy.builder()
                                                                      .condition(null != restartOpts ? restartOpts : RestartPolicy.RESTART_POLICY_ANY)
                                                                      .build())
                                          .resources(ResourceRequirements.builder()
                                                                         .limits(Resources.builder()
                                                                                          .memoryBytes(null != memory ? Long.valueOf(memory) : properties.getMemory())
                                                                                          .nanoCpus(null != cpu ? Long.valueOf(cpu) : properties.getCpu())
                                                                                          .build())
                                                                         .build()
                                          )
                                          .build();

        return taskSpec;
    }

    private ServiceSpec createSwarmServiceSpec(String serviceName, TaskSpec taskSpec, Map<String, String> idMap,
                                               int replicas, Set<PortMap> portMap, List<String> networkNames) {
        final ServiceMode serviceMode = ServiceMode.withReplicas(replicas);

        ServiceSpec.Builder builder = ServiceSpec.builder()
                                                 .labels(idMap)
                                                 .name(serviceName)
                                                 .taskTemplate(taskSpec)
                                                 .mode(serviceMode)
                                                 .networks(networkNames.stream()
                                                                       .map(temp -> NetworkAttachmentConfig.builder().target(temp).build())
                                                                       .collect(Collectors.toList()));
        if (null != portMap) {
            builder.endpointSpec(addEndPointSpec(portMap));
        }

        return builder.build();
    }

    @Override
    public AppStatus status(String appId) {
        List<Task> taskList = null;
        AppStatus status;

        Long replicas = 0L;

        try {
            List<Service> services = client.listServices(Service.find().serviceName(appId).build());
            for (Service s : services) {
                if (null != s.spec().mode().replicated()) {
                    replicas += s.spec().mode().replicated().replicas();
                } else {
                    replicas++;
                }
            }

            taskList = client.listTasks(Task.find()
                                            .serviceName(appId)
                                            .build());
        } catch (DockerException | InterruptedException e) {
            if (logger.isDebugEnabled()) {
                e.printStackTrace(); // TODO replace with logging
            }
        }

        logger.debug("Building AppStatus for app: {}", appId);

        AppStatus.Builder statusBuilder = AppStatus.of(appId);

        if (null != taskList && taskList.size() > 0) {
            // first count running tasks
            for (Task t : taskList) {
                if (!t.status().state().equals(TaskStatus.TASK_STATE_RUNNING)) {
                    continue;
                }

                if (0 >= replicas--) {
                    break;
                }

                statusBuilder.with(new SwarmAppInstanceStatus(properties, appId, t));
            }

            // if running tasks < replicas add not running tasks
            if (0L < replicas) {
                for (Task t : taskList) {
                    if (t.status().state().equals(TaskStatus.TASK_STATE_RUNNING)) {
                        continue;
                    }

                    if (0L >= replicas--) {
                        break;
                    }

                    statusBuilder.with(new SwarmAppInstanceStatus(properties, appId, t));
                }
            }
        } else {
            statusBuilder.with(new SwarmAppInstanceStatus(properties, appId, null));
        }

        status = statusBuilder.build();

        logger.debug("Status for app: {} is {}", appId, status);
        return status;
    }

    @Override
    public RuntimeEnvironmentInfo environmentInfo() {
        RuntimeEnvironmentInfo.Builder builder = new RuntimeEnvironmentInfo.Builder();
        builder
                .implementationName("Docker Swarm Deployer")
                .implementationVersion("1.4.0")
                .platformApiVersion("1.4.0")
                .platformClientVersion("1.4.0")
                .platformHostVersion("1.4.0")
                .platformType("1.4.0")
                .spiClass(SwarmAppDeployer.class);

        return builder.build();
    }

    //this function is made for test purposes
    AppStatus status(String appId, Task task) {
        if (logger.isDebugEnabled()) {
            logger.debug("Building AppStatus for app: {}", appId);
        }
        AppStatus.Builder statusBuilder = AppStatus.of(appId);
        AppStatus status = statusBuilder.with(new SwarmAppInstanceStatus(properties, appId, task)).build();
        logger.debug("Status for app: {} is {}", appId, status);
        return status;
    }

    private int configureExternalPort(final AppDeploymentRequest request) {
        int externalPort = 0;
        Map<String, String> parameters = request.getDefinition().getProperties();
        if (parameters.containsKey(SERVER_PORT_KEY)) {
            externalPort = Integer.parseInt(parameters.get(SERVER_PORT_KEY));
        }
        return externalPort;
    }

    private EndpointSpec addEndPointSpec(Set<PortMap> portMap) {
        Stream<PortConfig> ports = createSwarmContainerPortConfig(portMap);

        EndpointSpec.Builder builder = EndpointSpec.builder();
        if (null != ports) {
            builder.ports(ports.collect(Collectors.toList()));
        }

        return builder.build();
    }

    private Stream<PortConfig> createSwarmContainerPortConfig(Set<PortMap> portMap) {
        if (null != portMap) {
            return portMap.stream()
                          .map(map -> PortConfig.builder()
                                                .name("endpoint")
                                                .publishedPort(map.getHostPort())
                                                .targetPort(map.getContainerPort())
                                                .protocol("http")
                                                .build());
        }
        return null;
    }
}