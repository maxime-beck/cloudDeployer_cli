# Tomcat-in-the-cloud Deployer
The purpose of this application is to provide an easy and automated way to deploy *tomcat-in-the-cloud* to most major cloud provider services. Currently, the application supports Google Cloud Platform, Amazon Web Services, and Openshift.

## Pre-configuration
### Openshift
If you're looking to deploy on Openshift, you may have a Docker registry that requires authentication. In order for the nodes to pull images on your behalf, they have to have the credentials. You can provide this information by creating a dockercfg secret as shown below :

    $ kubectl create secret docker-registry myregistrykey --docker-server=<docker_registry_address> --docker-username=$(oc whoami) --docker-password=$(oc whoami -t) --docker-email=<email>

> You may want to run `oc get routes --all-namespaces` with a user that is part of the cluster-admin role to discover your docker registry address.

Then, attaching it to your service account (here default) :

    $ kubectl patch serviceaccount default -p '{"imagePullSecrets": [{"name": "myregistrykey"}]}'

Finally, you need to authorize the pods to see their peers for the clustering to work :

    $ oc policy add-role-to-user view system:serviceaccount:$(oc project -q):default -n $(oc project -q)

## Configure
Once you clone this repository, you'll have to configure a few things. First, build _tomcat-in-the-cloud_. To do this, you can run the following script  :

    // Build CloudStreamProvider
    git clone https://github.com/maxime-beck/cloudStreamProvider.git && \
    mvn -f cloudStreamProvider/pom.xml install && \

    // Build CloudMemberProvider
    git clone https://github.com/maxime-beck/cloudMemberProvider.git && \
    mvn -f cloudMemberProvider/pom.xml install && \

    // Build Tomcat-in-the-cloud
    git clone https://github.com/web-servers/tomcat-in-the-cloud.git && \
    mvn -f tomcat-in-the-cloud/pom.xml install

then edit _config/cloud.properties_ and fill in the properties with their corresponding values. Here is a description of these properties :

| Property                    	| OpenShift 	| Google Cloud 	| AWS 	| Description                                                                                                                             	|
|-----------------------------	|-----------	|--------------	|-----	|-----------------------------------------------------------------------------------------------------------------------------------------	|
| PROVIDER                    	|     X     	|       X      	|  X  	| Cloud provider on which to deploy                                                                                                       	|
| MODE                        	|     X     	|       X      	|  X  	| Either MONOLITHIC or MICROSERVICE (see mode section)                                                                                    	|
| REGISTRY_ADDRESS            	|     X     	|              	|     	| Address of the docker registry                                                                                                          	|
| HOST_ADDRESS                	|     X     	|       X      	|  X  	| Address of the cloud provider's server                                                                                                  	|
| REGISTRY_ID                 	|     X     	|       X      	|     	| Registry identifier. On some cloud providers, this is also referred as "Project identifier"                                             	|
| TOMCAT_IN_THE_CLOUD_BASEDIR 	|     X     	|       X      	|  X  	| The base directory of the builded sources of Tomcat-in-the-cloud                                                                        	|
| WAR                         	|    (X)    	|      (X)     	| (X) 	| Path to the war file to deploy. Note that the path must be relative to TOMCAT_IN_THE_CLOUD_BASEDIR. Only required on MICROSERVICE mode. 	|
| REPOSITORY_NAME             	|           	|              	|     	| Name of the repository on which to deploy                                                                                               	|
| DOCKER_AUTH_FILE            	|    (X)    	|      (X)     	| (X) 	| Docker registry authentification file. This must be provided if no docker username and password are used                                	|
| DOCKER_USERNAME             	|    (X)    	|      (X)     	| (X) 	| Docker registry username. Must be provided if no docker authentification file is used                                                   	|
| DOCKER_PASSWORD             	|    (X)    	|      (X)     	| (X) 	| Docker registry password (Token). Must be provided if no docker authentification file is used                                           	|
| DOCKER_IMAGE_NAME           	|     X     	|       X      	|  X  	| Name of the docker image that will be built                                                                                             	|
| DOCKER_IMAGE_VERSION        	|     X     	|       X      	|  X  	| Version of the docker image that will be built                                                                                          	|
| DEPLOYMENT_NAME             	|     X     	|       X      	|  X  	| Name you would like to attribute to the deployment                                                                                      	|
| DEPLOYMENT_PORT             	|     X     	|       X      	|  X  	| Port on which to deploy                                                                                                                 	|
| EXPOSED_PORT                	|     X     	|       X      	|  X  	| Port on which to expose the running application                                                                                         	|
| REPLICAS                    	|     X     	|       X      	|  X  	| Number of replicas at start     

## Configuration samples
Here are some configuration samples to deploy _tomcat-in-the-cloud_ on the supported cloud providers.

**Google Cloud Platform**

  	PROVIDER=GCLOUD
    MODE=MICROSERVICE
    #REGISTRY_ADDRESS=
  	HOST_ADDRESS=35.195.68.233
  	#REGISTRY_ID=
    TOMCAT_IN_THE_CLOUD_BASEDIR=tomcat-in-the-cloud
    WAR=test_replication.war
  	REPOSITORY_NAME=test-app

  	DOCKER_AUTH_FILE=<json file>
  	#DOCKER_USERNAME=
  	#DOCKER_PASSWORD=

  	DOCKER_IMAGE_NAME=tomcat-in-the-cloud
  	DOCKER_IMAGE_VERSION=latest

  	DEPLOYMENT_NAME=tomcat-in-the-cloud
  	DEPLOYMENT_PORT=8080
  	EXPOSED_PORT=80
  	REPLICAS=3

**AWS**

  	PROVIDER=AWS
    MODE=MICROSERVICE
    #REGISTRY_ADDRESS=
  	HOST_ADDRESS=api-tomcat-bucket-k8s-loc-onige1-1997602364.eu-central-1.elb.amazonaws.com
  	REGISTRY_ID=794491693827
    TOMCAT_IN_THE_CLOUD_BASEDIR=tomcat-in-the-cloud
    WAR=test_replication.war
  	REPOSITORY_NAME=test-app-repo

  	#DOCKER_AUTH_FILE=
  	DOCKER_USERNAME=<username>
  	DOCKER_PASSWORD=<user token>

  	DOCKER_IMAGE_NAME=tomcat-in-the-cloud
  	DOCKER_IMAGE_VERSION=latest

  	DEPLOYMENT_NAME=tomcat-in-the-cloud
  	DEPLOYMENT_PORT=8080
  	EXPOSED_PORT=80
  	REPLICAS=3

**Openshift**

    PROVIDER=OPENSHIFT
    MODE=MICROSERVICE
    REGISTRY_ADDRESS=registry.starter-us-east-1.openshift.com
    HOST_ADDRESS=console.starter-us-east-1.openshift.com
    REGISTRY_ID=test-app
    TOMCAT_IN_THE_CLOUD_BASEDIR=tomcat-in-the-cloud
    WAR=test_replication.war
    #REPOSITORY_NAME=""

    #DOCKER_AUTH_FILE=
    DOCKER_USERNAME=<username>
    DOCKER_TOKEN=<token>

    DOCKER_IMAGE_NAME=tomcat-in-the-cloud
    DOCKER_IMAGE_VERSION=latest

    DEPLOYMENT_NAME=tomcat-in-the-cloud
    DEPLOYMENT_PORT=8080
    EXPOSED_PORT=80
    REPLICAS=3

## Build and run
To build the project run the following command at the root of the project:

    $ mvn install

Then run the following the run it:

    $ java -jar target/googleCloudDeployer-1.0-SNAPSHOT-jar-with-dependencies.jar

## References
- Install and Set Up kubectl                        
https://kubernetes.io/docs/tasks/tools/install-kubectl/
