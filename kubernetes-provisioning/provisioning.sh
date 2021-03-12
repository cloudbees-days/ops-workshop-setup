PROJECT_ID=core-workshop

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
kubectl apply -f ./secrets-store-csi-gcp/provider-gcp-plugin.yaml

CBCI_HOSTNAME=staging-cbci.workshop.cb-sa.io
DNS_ZONE=workshop-cb-sa
#get ingress-nginx lb ip
INGRESS_IP=$(kubectl get services -n ingress-nginx | grep ingress-nginx-nginx-ingress-controller | awk '{print $4}')
#update DNS entry for CBCI above hostname to map to that IP
gcloud dns --project=$PROJECT_ID record-sets transaction start --zone=$DNS_ZONE
gcloud dns --project=$PROJECT_ID record-sets transaction add $INGRESS_IP --name=$CBCI_HOSTNAME. --ttl=300 --type=A --zone=$DNS_ZONE
gcloud dns --project=$PROJECT_ID record-sets transaction execute --zone=$DNS_ZONE

gcloud iam service-accounts add-iam-policy-binding \
  --role roles/iam.workloadIdentityUser \
  --member "serviceAccount:core-workshop.svc.id.goog[sda/jenkins]" \
  core-cloud-run@core-workshop.iam.gserviceaccount.com

kubectl create ns sda
kubectl -n sda create configmap cbci-oc-init-groovy --from-file=groovy-init/ --dry-run=client -o yaml | kubectl apply -f -
kubectl -n sda create configmap cbci-oc-quickstart-groovy --from-file=groovy-quickstart/ --dry-run=client -o yaml | kubectl apply -f -
kubectl -n sda create configmap cbci-op-casc-bundle --from-file=ops-config-bundle/ --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f ./k8s/cbci-jenkins-sa.yml
helm upgrade --install cbci cloudbees/cloudbees-core \
  --wait \
  --set OperationsCenter.HostName=$CBCI_HOSTNAME \
  --set nginx-ingress.Enabled=false \
  --set OperationsCenter.Ingress.tls.Host=$CBCI_HOSTNAME \
  --namespace='sda'  --create-namespace \
  --set-file 'OperationsCenter.ExtraGroovyConfiguration.z-quickstart-hook\.groovy'=./groovy-license-activated/z-quickstart-hook.groovy \
  --values ./helm/cbci-blue.yml
