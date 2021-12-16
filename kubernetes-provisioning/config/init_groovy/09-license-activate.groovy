import com.cloudbees.jenkins.plugins.license.nectar.EvaluationRegistrar
import com.cloudbees.opscenter.server.license.OperationsCenterEvaluationRegistrar
import groovy.transform.Field
import hudson.license.LicenseManager
import java.util.logging.Logger
@Field Logger LOGGER = Logger.getLogger("com.cloudbees.groovy")
LOGGER.info("Setting up trial license")
def main() {
    if (LicenseManager.getConfigFile().exists()) {
        LOGGER.info("License exists")
    } else {
        LOGGER.info("License doesn't exist, requesting a trial license")
        String company = "CloudBees"
        String email = "cb-demo@cloudbees.com"
        String firstname = "CI"
        String lastname = "Workshop"
        String productCode = OperationsCenterEvaluationRegistrar.EVAL_EDITION_MULTI_MASTER_SOURCE_TRIAL
        requestTrialLicenseWithWizardCode(firstname, lastname, email, company, productCode, false, true)
    }
}
main()
static def requestTrialLicenseWithWizardCode(String firstname, String lastname, String email, String company, String productCode, boolean subscribe, boolean agree) {
    EvaluationRegistrar registrar = new OperationsCenterEvaluationRegistrar(null, null, null, true, false, null);
    registrar.register(firstname, lastname, email, company, subscribe, agree, productCode)
}