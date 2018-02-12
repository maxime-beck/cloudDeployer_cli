# Tomcat-in-the-cloud Deployer Prototype
The purpose of this application is to provide an easy and automated way to deploy *tomcat-in-the-cloud* to most major cloud provider services. Currently, the application supports Google Cloud Platform, Amazon Web Services, and Openshift.

## Pre-configuration
### Openshift
If you're looking to deploy on Openshift, you may have a Docker registry that requires authentication. In order for the nodes to pull images on your behalf, they have to have the credentials. You can provide this information by creating a dockercfg secret as shown below :

    $ kubectl create secret docker-registry myregistrykey --docker-server=<docker_registry_address> --docker-username=$(oc whoami) --docker-password=$(oc whoami -t) --docker-email=<email>

and attaching it to your service account (here default) :

    $ kubectl patch serviceaccount default -p '{"imagePullSecrets": [{"name": "myregistrykey"}]}'

Finally, you need to authorize the pods to see their peers for the clustering to work :

    $ oc policy add-role-to-user view system:serviceaccount:$(oc project -q):default -n $(oc project -q)

## Configure
Once you clone this repository, you'll have to configure a few things. First, build _tomcat-in-the-cloud_. To do this, you can run the following script  :

    // Build CloudStreamProvider
    git clone https://github.com/maxime-beck/cloudStreamProvider.git
    mvn -f cloudStreamProvider/pom.xml install

    // Build CloudMemberProvider
    git clone https://github.com/maxime-beck/cloudMemberProvider.git
    mvn -f cloudMemberProvider/pom.xml install

    // Build Tomcat-in-the-cloud
    git clone https://github.com/web-servers/tomcat-in-the-cloud.git
    mvn -f tomcat-in-the-cloud/pom.xml install

then edit _config/cloud.properties_ and fill in the properties with their corresponding values. Here is a description of these properties :

| Property name               | Mandatory for                       | Description                                                                                              |
|-----------------------------|-------------------------------------|----------------------------------------------------------------------------------------------------------|
| PROVIDER                    | Openshift, AWS, Google Cloud Platform | Cloud provider on which to deploy                                                                        |
| PROVIDER_DOMAIN             | Openshift, AWS, Google Cloud Platform | The domain of the cloud provider                                                                         |
| DOCKER_REGISTRY_NAME        | Openshift                           | Name of the docker-registry                                                                              |
| HOST_ADDRESS                | Openshift, AWS, Google Cloud Platform | The full address (address:port) of the cloud provider                                                    |
| PROJECT_ID                  | Openshift, AWS, Google Cloud Platform | Identifier of the project. On some cloud providers, this is also referred as "Registry identifier"       |
| TOMCAT_IN_THE_CLOUD_BASEDIR | Openshift, AWS, Google Cloud Platform | The base directory of the builded sources of Tomcat-in-the-cloud                                         |
| REPOSITORY_NAME             | AWS                                 | Name of the repository on which to deploy                                                                |
| DOCKER_AUTH_FILE            | Openshift, AWS, Google Cloud Platform | Docker-registry authentification file. This must be provided if no docker username and password are used |
| DOCKER_USERNAME             | Openshift, AWS, Google Cloud Platform | Docker-registry username. Must be provided if no docker authentification file is used                    |
| DOCKER_PASSWORD             | Openshift, AWS, Google Cloud Platform | Docker-registry password. Must be provided if no docker authentification file is used                    |
| DOCKER_IMAGE_NAME           | Openshift, AWS, Google Cloud Platform | Name of the docker image that will be built                                                              |
| DOCKER_IMAGE_VERSION        | Openshift, AWS, Google Cloud Platform | Version of the docker image that will be built                                                           |
| DEPLOYMENT_NAME             | Openshift, AWS, Google Cloud Platform | Name you would like to attribute to the deployment                                                       |
| DEPLOYMENT_PORT             | Openshift, AWS, Google Cloud Platform | Port on which you would like to deploy                                                                   |
| EXPOSED_PORT                | Openshift, AWS, Google Cloud Platform | Port that you would like to expose your application to                                                   |
| REPLICAS                    | Openshift, AWS, Google Cloud Platform | Number of replicas                                                                                       |
| ACCESS_TOKEN                | Openshift, AWS, Google Cloud Platform | Token to access the REST API of your cloud provider                                                      |

## Configuration samples
Here are some configuration samples to deploy _tomcat-in-the-cloud_ on the supported cloud providers.

**Google Cloud Platform**

  	PROVIDER=GCLOUD
  	PROVIDER_DOMAIN=gcr.io
  	#DOCKER_REGISTRY_NAME=
  	HOST_ADDRESS=35.195.68.233
  	#REGISTRY_ID=
  	REPOSITORY_NAME=test-app
  	TOMCAT_IN_THE_CLOUD_BASEDIR=tomcat-in-the-cloud

  	DOCKER_AUTH_FILE=test-app-8947ffac8c02.json
  	#DOCKER_USERNAME=
  	#DOCKER_PASSWORD=

  	DOCKER_IMAGE_NAME = "tomcat-in-the-cloud";
  	DOCKER_IMAGE_VERSION = "latest";

  	DEPLOYMENT_NAME=tomcat-in-the-cloud
  	DEPLOYMENT_PORT=8080
  	EXPOSED_PORT=80
  	REPLICAS=3
  	ACCESS_TOKEN=<user token>

**AWS**

  	PROVIDER=AWS
  	PROVIDER_DOMAIN=dkr.ecr.eu-central-1.amazonaws.com
  	#DOCKER_REGISTRY_NAME=
  	HOST_ADDRESS=api-tomcat-bucket-k8s-loc-onige1-1997602364.eu-central-1.elb.amazonaws.com
  	REGISTRY_ID=794491693827
  	REPOSITORY_NAME=test-app-repo
  	TOMCAT_IN_THE_CLOUD_BASEDIR=tomcat-in-the-cloud

  	#DOCKER_AUTH_FILE=
  	DOCKER_USERNAME=<username>
  	DOCKER_PASSWORD=<user token>

  	DOCKER_IMAGE_NAME = "tomcat-in-the-cloud";
  	DOCKER_IMAGE_VERSION = "latest";

  	DEPLOYMENT_NAME=tomcat-in-the-cloud
  	DEPLOYMENT_PORT=8080
  	EXPOSED_PORT=80
  	REPLICAS=3
  	ACCESS_TOKEN=<user token>

**Openshift**

  	PROVIDER=OPENSHIFT
  	PROVIDER_DOMAIN=10.33.144.141.xip.io
  	DOCKER_REGISTRY_NAME=docker-registry-default
  	HOST_ADDRESS=10.33.144.141:8443
  	REGISTRY_ID=test-app
  	#REPOSITORY_NAME=
  	TOMCAT_IN_THE_CLOUD_BASEDIR=tomcat-in-the-cloud

  	#DOCKER_AUTH_FILE=
  	DOCKER_USERNAME=<username>
  	DOCKER_PASSWORD=<user token>

  	DOCKER_IMAGE_NAME = "tomcat-in-the-cloud";
  	DOCKER_IMAGE_VERSION = "latest";

  	DEPLOYMENT_NAME=tomcat-in-the-cloud
  	DEPLOYMENT_PORT=8080
  	EXPOSED_PORT=80
  	REPLICAS=3
  	ACCESS_TOKEN=<user token>

## Build and run
To build the project run the following command at the root of the project:

    $ mvn install

Then run the following the run it:

    $ java -jar target/googleCloudDeployer-1.0-SNAPSHOT-jar-with-dependencies.jar

## References
- Install and Set Up kubectl                        
https://kubernetes.io/docs/tasks/tools/install-kubectl/
