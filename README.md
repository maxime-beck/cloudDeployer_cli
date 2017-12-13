# Tomcat-in-the-cloud Deployer Prototype

## Enable Kubernetes Engine API
1. Visit the [Kubernetes Engine page](https://console.cloud.google.com/projectselector/kubernetes) in the Google Cloud Platform Console.
2. Create or select a project.
3. Wait for the API and related services to be enabled. This can take several minutes.
4. [Enable billing](https://cloud.google.com/billing/docs/how-to/modify-project?visit_id=1-636474745958210931-1883118929&rd=1#enable-billing) for your project.

## Download and install Kubernetes CLI
Download the latest release with the command:

    $ curl -LO https://storage.googleapis.com/kubernetes-release/release/$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)/bin/linux/amd64/kubectl

Make the kubectl binary executable:

    $ chmod +x ./kubectl

Move the binary in to your PATH:

	$ sudo mv ./kubectl /usr/local/bin/kubectl



## Configure
Once you clone this repository, you'll have to configure a few things. First, clone and build _Tomcat-in-the-cloud_ :

    $ git clone https://github.com/web-servers/tomcat-in-the-cloud.git
    $ mvn install -f build.xml

then edit _src/main/java/com/prototype/maximebeck/Main.java_ and fill the constantes values with your coresponding data. Most of these constants are self-explanatory, for those which aren't, here is how you can find their values :

**KUBERNETES_HOST_IP**

That is the IP address on which Kubernetes is running (so is its REST API), you can find this information by running:

    $ kubectl cluster-info

And checking for the IP address of the _Kubernetes master_.

**DOCKER_AUTH_FILE**

That constant represent the path to a service account key JSON file. Using the Google Cloud Dashboard, you can get this file following this procedure :
Create a service account key (JSON) :

1. Go to https://console.cloud.google.com/apis/credentials/serviceaccountkey
2. Make sure the correct project is selected (top of the page) or create one
3. Select following options and click "Create" :
		Service account		Compute Engine default service account
		Key type					JSON
4. Place the downloaded JSON at the root of the project and fill this constant with its name

**ACCESS_TOKEN**

The access token will give the program the access to the REST API. To get yours, run this command:

    $ kubectl describe secret $(kubectl get secrets | grep default | cut -f1 -d ' ') | grep -E '^token' | cut -f2 -d':' | tr -d '\t'

## Build and run
To build the project run the following command at the root of the project:

    $ mvn install

Then run the following the run it:

    $ java -jar target/googleCloudDeployer-1.0-SNAPSHOT-jar-with-dependencies.jar

## References
- Install and Set Up kubectl                        
https://kubernetes.io/docs/tasks/tools/install-kubectl/
