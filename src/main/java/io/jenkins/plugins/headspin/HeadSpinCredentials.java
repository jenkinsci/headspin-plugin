package io.jenkins.plugins.headspin;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.verb.POST;

import com.cloudbees.plugins.credentials.BaseCredentials;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.model.User;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;


@NameWith(value = HeadSpinCredentials.NameProvider.class)
public class HeadSpinCredentials extends BaseCredentials implements StandardCredentials {

    private static final String CREDENTIAL_DISPLAY_NAME = "HeadSpin API Token";
    private static final String OK_VALID_AUTH = "Success";
    private static final String ERR_INVALID_AUTH = "Invalid HeadSpin Token!";

    private final String id;

    private final String description;

    private final Secret apiToken;


    @DataBoundConstructor
    public HeadSpinCredentials(String id, String description, String apiToken) {
        super(CredentialsScope.GLOBAL);
        this.id = IdCredentials.Helpers.fixEmptyId(id);
        this.description = Util.fixNull(description);
        this.apiToken = Secret.fromString(apiToken);
    }

    @Exported
    public Secret getApiToken() {
        return apiToken;
    }

    public boolean hasApiToken() {
        return apiToken != null;
    }

    public String getDecryptedApiToken() {
        return apiToken.getPlainText();
    }

    @Exported
    public String getDescription() {
        return description;
    }

    @NonNull
    @Exported
    public String getId() {
        return id;
    }

    @Override
    public final boolean equals(Object o) {
        return IdCredentials.Helpers.equals(this, o);
    }

    @Override
    public final int hashCode() {
        return IdCredentials.Helpers.hashCode(this);
    }

    public static HeadSpinCredentials getCredentials(final AbstractItem buildItem, final String credentialsId) {
        List<HeadSpinCredentials> available = availableCredentials(buildItem);
        if (available.isEmpty()) {
            return null;
        }

        CredentialsMatcher matcher;
        if (credentialsId != null) {
            matcher = CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId));
        } else {
            matcher = CredentialsMatchers.always();
        }

        return CredentialsMatchers.firstOrDefault(
                available,
                matcher,
                available.get(0));
    }

    public static List<HeadSpinCredentials> availableCredentials(final AbstractItem abstractItem) {
        return CredentialsProvider.lookupCredentials(
        		HeadSpinCredentials.class,
                abstractItem,
                null,
                new ArrayList<DomainRequirement>());
    }

    @Extension(ordinal = 1.0D)
    public static class DescriptorImpl extends CredentialsDescriptor {

        public DescriptorImpl() {
            clazz.asSubclass(HeadSpinCredentials.class);
        }

        public DescriptorImpl(Class<? extends BaseStandardCredentials> clazz) {
            super(clazz);
        }

        @POST
        public final FormValidation doTestConnection(@QueryParameter("apiToken") String apiToken) throws IOException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (StringUtils.isBlank(apiToken)) {
                return FormValidation.error("HeadSpin API Token is empty!");
            }

            CloseableHttpClient httpclient = HttpClients.createDefault();
            try{
                String endpoint = String.format("https://%s@api-dev.headspin.io/v0/devices", apiToken);
                HttpGet httpget = new HttpGet(endpoint);
                CloseableHttpResponse response = httpclient.execute(httpget);
                try{
                    int statusCode = response.getStatusLine().getStatusCode();
                    if(statusCode != 200) {
                    	return FormValidation.error("Invalid HeadSpin API Token!");
                    }
                } catch(Exception e){
                    return FormValidation.error("Unstable Connection.");
                } finally{
                    response.close();
                }
            } catch (RuntimeException e) {
                throw e;
            } catch(Exception e){
                return FormValidation.error("Unstable Connection.");
            } finally {
                httpclient.close();
            }

            return FormValidation.ok("Usable HeadSpin API!");
        }

        @Override
        public String getDisplayName() {
            return CREDENTIAL_DISPLAY_NAME;
        }

        /**
         * @return always returns false since the scope of Local credentials are always Global.
         */
        @Override
        public boolean isScopeRelevant() {
            return false;
        }

        /**
         * @return always returns false since the scope of Local credentials are always Global.
         */
        @SuppressWarnings("unused")
        public boolean isScopeRelevant(ModelObject object) {
            return false;
        }
    }

    public static class NameProvider extends CredentialsNameProvider<HeadSpinCredentials> {

        @Override
        public String getName(HeadSpinCredentials credentials) {
            String description = Util.fixEmptyAndTrim(credentials.getDescription());
            return "******" + (description != null ? " (" + description + ")" : "");
        }
    }
}