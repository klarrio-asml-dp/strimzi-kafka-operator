/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.systemtest.templates.crd;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.api.kafka.Crds;
import io.strimzi.api.kafka.KafkaConnectList;
import io.strimzi.api.kafka.model.CertSecretSourceBuilder;
import io.strimzi.api.kafka.model.KafkaConnect;
import io.strimzi.api.kafka.model.KafkaConnectBuilder;
import io.strimzi.api.kafka.model.KafkaConnectResources;
import io.strimzi.api.kafka.model.KafkaResources;
import io.strimzi.systemtest.Constants;
import io.strimzi.systemtest.Environment;
import io.strimzi.systemtest.resources.ResourceManager;
import io.strimzi.systemtest.resources.kubernetes.NetworkPolicyResource;
import io.strimzi.systemtest.templates.specific.ScraperTemplates;
import io.strimzi.systemtest.utils.StUtils;
import io.strimzi.test.TestUtils;
import io.strimzi.test.k8s.KubeClusterResource;
import org.junit.jupiter.api.extension.ExtensionContext;

import static io.strimzi.systemtest.resources.ResourceManager.kubeClient;

public class KafkaConnectTemplates {

    private KafkaConnectTemplates() {}

    public static MixedOperation<KafkaConnect, KafkaConnectList, Resource<KafkaConnect>> kafkaConnectClient() {
        return Crds.kafkaConnectOperation(ResourceManager.kubeClient().getClient());
    }

    public static KafkaConnectBuilder kafkaConnect(ExtensionContext extensionContext, String name, final String namespaceName, String clusterName, int kafkaConnectReplicas, boolean allowNP) {
        KafkaConnect kafkaConnect = getKafkaConnectFromYaml(Constants.PATH_TO_KAFKA_CONNECT_CONFIG);
        kafkaConnect = defaultKafkaConnect(kafkaConnect, namespaceName, name, clusterName, kafkaConnectReplicas).build();
        return allowNP ? deployKafkaConnectWithNetworkPolicy(extensionContext, kafkaConnect) : new KafkaConnectBuilder(kafkaConnect);
    }

    public static KafkaConnectBuilder kafkaConnect(ExtensionContext extensionContext, String name, int kafkaConnectReplicas) {
        return kafkaConnect(extensionContext, name, ResourceManager.kubeClient().getNamespace(), name, kafkaConnectReplicas, true);
    }

    public static KafkaConnectBuilder kafkaConnect(ExtensionContext extensionContext, String name, String clusterName, int kafkaConnectReplicas) {
        return kafkaConnect(extensionContext, name, ResourceManager.kubeClient().getNamespace(), clusterName, kafkaConnectReplicas, true);
    }

    public static KafkaConnectBuilder kafkaConnect(ExtensionContext extensionContext, String name, final String namespaceName, String clusterName, int kafkaConnectReplicas) {
        return kafkaConnect(extensionContext, name, namespaceName, clusterName, kafkaConnectReplicas, true);
    }

    public static KafkaConnectBuilder kafkaConnect(ExtensionContext extensionContext, String name, int kafkaConnectReplicas, boolean allowNP) {
        return kafkaConnect(extensionContext, name, ResourceManager.kubeClient().getNamespace(), name, kafkaConnectReplicas, allowNP);
    }

    public static KafkaConnectBuilder kafkaConnectWithMetrics(String name, int kafkaConnectReplicas) {
        return kafkaConnectWithMetrics(name, name, kafkaConnectReplicas);
    }

    public static KafkaConnectBuilder kafkaConnectWithMetrics(String name, String clusterName, int kafkaConnectReplicas) {
        KafkaConnect kafkaConnect = getKafkaConnectFromYaml(Constants.PATH_TO_KAFKA_CONNECT_METRICS_CONFIG);
        ConfigMap metricsCm = TestUtils.configMapFromYaml(Constants.PATH_TO_KAFKA_CONNECT_METRICS_CONFIG, "connect-metrics");
        KubeClusterResource.kubeClient().getClient().configMaps().inNamespace(kubeClient().getNamespace()).createOrReplace(metricsCm);
        return defaultKafkaConnect(kafkaConnect, ResourceManager.kubeClient().getNamespace(), name, clusterName, kafkaConnectReplicas);
    }

    public static KafkaConnectBuilder defaultKafkaConnect(String name, String kafkaClusterName, int kafkaConnectReplicas) {
        KafkaConnect kafkaConnect = getKafkaConnectFromYaml(Constants.PATH_TO_KAFKA_CONNECT_CONFIG);
        return defaultKafkaConnect(kafkaConnect, ResourceManager.kubeClient().getNamespace(), name, kafkaClusterName, kafkaConnectReplicas);
    }

    private static KafkaConnectBuilder defaultKafkaConnect(KafkaConnect kafkaConnect, final String namespaceName, String name, String kafkaClusterName, int kafkaConnectReplicas) {
        return new KafkaConnectBuilder(kafkaConnect)
            .withNewMetadata()
                .withName(name)
                .withNamespace(namespaceName)
                .withClusterName(kafkaClusterName)
            .endMetadata()
            .editOrNewSpec()
                .withVersion(Environment.ST_KAFKA_VERSION)
                .withBootstrapServers(KafkaResources.tlsBootstrapAddress(kafkaClusterName))
                .withReplicas(kafkaConnectReplicas)
                .withNewTls()
                    .withTrustedCertificates(new CertSecretSourceBuilder().withSecretName(kafkaClusterName + "-cluster-ca-cert").withCertificate("ca.crt").build())
                .endTls()
                .addToConfig("group.id", KafkaConnectResources.deploymentName(name))
                .addToConfig("offset.storage.topic", KafkaConnectResources.configStorageTopicOffsets(name))
                .addToConfig("config.storage.topic", KafkaConnectResources.metricsAndLogConfigMapName(name))
                .addToConfig("status.storage.topic", KafkaConnectResources.configStorageTopicStatus(name))
                .withNewInlineLogging()
                    .addToLoggers("connect.root.logger.level", "DEBUG")
                .endInlineLogging()
            .endSpec();
    }

    private static KafkaConnectBuilder deployKafkaConnectWithNetworkPolicy(ExtensionContext extensionContext, KafkaConnect kafkaConnect) {
        if (Environment.DEFAULT_TO_DENY_NETWORK_POLICIES) {
            final String namespaceName = StUtils.isParallelNamespaceTest(extensionContext) && !Environment.isNamespaceRbacScope() ?
                // if parallel namespace test use namespace from store and if RBAC is enable we don't run tests in parallel mode and with that said we don't create another namespaces
                extensionContext.getStore(ExtensionContext.Namespace.GLOBAL).get(Constants.NAMESPACE_KEY).toString() :
                // otherwise use resource namespace
                kafkaConnect.getMetadata().getNamespace();

            final String scraperName = extensionContext.getStore(ExtensionContext.Namespace.GLOBAL).get(Constants.SCRAPER_KEY) != null ?
                extensionContext.getStore(ExtensionContext.Namespace.GLOBAL).get(Constants.SCRAPER_KEY).toString() :
                kafkaConnect.getMetadata().getName() + "-scraper";

            ResourceManager.getInstance().createResource(extensionContext, ScraperTemplates.scraperPod(namespaceName, scraperName).build());
            NetworkPolicyResource.allowNetworkPolicySettingsForResource(extensionContext, kafkaConnect, KafkaConnectResources.deploymentName(kafkaConnect.getMetadata().getName()));
        }
        return new KafkaConnectBuilder(kafkaConnect);
    }

    public static KafkaConnectBuilder kafkaConnectWithoutWait(KafkaConnect kafkaConnect) {
        kafkaConnectClient().inNamespace(ResourceManager.kubeClient().getNamespace()).createOrReplace(kafkaConnect);
        return new KafkaConnectBuilder(kafkaConnect);
    }

    public static void allowNetworkPolicyForKafkaConnect(ExtensionContext extensionContext, KafkaConnect kafkaConnect) {
        if (Environment.DEFAULT_TO_DENY_NETWORK_POLICIES) {
            NetworkPolicyResource.allowNetworkPolicySettingsForResource(extensionContext, kafkaConnect, KafkaConnectResources.deploymentName(kafkaConnect.getMetadata().getName()));
        }
    }

    public static void deleteKafkaConnectWithoutWait(String resourceName) {
        kafkaConnectClient().inNamespace(ResourceManager.kubeClient().getNamespace()).withName(resourceName).withPropagationPolicy(DeletionPropagation.FOREGROUND).delete();
    }

    private static KafkaConnect getKafkaConnectFromYaml(String yamlPath) {
        return TestUtils.configFromYaml(yamlPath, KafkaConnect.class);
    }
}
