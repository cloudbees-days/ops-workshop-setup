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
  def controllerFolder = Jenkins.instance.getItem(controllerFolderName)
  if (controllerFolder == null) {
      logger.info("$controllerFolderName Folder does not exist so creating")
      controllerFolder = Jenkins.instance.createProject(Folder.class, controllerFolderName);
  }
} else {
   controllerFolderName = "teams"
}
logger.info("controllerFolderName is ${controllerFolderName}")

String controllerName = "REPLACE_CONTROLLER_NAME" 
String cascRegexPath = "${controllerFolderName}/${controllerName}"
String controllerDefinitionYaml = """
provisioning:
  cpus: 1
  disk: 20
  memory: 4000
  yaml: |
    kind: Service
    metadata:
      annotations:
        prometheus.io/scheme: 'http'
        prometheus.io/path: '/${controllerFolderName}-${controllerName}/prometheus'
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
Map controllerDefinition = yamlMapper.readValue(controllerDefinitionYaml, Map.class);

logger.info("Create/update of controller '${controllerName}' beginning with CasC RegEx: ${cascRegexPath}.")

//Either update or create the mm with this config
if (OperationsCenter.getInstance().getConnectedMasters().any { it?.getName() == controllerName }) {
  return
} else {
    
    createMM(controllerName, cascRegexPath, controllerFolderName, controllerDefinition)
}
sleep(2500)
logger.info("Finished with controller '${controllerName}' with CasC RegEx: ${cascRegexPath}.\n")


//
//
// only function definitions below here
//
//
private void createMM(String controllerName, String cascRegexPath, String controllerFolderName, def controllerDefinition) {
  Logger logger = Logger.getLogger("oc-create-update-managed-controller")
  logger.info "controller '${controllerName}' does not exist yet. Creating it now."

  def configuration = new KubernetesMasterProvisioning()
  controllerDefinition.provisioning.each { k, v ->
      configuration["${k}"] = v
  }
  
  setRegex("$controllerFolderName-$controllerName", cascRegexPath)
  
  def controllerFolder = Jenkins.instance.getItem(controllerFolderName) 
  ManagedMaster controller = controllerFolder.createProject(ManagedMaster.class, controllerName)
    controller.setConfiguration(configuration)
    controller.properties.replace(new ConnectedMasterLicenseServerProperty(null))
    //needed for CasC RBAC
    //controller.properties.replace(new com.cloudbees.opscenter.server.security.SecurityEnforcer.OptOutProperty(com.cloudbees.opscenter.server.sso.AuthorizationOptOutMode.INSTANCE, false, null))
  //set casc bundle, but not for CasC workshop
  controller.properties.replace(new ConnectedMasterTokenProperty(hudson.util.Secret.fromString(UUID.randomUUID().toString())))
  controller.properties.replace(new ConnectedMasterCascProperty("$controllerFolderName-$controllerName"))
  
  controller.save()
  controller.onModified()

  //ok, now we can actually boot this thing up
  logger.info "Ensuring controller '${controllerName}' starts..."
  def validActionSet = controller.getValidActionSet()
  if (validActionSet.contains(ManagedMaster.Action.ACKNOWLEDGE_ERROR)) {
      controller.acknowledgeErrorAction()
      sleep(50)
  }

  validActionSet = controller.getValidActionSet()
  if (validActionSet.contains(ManagedMaster.Action.START)) {
      controller.startAction();
      sleep(50)
  } else if (validActionSet.contains(ManagedMaster.Action.PROVISION_AND_START)) {
      controller.provisionAndStartAction();
      sleep(50)
  } else {
      throw "Cannot start the controller." as Throwable
  }
  
  //configure controller RBAC
  String roleName = "workshop-admin"
  String groupName = "Team Administrators";
  
  def folderGroupItem = Jenkins.instance.getItem(controllerFolderName);
  def folderContainer = GroupContainerLocator.locate(folderGroupItem);
  
  Group group = new Group(folderContainer, groupName);
  group.doAddMember("REPLACE_JENKINS_USER");
  group.doAddMember("REPLACE_JENKINS_USER-admin");
  group.doAddMember("team-admin");
  group.doGrantRole(roleName, 0, Boolean.TRUE);
  folderContainer.addGroup(group);
  folderContainer.addRoleFilter(roleName);
  folderContainer.addRoleFilter("browse");
  
  sleep(500)
}

private static void setRegex(String bundleName, String cascRegexPath) {
    sleep(100)
    ExtensionList.lookupSingleton(BundleStorage.class).initialize();
    BundleStorage.AccessControl accessControl = ExtensionList.lookupSingleton(BundleStorage.class).getAccessControl();
    accessControl.updateRegex(bundleName, cascRegexPath);
}
