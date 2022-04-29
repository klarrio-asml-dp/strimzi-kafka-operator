Check: https://github.com/strimzi/strimzi-kafka-operator/blob/main/development-docs/DEV_GUIDE.md#build-and-deploy-from-source

Use java 11:
- update-java-alternatives --list
- sudo update-java-alternatives --set /path/to/java/version

Comment out Klarrio ~/.m2/settings.xml

```
export DOCKER_ORG=docker_hub_username (I used mathieutheerens)
export DOCKER_REGISTRY=docker_registry_name  #defaults to docker.io if unset
```

make MVN_ARGS='-DskipTests' all


If necessary change the path to images in `060-Deployment-strimzi-cluster-operator.yaml` to use a different artifactory than
docker.io (docker.io/mathieutheerens/...)



`cd docker-images/kafka-based`

Copy the jar from the custom principal builder repo (https://github.com/klarrio-asml-dp/spiffe-principal-builder) to:
- docker-images/kafka-based/kafka/tmp/3.0.0/libs
- docker-images/kafka-based/kafka/tmp/3.0.1/libs
- docker-images/kafka-based/kafka/tmp/3.1.0/libs

`make docker_build && make docker_push`