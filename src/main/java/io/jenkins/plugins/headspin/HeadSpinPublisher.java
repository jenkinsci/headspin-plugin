package io.jenkins.plugins.headspin;

import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.ListBoxModel;
import hudson.util.FormValidation;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.Item;
import hudson.model.Result;
import hudson.tasks.Recorder;
import hudson.tasks.Publisher;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.security.ACL;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.export.Exported;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import net.sf.json.JSONObject;

import jenkins.model.ArtifactManager;
import jenkins.model.Jenkins;

import javax.servlet.ServletException;
import java.io.IOException;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.UUID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import java.lang.Thread;
import java.lang.ProcessBuilder;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.URL;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.JsonArray;


public class HeadSpinPublisher extends Recorder implements SimpleBuildStep {

    private HeadSpinCredentials apiTokenCredential;
    private final String apiToken;
    private final String appId;
    private List<HeadSpinDeviceSelector> devices;

    @DataBoundConstructor
    public HeadSpinPublisher(String apiToken, String appId, List<HeadSpinDeviceSelector> devices) {
        this.apiToken = apiToken;
        this.appId = appId;
        this.devices = devices;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public HeadSpinCredentials getApiToken() {
        return apiTokenCredential;
    }

    @Exported
    public String getAppId(){
        return appId;
    }

    @Exported
    public List<HeadSpinDeviceSelector> getDevices() {
        if(devices == null){
            devices = new ArrayList<>();
        }

        return Collections.unmodifiableList(devices);
    }
    
    public static String getHeadSpinDevices(String headspinApiToken) {
    	CloseableHttpClient httpclient = HttpClients.createDefault();
        String url = String.format("https://%s@api-canary.headspin.io/v0/devices", headspinApiToken);
        HttpGet httpget = new HttpGet(url);
        try {
			CloseableHttpResponse response = httpclient.execute(httpget);
			String res = EntityUtils.toString(response.getEntity());
			JsonObject jsonResponse = new JsonParser().parse(res).getAsJsonObject();
			if(jsonResponse.has("devices")){
				return jsonResponse.get("devices").getAsJsonArray().toString();
        	} else {
        		return "[]";
        	}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
			return "[]";
		} catch (IOException e) {
			e.printStackTrace();
			return "[]";
		}
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        try {
            // Get HeadSpin API Token user set
            apiTokenCredential = HeadSpinCredentials.getCredentials(run.getParent(), apiToken);
            if(apiTokenCredential == null) {
                listener.getLogger().println("Could not find HeadSpin API Token.");
                return;   
            }
            listener.getLogger().println(apiTokenCredential.getDecryptedApiToken());
            String token = apiTokenCredential.getDecryptedApiToken();
            
            // create Jenkins build id
            UUID uuid = UUID.randomUUID();
            String buildId = uuid.toString();

            // open http client to communicate via HeadSpin API
            CloseableHttpClient httpclient = HttpClients.createDefault();
            String url = null;
            HttpPost httppost = null;
            StringEntity postingString = null;
            CloseableHttpResponse response = null;
            String res = null;
            JsonObject jsonResponse = null;
            Map<String, Process> runningProcesses = new HashMap<String, Process>();
            Map<String, HeadSpinAction> runningActions = new HashMap<String, HeadSpinAction>();
            
            if(run.getArtifacts().size() <= 0) {
            	listener.getLogger().println("Could not find app file.");
                run.setResult(hudson.model.Result.FAILURE);
                return;
            }
            String appPath = run.getArtifactManager().root().toURI().getPath() + run.getArtifacts().get(0).relativePath;
            
            for(HeadSpinDeviceSelector test: devices) {
            	Map<String, String> deviceSelectorMap = new HashMap<String, String>();
            	if(test.getCarrier() != null && !test.getCarrier().equals(""))
            		deviceSelectorMap.put("carrier", test.getCarrier());
            	if(test.getDevice() != null && !test.getDevice().equals(""))
            		deviceSelectorMap.put("model", test.getDevice());
            	if(test.getLocation() != null && !test.getLocation().equals("")) {
            		String[] geo = test.getLocation().split(",");
            		if(geo.length > 0)
            			deviceSelectorMap.put("city", geo[0].trim());
            		if(geo.length > 1)
            			deviceSelectorMap.put("country", geo[1].trim());
            	}
            	String deviceSelector = new Gson().toJson(deviceSelectorMap);
            	listener.getLogger().println(deviceSelector);
            	
            	// lock device
                postingString = new StringEntity(deviceSelector);
                url = String.format("https://%s@api-canary.headspin.io/v0/devices/lock", token);
                httppost = new HttpPost(url);
                httppost.addHeader("Content-Type", "application/json");
                httppost.setEntity(postingString);
                response = httpclient.execute(httppost);
                res = EntityUtils.toString(response.getEntity());
                listener.getLogger().println(res);
                jsonResponse = new JsonParser().parse(res).getAsJsonObject();
                
                // if device is locked
                if(jsonResponse.get("status_code").getAsInt() == 200){
                    String hostname = jsonResponse.get("hostname").getAsString();
                    String serial = jsonResponse.get("serial").getAsString();
                    
                    // install
                    listener.getLogger().println(run.getArtifactManager().root().toURI().getPath());
                    listener.getLogger().println(run.getArtifacts().get(0).relativePath);
                    
                    if(appPath.endsWith(".apk")) {
                    	url = String.format("https://%s@api-canary.headspin.io/v0/adb/%s/install", token, serial);
    	               
                    } else if (appPath.endsWith(".ipa")) {
                    	url = String.format("https://%s@api-canary.headspin.io/v0/idevice/%s/installer/install", token, serial);
                    } else {
                    	listener.getLogger().println("App is neither apk nor ipa");
                    	run.setResult(hudson.model.Result.FAILURE);
                    	return;
                    }

	                httppost = new HttpPost(url);
                    FileEntity reqEntity = new FileEntity(new File(appPath));
	                httppost.setEntity(reqEntity);
	                response = httpclient.execute(httppost);
	                res = EntityUtils.toString(response.getEntity());
	                listener.getLogger().println(res);
	  
	                // unlock
	                postingString = new StringEntity(deviceSelector);
	                url = String.format("https://%s@api-canary.headspin.io/v0/devices/unlock", token);
	                httppost = new HttpPost(url);
	                httppost.addHeader("Content-Type", "application/json");
	                httppost.setEntity(postingString);
	                response = httpclient.execute(httppost);
	                res = EntityUtils.toString(response.getEntity());
	                listener.getLogger().println(res);
	                
	                // run scripts
                    ProcessBuilder builder = new ProcessBuilder();
                    Map<String, String> env = builder.environment();
                    env.put("HSJENKINS_DEVICE_ID", serial);
                    env.put("HSJENKINS_BUILD_ID", buildId);
                    env.put("HSJENKINS_DEVICE_URL", String.format("https://appium-canary.headspin.io/v0/%s/wd/hub", token));
                    env.put("HSJENKINS_PACKAGE_NAME", appId);
                    builder.command(test.getTestShellCommand().split(" "));
                    Process process = builder.start();
                    StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), listener.getLogger()::println);
                    Executors.newSingleThreadExecutor().submit(streamGobbler);
                    runningProcesses.put(serial, process);
                    HeadSpinAction action = new HeadSpinAction(buildId, serial);
                    run.addAction(action);
                    runningActions.put(serial, action);
                    
                }else{
                    String failedReason = jsonResponse.get("status").getAsString();
                    listener.getLogger().println(String.format("Locking %s Failed... %s", deviceSelector, failedReason));
                    run.setResult(hudson.model.Result.FAILURE);
                }
                
                for(String serial: runningProcesses.keySet()) {
	                int exitCode = runningProcesses.get(serial).waitFor();
                    listener.getLogger().print("Exit Code:");
                    listener.getLogger().println(exitCode);

                    if(exitCode != 0){
                        run.setResult(hudson.model.Result.UNSTABLE);
                        runningActions.get(serial).setResult("Failed");
                    } else {
                        runningActions.get(serial).setResult("Success");
                    }
	
	                // uninstall
	                if(appPath.endsWith(".apk")) {
	                	url = String.format("https://%s@api-canary.headspin.io/v0/adb/%s/uninstall?package=%s", token, serial, appId);
	                } else if (appPath.endsWith(".ipa")) {
	                	url = String.format("https://%s@api-canary.headspin.io/v0/idevice/%s/installer/uninstall?appid=%s", token, serial, appId);
	                }
	                httppost = new HttpPost(url);
	                response = httpclient.execute(httppost);
	                res = EntityUtils.toString(response.getEntity());
	                listener.getLogger().println(res);
                }
            }

            return;

        } catch (Exception e) {
            run.setResult(hudson.model.Result.FAILURE);
            e.printStackTrace(listener.getLogger());
        }
    }

    private static class StreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;
     
        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }
     
        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
              .forEach(consumer);
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private static String apiToken;

        public DescriptorImpl() {
            clazz.asSubclass(HeadSpinPublisher.class);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.HeadSpinPublisher_DescriptorImpl_DisplayName();
        }

        public ListBoxModel doFillApiTokenItems(@AncestorInPath final Item context, @QueryParameter String apiToken) {
            // StandardListBoxModel result = new StandardListBoxModel();

            if (context != null && !context.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }

            return new StandardListBoxModel()
            	.includeMatchingAs(
            			ACL.SYSTEM, 
            			context, 
            			HeadSpinCredentials.class, 
            			new ArrayList<DomainRequirement>(), 
            			CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(HeadSpinCredentials.class)));
        }

        public FormValidation doCheckApiToken(@AncestorInPath Item item, @QueryParameter String apiToken){
        	CredentialsMatcher credentialMatcher = null;
        	if(apiToken == null || apiToken.contentEquals("")) {
        		credentialMatcher = CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(HeadSpinCredentials.class));
        	} else {
        		credentialMatcher = CredentialsMatchers.withId(apiToken);
        	}
        	HeadSpinCredentials apiTokenCredential = CredentialsMatchers.firstOrNull(
        			CredentialsProvider.lookupCredentials(
        					HeadSpinCredentials.class, item ,ACL.SYSTEM, new ArrayList<DomainRequirement>()
        			), credentialMatcher
        	);
            if(apiTokenCredential == null) {
            	return FormValidation.error("Could not find HeadSpin API Token.");   
            }
            
            String headspinApiToken = apiTokenCredential.getDecryptedApiToken();
            HeadSpinDeviceSelector.setDevices(getHeadSpinDevices(headspinApiToken));
            
            return FormValidation.ok();
        }
        
        public FormValidation doCheckDevices(@AncestorInPath Item item, @QueryParameter String apiToken) {
        	return FormValidation.ok();
        }

        public String getApiToken() {
            return apiToken;
        }

        public void setApiToken(String apiToken) {
            this.apiToken = apiToken;
        }

    }

}
