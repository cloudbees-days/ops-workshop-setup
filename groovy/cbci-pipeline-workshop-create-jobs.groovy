import jenkins.model.*;
import org.jenkinsci.plugins.workflow.libs.*;
import jenkins.scm.api.SCMSource;
import jenkins.plugins.git.*; 
import com.cloudbees.pipeline.governance.templates.*;
import com.cloudbees.pipeline.governance.templates.catalog.*;
import org.jenkinsci.plugins.github.GitHubPlugin;
import java.util.logging.Logger;

Logger logger = Logger.getLogger("cbci-pipeline-workshop-create-jobs.groovy");

def jenkins = Jenkins.instance;
def templateFolderName = "pipelines";
def templateFolderJob = jenkins.getItemByFullName(templateFolderName);
if (templateFolderJob == null) {
  //Pipeline Template Catalog
  SCMSource scm = new GitSCMSource("https://github.com/REPLACE_GITHUB_ORG/pipeline-template-catalog.git");
  scm.setCredentialsId("cloudbees-ci-pipeline-workshop-github-app");
  TemplateCatalog catalog = new TemplateCatalog(scm, "main");
  catalog.setUpdateInterval("1h");
  GlobalTemplateCatalogManagement.get().addCatalog(catalog);
  GlobalTemplateCatalogManagement.get().save();
  logger.info("Creating new Pipeline Template Catalog");
  catalog.updateFromSCM(); 
    
    def templateFolderXml = """
<com.cloudbees.hudson.plugins.folder.Folder plugin="cloudbees-folder@6.15">
  <actions/>
  <description></description>
  <properties>
    <com.cloudbees.hudson.plugins.folder.properties.SubItemFilterProperty plugin="cloudbees-folders-plus@3.12">
      <allowedTypes>
        <string>org.jenkinsci.plugins.workflow.job.WorkflowJob</string>
        <string>workshopCatalog/maven</string>
        <string>org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject</string>
        <string>workshopCatalog/nodejs-app</string>
        <string>com.cloudbees.hudson.plugins.folder.Folder</string>
        <string>jenkins.branch.OrganizationFolder.org.jenkinsci.plugins.github_branch_source.GitHubSCMNavigator</string>
      </allowedTypes>
    </com.cloudbees.hudson.plugins.folder.properties.SubItemFilterProperty>
  </properties>
  <icon class="com.cloudbees.hudson.plugins.folder.icons.StockFolderIcon"/>
</com.cloudbees.hudson.plugins.folder.Folder>
  """;
  
  def f = jenkins.createProjectFromXML(templateFolderName, new ByteArrayInputStream(templateFolderXml.getBytes("UTF-8")));

  logger.info("created $templateFolderName job");
} else {
  logger.info("$templateFolderName job already exists");
}
