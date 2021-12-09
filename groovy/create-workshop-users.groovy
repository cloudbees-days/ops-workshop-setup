import jenkins.model.Jenkins
import hudson.model.User

import java.util.logging.Logger

Logger logger = Logger.getLogger("create-workshop-users")

String jenkinsUserId = "REPLACE_JENKINS_USER"
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
