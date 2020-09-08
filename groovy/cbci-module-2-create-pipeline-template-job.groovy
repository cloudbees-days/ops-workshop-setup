import jenkins.model.*;
import org.jenkinsci.plugins.workflow.libs.*;
import jenkins.scm.api.SCMSource;
import jenkins.plugins.git.*; 
import com.cloudbees.pipeline.governance.templates.*;
import com.cloudbees.pipeline.governance.templates.catalog.*;
import org.jenkinsci.plugins.github.GitHubPlugin;
import java.util.logging.Logger;

Logger logger = Logger.getLogger("cbci-module-2-create-pipeline-template-job.groovy");

def jenkins = Jenkins.instance
def name = "config-bundle-ops"
def configOpsJob = jenkins.getItemByFullName(name)
if (configOpsJob == null) {
  //Pipeline Template Catalog
  SCMSource scm = new GitSCMSource("https://github.com/REPLACE_GITHUB_ORG/pipeline-template-catalog.git");
  scm.setCredentialsId("cloudbees-ci-workshop-github-app");
  TemplateCatalog catalog = new TemplateCatalog(scm, "master");
  catalog.setUpdateInterval("1h");
  GlobalTemplateCatalogManagement.get().addCatalog(catalog);
  GlobalTemplateCatalogManagement.get().save();
  logger.info("Creating new Pipeline Template Catalog");
  catalog.updateFromSCM(); 

  //microblog-fronted job from Pipeline Template
  def configOpsJobXml = """
<org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject plugin="workflow-multibranch@2.21">
  <properties>
    <com.cloudbees.pipeline.governance.templates.classic.multibranch.GovernanceMultibranchPipelinePropertyImpl plugin="cloudbees-workflow-template@3.7">
      <instance>
        <model>workshopCatalog/casc-bundle</model>
        <values class="tree-map">
          <entry>
            <string>githubCredentialId</string>
            <string>cloudbees-ci-workshop-github-app</string>
          </entry>
          <entry>
            <string>name</string>
            <string>config-bundle-ops</string>
          </entry>
          <entry>
            <string>repoOwner</string>
            <string>REPLACE_GITHUB_ORG</string>
          </entry>
          <entry>
            <string>repository</string>
            <string>cloudbees-ci-config-bundle</string>
          </entry>
        </values>
      </instance>
    </com.cloudbees.pipeline.governance.templates.classic.multibranch.GovernanceMultibranchPipelinePropertyImpl>
  </properties>
  <factory class="com.cloudbees.pipeline.governance.templates.classic.multibranch.FromTemplateBranchProjectFactory" plugin="cloudbees-workflow-template@3.7">
    <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
    <catalogName>workshopCatalog</catalogName>
    <templateDirectory>casc-bundle</templateDirectory>
    <markerFile>.markerfile</markerFile>
  </factory>
</org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>
  """

  def p = jenkins.createProjectFromXML(name, new ByteArrayInputStream(configOpsJobXml.getBytes("UTF-8")));

  logger.info("created $name job")
} else {
  logger.info("$name job already exists")
}
