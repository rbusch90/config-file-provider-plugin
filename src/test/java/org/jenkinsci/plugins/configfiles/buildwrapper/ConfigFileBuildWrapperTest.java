package org.jenkinsci.plugins.configfiles.buildwrapper;

import hudson.ExtensionList;
import hudson.Launcher;
import hudson.maven.MavenModuleSet;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.Cause.UserCause;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.tasks.Builder;

import java.io.IOException;
import java.util.Collections;

import javax.inject.Inject;

import jenkins.model.Jenkins;

import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.ConfigFilesManagement;
import org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig.MavenSettingsConfigProvider;
import org.jenkinsci.plugins.configfiles.xml.XmlConfig.XmlConfigProvider;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.JenkinsRule;

@Ignore
public class ConfigFileBuildWrapperTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Inject
    ConfigFilesManagement configManagement;

    @Inject
    MavenSettingsConfigProvider mavenSettingProvider;

    @Inject
    XmlConfigProvider xmlProvider;
    

    @Test
    public void envVariableMustBeAvailableInMavenModuleSetBuild() throws Exception {
        j.jenkins.getInjector().injectMembers(this);

        final ExtensionList<ConfigProvider> all = ConfigProvider.all();
        final ExtensionList<ConfigProvider> extensionList = j.jenkins.getInstance().getExtensionList(ConfigProvider.class);        
        
        final MavenModuleSet p = j.createMavenProject("mvn");

        // p.getBuildWrappersList().add(new ConfigFileBuildWrapper(managedFiles))
        p.setMaven(j.configureMaven3().getName());
        p.setScm(new ExtractResourceSCM(getClass().getResource("/org/jenkinsci/plugins/configfiles/maven3-project.zip")));
        p.setGoals("initialize"); // -s ${MVN_SETTING}

        final Config settings = createSetting(xmlProvider);
        final ManagedFile mSettings = new ManagedFile(settings.id, "/tmp/new_settings.xml", "MY_SETTINGS");
        ConfigFileBuildWrapper bw = new ConfigFileBuildWrapper(Collections.singletonList(mSettings));
        p.getBuildWrappersList().add(bw);

        ParametersDefinitionProperty parametersDefinitionProperty = new ParametersDefinitionProperty(new StringParameterDefinition("MVN_SETTING", "/tmp/settings.xml"));
        p.addProperty(parametersDefinitionProperty);
        p.getPostbuilders().add(new VerifyBuilder("MVN_SETTING", "/tmp/settings.xml"));

        j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, new UserCause()).get());
    }

    private static final class VerifyBuilder extends Builder {
        private final String var, expectedValue;

        public VerifyBuilder(String var, String expectedValue) {
            this.var = var;
            this.expectedValue = expectedValue;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            try {
                final String expanded = TokenMacro.expandAll(build, listener, "${ENV, var=\"" + var + "\"}");
                System.out.println("-->" + expanded);
                Assert.assertEquals(expectedValue, expanded);
            } catch (MacroEvaluationException e) {
                Assert.fail("not able to expand var: " + e.getMessage());
            }
            return true;
        }
    }

    private Config createSetting(ConfigProvider provider) {
        Config c1 = provider.newConfig();
        provider.save(c1);
        return c1;
    }
}
