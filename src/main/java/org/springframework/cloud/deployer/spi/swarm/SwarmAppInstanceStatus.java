package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.messages.swarm.ContainerStatus;
import com.spotify.docker.client.messages.swarm.Task;
import com.spotify.docker.client.messages.swarm.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.deployer.spi.app.AppInstanceStatus;
import org.springframework.cloud.deployer.spi.app.DeploymentState;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by joriscaloud on 13/10/16.
 * The Spring DeploymentState / Swarm State mapping was simplified
 * for integration matters
 */

public class SwarmAppInstanceStatus implements AppInstanceStatus {

    private static Logger logger = LoggerFactory.getLogger(SwarmAppInstanceStatus.class);
    private SwarmDeployerProperties properties;
    private Task task;
    private TaskStatus taskStatus;
    private ContainerStatus containerStatus;
    private String appId;
    private DeploymentState deploymentState;

    public SwarmAppInstanceStatus(SwarmDeployerProperties properties, String appId, Task task) {
        this.properties = properties;
        this.appId = appId;
        this.task = task;
        if (task != null) {

            this.taskStatus = task.status();
            this.containerStatus = taskStatus.containerStatus();
        }
        this.deploymentState = getState();

    }

    public String getId() {
        return String.format("%s", appId);
    }

    public DeploymentState getState() {
        return taskStatus != null ? mapState() : DeploymentState.unknown;
    }


    /**
     * Maps SWARM phases/states onto Spring Cloud Deployer states
     */
    private DeploymentState mapState() {
        logger.debug("{} - ContainerStatus [ {} ]", taskStatus.containerStatus());
        switch (task.status().state()) {

            case TaskStatus.TASK_STATE_NEW:
                return DeploymentState.deploying;

            case TaskStatus.TASK_STATE_PENDING:
                return DeploymentState.deploying;

            case TaskStatus.TASK_STATE_ASSIGNED:
                return DeploymentState.deploying;

            case TaskStatus.TASK_STATE_PREPARING:
                return DeploymentState.deploying;

            case TaskStatus.TASK_STATE_READY:
                return DeploymentState.deployed;

            case TaskStatus.TASK_STATE_STARTING:
                return DeploymentState.deploying;

            case TaskStatus.TASK_STATE_FAILED:
                return DeploymentState.failed;

            case TaskStatus.TASK_STATE_REJECTED:
                return DeploymentState.error;

            case TaskStatus.TASK_STATE_SHUTDOWN:
                return DeploymentState.undeployed;

            case TaskStatus.TASK_STATE_COMPLETE:
                return DeploymentState.deployed;

            case TaskStatus.TASK_STATE_RUNNING:
                return DeploymentState.deployed;


            case TaskStatus.TASK_STATE_ALLOCATED:
                return DeploymentState.deployed;

            default:
                return DeploymentState.unknown;
        }
    }

    public Map<String, String> getAttributes() {
        Map<String, String> result = new HashMap<>();

        if (containerStatus != null) {
            result.put("container_id", containerStatus.containerId());

        }
;
        if (taskStatus != null && containerStatus.exitCode() != null) {
            result.put("container_last_termination_exit_code", "" + containerStatus.exitCode());
        }
        return result;
    }

}
