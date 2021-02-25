import hudson.model.User
import jenkins.model.Jenkins
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import jenkins.security.ApiTokenProperty

import com.cloudbees.hudson.plugins.folder.*
import com.cloudbees.hudson.plugins.folder.properties.*
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider.FolderCredentialsProperty
import com.cloudbees.plugins.credentials.impl.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.*

import java.util.logging.Logger

String scriptName = "05-create-k8s-secret-with-jenkins-token.groovy"
Logger logger = Logger.getLogger(scriptName)
logger.info("Starting ${scriptName}")

def userName = 'admin'
def jenkinsTokenName = 'token-for-k8s-secret'
def k8sTokenName = "cbci-admin-token-secret"
def namespace = "sda"

def user = User.get(userName, false)
def apiTokenProperty = user.getProperty(ApiTokenProperty.class)
def tokens = apiTokenProperty.tokenStore.getTokenListSortedByName().findAll {it.name==jenkinsTokenName}

if(tokens.size() != 0) {
    logger.info("Token exists. Revoking any with this name and recreating to ensure we have a valid value stored in the secret.")
    tokens.each {
        apiTokenProperty.tokenStore.revokeToken(it.getUuid())
    }
}

def result = apiTokenProperty.tokenStore.generateNewToken(jenkinsTokenName).plainValue
user.save()

//create credentials in ops folder for api token
String id = "admin-cli-token"
Credentials c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, id, "description:"+id, userName, result)
def jenkins = Jenkins.instance 
//create admin-cli-token credential at admin folder level for workshop setup 
def opsFolder = jenkins.getItem("operations")
if (opsFolder == null) {
    println("admin Folder does not exist so creating")
    opsFolder = jenkins.createProject(Folder.class, "operations");
}
AbstractFolder<?> folderAbs = AbstractFolder.class.cast(opsFolder)
FolderCredentialsProperty property = folderAbs.getProperties().get(FolderCredentialsProperty.class)
property = new FolderCredentialsProperty([c])
folderAbs.addProperty(property)

def client = new DefaultKubernetesClient()
def createdSecret = client.secrets().inNamespace(namespace).createOrReplaceWithNew()
        .withNewMetadata().withName(k8sTokenName).endMetadata()
        .addToStringData("apiToken", result)
        .done()
