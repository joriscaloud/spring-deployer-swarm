package org.springframework.cloud.deployer.spi.swarm;

import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo;
import org.springframework.cloud.deployer.spi.core.RuntimeEnvironmentInfo.Builder;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.task.TaskStatus;

/**
 * Created by adrienplagnol on 09/12/2016.
 */
// FIXME implement this maybe
public class SwarmTaskLauncher implements TaskLauncher {

    @Override
    public String launch(AppDeploymentRequest request) {
        return "not_implemented";
    }

    @Override
    public void cancel(String id) {
    }

    @Override
    public TaskStatus status(String id) {
        return null;
    }

    @Override
    public void cleanup(String s) {
    }

    @Override
    public void destroy(String s) {
    }

    @Override
    public RuntimeEnvironmentInfo environmentInfo() {
        Builder builder = new Builder();
        builder
                .implementationName("Docker Swarm Launcher")
                .implementationVersion("1.4.0")
                .platformApiVersion("1.4.0")
                .platformClientVersion("1.4.0")
                .platformHostVersion("1.4.0")
                .platformType("1.4.0")
                .spiClass(SwarmAppDeployer.class);

        return builder.build();
    }
}