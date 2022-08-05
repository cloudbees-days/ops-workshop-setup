# Example: ./provision.sh -p core-workshop -h staging -n cbci -c cbci-workshop-pink
# prod example: ./provision.sh -p core-workshop -h production -n cbci -c cbci-workshop-green
# kubectl -n cbci exec --stdin --tty cjoc-0 -- /bin/bash
# kubectl -n controllers exec --stdin --tty bee-ci-controller-0 -- /bin/bash
# helm ls --namespace cbci
# helm rollback cbci 2 --namespace cbci
# helm -n cbci uninstall cbci
# kubectl delete pod jcasc-controllers-secrets-store-inline -n controllers --grace-period=0 --force


PROJECT_ID=core-workshop
DEPLOY_ENV=staging
NAMESPACE=cbci
CLUSTER_NAME=cbci-workshop-green

while getopts ":p:h:n:c:" option; do
   case $option in
      p) # gcp project id
         PROJECT_ID=${OPTARG};;
      h) # cbci deployment environment target - staging or production
         DEPLOY_ENV=${OPTARG};;
      n) # cbci namespace
         NAMESPACE=${OPTARG};;
      c) # GKE cluster name
         CLUSTER_NAME=${OPTARG};;
     \?) # Invalid option
         echo "Error: Invalid option"
         exit;;
   esac
done

echo "PROJECT_ID = ${PROJECT_ID}"
echo "DEPLOY_ENV = ${DEPLOY_ENV}"
echo "NAMESPACE = ${NAMESPACE}"
echo "CLUSTER_NAME = ${CLUSTER_NAME}"

HOST_PREFIX=""
if [ "$DEPLOY_ENV" != "production" ]; then
  HOST_PREFIX="${DEPLOY_ENV}-"
fi
echo "HOST_PREFIX = $HOST_PREFIX"

# SMEE_HOSTNAME="staging-smee.workshop.cb-sa.io"
# PROD SMEE_HOSTNAME="smee.workshop.cb-sa.io"
SMEE_HOSTNAME="${HOST_PREFIX}smee.workshop.cb-sa.io"
# CBCI_HOSTNAME="staging-cbci.workshop.cb-sa.io"
# PROD CBCI_HOSTNAME="cbci.workshop.cb-sa.io"
CBCI_HOSTNAME="${HOST_PREFIX}cbci.workshop.cb-sa.io"
DNS_ZONE=workshop-cb-sa

echo "CBCI_HOSTNAME = $CBCI_HOSTNAME"

gcloud config set project $PROJECT_ID
gcloud beta container --project "${PROJECT_ID}" clusters create $CLUSTER_NAME \
  --region "us-east1" --no-enable-basic-auth --release-channel "regular" \
  --machine-type "n1-standard-8" --image-type "COS_CONTAINERD" --disk-type "pd-ssd" --disk-size "40" \
  --service-account "gke-nodes-for-workshop-testing@${PROJECT_ID}.iam.gserviceaccount.com" \
  --enable-autoscaling --min-nodes "0" --max-nodes "40" \
  --addons HorizontalPodAutoscaling,HttpLoadBalancing,GcePersistentDiskCsiDriver \
  --logging=SYSTEM,WORKLOAD --monitoring=SYSTEM \
  --enable-autoupgrade --enable-autorepair --max-surge-upgrade 1 --max-unavailable-upgrade 0 \
  --maintenance-window-start "2020-08-10T04:00:00Z" --maintenance-window-end "2020-08-11T04:00:00Z" --maintenance-window-recurrence "FREQ=WEEKLY;BYDAY=SA,SU" \
  --enable-dataplane-v2 \
  --autoscaling-profile optimize-utilization --workload-pool "${PROJECT_ID}.svc.id.goog" --node-locations "us-east1-b","us-east1-c"

helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo add jetstack https://charts.jetstack.io
helm repo add secrets-store-csi-driver https://kubernetes-sigs.github.io/secrets-store-csi-driver/charts
helm repo add cloudbees https://charts.cloudbees.com/public/cloudbees
helm repo add smee-server https://cloudbees-days.github.io/smee.io/

helm repo update

helm upgrade --install --wait ingress-nginx ingress-nginx/ingress-nginx \
    -n ingress-nginx --create-namespace --version 4.0.19 \
    -f ./helm/ingress-nginx.yaml

helm upgrade --install cert-manager jetstack/cert-manager --namespace cert-manager --create-namespace \
  --version v1.7.2 \
  --set global.leaderElection.namespace=cert-manager  --set prometheus.enabled=false \
  --set extraArgs={--issuer-ambient-credentials=true} \
  --set installCRDs=true --wait

kubectl annotate serviceaccount --namespace=cert-manager cert-manager \
    "iam.gke.io/gcp-service-account=dns01-solver@$PROJECT_ID.iam.gserviceaccount.com"

helm upgrade --install csi-secrets-store secrets-store-csi-driver/secrets-store-csi-driver --set syncSecret.enabled=true
#install GCP secrets-store-csi-driver
git clone https://github.com/GoogleCloudPlatform/secrets-store-csi-driver-provider-gcp.git
kubectl apply -f ./secrets-store-csi-driver-provider-gcp/deploy/provider-gcp-plugin.yaml

#get ingress-nginx lb ip
INGRESS_IP=$(kubectl get services -n ingress-nginx | grep LoadBalancer | awk '{print $4}')
gcloud dns record-sets delete $SMEE_HOSTNAME. --type=A --zone=$DNS_ZONE
#create DNS entry for smee above hostname to map to that IP
gcloud dns --project=$PROJECT_ID record-sets transaction start --zone=$DNS_ZONE
gcloud dns --project=$PROJECT_ID record-sets transaction add $INGRESS_IP --name=$SMEE_HOSTNAME. --ttl=300 --type=A --zone=$DNS_ZONE
gcloud dns --project=$PROJECT_ID record-sets transaction execute --zone=$DNS_ZONE

gcloud dns record-sets delete $CBCI_HOSTNAME. --type=A --zone=$DNS_ZONE
#create DNS entry for CBCI above hostname to map to that IP
gcloud dns --project=$PROJECT_ID record-sets transaction start --zone=$DNS_ZONE
gcloud dns --project=$PROJECT_ID record-sets transaction add $INGRESS_IP --name=$CBCI_HOSTNAME. --ttl=300 --type=A --zone=$DNS_ZONE
gcloud dns --project=$PROJECT_ID record-sets transaction execute --zone=$DNS_ZONE

gcloud iam service-accounts add-iam-policy-binding \
  --role roles/iam.workloadIdentityUser \
  --member "serviceAccount:${PROJECT_ID}.svc.id.goog[cbci/jenkins]" \
  workshop-controllers@${PROJECT_ID}.iam.gserviceaccount.com

gcloud iam service-accounts add-iam-policy-binding \
  --role roles/iam.workloadIdentityUser \
  --member "serviceAccount:${PROJECT_ID}.svc.id.goog[cbci/cjoc]" \
  workshop-controllers@${PROJECT_ID}.iam.gserviceaccount.com

chmod +x kustomize-wrapper.sh
helm upgrade --install cbci cloudbees/cloudbees-core \
  --wait --debug \
  --set OperationsCenter.HostName=$CBCI_HOSTNAME \
  --set OperationsCenter.Ingress.tls.Host=$CBCI_HOSTNAME \
  --namespace=$NAMESPACE  --create-namespace \
  --set-file 'OperationsCenter.ExtraGroovyConfiguration.z-quickstart-hook\.groovy'=./config/groovy-license-activated/z-quickstart-hook.groovy \
  --values ./helm/cbci.yml --post-renderer ./kustomize-wrapper.sh

#kubectl cp --namespace $NAMESPACE casc/base cjoc-0:/var/jenkins_config/jcasc-bundles-store/
#kubectl cp --namespace $NAMESPACE casc/ops cjoc-0:/var/jenkins_config/jcasc-bundles-store/  

helm upgrade --install smee smee/smee --wait  --set autoscaling.enabled=false --set ingress.host=$SMEE_HOSTNAME --set serviceAccount.name=jenkins --namespace=$NAMESPACE --create-namespace

cd controllers

gcloud iam service-accounts add-iam-policy-binding \
  --role roles/iam.workloadIdentityUser \
  --member "serviceAccount:${PROJECT_ID}.svc.id.goog[controllers/jenkins]" \
  workshop-controllers@${PROJECT_ID}.iam.gserviceaccount.com

chmod +x kustomize-wrapper.sh
kubectl apply -f ./resources/controllers-namespace.yaml
helm upgrade --install controllers cloudbees/cloudbees-core \
  --wait \
  --set OperationsCenter.HostName=$CBCI_HOSTNAME \
  --set OperationsCenter.Ingress.tls.Host=$CBCI_HOSTNAME \
  --namespace='controllers'  \
  --values ./helm/controllers-values.yml --post-renderer ./kustomize-wrapper.sh

