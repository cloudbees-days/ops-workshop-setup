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
    //updateMM(masterName, masterDefinition)
  return
} else {
    createMM(masterName, masterDefinition)
}
sleep(2500)
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

