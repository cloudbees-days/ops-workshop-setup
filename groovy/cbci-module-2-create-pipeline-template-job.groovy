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
def fullName = "template-jobs/${name}"
def configOpsJob = jenkins.getItemByFullName(fullName)
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
  
  def catalogMultibranchName = "pipeline-catalog-ops"
  def catalogMultibranchXml = """
  <org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject plugin="workflow-multibranch@2.22">
      <actions/>
      <properties>
        <com.cloudbees.hudson.plugins.folder.properties.EnvVarsFolderProperty plugin="cloudbees-folders-plus@3.10">
          <properties></properties>
        </com.cloudbees.hudson.plugins.folder.properties.EnvVarsFolderProperty>
        <org.csanchez.jenkins.plugins.kubernetes.KubernetesFolderProperty plugin="kubernetes@1.26.4">
          <permittedClouds/>
        </org.csanchez.jenkins.plugins.kubernetes.KubernetesFolderProperty>
      </properties>
      <folderViews class="jenkins.branch.MultiBranchProjectViewHolder" plugin="branch-api@2.5.8">
        <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
      </folderViews>
      <icon class="jenkins.branch.MetadataActionFolderIcon" plugin="branch-api@2.5.8">
        <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
      </icon>
      <orphanedItemStrategy class="com.cloudbees.hudson.plugins.folder.computed.DefaultOrphanedItemStrategy" plugin="cloudbees-folder@6.14">
        <pruneDeadBranches>true</pruneDeadBranches>
        <daysToKeep>-1</daysToKeep>
        <numToKeep>-1</numToKeep>
      </orphanedItemStrategy>
      <triggers/>
      <disabled>false</disabled>
      <sources class="jenkins.branch.MultiBranchProject\$BranchSourceList" plugin="branch-api@2.5.8">
        <data>
          <jenkins.branch.BranchSource>
            <source class="org.jenkinsci.plugins.github_branch_source.GitHubSCMSource" plugin="github-branch-source@2.8.3">
              <apiUri>https://api.github.com</apiUri>
              <credentialsId>cloudbees-ci-workshop-github-app</credentialsId>
              <repoOwner>REPLACE_GITHUB_ORG</repoOwner>
              <repository>pipeline-template-catalog</repository>
              <repositoryUrl>https://github.com/REPLACE_GITHUB_ORG/pipeline-template-catalog.git</repositoryUrl>
              <traits>
                <org.jenkinsci.plugins.github__branch__source.BranchDiscoveryTrait>
                  <strategyId>1</strategyId>
                </org.jenkinsci.plugins.github__branch__source.BranchDiscoveryTrait>
                <org.jenkinsci.plugins.github__branch__source.OriginPullRequestDiscoveryTrait>
                  <strategyId>1</strategyId>
                </org.jenkinsci.plugins.github__branch__source.OriginPullRequestDiscoveryTrait>
                <org.jenkinsci.plugins.github__branch__source.ForkPullRequestDiscoveryTrait>
                  <strategyId>1</strategyId>
                  <trust class="org.jenkinsci.plugins.github_branch_source.ForkPullRequestDiscoveryTrait\$TrustPermission"/>
                </org.jenkinsci.plugins.github__branch__source.ForkPullRequestDiscoveryTrait>
              </traits>
            </source>
            <strategy class="jenkins.branch.DefaultBranchPropertyStrategy">
              <properties class="empty-list"/>
            </strategy>
          </jenkins.branch.BranchSource>
        </data>
        <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
      </sources>
      <factory class="org.jenkinsci.plugins.workflow.multibranch.WorkflowBranchProjectFactory">
        <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
        <scriptPath>Jenkinsfile</scriptPath>
      </factory>
    </org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>
    """
    jenkins.createProjectFromXML(catalogMultibranchName, new ByteArrayInputStream(configOpsJobXml.getBytes("UTF-8")))
    
    def templateFolderName = "template-jobs"
    def templateFolderXml = """
    <com.cloudbees.hudson.plugins.folder.Folder plugin="cloudbees-folder@6.14">
    <actions/>
    <description></description>
    <properties>
      <com.cloudbees.hudson.plugins.folder.properties.SubItemFilterProperty plugin="cloudbees-folders-plus@3.10">
        <allowedTypes>
          <string>workshopCatalog/maven</string>
          <string>workshopCatalog/pipeline-policies</string>
          <string>workshopCatalog/casc-bundle</string>
        </allowedTypes>
      </com.cloudbees.hudson.plugins.folder.properties.SubItemFilterProperty>
    </properties>
    <icon class="com.cloudbees.hudson.plugins.folder.icons.StockFolderIcon"/>
  </com.cloudbees.hudson.plugins.folder.Folder>
  """
  
  def f = jenkins.createProjectFromXML(templateFolderName, new ByteArrayInputStream(templateFolderXml.getBytes("UTF-8")));
  
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

  def p = f.createProjectFromXML(name, new ByteArrayInputStream(configOpsJobXml.getBytes("UTF-8")));

  logger.info("created $name job")
} else {
  logger.info("$name job already exists")
}
