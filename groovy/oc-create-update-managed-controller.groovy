//only runs on CJOC
import jenkins.security.ApiTokenProperty
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
import com.cloudbees.hudson.plugins.folder.*;
import com.cloudbees.hudson.plugins.folder.properties.*;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider.FolderCredentialsProperty;
import com.cloudbees.plugins.credentials.impl.*;
import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.domains.*; 
import com.cloudbees.opscenter.server.casc.config.ConnectedMasterCascProperty;
import com.cloudbees.opscenter.server.casc.config.ConnectedMasterTokenProperty;

import java.util.logging.Logger

Logger logger = Logger.getLogger("oc-create-update-managed-controller.groovy")
String jenkinsUserId = "REPLACE_JENKINS_USER"

def user = User.get(jenkinsUserId, false)
if(user==null) {
  Jenkins.instance.securityRealm.createAccount(jenkinsUserId, "REPLACE_WORKSHOP_ATTENDEES_PASSWORD")
}

String adminUserId = "REPLACE_JENKINS_USER-admin"
def adminUser = User.get(adminUserId, false)
if(adminUser==null) {
  Jenkins.instance.securityRealm.createAccount(adminUserId, "REPLACE_WORKSHOP_ATTENDEES_PASSWORD")
}

String controllerFolderName = "REPLACE_FOLDER_NAME"
if(!controllerFolderName.startsWith("REPLACE_FOLDER")) {
  //do nothing - use the replaced value
} else {
   controllerFolderName = "teams"
}
println("controllerFolderName is ${controllerFolderName}")

String masterName = "REPLACE_CONTROLLER_NAME" 
String cascRegexPath = "${controllerFolderName}/${masterName}"
String masterDefinitionYaml = """
provisioning:
  cpus: 1
  disk: 20
  memory: 4000
  yaml: |
    kind: Service
    metadata:
      annotations:
        prometheus.io/scheme: 'http'
        prometheus.io/path: '/${controllerFolderName}-${masterName}/prometheus'
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
            - name: "jcasc-secrets"
              mountPath: "/var/jenkins_home/jcasc_secrets"
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

println("Create/update of master '${masterName}' beginning with CasC RegEx: ${cascRegexPath}.")

//Either update or create the mm with this config
if (OperationsCenter.getInstance().getConnectedMasters().any { it?.getName() == masterName }) {
    //updateMM(masterName, cascRegexPath, controllerFolderName, masterDefinition)
  return
} else {
    
    createMM(masterName, cascRegexPath, controllerFolderName, masterDefinition)
}
sleep(2500)
println("Finished with master '${masterName}' with CasC RegEx: ${cascRegexPath}.\n")


//
//
// only function definitions below here
//
//
private void createMM(String masterName, String cascRegexPath, String controllerFolderName, def masterDefinition) {
  Logger logger = Logger.getLogger("oc-create-update-managed-controller")
  println "Master '${masterName}' does not exist yet. Creating it now."
  String workshopId = "REPLACE_WORKSHOP_ID"

  def configuration = new KubernetesMasterProvisioning()
  masterDefinition.provisioning.each { k, v ->
      configuration["${k}"] = v
  }
  if(!workshopId.equals("cloudbees-ci-casc-workshop")) {
    setRegex(masterName, cascRegexPath)
  }
  
  def controllerFolder = Jenkins.instance.getItem(controllerFolderName) 
  ManagedMaster master = controllerFolder.createProject(ManagedMaster.class, masterName)
    master.setConfiguration(configuration)
    master.properties.replace(new ConnectedMasterLicenseServerProperty(null))
    //needed for CasC RBAC
    //master.properties.replace(new com.cloudbees.opscenter.server.security.SecurityEnforcer.OptOutProperty(com.cloudbees.opscenter.server.sso.AuthorizationOptOutMode.INSTANCE, false, null))
  //set casc bundle, but not for CasC workshop
  if(!workshopId.equals("cloudbees-ci-casc-workshop")) {
    master.properties.replace(new ConnectedMasterTokenProperty(hudson.util.Secret.fromString(UUID.randomUUID().toString())))
    master.properties.replace(new ConnectedMasterCascProperty(masterName))
  }
  master.save()
  master.onModified()

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
  //configure controller RBAC
  def Jenkins jenkins = Jenkins.getInstance()
  String roleName = "workshop-admin"
  String groupName = "Team Administrators";
  def groupItem = controllerFolder.getItem(masterName);
  def container = GroupContainerLocator.locate(groupItem);
  if(!container.getGroups().any{it.name=groupName}) {
    Group group = new Group(container, groupName);
    group.doAddMember("REPLACE_JENKINS_USER");
    group.doAddMember("REPLACE_JENKINS_USER-admin");
    group.doAddMember("team-admin");
    group.doGrantRole(roleName, 0, Boolean.TRUE);
    container.addGroup(group);
    container.addRoleFilter(roleName);
    container.addRoleFilter("browse");
  }
  sleep(500)
}

private void updateMM(String masterName, String cascRegexPath, String controllerFolderName, def masterDefinition) {
    println "Master '${masterName}' already exists. Updating it."

    ManagedMaster managedMaster = OperationsCenter.getInstance().getConnectedMasters().find { it.name == masterName } as ManagedMaster

    def currentConfiguration = managedMaster.configuration
    masterDefinition.provisioning.each { k, v ->
        if (currentConfiguration["${k}"] != v) {
            currentConfiguration["${k}"] = v
            println "Master '${masterName}' had provisioning configuration item '${k}' change. Updating it."
        }
    }
  
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

private static void setRegex(String masterName, String cascRegexPath) {
    sleep(100)
    ExtensionList.lookupSingleton(BundleStorage.class).initialize();
    BundleStorage.AccessControl accessControl = ExtensionList.lookupSingleton(BundleStorage.class).getAccessControl();
    accessControl.updateRegex(masterName, cascRegexPath);
}

