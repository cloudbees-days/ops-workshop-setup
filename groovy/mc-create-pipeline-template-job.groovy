import jenkins.model.*;
import org.jenkinsci.plugins.workflow.libs.*;
import jenkins.scm.api.SCMSource;
import jenkins.plugins.git.*; 
import com.cloudbees.pipeline.governance.templates.*;
import com.cloudbees.pipeline.governance.templates.catalog.*;
import org.jenkinsci.plugins.github.GitHubPlugin;
import java.util.logging.Logger;

Logger logger = Logger.getLogger("mc-create-pipeline-template-job.groovy");

def jenkins = Jenkins.instance

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
def name = "microblog-frontend"
def frontendJobXml = """
<org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject plugin="workflow-multibranch@2.21">
  <properties>
    <com.cloudbees.pipeline.governance.templates.classic.multibranch.GovernanceMultibranchPipelinePropertyImpl plugin="cloudbees-workflow-template@3.7">
      <instance>
        <model>workshopCatalog/vuejs-app</model>
        <values class="tree-map">
          <entry>
            <string>deploymentDomain</string>
            <string>workshop.cb-sa.io</string>
          </entry>
          <entry>
            <string>gcpProject</string>
            <string>core-workshop</string>
          </entry>
          <entry>
            <string>githubCredentialId</string>
            <string>cloudbees-ci-workshop-github-app</string>
          </entry>
          <entry>
            <string>name</string>
            <string>REPLACE_GITHUB_ORG</string>
          </entry>
          <entry>
            <string>repoOwner</string>
            <string>REPLACE_GITHUB_ORG</string>
          </entry>
          <entry>
            <string>repository</string>
            <string>microblog-frontend</string>
          </entry>
        </values>
      </instance>
    </com.cloudbees.pipeline.governance.templates.classic.multibranch.GovernanceMultibranchPipelinePropertyImpl>
  </properties>
  <folderViews class="jenkins.branch.MultiBranchProjectViewHolder" plugin="branch-api@2.5.8">
    <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
  </folderViews>
  <healthMetrics>
    <com.cloudbees.hudson.plugins.folder.health.WorstChildHealthMetric plugin="cloudbees-folder@6.14">
      <nonRecursive>false</nonRecursive>
    </com.cloudbees.hudson.plugins.folder.health.WorstChildHealthMetric>
    <com.cloudbees.hudson.plugins.folder.health.AverageChildHealthMetric plugin="cloudbees-folders-plus@3.10"/>
    <com.cloudbees.hudson.plugins.folder.health.JobStatusHealthMetric plugin="cloudbees-folders-plus@3.10">
      <success>true</success>
      <failure>true</failure>
      <unstable>true</unstable>
      <unbuilt>true</unbuilt>
      <countVirginJobs>false</countVirginJobs>
    </com.cloudbees.hudson.plugins.folder.health.JobStatusHealthMetric>
    <com.cloudbees.hudson.plugins.folder.health.ProjectEnabledHealthMetric plugin="cloudbees-folders-plus@3.10"/>
  </healthMetrics>
  <icon class="jenkins.branch.MetadataActionFolderIcon" plugin="branch-api@2.5.5">
    <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
  </icon>
  <orphanedItemStrategy class="com.cloudbees.hudson.plugins.folder.computed.DefaultOrphanedItemStrategy" plugin="cloudbees-folder@6.9">
    <pruneDeadBranches>true</pruneDeadBranches>
    <daysToKeep>-1</daysToKeep>
    <numToKeep>-1</numToKeep>
  </orphanedItemStrategy>
  <triggers>
    <com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger plugin="cloudbees-folder@6.9">
      <spec>H H/4 * * *</spec>
      <interval>86400000</interval>
    </com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger>
  </triggers>
  <disabled>false</disabled>
  <sources>
    <jenkins.branch.BranchSource plugin="branch-api@2.5.5">
      <source class="org.jenkinsci.plugins.github_branch_source.GitHubSCMSource" plugin="github-branch-source@2.8.3">
        <id>VueJS</id>
        <apiUri>https://api.github.com</apiUri>
        <credentialsId>cloudbees-ci-workshop-github-app</credentialsId>
        <repoOwner>REPLACE_GITHUB_ORG</repoOwner>
        <repository>microblog-frontend</repository>
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
        <properties class="java.util.Arrays\$ArrayList">
          <a class="jenkins.branch.BranchProperty-array"/>
        </properties>
      </strategy>
    </jenkins.branch.BranchSource>
  </sources>
  <factory class="com.cloudbees.pipeline.governance.templates.classic.multibranch.FromTemplateBranchProjectFactory" plugin="cloudbees-workflow-template@3.7">
    <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
    <catalogName>workshopCatalog</catalogName>
    <templateDirectory>vuejs-app</templateDirectory>
    <markerFile>.vuejs</markerFile>
  </factory>
</org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>
"""

def p = jenkins.createProjectFromXML(name, new ByteArrayInputStream(frontendJobXml.getBytes("UTF-8")));

logger.info("created $name job")
