import jenkins.model.*;
import hudson.model.*;
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
