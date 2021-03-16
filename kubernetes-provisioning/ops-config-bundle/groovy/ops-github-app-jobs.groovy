import jenkins.model.*;
import hudson.model.*;
import com.cloudbees.hudson.plugins.folder.Folder;
import java.util.logging.Logger;

Logger logger = Logger.getLogger("ops-github-app-jobs.groovy");

def jenkins = Jenkins.instance

def cbciWorkshopSetupJobName = "cbci-workshop-setup"
def cbciWorkshopSetupJob = jenkins.getItemByFullName(cbciWorkshopSetupJobName)
if (cbciWorkshopSetupJob == null) {
  def cbciWorkshopSetupJobXml = """
  <flow-definition plugin="workflow-job@2.40">
    <actions>
      <org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobAction plugin="pipeline-model-definition@1.7.2"/>
      <org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction plugin="pipeline-model-definition@1.7.2">
        <jobProperties/>
        <parameters/>
        <options/>
        <triggers/>
      </org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction>
    </actions>
    <description>Sets up CBCI workshop based on GitHub App install.</description>
    <keepDependencies>false</keepDependencies>
    <properties>
      <com.coravy.hudson.plugins.github.GithubProjectProperty plugin="github@1.32.0">
        <projectUrl>https://github.com/cloudbees-days/ops-workshop-setup/</projectUrl>
        <displayName></displayName>
      </com.coravy.hudson.plugins.github.GithubProjectProperty>
    </properties>
    <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition" plugin="workflow-cps@2.87">
      <scm class="hudson.plugins.git.GitSCM" plugin="git@4.5.2">
        <configVersion>2</configVersion>
        <userRemoteConfigs>
          <hudson.plugins.git.UserRemoteConfig>
            <url>https://github.com/cloudbees-days/ops-workshop-setup.git</url>
            <credentialsId>field-workshops-github-app</credentialsId>
          </hudson.plugins.git.UserRemoteConfig>
        </userRemoteConfigs>
        <branches>
          <hudson.plugins.git.BranchSpec>
            <name>master</name>
          </hudson.plugins.git.BranchSpec>
        </branches>
        <submoduleCfg class="list"/>
        <extensions/>
      </scm>
      <scriptPath>provision-managed-controller</scriptPath>
      <lightweight>true</lightweight>
    </definition>
    <triggers/>
    <disabled>false</disabled>
  </flow-definition>
  """
  
  cbciWorkshopSetupJob = jenkins.createProjectFromXML(cbciWorkshopSetupJobName, new ByteArrayInputStream(cbciWorkshopSetupJobXml.getBytes("UTF-8")));
  hudson.model.Hudson.instance.queue.schedule(cbciWorkshopSetupJob, 0)
}

def cbciWorkshopMod2SetupJobName = "cbci-workshop-module-2-setup"
def cbciWorkshopMod2SetupJob = jenkins.getItemByFullName(cbciWorkshopMod2SetupJobName)
if (cbciWorkshopMod2SetupJob == null) {
  def cbciWorkshopMod2SetupJobXml = """
<flow-definition plugin="workflow-job@2.40">
  <actions>
    <org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobAction plugin="pipeline-model-definition@1.7.2"/>
    <org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction plugin="pipeline-model-definition@1.7.2">
      <jobProperties/>
      <triggers/>
      <parameters/>
      <options/>
    </org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction>
  </actions>
  <description></description>
  <keepDependencies>false</keepDependencies>
  <properties>
    <com.coravy.hudson.plugins.github.GithubProjectProperty plugin="github@1.32.0">
      <projectUrl>https://github.com/cloudbees-days/ops-workshop-setup/</projectUrl>
    </com.coravy.hudson.plugins.github.GithubProjectProperty>
  </properties>
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition" plugin="workflow-cps@2.87">
    <scm class="hudson.plugins.git.GitSCM" plugin="git@4.5.2">
      <configVersion>2</configVersion>
      <userRemoteConfigs>
        <hudson.plugins.git.UserRemoteConfig>
          <url>https://github.com/cloudbees-days/ops-workshop-setup.git</url>
          <credentialsId>field-workshops-github-app</credentialsId>
        </hudson.plugins.git.UserRemoteConfig>
      </userRemoteConfigs>
      <branches>
        <hudson.plugins.git.BranchSpec>
          <name>master</name>
        </hudson.plugins.git.BranchSpec>
      </branches>
      <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
      <gitTool>Default</gitTool>
      <extensions/>
    </scm>
    <scriptPath>cbci-module-2-setup</scriptPath>
    <lightweight>true</lightweight>
  </definition>
  <triggers/>
  <disabled>false</disabled>
</flow-definition>
  """
  
  cbciWorkshopMod2SetupJob = jenkins.createProjectFromXML(cbciWorkshopMod2SetupJobName, new ByteArrayInputStream(cbciWorkshopMod2SetupJobXml.getBytes("UTF-8")));
  hudson.model.Hudson.instance.queue.schedule(cbciWorkshopMod2SetupJob, 0)
}

def cbciPipelineWorkshopSetupJobName = "cbci-pipeline-workshop-setup"
def cbciPipelineWorkshopSetupJob = jenkins.getItemByFullName(cbciPipelineWorkshopSetupJobName)
if (cbciPipelineWorkshopSetupJob == null) {
  def cbciPipelineWorkshopSetupJobXml = """
<flow-definition plugin="workflow-job@2.40">
  <actions>
    <org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobAction plugin="pipeline-model-definition@1.7.2"/>
    <org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction plugin="pipeline-model-definition@1.7.2">
      <jobProperties/>
      <triggers/>
      <parameters/>
      <options/>
    </org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction>
  </actions>
  <description></description>
  <keepDependencies>false</keepDependencies>
  <properties/>
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition" plugin="workflow-cps@2.87">
    <scm class="hudson.plugins.git.GitSCM" plugin="git@4.5.2">
      <configVersion>2</configVersion>
      <userRemoteConfigs>
        <hudson.plugins.git.UserRemoteConfig>
          <url>https://github.com/cloudbees-days/ops-workshop-setup.git</url>
          <credentialsId>field-workshops-github-app</credentialsId>
        </hudson.plugins.git.UserRemoteConfig>
      </userRemoteConfigs>
      <branches>
        <hudson.plugins.git.BranchSpec>
          <name>*/master</name>
        </hudson.plugins.git.BranchSpec>
      </branches>
      <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
      <submoduleCfg class="list"/>
      <extensions/>
    </scm>
    <scriptPath>provision-cbci-pipeline-workshop</scriptPath>
    <lightweight>true</lightweight>
  </definition>
  <triggers/>
  <disabled>false</disabled>
</flow-definition>
  """
  
  cbciPipelineWorkshopSetupJob = jenkins.createProjectFromXML(cbciPipelineWorkshopSetupJobName, new ByteArrayInputStream(cbciPipelineWorkshopSetupJobXml.getBytes("UTF-8")));
  hudson.model.Hudson.instance.queue.schedule(cbciPipelineWorkshopSetupJob, 0)
}

def utilitiyJobsFolderName = "utility-jobs"
def utilitiyJobsFolder = jenkins.getItemByFullName(utilitiyJobsFolderName)
if (utilitiyJobsFolder == null) {
  utilitiyJobsFolder = jenkins.createProject(Folder.class, utilitiyJobsFolderName) 
  
  def cleanupCompletedPodsName = "cleanup-completed-pods"
  def cleanupCompletedPodsJobXml = """
  <flow-definition plugin="workflow-job@2.40">
    <actions>
      <org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobAction plugin="pipeline-model-definition@1.7.2"/>
      <org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction plugin="pipeline-model-definition@1.7.2">
        <jobProperties/>
        <triggers>
          <string>hudson.triggers.TimerTrigger</string>
        </triggers>
        <parameters/>
        <options>
          <string>skipDefaultCheckout</string>
        </options>
      </org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction>
    </actions>
    <description></description>
    <keepDependencies>false</keepDependencies>
    <properties>
      <jenkins.model.BuildDiscarderProperty>
        <strategy class="hudson.tasks.LogRotator">
          <daysToKeep>-1</daysToKeep>
          <numToKeep>10</numToKeep>
          <artifactDaysToKeep>-1</artifactDaysToKeep>
          <artifactNumToKeep>-1</artifactNumToKeep>
        </strategy>
      </jenkins.model.BuildDiscarderProperty>
      <org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty>
        <triggers>
          <hudson.triggers.TimerTrigger>
            <spec>H 6-22 * * 1-5</spec>
          </hudson.triggers.TimerTrigger>
        </triggers>
      </org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty>
    </properties>
    <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition" plugin="workflow-cps@2.87">
      <scm class="hudson.plugins.git.GitSCM" plugin="git@4.5.2">
        <configVersion>2</configVersion>
        <userRemoteConfigs>
          <hudson.plugins.git.UserRemoteConfig>
            <url>https://github.com/cloudbees-days/ops-workshop-setup.git</url>
            <credentialsId>field-workshops-github-app</credentialsId>
          </hudson.plugins.git.UserRemoteConfig>
        </userRemoteConfigs>
        <branches>
          <hudson.plugins.git.BranchSpec>
            <name>*/master</name>
          </hudson.plugins.git.BranchSpec>
        </branches>
        <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>
        <submoduleCfg class="list"/>
        <extensions/>
      </scm>
      <scriptPath>cleanup-completed-pods</scriptPath>
      <lightweight>true</lightweight>
    </definition>
    <triggers/>
    <disabled>false</disabled>
  </flow-definition>
  """
  def cleanupCompletedPodsJob = utilitiyJobsFolder.createProjectFromXML(cleanupCompletedPodsName, new ByteArrayInputStream(cleanupCompletedPodsJobXml.getBytes("UTF-8")));
  hudson.model.Hudson.instance.queue.schedule(cleanupCompletedPodsJob, 0)
}


