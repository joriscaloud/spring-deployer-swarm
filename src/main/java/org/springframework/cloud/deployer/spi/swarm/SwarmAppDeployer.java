package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ServiceCreateOptions;
import com.spotify.docker.client.messages.ServiceCreateResponse;
import com.spotify.docker.client.messages.swarm.ContainerSpec;
import com.spotify.docker.client.messages.swarm.EndpointSpec;
import com.spotify.docker.client.messages.swarm.PortConfig;
import com.spotify.docker.client.messages.swarm.Service;
import com.spotify.docker.client.messages.swarm.ServiceMode;
import com.spotify.docker.client.messages.swarm.ServiceSpec;
import com.spotify.docker.client.messages.swarm.TaskSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;

import java.io.IOException;
import java.net.URI;
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
    
    private SwarmDeployerProperties properties = new SwarmDeployerProperties();

    private DefaultDockerClient client;

    private URI dockerEndpoint;

    @Autowired
    public SwarmAppDeployer(SwarmDeployerProperties properties, DefaultDockerClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public String deploy(AppDeploymentRequest request) {
        String appId = createDeploymentId(request);
        logger.debug("Deploying app: {}", appId);

        try {
            AppStatus status = status(appId);
            if (!status.getState().equals(DeploymentState.unknown)) {
                throw new IllegalStateException(String.format("App '%s' is already deployed", appId));
            }
            int externalPort = configureExternalPort(request);
            String countProperty = request.getDeploymentProperties().get(COUNT_PROPERTY_KEY);
            int count = (countProperty != null) ? Integer.parseInt(countProperty) : 1;

            String[] commands = new String[]
                    {"while :", "do", "echo blablabla", "sleep 1", "done"};


            String indexedProperty = request.getDeploymentProperties().get(INDEXED_PROPERTY_KEY);
            boolean indexed = (indexedProperty != null) && Boolean.valueOf(indexedProperty);

            try {
                final DefaultDockerClient.Builder builder = DefaultDockerClient.fromEnv();
                dockerEndpoint = builder.uri();
                client = builder.build();
            }
            catch (DockerCertificateException e) {
                logger.error(e.getMessage(), e);
            }

            if (indexed) {


                for (int index=0 ; index < count ; index++) {

                    String indexedId = appId + "-" + index;
                    Map<String, String> idMap = createIdMap(appId, request, index);
                    logger.debug("Creating service: {} on {} with index {}", appId, externalPort, index);
                    //creation of the swarm service with all its specificities
                    final ServiceSpec serviceSpec = createSwarmServiceSpec(appId, request, idMap, commands, 1, externalPort);
                }
            }
            else try {
                Map<String, String> idMap = createIdMap(appId, request, null);
                logger.debug("Creating service: {} on {}", appId, externalPort);
                final ServiceSpec serviceSpec = createSwarmServiceSpec(appId, request, idMap, commands, count, externalPort);
                final ServiceCreateResponse response = client.createService(serviceSpec, new ServiceCreateOptions());

            }
            catch (DockerException |InterruptedException e) {
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
            List<Service> services =
                    client.listServices(Service.find().withServiceName(appId).build());
            for (Service rc : services) {
                String appIdToDelete = rc.id();
                logger.debug("Deleting service for : {}", appIdToDelete);
                client.stopContainer(appIdToDelete, timeToWait);
            }
        }
        catch (DockerException | InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }


    private ServiceSpec createSwarmServiceSpec(String serviceName, AppDeploymentRequest request, Map<String, String> idMap,
                                               String[] commands, int replicas, int externalPort) {
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
                                        .build();

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


    protected int configureExternalPort(final AppDeploymentRequest request) {
        int externalPort = 8080;
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
        AppStatus status = buildAppStatus(properties, appId);
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
                    .withPublishedPort(port)
                    .withTargetPort(port)
                    .withProtocol("tcp")
                    .build();
        }
        return portConfig;
    }
}

