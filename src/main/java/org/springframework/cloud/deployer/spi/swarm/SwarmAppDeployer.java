package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Ipam;
import com.spotify.docker.client.messages.NetworkConfig;
import com.spotify.docker.client.messages.NetworkCreation;
import com.spotify.docker.client.messages.ServiceCreateOptions;
import com.spotify.docker.client.messages.ServiceCreateResponse;
import com.spotify.docker.client.messages.swarm.ContainerSpec;
import com.spotify.docker.client.messages.swarm.EndpointSpec;
import com.spotify.docker.client.messages.swarm.NetworkAttachmentConfig;
import com.spotify.docker.client.messages.swarm.PortConfig;
import com.spotify.docker.client.messages.swarm.RestartPolicy;
import com.spotify.docker.client.messages.swarm.Service;
import com.spotify.docker.client.messages.swarm.ServiceMode;
import com.spotify.docker.client.messages.swarm.ServiceSpec;
import com.spotify.docker.client.messages.swarm.Task;
import com.spotify.docker.client.messages.swarm.TaskSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by joriscaloud on 12/10/16.
 */
public class SwarmAppDeployer extends AbstractSwarmDeployer implements AppDeployer{

    private static final String SERVER_PORT_KEY = "server.port";

    @Autowired
    private SwarmDeployerProperties properties;

    @Autowired
    private DockerClient client;

    public boolean testing = false;

    public boolean withNetwork = false;

    public Map<String, Object> testInformations = new HashMap<String, Object>();


    @Override
    public String deploy(AppDeploymentRequest request) {
        String appId = createDeploymentId(request);
        logger.debug("Deploying app: {}", appId);

        try {
            AppStatus appStatus = status(appId);
            if (!appStatus.getState().equals(DeploymentState.unknown)) {
                throw new IllegalStateException(String.format("App '%s' is already deployed", appId));
            }
            configureExternalPort(request);
            String countProperty = request.getDeploymentProperties().get(COUNT_PROPERTY_KEY);
            int count = (countProperty != null) ? Integer.parseInt(countProperty) : 1;

            String indexedProperty = request.getDeploymentProperties().get(INDEXED_PROPERTY_KEY);
            boolean indexed = (indexedProperty != null) ? Boolean.valueOf(indexedProperty).booleanValue() : false;

            //indexed container deployment
            if (indexed) try {
                String indexedId = appId + "-" + count;
                Map<String, String> idMap = createIdMap(appId, request, count);
                logger.debug("Creating service: {} on {} with index count {}", appId, 0, count);
                TaskSpec taskSpec = createSwarmTaskSpec(request);
                ServiceSpec serviceSpec = null;
                if (withNetwork) {
                    final String networkName = randomName();
                    serviceSpec = createSwarmServiceSpecWithNetwork(appId, taskSpec, idMap, count, 0, networkName);
                    try {
                        createNetwork(networkName);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else {
                    serviceSpec = createSwarmServiceSpec(appId, taskSpec, idMap, count, 0);
                }
                ServiceCreateResponse response = client.createService(serviceSpec, new ServiceCreateOptions());
                if (testing) {
                    this.testInformations.put("TaskSpec", taskSpec);
                    this.testInformations.put("ServiceSpec", serviceSpec);
                    this.testInformations.put("Last Response", response);
                }
                List<Task> taskList = client.listTasks();
                for (int index=0 ; index < count ; index++) {
                    Task createdTask = client.inspectTask(taskList.get(index).id());
                    appStatus = status(indexedId, createdTask);
                    if (testing) {
                        this.testInformations.put("Task " + index, createdTask);
                        this.testInformations.put("AppStatus " + index, appStatus);
                    }
                }
            }
            catch (DockerException|InterruptedException e) {
                e.printStackTrace();
            }

            //Single container deployment
            else try {
                Map<String, String> idMap = createIdMap(appId, request, null);
                logger.debug("Creating service: {} on {}", appId);
                final TaskSpec taskSpec = createSwarmTaskSpec(request);
                ServiceSpec serviceSpec = null;
                if (withNetwork) {
                    final String networkName = randomName();
                    serviceSpec = createSwarmServiceSpecWithNetwork(appId, taskSpec, idMap, count, 0, networkName);
                    try {
                        createNetwork(networkName);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else {
                    serviceSpec = createSwarmServiceSpec(appId, taskSpec, idMap, count, 0);
                }
                ServiceCreateResponse response = client.createService(serviceSpec, new ServiceCreateOptions());
                Task listTask = client.listTasks().iterator().next();
                Task createdTask = null;
                if (listTask.serviceId().equals(response.id())) {
                     createdTask = listTask;
                }
                appStatus = status(appId, createdTask);
                if (testing) {
                    this.testInformations.put("TaskSpec", taskSpec);
                    this.testInformations.put("ServiceSpec", serviceSpec);
                    this.testInformations.put("Last Response", response);
                    this.testInformations.put("Task", createdTask);
                    this.testInformations.put("appStatus", appStatus);
                }
            }
            catch (DockerException|InterruptedException e) {
                e.printStackTrace();
            }

            return appId;

        } catch (RuntimeException e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }


    @Override
    public void undeploy(String appId) {
        logger.debug("Undeploying app: {}", appId);
        final int timeToWait = 10;
        try {
            logger.debug("Deleting service for : {}", appId);
            client.stopContainer(appId, timeToWait);
        }
        catch (DockerException | InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }



    public void updateReplicasNumber(String appId, int replicas) {
        logger.debug("Undeploying app: {}", appId);
        try {
            List<Service> services =
                    client.listServices(Service.find().withServiceName(appId).build());
            for (Service rc : services) {
                logger.debug("Updating replicas number for : {}", rc.id());
                client.updateService(rc.id(), rc.version().index(), ServiceSpec.builder()
                        .withName(rc.spec().name())
                        .withTaskTemplate(rc.spec().taskTemplate())
                        .withServiceMode(ServiceMode.withReplicas(replicas))
                        .withEndpointSpec(rc.spec().endpointSpec())
                        .withUpdateConfig(rc.spec().updateConfig())
                        .build());
                if (replicas == 0) {
                    client.removeService(rc.id());
                }
            }

        }
        catch (DockerException | InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }


    public void createNetwork(String networkName) throws Exception{
        final NetworkCreation networkCreation = client
                .createNetwork(NetworkConfig.builder().driver("overlay")
                        // TODO: workaround for https://github.com/docker/docker/issues/25735
                        .ipam(Ipam.builder().driver("default").build())
                        //
                        .name(networkName).build());
        this.testInformations.put("Network", networkCreation);
    }

    protected String randomName() {
        return UUID.randomUUID().toString();
    }


    private TaskSpec createSwarmTaskSpec(AppDeploymentRequest request) {
        String image = null;
        try {
            image = request.getResource().getURI().getSchemeSpecificPart();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to get URI for " + request.getResource(), e);
        }
        //Map<String, Long> resourceLimits = deduceResourceLimits(properties, request);

        final TaskSpec taskSpec = TaskSpec.builder()
                .withContainerSpec(ContainerSpec.builder()
                        .withImage(image)
                        .build())
                .withRestartPolicy(RestartPolicy.builder()
                                    .withCondition(RestartPolicy.RESTART_POLICY_NONE)
                                    .build())
                .build();
        return taskSpec;
    }


    private ServiceSpec createSwarmServiceSpec(String serviceName, TaskSpec taskSpec, Map<String, String> idMap,
                                               int replicas, int externalPort) {


        final ServiceMode serviceMode = ServiceMode.withReplicas(replicas);

        ServiceSpec service = ServiceSpec.builder()
                    .withLabels(idMap)
                    .withName(serviceName)
                    .withTaskTemplate(taskSpec)
                    .withServiceMode(serviceMode)
                    .withEndpointSpec(addEndPointSpec(externalPort))
                    .build();

        return service;
    }


    private ServiceSpec createSwarmServiceSpecWithNetwork(String serviceName, TaskSpec taskSpec, Map<String, String> idMap,
                                               int replicas, int externalPort, String networkName) {


        final ServiceMode serviceMode = ServiceMode.withReplicas(replicas);

        ServiceSpec service = ServiceSpec.builder()
                .withLabels(idMap)
                .withName(serviceName)
                .withTaskTemplate(taskSpec)
                .withServiceMode(serviceMode)
                .withEndpointSpec(addEndPointSpec(externalPort))
                .withNetworks((NetworkAttachmentConfig.builder().withTarget(networkName).build()))
                .build();

        return service;
    }


    protected int configureExternalPort(final AppDeploymentRequest request) {
        int externalPort = 0;
        Map<String, String> parameters = request.getDefinition().getProperties();
        if (parameters.containsKey(SERVER_PORT_KEY)) {
            externalPort = Integer.valueOf(parameters.get(SERVER_PORT_KEY));
        }
        return externalPort;
    }

    @Override
    public AppStatus status(String appId) {
        if (logger.isDebugEnabled()) {
            logger.debug("Building AppStatus for app: {}", appId);
        }
        AppStatus status = buildAppStatus(properties, appId, null);
        logger.debug("Status for app: {} is {}", appId, status);
        return status;
    }

    public AppStatus status(String appId, Task task) {
        if (logger.isDebugEnabled()) {
            logger.debug("Building AppStatus for app: {}", appId);
        }
        AppStatus status = buildAppStatus(properties, appId, task);
        logger.debug("Status for app: {} is {}", appId, status);
        return status;
    }


    private EndpointSpec addEndPointSpec(int port) {
        return EndpointSpec.builder()
                .withPorts(new PortConfig[]{createSwarmContainerPortConfig(port)})
                .build();

    }

    public PortConfig createSwarmContainerPortConfig(Integer port) {
        PortConfig portConfig = new PortConfig();
        if (port != null) {
            portConfig = PortConfig.builder()
                    .withName("endpoint")
                    .withPublishedPort(port)
                    .withTargetPort(port)
                    .withProtocol("http")
                    .build();
        }
        return portConfig;
    }
}

