package org.springframework.cloud.deployer.spi.swarm;


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

    protected static final Logger logger = LoggerFactory.getLogger(SwarmServiceLauncher.class);



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


    protected AppStatus buildAppStatus(SwarmDeployerProperties properties, String appId) {
        AppStatus.Builder statusBuilder = AppStatus.of(appId);

        statusBuilder.with(new SwarmAppInstanceStatus(properties, appId, null));
        return statusBuilder.build();
    }


}
