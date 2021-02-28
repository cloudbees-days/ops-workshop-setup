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

import java.util.logging.Logger
 
String jenkinsUserId = "REPLACE_JENKINS_USER"
def user = User.get(jenkinsUserId, false)
if(user==null) {
  Jenkins.instance.securityRealm.createAccount(jenkinsUserId, "cb2021")
}
String adminUserId = jenkinsUserId + "-admin"
def adminUser = User.get(adminUserId, false)
if(adminUser==null) {
  Jenkins.instance.securityRealm.createAccount(adminUserId, "cb2021-admin")
}

String masterName = "REPLACE_CONTROLLER_NAME" 
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
        prometheus.io/path: '/teams-${masterName}/prometheus'
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
            - name: REPLACE_CONTROLLER_NAME-init-groovy
              mountPath: /var/jenkins_config/configure-jenkins.groovy.d/
              readOnly: true
          volumes:
          - name: "jcasc-secrets"
            csi:
              driver: secrets-store.csi.k8s.io
              readOnly: true
              volumeAttributes:
                secretProviderClass: "cbci-mc-secret-provider"
          - name: REPLACE_CONTROLLER_NAME-init-groovy
            configMap:
              name: REPLACE_CONTROLLER_NAME-init-groovy
              defaultMode: 420
    ---
    kind: ConfigMap
    metadata:
      name: "REPLACE_CONTROLLER_NAME-init-groovy"
      namespace: sda
    data:
      z-team-admin-api-token.groovy: |
        import jenkins.model.Jenkins
        import jenkins.security.ApiTokenProperty
        import hudson.model.User
        import com.cloudbees.plugins.credentials.domains.Domain
        import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
        import com.cloudbees.plugins.credentials.CredentialsScope
        import java.util.logging.Logger

        Logger logger = Logger.getLogger("03-team-admin-api-token.groovy")
        
        def adminUserId = 'REPLACE_JENKINS_USER-admin'
        def jenkinsTokenName = 'team-admin-api-token'
        def adminUser = User.get(adminUserId, false)
        while(adminUser==null) {
          adminUser = User.get(adminUserId, false)
        }
        def apiTokenProperty = adminUser.getProperty(ApiTokenProperty.class)
        def tokens = apiTokenProperty.tokenStore.getTokenListSortedByName().findAll {it.name==jenkinsTokenName}

        if(tokens.size() != 0) {
            logger.info("Token exists. Revoking any with this name and recreating to ensure we have a valid value stored in the secret.")
            tokens.each {
                apiTokenProperty.tokenStore.revokeToken(it.getUuid())
            }
        }

        def tokenPlainValue = apiTokenProperty.tokenStore.generateNewToken(jenkinsTokenName).plainValue
        adminUser.save()

        def jenkins = Jenkins.instance
        def domain = Domain.global()
        def store = jenkins.getExtensionList("com.cloudbees.plugins.credentials.SystemCredentialsProvider")[0].getStore()

        String id = "admin-cli-token"
        def adminApiTokenCred = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, id, "Jenkins API token: "+id, adminUserId, tokenPlainValue)

        store.addCredentials(domain, adminApiTokenCred)
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
    Logger logger = Logger.getLogger("oc-create-update-managed-controller")
    println "Master '${masterName}' does not exist yet. Creating it now."

    def configuration = new KubernetesMasterProvisioning()
    masterDefinition.provisioning.each { k, v ->
        configuration["${k}"] = v
    }
  def teamsFolder = Jenkins.instance.getItem('teams') 
  ManagedMaster master = teamsFolder.createProject(ManagedMaster.class, masterName)
    master.setConfiguration(configuration)
    master.properties.replace(new ConnectedMasterLicenseServerProperty(null))
    //needed for CasC RBAC
    //master.properties.replace(new com.cloudbees.opscenter.server.security.SecurityEnforcer.OptOutProperty(com.cloudbees.opscenter.server.sso.AuthorizationOptOutMode.INSTANCE, false, null))
    master.save()
    master.onModified()

    setBundleSecurity(masterName)

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
    def groupItem = teamsFolder.getItem(masterName);
    def container = GroupContainerLocator.locate(groupItem);
    if(!container.getGroups().any{it.name=groupName}) {
      Group group = new Group(container, groupName);
      group.doAddMember("REPLACE_JENKINS_USER");
      group.doAddMember("REPLACE_JENKINS_USER-admin");
      group.doGrantRole(roleName, 0, Boolean.TRUE);
      container.addGroup(group);
      container.addRoleFilter(roleName);
      container.addRoleFilter("browse");
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

private static void setBundleSecurity(String masterName) {
    sleep(100)
    ExtensionList.lookupSingleton(BundleStorage.class).initialize()
    BundleStorage.AccessControl accessControl = ExtensionList.lookupSingleton(BundleStorage.class).getAccessControl()
    accessControl.updateMasterPath(masterName, "teams/" + masterName)
}

