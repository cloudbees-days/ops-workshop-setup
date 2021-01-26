//only runs on CJOC
import com.cloudbees.masterprovisioning.kubernetes.KubernetesMasterProvisioning
import com.cloudbees.opscenter.server.casc.BundleStorage
import com.cloudbees.opscenter.server.model.ManagedMaster
import com.cloudbees.opscenter.server.model.OperationsCenter
import com.cloudbees.opscenter.server.security.SecurityEnforcer
import com.cloudbees.opscenter.server.sso.AuthorizationOptOutMode
import jenkins.model.Jenkins
import hudson.*
import hudson.model.*

String masterName = "REPLACE_CONTROLLER_NAME" 

println "Master '${masterName}' already exists. Updating it."
ManagedMaster managedMaster = OperationsCenter.getInstance().getConnectedMasters().find { it.name == masterName } as ManagedMaster

//needed for CasC RBAC
managedMaster.properties.replace(new SecurityEnforcer.OptOutProperty(AuthorizationOptOutMode.INSTANCE, false, null))
managedMaster.save()
managedMaster.onModified()
