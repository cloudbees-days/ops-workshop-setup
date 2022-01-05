PROJECT_ID=core-workshop
gcloud config set project $PROJECT_ID
CLUSTER_NAME=cbci-workshop-pink
gcloud beta container --project "core-workshop" clusters create $CLUSTER_NAME \
  --region "us-east1" --no-enable-basic-auth --release-channel "regular" \
  --machine-type "n1-standard-8" --image-type "COS_CONTAINERD" --disk-type "pd-ssd" --disk-size "50" \
  --service-account "gke-nodes-for-workshop-testing@core-workshop.iam.gserviceaccount.com" \
  --enable-autoscaling --min-nodes "0" --max-nodes "30" \
  --addons HorizontalPodAutoscaling,HttpLoadBalancing,GcePersistentDiskCsiDriver \
  --enable-autoupgrade --enable-autorepair --max-surge-upgrade 1 --max-unavailable-upgrade 0 \
  --maintenance-window-start "2020-08-10T04:00:00Z" --maintenance-window-end "2020-08-11T04:00:00Z" --maintenance-window-recurrence "FREQ=WEEKLY;BYDAY=SA,SU" \
  --enable-dataplane-v2 \
  --autoscaling-profile optimize-utilization --workload-pool "core-workshop.svc.id.goog" --node-locations "us-east1-b","us-east1-c"

kubectl apply -f hnc-apply-spec.yaml

helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo add jetstack https://charts.jetstack.io
helm repo add secrets-store-csi-driver https://raw.githubusercontent.com/kubernetes-sigs/secrets-store-csi-driver/master/charts
helm repo add cloudbees https://charts.cloudbees.com/public/cloudbees
helm repo add smee-server https://cloudbees-days.github.io/smee.io/

helm repo update

helm upgrade --install --wait ingress-nginx stable/nginx-ingress \
    -n ingress-nginx --create-namespace --version 1.25.0 \
    -f ./helm/ingress-nginx.yml

helm upgrade --install cert-manager jetstack/cert-manager --namespace cert-manager --create-namespace --version v1.2.0 --set installCRDs=true --wait
kubectl apply -f ./k8s/cluster-issuers.yml

helm upgrade --install csi-secrets-store secrets-store-csi-driver/secrets-store-csi-driver
#install GCP secrets-store-csi-driver
git clone https://github.com/GoogleCloudPlatform/secrets-store-csi-driver-provider-gcp.git
kubectl apply -f ./secrets-store-csi-driver-provider-gcp/deploy/provider-gcp-plugin.yaml

CBCI_HOSTNAME=pink.workshop.cb-sa.io
DNS_ZONE=workshop-cb-sa
#get ingress-nginx lb ip
INGRESS_IP=$(kubectl get services -n ingress-nginx | grep ingress-nginx-nginx-ingress-controller | awk '{print $4}')
gcloud dns record-sets delete $CBCI_HOSTNAME. --type=A --zone=$DNS_ZONE
#create DNS entry for CBCI above hostname to map to that IP
gcloud dns --project=$PROJECT_ID record-sets transaction start --zone=$DNS_ZONE
gcloud dns --project=$PROJECT_ID record-sets transaction add $INGRESS_IP --name=$CBCI_HOSTNAME. --ttl=300 --type=A --zone=$DNS_ZONE
gcloud dns --project=$PROJECT_ID record-sets transaction execute --zone=$DNS_ZONE

# Select the latest version of HNC
HNC_VERSION=v0.9.0

# Install HNC. Afterwards, wait up to 30s for HNC to refresh the certificates on its webhooks.
kubectl apply -f https://github.com/kubernetes-sigs/hierarchical-namespaces/releases/download/${HNC_VERSION}/hnc-manager.yaml 

gcloud iam service-accounts add-iam-policy-binding \
  --role roles/iam.workloadIdentityUser \
  --member "serviceAccount:core-workshop.svc.id.goog[cbci/jenkins]" \
  core-cloud-run@core-workshop.iam.gserviceaccount.com

chmod +x kustomize-wrapper.sh
helm upgrade --install cbci cloudbees/cloudbees-core \
  --wait \
  --set OperationsCenter.HostName=$CBCI_HOSTNAME \
  --set nginx-ingress.Enabled=false \
  --set OperationsCenter.Ingress.tls.Host=$CBCI_HOSTNAME \
  --namespace='cbci'  --create-namespace \
  --set-file 'OperationsCenter.ExtraGroovyConfiguration.z-quickstart-hook\.groovy'=./config/groovy-license-activated/z-quickstart-hook.groovy \
  --values ./helm/cbci.yml --post-renderer ./kustomize-wrapper.sh

cd controllers
chmod +x kustomize-wrapper.sh
helm upgrade --install controllers cloudbees/cloudbees-core \
  --wait \
  --set OperationsCenter.HostName=$CBCI_HOSTNAME \
  --set nginx-ingress.Enabled=false \
  --set OperationsCenter.Ingress.tls.Host=$CBCI_HOSTNAME \
  --namespace='controllers'  \
  --values ./helm/controllers-values.yml --post-renderer ./kustomize-wrapper.sh

