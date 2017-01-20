## Spring-cloud-deployer-swarm

The docker-client used in this project can be found there :
https://github.com/spotify/docker-client

Other Spring Cloud deployers or tools can be found there :
https://github.com/spring-cloud

This project was developed by resuming the architecture of the 
spring-cloud-deployer-kubernetes.
It has some features that can be further developed or added, feel free
to collaborate !
All the tests are effective using Docker 1.13 RC7 experimental.

## Build the project 

Build the project without running tests using:

```
./mvn clean install -DskipTests
```

### Initiate a Docker Swarm Cluster

You can follow the instructions on the Docker documentation thanks to the following link.
https://docs.docker.com/engine/reference/commandline/swarm_init/

You can run a Swarm on a single node. 
Initiate the Swarm on the manager node you chose with : 

> docker swarm init --advertise-addr *interface address*

It generates a  token that you'll use on your slave nodes in order to 
join the swarm :

> docker swarm join --token *token*  *interface adress:port*


##Customize Your Docker Configuration
The port on which Docker Swarm daemon can be accessed remotely is supposed to
be 2375, 2376 or 2377.
You can choose the Port or add options to your Docker by modifying your 
docker conf in :

/etc/systemd/system/docker.service.d/docker.conf 

For instance, you can choose to run Docker in experimental and debug mode, 
make the daemon accessible on the docker socket and on tcp://0.0.0.0:2375 and 
to choose your own registry with the following configuration :

[Service]
ExecStart=
ExecStart=/usr/bin/dockerd --experimental -H unix:///var/run/docker.sock 
-H tcp://0.0.0.0:2375 --debug --insecure-registry *your-registry*

Replicate this configuration on each node of your Swarm.

##Run the tests on a remote Swarm cluster
This project is configured to run the tests on a remote Docker Swarm
Cluster. 

You have to define which endpoint is going to receive your deployment
requests, define the endpoint's URI in the SwarmDeployerProperties.class

Locally : Use the unix docker socket or http://0.0.0.0:2375 for example.

Remotely : Use http://swarm-manager-ip:2375

If a ssh connection has to be made to connect to the swarm manager, 
bind the rights ports when you do it :

> ssh -L 2375:localhost:2375 swarm-manager-ip

Don't forget to disable Docker locally or your docker client will talk
to your local docker and not to the remote one through the ssh tunnel,
use the command :
 
> systemctl stop docker

Now you're ready to make your own tests !

Don't forget that in order to deploy containers on multiple hosts from an
image, all the hosts need to pull the said image before running the 
service.

### Reminder of a few Docker Swarm commands if you want to check your tests

On your Swarm Manager, list your launched service with :

> docker service ls

Watch dynamically you services deploy and undeploy with :

> watch -n 0,2 docker service ls 

as they deploy and undeploy very quickly 

To detail a specific service :

> docker service ps *service-name* --no-trunc

If you want to access the logs of a specific container, you can get from
the command above the hosts names on which the different containers of a service
are running.
To get the logs of a specific container, log in the host where it's running
and run :
> docker ps 
> docker logs *container-id*

To inspect a service and see if the parameters you passed in the code to
it are effective :

> docker service inspect *service-name*

Remove a service :

> docker service rm *service-name*

Update (and restart) a service :

> docker service update *service-name* --parameters

A lot of parameters can be passed to this command, see the following 
link :

https://docs.docker.com/engine/reference/commandline/service_update/


