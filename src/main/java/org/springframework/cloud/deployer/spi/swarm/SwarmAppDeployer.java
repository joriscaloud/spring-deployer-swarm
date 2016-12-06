package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
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

/**
 * Created by joriscaloud on 12/10/16.
 */
public class SwarmAppDeployer extends AbstractSwarmDeployer implements AppDeployer{

    private static final String SERVER_PORT_KEY = "server.port";
    private static final String HELLOWORLD = "hello-world";
    private static final String JAVA = "java";

    @Autowired
    private SwarmDeployerProperties properties;

    @Autowired
    private DockerClient client;

    public boolean testing = false;

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
            int externalPort = configureExternalPort(request);
            String countProperty = request.getDeploymentProperties().get(COUNT_PROPERTY_KEY);
            int count = (countProperty != null) ? Integer.parseInt(countProperty) : 1;

            String indexedProperty = request.getDeploymentProperties().get(INDEXED_PROPERTY_KEY);
            boolean indexed = (indexedProperty != null) ? Boolean.valueOf(indexedProperty).booleanValue() : false;

            if (indexed) try {
                String indexedId = appId + "-" + count;
                Map<String, String> idMap = createIdMap(appId, request, count);
                logger.debug("Creating service: {} on {} with index count {}", appId, externalPort, count);
                final TaskSpec taskSpec = createSwarmTaskSpec(request);
                final ServiceSpec serviceSpec = createSwarmServiceSpec(appId, taskSpec, idMap, count, externalPort);
                final ServiceCreateResponse response = client.createService(serviceSpec, new ServiceCreateOptions());
                if (testing) {
                    this.testInformations.put("TaskSpec", taskSpec);
                    this.testInformations.put("ServiceSpec", serviceSpec);
                    this.testInformations.put("Response", response);
                }
                List<Task> taskList = client.listTasks();
                for (int index=0 ; index < count ; index++) {
                    final Task createdTask = client.inspectTask(taskList.get(index).id());
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

            else try {
                Map<String, String> idMap = createIdMap(appId, request, null);
                logger.debug("Creating service: {} on {}", appId, externalPort);
                final TaskSpec taskSpec = createSwarmTaskSpec(request);
                final ServiceSpec serviceSpec = createSwarmServiceSpec(appId, taskSpec, idMap, count, externalPort);
                final ServiceCreateResponse response = client.createService(serviceSpec, new ServiceCreateOptions());
                final Task createdTask = client.inspectTask(serviceSpec.name());
                appStatus = status(appId,  createdTask);
                if (testing) {
                    this.testInformations.put("TaskSpec", taskSpec);
                    this.testInformations.put("ServiceSpec", serviceSpec);
                    this.testInformations.put("Response", response);
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

    public String deployWithNetwork(AppDeploymentRequest request, String networkName) {
        String appId = createDeploymentId(request);
        logger.debug("Deploying app: {}", appId);

        try {
            AppStatus appStatus = status(appId);
            if (!appStatus.getState().equals(DeploymentState.unknown)) {
                throw new IllegalStateException(String.format("App '%s' is already deployed", appId));
            }
            int externalPort = configureExternalPort(request);
            String countProperty = request.getDeploymentProperties().get(COUNT_PROPERTY_KEY);
            int count = (countProperty != null) ? Integer.parseInt(countProperty) : 1;


            String indexedProperty = request.getDeploymentProperties().get(INDEXED_PROPERTY_KEY);
            boolean indexed = (indexedProperty != null) && Boolean.valueOf(indexedProperty);

            if (indexed) try {
                    String indexedId = appId + "-" + count;
                    Map<String, String> idMap = createIdMap(appId, request, count);
                    logger.debug("Creating service: {} on {} with index {}", appId, externalPort, count);
                    //creation of the swarm service with all its specificities
                    final TaskSpec taskSpec = createSwarmTaskSpec(request);
                    final ServiceSpec serviceSpec = createSwarmServiceSpecWithNetwork(appId, taskSpec, idMap, count, externalPort, networkName);
                    final ServiceCreateResponse response = client.createService(serviceSpec, new ServiceCreateOptions());
                    if (testing) {
                        this.testInformations.put("TaskSpec", taskSpec);
                        this.testInformations.put("ServiceSpec", serviceSpec);
                        this.testInformations.put("Response", response);
                    }
                    List<Task> taskList = client.listTasks();
                    for (int index=0 ; index < count ; index++) {
                        final Task createdTask = client.inspectTask(taskList.get(index).id());
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

            else try {
                Map<String, String> idMap = createIdMap(appId, request, null);
                logger.debug("Creating service: {} on {}", appId, externalPort);
                final TaskSpec taskSpec = createSwarmTaskSpec(request);
                final ServiceSpec serviceSpec = createSwarmServiceSpecWithNetwork(appId, taskSpec, idMap, count, externalPort, networkName);
                final ServiceCreateResponse response = client.createService(serviceSpec, new ServiceCreateOptions());
                final Task createdTask = client.inspectTask(serviceSpec.name());
                appStatus = status(appId, createdTask);
                if (testing) {
                    this.testInformations.put("TaskSpec", taskSpec);
                    this.testInformations.put("ServiceSpec", serviceSpec);
                    this.testInformations.put("Response", response);
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
            }
        }
        catch (DockerException | InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }



    private TaskSpec createSwarmTaskSpec(AppDeploymentRequest request) {
        String image = null;
        try {
            image = request.getResource().getURI().getSchemeSpecificPart();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to get URI for " + request.getResource(), e);
        }
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
        int externalPort = 2375;
        Map<String, String> parameters = request.getDefinition().getProperties();
        if (parameters.containsKey(SERVER_PORT_KEY)) {
            externalPort = Integer.valueOf(parameters.get(SERVER_PORT_KEY));
        }
        return externalPort;
    }

    @Override
    public AppStatus status(String appId) {
        Map<String, String> selector = new HashMap<>();
        selector.put(SPRING_APP_KEY, appId);
        if (logger.isDebugEnabled()) {
            logger.debug("Building AppStatus for app: {}", appId);
        }
        AppStatus status = buildAppStatus(properties, appId, null);
        logger.debug("Status for app: {} is {}", appId, status);
        return status;
    }

    public AppStatus status(String appId, Task task) {
        Map<String, String> selector = new HashMap<>();
        selector.put(SPRING_APP_KEY, appId);
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

