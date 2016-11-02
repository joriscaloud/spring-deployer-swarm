package org.springframework.cloud.deployer.spi.swarm;

import com.spotify.docker.client.messages.Container;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;

/**
 * Created by joriscaloud on 19/10/16.
 */
public interface DefaultContainerFactory {
    Container create(String appId, AppDeploymentRequest request, Integer externalPort, Integer instanceIndex);

}
