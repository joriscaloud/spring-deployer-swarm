package org.springframework.cloud.deployer.spi.swarm;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;

/**
 * Created by A542458 on 09/12/2016.
 */
// FIXME implement this maybe
public class SwarmTaskLauncher implements TaskLauncher {
    @Override
    public String launch(AppDeploymentRequest request) {
        return "noop";
    }

    @Override
    public void cancel(String id) {
    }

    @Override
    public TaskStatus status(String id) {
        return null;
    }
}
