import hudson.model.User
import jenkins.model.Jenkins
import jenkins.security.ApiTokenProperty

import java.util.logging.Logger

String scriptName = "04-create-admin-jenkins-token.groovy"
Logger logger = Logger.getLogger(scriptName)
logger.info("Starting ${scriptName}")

def userName = 'admin'
def jenkinsTokenName = 'token-for-ops-controller'

def user = User.get(userName, false)
def apiTokenProperty = user.getProperty(ApiTokenProperty.class)
def tokens = apiTokenProperty.tokenStore.getTokenListSortedByName().findAll {it.name==jenkinsTokenName}

if(tokens.size() != 0) {
    logger.info("Token exists. Revoking any with this name and recreating to ensure we have a valid value stored in the secret.")
    tokens.each {
        apiTokenProperty.tokenStore.revokeToken(it.getUuid())
    }
}
def tokenValue
new File("/var/jenkins_home/jcasc_secrets/cbciWorkshopCjocAdminToken").withReader { tokenValue = it.readLine() }  
apiTokenProperty.tokenStore.addFixedNewToken(jenkinsTokenName, tokenValue)
user.save()
