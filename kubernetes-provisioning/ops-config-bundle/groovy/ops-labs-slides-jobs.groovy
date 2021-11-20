import jenkins.model.*;
import org.jenkinsci.plugins.workflow.libs.*;
import jenkins.scm.api.SCMSource;
import jenkins.plugins.git.*; 
import org.jenkinsci.plugins.github.GitHubPlugin;
import java.util.logging.Logger;

Logger logger = Logger.getLogger("ops-labs-slides-jobs.groovy");

def jenkins = Jenkins.instance

//check for folder
def labJobsFolderName = "labs-slides"
def labJobsFolder = jenkins.getItemByFullName(labJobsFolderName)
if (labJobsFolder == null) {
    def labJobsFolderXml = """
    <com.cloudbees.hudson.plugins.folder.Folder plugin="cloudbees-folder@6.14">
    <actions/>
    <description></description>
    <properties>
      <com.cloudbees.hudson.plugins.folder.properties.SubItemFilterProperty plugin="cloudbees-folders-plus@3.10">
        <allowedTypes>
          <string>workshopCatalog/hugo</string>
          <string>org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject</string>
        </allowedTypes>
      </com.cloudbees.hudson.plugins.folder.properties.SubItemFilterProperty>
    </properties>
    <icon class="com.cloudbees.hudson.plugins.folder.icons.StockFolderIcon"/>
  </com.cloudbees.hudson.plugins.folder.Folder>
  """
  
  labJobsFolder = jenkins.createProjectFromXML(labJobsFolderName, new ByteArrayInputStream(labJobsFolderXml.getBytes("UTF-8")));
  

}

def cbciLabJobName = "cloudbees-ci-workshop-labs"
def cbciLabJobFullName = "labs-slides/${cbciLabJobName}"
def cbciLabJob = jenkins.getItemByFullName(cbciLabJobFullName)
if (cbciLabJob == null) {

  //hugo job for CBCI workshop from Pipeline Template
  def cbciLabJobXml = """
<org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject plugin="workflow-multibranch@2.21">
  <properties>
    <com.cloudbees.pipeline.governance.templates.classic.multibranch.GovernanceMultibranchPipelinePropertyImpl plugin="cloudbees-workflow-template@3.7">
      <instance>
        <model>workshopCatalog/hugo</model>
        <values class="tree-map">
          <entry>
            <string>baseUrl</string>
            <string>https://cloudbees-ci.labs.cb-sa.io</string>
          </entry>
          <entry>
            <string>bucketFolderName</string>
            <string></string>
          </entry>
          <entry>
            <string>bucketName</string>
            <string>cbci_workshop_labs</string>
          </entry>
          <entry>
            <string>changesetDir</string>
            <string>labs/cloudbees-ci/</string>
          </entry>
          <entry>
            <string>clusterNameMaster</string>
            <string></string>
          </entry>
          <entry>
            <string>clusterNamePR</string>
            <string></string>
          </entry>
          <entry>
            <string>config</string>
            <string>../cloudbees-ci/config.toml</string>
          </entry>
          <entry>
            <string>contentDir</string>
            <string>../cloudbees-ci/content/</string>
          </entry>
          <entry>
            <string>deployTypeMaster</string>
            <string>managed</string>
          </entry>
          <entry>
            <string>deployTypePR</string>
            <string>managed</string>
          </entry>
          <entry>
            <string>gcpProject</string>
            <string>core-workshop</string>
          </entry>
          <entry>
            <string>gcpRegionMaster</string>
            <string>us-central1</string>
          </entry>
          <entry>
            <string>gcpRegionPR</string>
            <string>us-central1</string>
          </entry>
          <entry>
            <string>githubCredentialId</string>
            <string>field-workshops-github-app</string>
          </entry>
          <entry>
            <string>name</string>
            <string>cloudbees-ci-workshop-labs</string>
          </entry>
          <entry>
            <string>namespaceMaster</string>
            <string></string>
          </entry>
          <entry>
            <string>namespacePR</string>
            <string></string>
          </entry>
          <entry>
            <string>projectName</string>
            <string>cloudbees-ci-workshop-labs</string>
          </entry>
          <entry>
            <string>repo</string>
            <string>cloudbees-field-workshops</string>
          </entry>
          <entry>
            <string>repoOwner</string>
            <string>cloudbees-days</string>
          </entry>
          <entry>
            <string>sourceDir</string>
            <string>labs/base/</string>
          </entry>
        </values>
      </instance>
    </com.cloudbees.pipeline.governance.templates.classic.multibranch.GovernanceMultibranchPipelinePropertyImpl>
  </properties>
  <factory class="com.cloudbees.pipeline.governance.templates.classic.multibranch.FromTemplateBranchProjectFactory" plugin="cloudbees-workflow-template@3.7">
    <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
    <catalogName>workshopCatalog</catalogName>
    <templateDirectory>hugo</templateDirectory>
  </factory>
</org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>
  """

  def p = labJobsFolder.createProjectFromXML(cbciLabJobName, new ByteArrayInputStream(cbciLabJobXml.getBytes("UTF-8")));

  logger.info("created $cbciLabJobName job")
} else {
  logger.info("$cbciLabJobName job already exists")
}