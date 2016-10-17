package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.ContainerState;
import com.spotify.docker.client.messages.swarm.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.deployer.spi.app.DeploymentState;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by joriscaloud on 13/10/16.
 */
public class SwarmAppInstanceStatus {

    private static Logger logger = LoggerFactory.getLogger(SwarmAppInstanceStatus.class);
    private final String moduleId;
    private SwarmDeployerProperties properties;
    private Container container;
    private ContainerState containerState;
    private TaskStatus taskStatus;
    private ContainerInfo containerInfo;

    public SwarmAppInstanceStatus(String moduleId, SwarmDeployerProperties properties, TaskStatus taskStatus) {
        this.moduleId = moduleId;
        this.properties = properties;
        this.taskStatus = taskStatus;
        // we assume one container per pod
        }

    public String getId() {
        return String.format("%s:%s", moduleId);

    }

    public DeploymentState getState() {
        return taskStatus.containerStatus() != null ? mapState() : DeploymentState.unknown;
    }


    /**
     * Maps Kubernetes phases/states onto Spring Cloud Deployer states
     */
    private DeploymentState mapState() {
        logger.debug("{} - ContainerStatus [ {} ]", taskStatus.containerStatus());
        switch (taskStatus.state()) {

            case TaskStatus.TASK_STATE_PENDING:
                return DeploymentState.deploying;

            case  TaskStatus.TASK_STATE_FAILED:
                return DeploymentState.failed;

            case TaskStatus.TASK_STATE_REJECTED:
                return DeploymentState.error;

            case TaskStatus.TASK_STATE_SHUTDOWN:
                return DeploymentState.undeployed;

            case TaskStatus.TASK_STATE_COMPLETE:
                return DeploymentState.deployed;

            default:
                return DeploymentState.unknown;
        }
    }

    public Map<String, String> getAttributes() {
        Map<String, String> result = new HashMap<>();

        if (containerInfo.state() != null) {
            result.put("container_restart_count", "" + containerInfo.restartCount());
            if (containerInfo.state() != null && containerInfo.state().exitCode() != null) {
                result.put("container_last_termination_exit_code", "" + containerInfo.state().exitCode());
                result.put("container_last_termination_reason", containerInfo.state().status());
            }
        }

        return result;
    }

}
