import jenkins.*
import jenkins.model.*
import com.cloudbees.plugins.credentials.impl.*;
import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.domains.Domain;
import org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials;

def gitHubOrg = "REPLACE_GITHUB_ORG"
Jenkins jenkins = Jenkins.getInstance()

def credentials = CredentialsProvider.lookupCredentials(
        org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials.class,
        jenkins,
        null,
        null
);

def cred = credentials.findResult { it.id == "REPLACE_BASE_CREDENTIAL_ID" ? it : null }

Credentials newCred = new GitHubAppCredentials(CredentialsScope.GLOBAL, gitHubOrg, "${gitHubOrg} temp github app cred", cred.appID, cred.privateKey)
newCred.setOwner(gitHubOrg)

def domain = Domain.global()
def store = jenkins.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()
store.addCredentials(domain, newCred)
