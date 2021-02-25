import jenkins.model.Jenkins

import java.nio.file.Path
import java.nio.file.Paths

import java.util.logging.Logger

String scriptName = "09-install-plugins.groovy"

Logger logger = Logger.getLogger(scriptName)

File disableScript = new File(Jenkins.getInstance().getRootDir(), ".disable-init-script")
if (disableScript.exists()) {
    logger.info("DISABLE install plugins script")
    return
}

Jenkins jenkins = Jenkins.getInstance()

Path filePath = Paths.get('/var/jenkins_home/quickstart.groovy.d/plugins.txt')
def plugins = filePath.toFile() as String[]
def pm = Jenkins.instance.pluginManager
plugins.each { pluginName ->
  logger.info("installing $pluginName")
  jenkins.instance.updateCenter.getPlugin(pluginName).deploy()
}

disableScript.createNewFile()
