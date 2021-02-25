import com.cloudbees.masterprovisioning.kubernetes.KubernetesMasterProvisioning
import com.cloudbees.opscenter.server.casc.BundleStorage
import com.cloudbees.opscenter.server.model.ManagedMaster
import com.cloudbees.opscenter.server.model.OperationsCenter
import com.cloudbees.opscenter.server.properties.ConnectedMasterLicenseServerProperty
import com.cloudbees.hudson.plugins.folder.Folder;
import nectar.plugins.rbac.groups.Group;
import nectar.plugins.rbac.groups.GroupContainerLocator;
import hudson.ExtensionList
import io.fabric8.kubernetes.client.utils.Serialization
import jenkins.model.Jenkins
import hudson.*
import hudson.model.*
import org.apache.commons.io.FileUtils
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.client.DefaultKubernetesClient

import java.util.logging.Logger

String scriptName = "10-create-ops-controller.groovy"
Logger logger = Logger.getLogger(scriptName)
logger.info("Starting ${scriptName}")

String masterName = "ops" 
String masterDefinitionYaml = """
provisioning:
  cpus: 2
  disk: 40
  memory: 6000
  yaml: |
    kind: Service
    metadata:
      annotations:
        prometheus.io/scheme: 'http'
        prometheus.io/path: '/operations-${masterName}/prometheus'
        prometheus.io/port: '8080'
        prometheus.io/scrape: 'true'
    kind: "StatefulSet"
    spec:
      template:
        spec:
          containers:
          - name: "jenkins"
            env:
            - name: "SECRETS"
              value: "/var/jenkins_home/jcasc_secrets"
            volumeMounts:
            - mountPath: "/var/jenkins_home/jcasc_secrets"
              name: "jcasc-secrets"
          volumes:
          - name: "jcasc-secrets"
            csi:
              driver: secrets-store.csi.k8s.io
              readOnly: true
              volumeAttributes:
                secretProviderClass: "cbci-ops-secret-provider"
"""

def yamlMapper = Serialization.yamlMapper()
Map masterDefinition = yamlMapper.readValue(masterDefinitionYaml, Map.class);

logger.info("Create/update of master '${masterName}' beginning with definition: ${masterDefinition}.")

//Either update or create the mm with this config
if (OperationsCenter.getInstance().getConnectedMasters().any { it?.getName() == masterName }) {
    //updateMM(masterName, masterDefinition)
  return
} else {
    createMM(masterName, masterDefinition)
}
sleep(2500)
logger.info("Finished with master '${masterName}'.\n")

//
//
// only function definitions below here
//
//

private void createMM(String masterName, def masterDefinition) {
  Logger logger = Logger.getLogger(masterName)  
  logger.info("Master '${masterName}' does not exist yet. Creating it now.")

  def configuration = new KubernetesMasterProvisioning()
  masterDefinition.provisioning.each { k, v ->
      configuration["${k}"] = v
  }
  Jenkins jenkins = Jenkins.getInstance()
  def opsFolder = jenkins.getItem("operations")
  jenkins.model.JenkinsLocationConfiguration.get().setUrl("https://staging-cbci.workshop.cb-sa.io/cjoc/")
  ManagedMaster master = opsFolder.createProject(ManagedMaster.class, masterName)
    master.setConfiguration(configuration)
    master.properties.replace(new ConnectedMasterLicenseServerProperty(null))
    //needed for CasC RBAC
    //master.properties.replace(new com.cloudbees.opscenter.server.security.SecurityEnforcer.OptOutProperty(com.cloudbees.opscenter.server.sso.AuthorizationOptOutMode.INSTANCE, false, null))
    master.save()
    master.onModified()
    
    String masterBundleDirPath = "/var/jenkins_home/jcasc-bundles-store/${masterName}"
    def masterBundleDirHandle = new File(masterBundleDirPath)

    File jenkinsYamlHandle = new File(masterBundleDirPath + "/jenkins.yaml")
    File pluginsYamlHandle = new File(masterBundleDirPath + "/plugins.yaml")
    File pluginCatalogYamlHandle = new File(masterBundleDirPath + "/plugin-catalog.yaml")
    File bundleYamlHandle = new File(masterBundleDirPath + "/bundle.yaml")
    if (!masterBundleDirHandle.exists()) {
        FileUtils.forceMkdir(masterBundleDirHandle)
    }

    def client = new DefaultKubernetesClient()
    ConfigMap configMap = client.configMaps().inNamespace("sda").withName("cbci-op-casc-bundle").get()

    jenkinsYamlHandle.createNewFile()
    jenkinsYamlHandle.text = configMap.getData()["jenkins.yaml"]

    pluginsYamlHandle.createNewFile()
    pluginsYamlHandle.text = configMap.getData()["plugins.yaml"]

    pluginCatalogYamlHandle.createNewFile()
    pluginCatalogYamlHandle.text = configMap.getData()["plugin-catalog.yaml"]

    bundleYamlHandle.createNewFile()
    bundleYamlHandle.text = configMap.getData()["bundle.yaml"]

    setBundleSecurity(masterName)

    //ok, now we can actually boot this thing up
    logger.info("Ensuring master '${masterName}' starts...")
    def validActionSet = master.getValidActionSet()
    if (validActionSet.contains(ManagedMaster.Action.ACKNOWLEDGE_ERROR)) {
        master.acknowledgeErrorAction()
        sleep(50)
    }

    validActionSet = master.getValidActionSet()
    if (validActionSet.contains(ManagedMaster.Action.START)) {
        master.startAction();
        sleep(50)
    } else if (validActionSet.contains(ManagedMaster.Action.PROVISION_AND_START)) {
        master.provisionAndStartAction();
        sleep(50)
    } else {
        throw "Cannot start the master." as Throwable
    }
    sleep(500)
}

private void updateMM(String masterName, def masterDefinition) {
    Logger logger = Logger.getLogger(masterName)
    logger.info("Master '${masterName}' already exists. Updating it.")

    ManagedMaster managedMaster = OperationsCenter.getInstance().getConnectedMasters().find { it.name == masterName } as ManagedMaster

    def currentConfiguration = managedMaster.configuration
    masterDefinition.provisioning.each { k, v ->
        if (currentConfiguration["${k}"] != v) {
            currentConfiguration["${k}"] = v
            logger.info("Master '${masterName}' had provisioning configuration item '${k}' change. Updating it.")
        }
    }

    setBundleSecurity(masterName, false)

    managedMaster.configuration = currentConfiguration
    managedMaster.save()

    logger.info("Restarting master '${masterName}'.")
    def validActionSet = managedMaster.getValidActionSet()
    if (validActionSet.contains(ManagedMaster.Action.ACKNOWLEDGE_ERROR)) {
        managedMaster.acknowledgeErrorAction()
        sleep(50)
    }

    validActionSet = managedMaster.getValidActionSet()
    if (validActionSet.contains(ManagedMaster.Action.RESTART)) {
        managedMaster.restartAction(false);
        sleep(50)
    } else if (validActionSet.contains(ManagedMaster.Action.START)) {
        managedMaster.startAction();
        sleep(50)
    } else if (validActionSet.contains(ManagedMaster.Action.PROVISION_AND_START)) {
        managedMaster.provisionAndStartAction();
        sleep(50)
    } else {
        throw "Cannot (re)start the master." as Throwable
    }
}

private static void setBundleSecurity(String masterName) {
    sleep(100)
    ExtensionList.lookupSingleton(BundleStorage.class).initialize()
    BundleStorage.AccessControl accessControl = ExtensionList.lookupSingleton(BundleStorage.class).getAccessControl()
    accessControl.updateMasterPath(masterName, "operations/" + masterName)
}
