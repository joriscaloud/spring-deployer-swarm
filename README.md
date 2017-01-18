#spring-cloud-deployer-swarm

The docker-client used in this project can be found there :
https://github.com/spotify/docker-client

Other Spring Cloud deployers or tools can be found there :
https://github.com/spring-cloud

## Building

Build the project without running tests using:

```
./mvn clean install -DskipTests
```

### Initiate a Docker Swarm Cluster

You can follow the instructions on the Docker documentation thanks to the following link.
https://docs.docker.com/engine/reference/commandline/swarm_init/

You can run a Swarm on a single node. 
Initiate the Swarm on the manager node you chose with : 

docker swarm init --advertise-addr *interface address*

It generates a  token that you'll use on your slave nodes in order to 
join the swarm :

docker swarm join --token *token*  *interface adress:port*


####Customize Your Docker Configuration
The port on which Docker Swarm daemon can be accessed remotely is supposed to
be 2375, 2376 or 2377.
You can chose the Port or add options to your Docker by modifying your 
docker conf in :

/etc/systemd/system/docker.service.d/docker.conf 

For instance, you can choose to run Docker in experimental and debug mode, 
make the daemon accessible on its socket and on tcp://0.0.0.0:2375 and 
to choose your own registry with the following configuration :

[Service]
ExecStart=
ExecStart=/usr/bin/dockerd --experimental -H unix:///var/run/docker.sock 
-H tcp://0.0.0.0:2375 --debug --insecure-registry *your-registry*

Replicate this configuration on each node of your Swarm.

#####Run the spring-cloud-deployer-tests
This project is configured to run the tests on a remote Docker Swarm
Cluster.
The client communicates with the Swarm with HTTP protocol,
on the http://0.0.0.0:2375 address (cf. SwarmDeployerProperties.class). 
A connection has to be made with ssh tunnel to a Swarm Manager, 
the 2375 ports are then bound :

ssh -L 2375:localhost:2375 remote-machine

Don't forget to disable Docker locally or your docker client will talk
to your local docker and not to the remote one through the ssh tunnel,
use the command :
 
systemctl stop docker

Now you're ready to make your own tests !
 
