import jenkins.model.Jenkins
import hudson.model.Describable
import hudson.security.AuthorizationStrategy
import hudson.security.Permission
import hudson.security.HudsonPrivateSecurityRealm
import nectar.plugins.rbac.groups.*
import nectar.plugins.rbac.strategy.DefaultRoleMatrixAuthorizationConfig
import nectar.plugins.rbac.strategy.RoleMatrixAuthorizationConfig
import nectar.plugins.rbac.strategy.RoleMatrixAuthorizationPlugin
import nectar.plugins.rbac.strategy.RoleMatrixAuthorizationStrategyImpl
import com.cloudbees.hudson.plugins.folder.*
import com.cloudbees.opscenter.server.security.SecurityEnforcer
import com.cloudbees.opscenter.server.sso.SecurityEnforcerImpl
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.List
import java.util.Map
import java.util.Set
import hudson.model.Item
import hudson.model.User
import java.util.logging.Logger

String scriptName = "02-configure-rbac.groovy"
int version = 1

int markerVersion = 0
Logger logger = Logger.getLogger(scriptName)

logger.info("Migrating from version $markerVersion to version $version")

Jenkins jenkins = Jenkins.getInstance()

AuthorizationStrategy authorizationStrategy = jenkins.getAuthorizationStrategy()

String authorizationStrategyBefore = authorizationStrategy.getClass().getName()

HudsonPrivateSecurityRealm hudsonPrivateSecurityRealm = new HudsonPrivateSecurityRealm(true, false, null)
jenkins.setSecurityRealm(hudsonPrivateSecurityRealm)

    String ROLE_ADMINISTER = "administer";
	  String ROLE_WORKSHOP_ADMIN = "workshop-admin"
    String ROLE_BROWSE = "browse";

     RoleMatrixAuthorizationPlugin matrixAuthorizationPlugin = RoleMatrixAuthorizationPlugin.getInstance()
     RoleMatrixAuthorizationConfig config = new DefaultRoleMatrixAuthorizationConfig();
     RoleMatrixAuthorizationStrategyImpl roleMatrixAuthorizationStrategy = new RoleMatrixAuthorizationStrategyImpl()
     jenkins.setAuthorizationStrategy(roleMatrixAuthorizationStrategy)

     Map<String, Set<String>> roles = new HashMap<String, Set<String>>();
     for (Permission p : Permission.getAll()) {
         roles.put(p.getId(), new HashSet<String>(Collections.singleton(ROLE_WORKSHOP_ADMIN)));
     }
	   
     //for admin role
	   roles.get(Jenkins.ADMINISTER.getId()).add(ROLE_ADMINISTER);

     roles.get(Jenkins.READ.getId()).add(ROLE_BROWSE);
     roles.get(Item.DISCOVER.getId()).add(ROLE_BROWSE);
     roles.get(Item.READ.getId()).add(ROLE_BROWSE);
	

     config.setRolesByPermissionIdMap(roles);
     config.setFilterableRoles(new HashSet<String>(Arrays.asList(ROLE_BROWSE, ROLE_WORKSHOP_ADMIN)));
     List<Group> rootGroups = new ArrayList<Group>();
     Group g = new Group("Administrators");
     List<String> adminMembers = new ArrayList<String>();
     adminMembers.add("admin")
     g.setMembers(adminMembers);
     g.setRoleAssignments(Collections.singletonList(new Group.RoleAssignment(ROLE_ADMINISTER)));
     rootGroups.add(g);
     g = new Group("Browsers");
     g.setMembers(Collections.singletonList("authenticated"));
     g.setRoleAssignments(Collections.singletonList(new Group.RoleAssignment(ROLE_BROWSE)));
     rootGroups.add(g);
     config.setGroups(rootGroups);

     matrixAuthorizationPlugin.configuration = config
     matrixAuthorizationPlugin.save()
     logger.info("RBAC Roles and Groups defined")

    // Set Client Master Security to SSO
    SecurityEnforcer ssoSecurity = new SecurityEnforcerImpl(false, false, null)
    SecurityEnforcer.GlobalConfigurationImpl securityEnforcerConfig = Jenkins.getInstance().getExtensionList(Describable.class).get(SecurityEnforcer.GlobalConfigurationImpl.class);
    securityEnforcerConfig.setGlobal(ssoSecurity)