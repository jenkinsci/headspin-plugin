package io.jenkins.plugins.headspin;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import static java.util.Arrays.asList;

import static hudson.Util.fixEmpty;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;


@ExportedBean
public class HeadSpinDeviceSelector extends AbstractDescribableImpl<HeadSpinDeviceSelector> implements Serializable {

    private static JsonArray headspinDevices;
    private final String location;
    private final String device;
    private final String carrier;
    private final String desiredCapabilities;
    private final String testShellCommand;

    @DataBoundConstructor
    public HeadSpinDeviceSelector(String location, String device, String carrier, String desiredCapabilities, String testShellCommand) {
        this.location = fixEmpty(location);
        this.device = fixEmpty(device);
        this.carrier = fixEmpty(carrier);
        this.desiredCapabilities = fixEmpty(desiredCapabilities);
        this.testShellCommand = fixEmpty(testShellCommand);
    }

    @Exported
    public String getLocation() {
        return location;
    }

    @Exported
    public String getDevice() {
        return device;
    }

    @Exported
    public String getCarrier() {
        return carrier;
    }

    @Exported
    public String getDesiredCapabilities() {
        return desiredCapabilities;
    }

    @Exported
    @CheckForNull
    public String getTestShellCommand() {
        return testShellCommand;
    }
    
    public static void setDevices(String devices) {
    	headspinDevices = new JsonParser().parse(devices).getAsJsonArray();
    }
    
    private static List<String> getDeviceInfo(String key, List<List<String>> matchers) {
    	ArrayList<String> info = new ArrayList<>();
    	if(headspinDevices == null) {
    		return info;
    	}
    	for(int i=0; i<headspinDevices.size(); i++) {
    		boolean match = true;
    		if(matchers != null) {
    			for(List<String> matcher: matchers) {
    				if(matcher.get(1) == null || matcher.get(1).equals(""))
    					continue;
    				try {
		    			if(!headspinDevices.get(i).getAsJsonObject().get(matcher.get(0)).getAsString().equals(matcher.get(1))) {
		    				match = false;
		    				break;
		    			}
    				} catch (Exception e){
    					// key does not exist
    					match = false;
	    				break;
    				}
    			}
    			if(!match)
    				continue;
    		}
    		try {
	    		if(!info.contains(headspinDevices.get(i).getAsJsonObject().get(key).getAsString()))
	    			info.add(headspinDevices.get(i).getAsJsonObject().get(key).getAsString());
    		} catch (Exception e) {
    			// key does not exist
    		}
    	}
    	return info;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<HeadSpinDeviceSelector> {

        public ListBoxModel doFillLocationItems(@AncestorInPath Item item, @QueryParameter String location){
            ListBoxModel items = new ListBoxModel();
            items.add("Not Set", "");
            for (String s : getDeviceInfo("geos", null)) {
            	items.add(s,s);
            }
            
            return items;
        }

        public ListBoxModel doFillDeviceItems(@AncestorInPath Item item, @QueryParameter String location){
            ListBoxModel items = new ListBoxModel();
            items.add("Not Set", "");
            for (String s : getDeviceInfo("model", asList(asList("geos", location))))
            	items.add(s,s);

            return items;
        }

        public ListBoxModel doFillCarrierItems(@AncestorInPath Item item, @QueryParameter String location, @QueryParameter String device){
        	ListBoxModel items = new ListBoxModel();
        	items.add("Not Set", "");
            for (String s : getDeviceInfo("carrier", asList(asList("geos", location), asList("model", device))))
            	items.add(s,s);

            return items;
        }

        @Override
        public String getDisplayName() {
            return "";
        }
    }
}