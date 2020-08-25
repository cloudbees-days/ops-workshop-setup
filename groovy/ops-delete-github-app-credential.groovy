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

def cred = credentials.findResult { it.id == gitHubOrg ? it : null }

def domain = Domain.global()
def store = jenkins.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()
store.removeCredentials(domain, cred)
