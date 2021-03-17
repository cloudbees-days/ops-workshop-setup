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
def templateFolderName = "template-jobs";
def templateFolderJob = jenkins.getItemByFullName(templateFolderName);
if (templateFolderJob == null) {
  //Pipeline Template Catalog
  SCMSource scm = new GitSCMSource("https://github.com/REPLACE_GITHUB_ORG/pipeline-template-catalog.git");
  scm.setCredentialsId("cloudbees-ci-pipeline-workshop-github-app");
  TemplateCatalog catalog = new TemplateCatalog(scm, "master");
  catalog.setUpdateInterval("1h");
  GlobalTemplateCatalogManagement.get().addCatalog(catalog);
  GlobalTemplateCatalogManagement.get().save();
  logger.info("Creating new Pipeline Template Catalog");
  catalog.updateFromSCM(); 
    
    def templateFolderXml = """
    <com.cloudbees.hudson.plugins.folder.Folder plugin="cloudbees-folder@6.14">
    <actions/>
    <description></description>
    <properties>
      <com.cloudbees.hudson.plugins.folder.properties.SubItemFilterProperty plugin="cloudbees-folders-plus@3.10">
        <allowedTypes>
          <string>workshopCatalog/nodejs-app</string>
        </allowedTypes>
      </com.cloudbees.hudson.plugins.folder.properties.SubItemFilterProperty>
    </properties>
    <icon class="com.cloudbees.hudson.plugins.folder.icons.StockFolderIcon"/>
  </com.cloudbees.hudson.plugins.folder.Folder>
  """;
  
  def f = jenkins.createProjectFromXML(templateFolderName, new ByteArrayInputStream(templateFolderXml.getBytes("UTF-8")));

  logger.info("created $name job");
} else {
  logger.info("$name job already exists");
}
