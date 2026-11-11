###What is in the image

An _empty_, _open_, _default_ broker with an acceptor on port 61616

 - by empty: has no addresses or queues but will auto create on demand
 - by open: has no security; authentication or authorization, users or roles
 - by default: has no configuration, it is dependent on the hard coded defaults of the embedded broker service

###How will the image behave

 1) the image will use or create `/app/data` for persistence of data

 2) the image will use any [.properties files](https://activemq.apache.org/components/artemis/documentation/latest/configuration-index.html#broker_properties) from `/app/etc` to augment broker configuration

 3) the image will use `/app/etc/broker.xml` if present, to bootstrap configuration, the 'bring your own config' use case

###Build and Use

First build an OCI image tar file from this artemis project using mvn. 
The image wraps a plain java application based on a jre image. You should note the default `fromImage` property in the pom.xml and potentially override using `-DfromImage=<image url>` with your choice, as it may be out of date.

To build the image, from this directory, use:

 `$> mvn compile jib:buildTar@now`

An OCI image is created as a tar file.

> *Note that any OCI compatible container runtime and registry can be used for the next steps, eg: docker, podman... I have used podman.*

To load the image tar into the local container registry, use:

 `$> podman image load --input target/jib-image.tar`

To run the image detached* and rootless with port 61616 exposed to localhost by podman, use:

 `$> podman run --name=artemis -dp 61616:61616 localhost/target/activemq-artemis-image:<version>`

The `:<version>` part of the image name is the maven ${project.version} from the pom.xml. You can use tab completion to have podman help you pick that exact container.

> **Note that you can later stop the detached container with: `$> podman stop artemis`*

Execute the artemis producer/consumer command line tools to interact with the broker.

 `$> ./bin/artemis producer --url tcp://localhost:61616`

 `$> ./bin/artemis consumer --url tcp://localhost:61616`

###Intent

The intent is that this image is useful as is. If one can trust users and is happy with configuration defaults, having no access control or limits can work fine.

If more control is necessary then this image can be configured by mounting an `/app/etc` directory with property files that augment default broker configuration.

This image could also be the base for a derived jib image, by simply adding more property files to the `src/main/jib/config` directory.

see examples/README.md for some more detail.

