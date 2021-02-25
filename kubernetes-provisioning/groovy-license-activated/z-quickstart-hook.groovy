import jenkins.model.Jenkins
import hudson.ExtensionList

import java.nio.file.Path
import java.nio.file.Paths

import hudson.security.ACL
import jenkins.util.groovy.GroovyHookScript

import java.util.logging.Logger

String scriptName = "z-quickstart-hook.groovy"

Logger logger = Logger.getLogger(scriptName)

File disableScript = new File(Jenkins.getInstance().getRootDir(), ".disable-quickstart-hook-script")
if (disableScript.exists()) {
    logger.info("DISABLE install plugins script")
    return
}

logger.info("Running quickstart hook")
//kickoff quickstart scripts once licensed and plugins are installed
ACL.impersonate(ACL.SYSTEM, new Runnable() {
    @Override
    public void run() {
      new GroovyHookScript("quickstart").run();
    }
});

disableScript.createNewFile()
