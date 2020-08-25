def org
pipeline {
  agent {label 'default-jnlp'}
  options { 
    skipDefaultCheckout true
  }
  triggers {
    eventTrigger jmespathQuery("action=='created' && installation.app_slug=='cloudbees-ci-workshop'")
  }  
  stages {
    stage('Created Installation') {
      when {
        beforeAgent true
        triggeredBy 'EventTriggerCause'
      }
      steps {
        withCredentials([usernamePassword(credentialsId: 'admin-cli-token', usernameVariable: 'JENKINS_CLI_USR', passwordVariable: 'JENKINS_CLI_PSW')]) {
          script {
            org = sh (script: "curl -u $JENKINS_CLI_USR:$JENKINS_CLI_PSW --silent ${BUILD_URL}api/json | jq -r '.actions[0].causes[0].event.installation.account.login' | tr -d '\n'", 
                returnStdout: true)
          }
        }
        echo "GitHub Org: ${org}"
        checkout scm
        sh "sed -i \"s/REPLACE_GITHUB_ORG/$org/g\" ./groovy/ops-create-github-app-credential.groovy"
        sh "curl -O http://teams-ops/teams-ops/jnlpJars/jenkins-cli.jar"
        withCredentials([usernamePassword(credentialsId: 'admin-cli-token', usernameVariable: 'JENKINS_CLI_USR', passwordVariable: 'JENKINS_CLI_PSW')]) {
            sh """
                alias cli='java -jar jenkins-cli.jar -s http://teams-ops/teams-ops/ -auth $JENKINS_CLI_USR:$JENKINS_CLI_PSW'
                cli groovy =<./groovy/ops-create-github-app-credential.groovy
            """
        }
        withCredentials([usernamePassword(credentialsId: "${org}",
                                          usernameVariable: 'GITHUB_APP',
                                          passwordVariable: 'GITHUB_ACCESS_TOKEN')]) {
            sh """
                curl -H 'Accept: application/vnd.github.antiope-preview+json' \
                     -H 'authorization: Bearer ${GITHUB_ACCESS_TOKEN}' \
                     --data '{"organization":"${org}"}' https://api.github.com/repos/cloudbees-days/pipeline-library/forks
                curl -H 'Accept: application/vnd.github.antiope-preview+json' \
                     -H 'authorization: Bearer ${GITHUB_ACCESS_TOKEN}' \
                     --data '{"organization":"${org}"}' https://api.github.com/repos/cloudbees-days/cloudbees-ci-config-bundle/forks
                curl -H 'Accept: application/vnd.github.antiope-preview+json' \
                     -H 'authorization: Bearer ${GITHUB_ACCESS_TOKEN}' \
                     --data '{"organization":"${org}"}' https://api.github.com/repos/cloudbees-days/pipeline-template-catalog/forks
                curl -H 'Accept: application/vnd.github.antiope-preview+json' \
                     -H 'authorization: Bearer ${GITHUB_ACCESS_TOKEN}' \
                     --data '{"organization":"${org}"}' https://api.github.com/repos/cloudbees-days/simple-java-maven-app/forks
            """
        }
        sh "sed -i \"s/REPLACE_GITHUB_ORG/$org/g\" ./groovy/ops-delete-github-app-credential.groovy" 
        withCredentials([usernamePassword(credentialsId: 'admin-cli-token', usernameVariable: 'JENKINS_CLI_USR', passwordVariable: 'JENKINS_CLI_PSW')]) {
            sh """
                alias cli='java -jar jenkins-cli.jar -s http://teams-ops/teams-ops/ -auth $JENKINS_CLI_USR:$JENKINS_CLI_PSW'
                cli groovy =<./groovy/ops-delete-github-app-credential.groovy
            """
        }                                
      }
    }
  }
}
