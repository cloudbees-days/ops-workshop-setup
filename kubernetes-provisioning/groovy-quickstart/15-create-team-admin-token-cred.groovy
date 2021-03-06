import jenkins.model.Jenkins
import jenkins.security.ApiTokenProperty
import hudson.model.User
import com.cloudbees.plugins.credentials.impl.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.hudson.plugins.folder.*
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider.FolderCredentialsProperty
import java.util.logging.Logger

Logger logger = Logger.getLogger("15-create-team-admin-token-cred.groovy")

def userName = 'team-admin'
def user = User.get(userName, false)
if(user==null) {
  user = Jenkins.instance.securityRealm.createAccount(userName, "changeit")
  def jenkinsTokenName = 'team-admin-api-token'
  def apiTokenProperty = user.getProperty(ApiTokenProperty.class)
  def tokens = apiTokenProperty.tokenStore.getTokenListSortedByName().findAll {it.name==jenkinsTokenName}

  if(tokens.size() != 0) {
      logger.info("Token exists. Revoking any with this name and recreating to ensure we have a valid value stored in the secret.")
      tokens.each {
          apiTokenProperty.tokenStore.revokeToken(it.getUuid())
      }
  }

  def tokenPlainValue = apiTokenProperty.tokenStore.generateNewToken(jenkinsTokenName).plainValue
  user.save()
  
  String id = "admin-cli-token"
  Credentials c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, id, "description:"+id, userName, tokenPlainValue)

  def jenkins = Jenkins.instance

  def teamsFolder = jenkins.getItem("teams")
  if (teamsFolder == null) {
    println("teamsFolder does not exist so creating")
    teamsFolder = jenkins.createProject(Folder.class, "teams")
    teamsFolder.setDisplayName("Teams")
  }

  AbstractFolder<?> folderAbs = AbstractFolder.class.cast(teamsFolder)
  FolderCredentialsProperty property = folderAbs.getProperties().get(FolderCredentialsProperty.class)
  property = new FolderCredentialsProperty([c])
  folderAbs.addProperty(property)
}
