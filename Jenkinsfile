#!groovy

/**
 * Kill already started job.
 * Assume new commit takes precendence and results from previous
 * unfinished builds are not required.
 * This feature doesn't play well with disableConcurrentBuilds() option
 */
@Library('corda-shared-build-pipeline-steps')
import static com.r3.build.BuildControl.killAllExistingBuildsForJob

killAllExistingBuildsForJob(env.JOB_NAME, env.BUILD_NUMBER.toInteger())

pipeline {
    agent { label 'standard' }

    options {
        timestamps()
        timeout(time: 1, unit: 'HOURS')
        buildDiscarder(logRotator(daysToKeepStr: '14', artifactDaysToKeepStr: '14'))
    }

    tools {
        maven "maven"
    }

    environment {
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-credentials')
        CORDA_ARTIFACTORY_USERNAME = "${env.ARTIFACTORY_CREDENTIALS_USR}"
        CORDA_ARTIFACTORY_PASSWORD = "${env.ARTIFACTORY_CREDENTIALS_PSW}"
    }

    stages {

        stage('Build ') {
            steps {
                sh "mvn -B install -DskipTests -Pjdk8"
            }
        }

        stage('Test ') {
            steps {
                sh "mvn -B test -Pjdk8"
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/**/TEST-*.xml'
                    archiveArtifacts artifacts: '**/target/surefire-reports/**/TEST-*.xml', fingerprint: true
                }
            }
        }

        stage('Deploy SNAPSHOT to artifactory') {
            when {
                expression { return is219xBranch() }
            }
            steps {
                sh "mvn deploy -B -s .github/maven-settings.xml -DskipTests -Pjdk8 -Dartifactory.publish.buildInfo=true"
            }
        }

        stage('Deploy Release to artifactory') {
            when {
                expression { return isReleaseTag() }
            }
            steps {
                 sh "mvn deploy -B -s .github/maven-settings.xml -DskipTests -Pjdk8 -Dartifactory.publish.buildInfo=true"
            }
        }
    }
}


def isReleaseTag() {
    return (env.TAG_NAME =~ /^release-.*$/)
}

def isMainBranch() {
    return (env.BRANCH_NAME =~ /^master$/)
}

def is219xBranch() {
    return (env.BRANCH_NAME =~ /^2.19.x$/)
}
