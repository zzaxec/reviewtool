package de.setsoftware.reviewtool.plugin;

import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import de.setsoftware.reviewtool.base.Logger;
import de.setsoftware.reviewtool.config.ConfigurationInterpreter;
import de.setsoftware.reviewtool.ui.dialogs.DialogHelper;
import de.setsoftware.reviewtool.ui.views.ReviewModeListener;
import de.setsoftware.reviewtool.ui.views.ViewDataSource;

/**
 * Main class (i.e. "Activator") for the plugin.
 */
public class Activator extends AbstractUIPlugin {

    private static Activator plugin;

    @Override
    public void start(BundleContext context) throws Exception {
        plugin = this;
        Logger.setLogger(new Logger() {
            @Override
            protected void log(int status, String message) {
                Activator.this.getLog().log(
                        new Status(status, Activator.this.getBundle().getSymbolicName(), message));
            }

            @Override
            protected void log(int status, String message, Throwable exception) {
                Activator.this.getLog().log(
                        new Status(status, Activator.this.getBundle().getSymbolicName(), message, exception));
            }
        });
        DialogHelper.setPreferenceStore(this.getPreferenceStore());
        ViewDataSource.setInstance(new ViewDataSource() {
            @Override
            public void registerListener(ReviewModeListener l) {
                ReviewPlugin.getInstance().registerAndNotifyModeListener(l);
            }
        });
        this.initializeDefaultPreferences();
    }

    /**
     * Sets the default values for the preference store.
     */
    public void initializeDefaultPreferences() {
        final IPreferenceStore store = this.getPreferenceStore();
        store.setDefault(ConfigurationInterpreter.USER_PARAM_NAME, System.getProperty("user.name"));
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        DialogHelper.setPreferenceStore(null);
        Logger.setLogger(null);
    }

    public static Activator getDefault() {
        return plugin;
    }
}