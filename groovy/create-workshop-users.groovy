import jenkins.model.Jenkins
import hudson.model.User
import com.cloudbees.hudson.plugins.folder.*
import jenkins.security.ApiTokenProperty

import java.util.logging.Logger

Logger logger = Logger.getLogger("create-workshop-users")

def workshopFolderName = "REPLACE_GITHUB_APP"
def workshopFolder = Jenkins.instance.getItem(workshopFolderName)
if (workshopFolder == null) {
    logger.info("$workshopFolderName Folder does not exist so creating")
    workshopFolder = Jenkins.instance.createProject(Folder.class, workshopFolderName);
}

String jenkinsUserId = "REPLACE_JENKINS_USER"
def jenkinsTokenName = 'api-token'
def user = User.get(jenkinsUserId, false)
try {
  logger.info("user full name: " + user.getFullName())
} catch(Exception ex) {
  //
}
if(user==null) {
  user = Jenkins.instance.securityRealm.createAccount(jenkinsUserId, "REPLACE_WORKSHOP_ATTENDEES_PASSWORD")
}
while(user == null) {
  user = User.get(jenkinsUserId, false)
}
def apiTokenProperty = user.getProperty(ApiTokenProperty.class)
def tokens = apiTokenProperty.tokenStore.getTokenListSortedByName().findAll {it.name==jenkinsTokenName}
if(tokens.size() != 0) {
    logger.info("Token exists. Revoking any with this name and recreating to ensure we have a valid value stored in the secret.")
    tokens.each {
        apiTokenProperty.tokenStore.revokeToken(it.getUuid())
    }
}
def tokenValue
new File("/var/jenkins_home/jcasc_secrets/userApiToken").withReader { tokenValue = it.readLine() }  
apiTokenProperty.tokenStore.addFixedNewToken(jenkinsTokenName, tokenValue)
user.save()

String adminUserId = "REPLACE_JENKINS_USER-admin"
def adminUser = User.get(adminUserId, false)
if(adminUser==null) {
  Jenkins.instance.securityRealm.createAccount(adminUserId, "REPLACE_WORKSHOP_ATTENDEES_PASSWORD")
}
while(adminUser == null) {
  adminUser = User.get(adminUserId, false)
}
adminUser.save()
