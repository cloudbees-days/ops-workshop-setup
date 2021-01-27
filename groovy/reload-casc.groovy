import hudson.ExtensionList;
import com.cloudbees.opscenter.client.casc.ConfigurationUpdaterHelper;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundle;
import com.cloudbees.jenkins.cjp.installmanager.casc.ConfigurationBundleManager;
import com.cloudbees.opscenter.client.casc.ConfigurationBundleService;
import com.cloudbees.opscenter.client.casc.ConfigurationStatus;


ConfigurationUpdaterHelper.checkForUpdates();
ConfigurationBundleService service = ExtensionList.lookupSingleton(ConfigurationBundleService.class);
ConfigurationBundle bundle = ConfigurationBundleManager.get().getConfigurationBundle();
service.reloadIfIsHotReloadable(bundle);

ConfigurationStatus.INSTANCE.setUpdateAvailable(false);
ConfigurationStatus.INSTANCE.setOutdatedVersion(null);
