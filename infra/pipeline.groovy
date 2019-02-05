def mvnCmd = "mvn -s configuration/settings.xml"

pipeline {
agent {
    label 'maven'
}
stages {
    stage('Build App') {
        steps {
            git branch: 'master', url: 'http://github.com/hodrigohamalho/thorntail-demo.git'
            sh "${mvnCmd} install -DskipTests=true"
        }
    }

    stage('Test') {
        steps {
            sh "${mvnCmd} test"
            step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
        }
    }

    stage('Analise de Codigo') {
        steps {
            script {
            //    sh "${mvnCmd} sonar:sonar -Dsonar.host.url=http://sonarqube:9000 -DskipTests=true"
                echo "Analise de codigo..."
            }
        }
    }

    stage('Upload nexus') {
        steps {
            // sh "${mvnCmd} deploy -DskipTests=true -P nexus3"
            echo "Uploading to nexus..."
        }
    }

    stage('Build Image') {
        steps {
            sh "cp target/thorntail-demo.war target/ROOT.war"
            script {
                openshift.withCluster() {
                    openshift.withProject(env.DEV_PROJECT) {
                        openshift.selector("bc", "thorntail-demo").startBuild("--from-file=target/ROOT.war", "--wait=true")
                    }
                }
            }
        }
    }
    stage('Deploy DEV') {
        steps {
            script {
                openshift.withCluster() {
                    openshift.withProject(env.DEV_PROJECT) {
                        openshift.selector("dc", "thorntail-demo").rollout().latest();
                    }
                }
            }
        }
    }
    stage('Promote to STAGE?') {
        agent {
            label 'skopeo'
        }
        steps {
            timeout(time:15, unit:'MINUTES') {
                input message: "Promote to STAGE?", ok: "Promote"
            }

            script {
                openshift.withCluster() {
                    openshift.tag("${env.DEV_PROJECT}/thorntail-demo:latest", "${env.STAGE_PROJECT}/tasks:stage")
                }
            }
        }
    }

    stage('Deploy STAGE') {
        steps {
            script {
                openshift.withCluster() {
                    openshift.withProject(env.STAGE_PROJECT) {
                        openshift.selector("dc", "thorntail-demo").rollout().latest();
                    }
                }
            }
        }
    }
}
}
