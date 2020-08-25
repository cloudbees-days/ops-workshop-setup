//only runs on CJOC


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
import org.apache.commons.io.FileUtils

String masterName = "REPLACE_GITHUB_USERNAME" 
String masterDefinitionYaml = """
bundle:
  jcasc:
    jenkins:
      systemMessage: 'Jenkins configured using CloudBees CI CasC - v1'
    unclassified:
      hibernationConfiguration:
        activities:
        - "build"
        - "web"
        enabled: true
        gracePeriod: 7200
      gitHubConfiguration:
        apiRateLimitChecker: ThrottleForNormalize
      gitHubPluginConfig:
        hookSecretConfigs:
        - credentialsId: "cloudbees-ci-workshop-github-webhook-secret"
      globallibraries:
        libraries:
        - defaultVersion: "master"
          name: "pipeline-library"
          retriever:
            modernSCM:
              scm:
                github:
                  credentialsId: "cloudbees-ci-workshop-github-app"
                  repoOwner: "REPLACE_GITHUB_ORG"
                  repository: "pipeline-library"
    credentials:
      system:
        domainCredentials:
        - credentials:
          - string:
              description: "Webhook secret for CloudBees CI Workshop GitHub App"
              id: "cloudbees-ci-workshop-github-webhook-secret"
              scope: SYSTEM
              secret: "\${gitHubWebhookSecret}"
          - gitHubApp:
              apiUri: "https://api.github.com"
              appID: "77562"
              description: "CloudBees CI Workshop GitHub App credential"
              id: "cloudbees-ci-workshop-github-app"
              owner: "REPLACE_GITHUB_ORG"
              privateKey: "\${gitHubAppPrivateKey}"
    cloudbees-slack-integration:
      config:
        slackToken: "\${slackToken}"
        slackWorkspace: "T010A455W77"
        users:
        - id: "REPLACE_JENKINS_USER"
          jenkins: "REPLACE_JENKINS_USER"
          optedIn: true
          scmId: "REPLACE_GITHUB_USERNAME"
          slack: "REPLACE_USER_EMAIL"
          slackWorkspace: "T010A455W77"
  pluginCatalog:
    configurations:
    - description: tier 3 plugins
      includePlugins:
        analysis-model-api: {version: 8.2.1}
        bootstrap4-api: {version: 4.5.0-2}
        checks-api: {version: 0.2.3}
        cloudbees-disk-usage-simple: {version: 0.9}
        cloudbees-msteams: {url: "https://storage.googleapis.com/core-workshop-plugins/cloudbees-msteams-0.2.hpi"} 
        data-tables-api: {version: 1.10.21-2}
        echarts-api: {version: 4.8.0-2}
        extended-read-permission: {version: 3.2}
        font-awesome-api: {version: 5.13.0-1}
        forensics-api: {version: 0.7.0}
        jquery3-api: {version: 3.5.1-1}
        plugin-util-api: {version: 1.2.2}  
        popper-api: {version: 1.16.0-6}
        prometheus: {version: 2.0.7}
        warnings-ng: {version: 8.4.1}  
    displayName: CloudBees CI Workshop Plugin Catalog
    name: cbci-workshop-catalog
    type: plugin-catalog
    version: "1"
  plugins:
  - id: analysis-model-api
  - id: antisamy-markup-formatter
  - id: bootstrap4-api
  - id: checks-api
  - id: cloudbees-disk-usage-simple
  - id: cloudbees-github-reporting
  - id: cloudbees-groovy-view
  - id: cloudbees-monitoring
  - id: cloudbees-msteams
  - id: cloudbees-nodes-plus
  - id: cloudbees-slack
  - id: cloudbees-template
  - id: cloudbees-view-creation-filter
  - id: cloudbees-workflow-template
  - id: cloudbees-workflow-ui
  - id: configuration-as-code
  - id: data-tables-api
  - id: echarts-api
  - id: extended-read-permission
  - id: font-awesome-api
  - id: forensics-api
  - id: git
  - id: github-branch-source
  - id: jacoco
  - id: jquery3-api
  - id: managed-master-hibernation
  - id: maven-plugin
  - id: operations-center-cloud
  - id: pipeline-event-step
  - id: pipeline-model-extensions
  - id: pipeline-stage-view
  - id: plugin-util-api
  - id: popper-api
  - id: prometheus
  - id: warnings-ng
  - id: wikitext
  - id: workflow-aggregator
  - id: workflow-cps-checkpoint
provisioning:
  cpus: 1.5
  disk: 10
  memory: 4000
  yaml: |
    kind: Service
    metadata:
      annotations:
        prometheus.io/scheme: 'http'
        prometheus.io/path: '/${masterName}/prometheus'
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
          - name: "smee-client"
            image: "deltaprojects/smee-client:latest"
            args: ["-t", "http://managed-master-hibernation-monitor.cloudbees-core.svc.cluster.local/hibernation/ns/\$(NAMESPACE)/queue/\$(CONTROLLER_SUBPATH)/github-webhook/", "--url", "https://smee.io/laoLXS9UiScsQtE"]
            env:
            - name: CONTROLLER_SUBPATH
              valueFrom:
                fieldRef:
                  fieldPath: metadata.labels['tenant']
            - name: NAMESPACE
              valueFrom:
                fieldRef:
                  fieldPath: metadata.namespace
          volumes:
          - name: "jcasc-secrets"
            csi:
              driver: secrets-store.csi.k8s.io
              readOnly: true
              volumeAttributes:
                secretProviderClass: "cbci-mc-secret-provider"
"""

def yamlMapper = Serialization.yamlMapper()
Map masterDefinition = yamlMapper.readValue(masterDefinitionYaml, Map.class);

println("Create/update of master '${masterName}' beginning.")

//Either update or create the mm with this config
if (OperationsCenter.getInstance().getConnectedMasters().any { it?.getName() == masterName }) {
    updateMM(masterName, masterDefinition)
} else {
    createMM(masterName, masterDefinition)
}
sleep(150)
println("Finished with master '${masterName}'.\n")


//
//
// only function definitions below here
//
//

private void createMM(String masterName, def masterDefinition) {
    println "Master '${masterName}' does not exist yet. Creating it now."

    def configuration = new KubernetesMasterProvisioning()
    masterDefinition.provisioning.each { k, v ->
        configuration["${k}"] = v
    }

  def teamsFolder = Jenkins.instance.getItem('teams')  
  String jenkinsUserId = "REPLACE_JENKINS_USER"
  ManagedMaster master = teamsFolder.createProject(ManagedMaster.class, masterName)
    master.setConfiguration(configuration)
    master.properties.replace(new ConnectedMasterLicenseServerProperty(null))
    master.save()
    master.onModified()

    createEntryInSecurityFile(masterName)
    createOrUpdateBundle(masterDefinition.bundle, masterName)
    setBundleSecurity(masterName, true)

    //ok, now we can actually boot this thing up
    println "Ensuring master '${masterName}' starts..."
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
    def Jenkins jenkins = Jenkins.getInstance()
    String roleName = "workshop-admin"
    String groupName = "Team Administrators";
    def groupItem = teamsFolder.getItem(masterName);
    def container = GroupContainerLocator.locate(groupItem);
    if(!container.getGroups().any{it.name=groupName}) {
      Group group = new Group(container, groupName);
      group.doAddMember(jenkinsUserId);
      group.doGrantRole(roleName, 0, Boolean.TRUE);
      container.addGroup(group);
    }
    sleep(500)
}

private void updateMM(String masterName, def masterDefinition) {
    println "Master '${masterName}' already exists. Updating it."

    ManagedMaster managedMaster = OperationsCenter.getInstance().getConnectedMasters().find { it.name == masterName } as ManagedMaster

    def currentConfiguration = managedMaster.configuration
    masterDefinition.provisioning.each { k, v ->
        if (currentConfiguration["${k}"] != v) {
            currentConfiguration["${k}"] = v
            println "Master '${masterName}' had provisioning configuration item '${k}' change. Updating it."
        }
    }

    createOrUpdateBundle(masterDefinition.bundle, masterName)
    setBundleSecurity(masterName, false)

    managedMaster.configuration = currentConfiguration
    managedMaster.save()

    println "Restarting master '${masterName}'."
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

private static void setBundleSecurity(String masterName, boolean regenerateBundleToken) {
    sleep(100)
    ExtensionList.lookupSingleton(BundleStorage.class).initialize()
    BundleStorage.AccessControl accessControl = ExtensionList.lookupSingleton(BundleStorage.class).getAccessControl()
    accessControl.updateMasterPath(masterName, "teams/" + masterName)
    if (regenerateBundleToken) {
        accessControl.regenerate(masterName)
    }
}

private static void createOrUpdateBundle(def bundleDefinition, String masterName) {
    String masterBundleDirPath = getMasterBundleDirPath(masterName)
    def masterBundleDirHandle = new File(masterBundleDirPath)

    File jenkinsYamlHandle = new File(masterBundleDirPath + "/jenkins.yaml")
    File pluginsYamlHandle = new File(masterBundleDirPath + "/plugins.yaml")
    File pluginCatalogYamlHandle = new File(masterBundleDirPath + "/plugin-catalog.yaml")
    File bundleYamlHandle = new File(masterBundleDirPath + "/bundle.yaml")

    int bundleVersion = getExistingBundleVersion(bundleYamlHandle) + 1

    if (masterBundleDirHandle.exists()) {
        FileUtils.forceDelete(masterBundleDirHandle)
    }
    FileUtils.forceMkdir(masterBundleDirHandle)

    def yamlMapper = Serialization.yamlMapper()
    def jcascYaml = yamlMapper.writeValueAsString(bundleDefinition.jcasc)?.replace("---", "")?.trim()
    def pluginsYaml = yamlMapper.writeValueAsString([plugins: bundleDefinition.plugins])?.replace("---", "")?.trim()
    def pluginCatalogYaml = yamlMapper.writeValueAsString(bundleDefinition.pluginCatalog)?.replace("---", "")?.trim()
    def bundleYaml = getBundleYamlContents(masterName, bundleVersion)

    if (jcascYaml == "null") { jcascYaml = "" }
    if (pluginsYaml == "null") { pluginsYaml = "" }
    if (pluginCatalogYaml == "null") { pluginCatalogYaml = "" }

    jenkinsYamlHandle.createNewFile()
    jenkinsYamlHandle.text = jcascYaml

    pluginsYamlHandle.createNewFile()
    pluginsYamlHandle.text = pluginsYaml

    pluginCatalogYamlHandle.createNewFile()
    pluginCatalogYamlHandle.text = pluginCatalogYaml

    bundleYamlHandle.createNewFile()
    bundleYamlHandle.text = bundleYaml
}

private static String getMasterBundleDirPath(String masterName) {
    return "/var/jenkins_home/jcasc-bundles-store/${masterName}"
}

private static void createEntryInSecurityFile(String masterName) {
    //create entry in security file; only the first time we create a bundle and never again. Hopefully this goes
    //away in future versions of CB CasC
    // !!NOTE!! The secret specified here is a stub. It is always regenerated to a proper, secure value. See setBundleSecurity()
    String newerEntry = """\n<entry>
      <string>${masterName}</string>
      <com.cloudbees.opscenter.server.casc.BundleStorage_-AccessControlEntry>
        <secret>{aGVyZWJlZHJhZ29ucwo=}</secret>
        <masterPath>${masterName}</masterPath>
      </com.cloudbees.opscenter.server.casc.BundleStorage_-AccessControlEntry>
    </entry>\n"""

    def cascSecFilePath = "/var/jenkins_home/core-casc-security.xml"
    def cascSecFile = new File(cascSecFilePath)
    String cascSecFileContents = cascSecFile.getText('UTF-8')

    if (cascSecFileContents.contains("<entries/>")) {
        cascSecFileContents = cascSecFileContents.replace("<entries/>", "<entries></entries>")
    } else {
        cascSecFileContents = cascSecFileContents.replace("<entries>", "<entries>${newerEntry}")
    }
    cascSecFile.write(cascSecFileContents)
}

private static String getBundleYamlContents(String masterName, int bundleVersion) {
    return """id: '${masterName}'
version: '${bundleVersion}'
apiVersion: '1'
description: 'Bundle for ${masterName}'
plugins:
- 'plugins.yaml'
jcasc:
- 'jenkins.yaml'
catalog:
- 'plugin-catalog.yaml'
"""
}

private static int getExistingBundleVersion(File bundleYamlFileHandle) {
    if(!bundleYamlFileHandle.exists()) {
        return 0
    }
    def versionLine = bundleYamlFileHandle.readLines().find { it.startsWith("version") }
    String version = versionLine.replace("version:", "").replace(" ", "").replace("'", "").replace('"', '')
    return version as int
}
