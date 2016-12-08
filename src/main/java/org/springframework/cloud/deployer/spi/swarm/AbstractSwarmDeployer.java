package org.springframework.cloud.deployer.spi.swarm;


import com.spotify.docker.client.messages.swarm.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.app.AppStatus;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by joriscaloud on 12/10/16.
 */
public class AbstractSwarmDeployer {

    protected static final String SPRING_DEPLOYMENT_KEY = "spring-deployment-id";
    protected static final String SPRING_GROUP_KEY = "spring-group-id";
    protected static final String SPRING_APP_KEY = "spring-app-id";
    protected static final String SPRING_MARKER_KEY = "role";
    protected static final String SPRING_MARKER_VALUE = "spring-app";

    protected static final Logger logger = LoggerFactory.getLogger(AbstractSwarmDeployer.class);



    /**
     * Creates a map of labels for a given ID. This will allow Swarm services
     * to "select" the right ReplicationControllers.
     */

    protected Map<String, String> createIdMap(String appId, AppDeploymentRequest request, Integer instanceIndex) {
        //TODO: handling of app and group ids
        Map<String, String> map = new HashMap<String, String>();
        map.put(SPRING_APP_KEY, appId);
        String groupId = request.getDeploymentProperties().get(AppDeployer.GROUP_PROPERTY_KEY);
        if (groupId != null) {
            map.put(SPRING_GROUP_KEY, groupId);
        }
        String appInstanceId = instanceIndex == null ? appId : appId + "-" + instanceIndex;
        map.put(SPRING_DEPLOYMENT_KEY, appInstanceId);
        return map;
    }

    protected String createDeploymentId(AppDeploymentRequest request) {
        String groupId = request.getDeploymentProperties().get(AppDeployer.GROUP_PROPERTY_KEY);
        String deploymentId;
        if (groupId == null) {
            deploymentId = String.format("%s", request.getDefinition().getName());
        }
        else {
            deploymentId = String.format("%s-%s", groupId, request.getDefinition().getName());
        }
        return deploymentId.replace('.', '-');
    }


    protected AppStatus buildAppStatus(SwarmDeployerProperties properties, String appId, Task task) {
        AppStatus.Builder statusBuilder = AppStatus.of(appId);

        statusBuilder.with(new SwarmAppInstanceStatus(properties, appId, task));
        return statusBuilder.build();
    }

    protected Map<String, Long> deduceResourceLimits(SwarmDeployerProperties properties, AppDeploymentRequest request) {
        String memOverride = request.getDeploymentProperties().get("spring.cloud.deployer.swarm.memory");
        Long memory = null;
        if (memOverride == null) {
            memory = properties.getMemory();
        }
        else {
            memory = Long.parseLong(memOverride, 10);
        }

        String cpuOverride = request.getDeploymentProperties().get("spring.cloud.deployer.swarm.cpu");
        Long cpu = null;
        if (cpuOverride == null) {
            cpu = properties.getCpu();
        }
        else {
            cpu = Long.parseLong(cpuOverride, 10);
        }

        logger.debug("Using limits - cpu: " + cpuOverride + " mem: " + memOverride);

        Map<String, Long> limits = new HashMap<String, Long>();
        limits.put("memory", memory);
        limits.put("cpu", cpu);
        return limits;
    }

}
