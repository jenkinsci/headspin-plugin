package io.jenkins.plugins.headspin;

import hudson.model.Run;
import jenkins.model.RunAction2;
import java.util.logging.Level;
import java.util.logging.Logger;


public class HeadSpinAction implements RunAction2 {

    /**
     * Logger instance.
     */
    private static final Logger logger = Logger.getLogger(HeadSpinAction.class.getName());

    private transient Run run;
    private String buildId;
    private String deviceId;
    private String result;

    public HeadSpinAction(String buildId, String deviceId) {
        this.buildId = buildId;
        this.deviceId = deviceId;
        this.result = "In Process...";
    }

    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run; 
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run; 
    }

    @Override
    public String getIconFileName() {
        return "/plugin/demo/icons/headspin-logo-3-96.png"; 
    }

    @Override
    public String getDisplayName() {
        return "HeadSpin Report"; 
    }

    @Override
    public String getUrlName() {
        return "headspin-report-"+deviceId; 
    }

    public Run getRun() { 
        return run;
    }

    public String getBuildId() {
        return buildId;
    }

    public void setBuildId(String buildId){
        this.buildId = buildId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId){
        this.deviceId = deviceId;
    }

    public String getResult(){
        return result;
    }

    public void setResult(String result){
        this.result = result;
    }
}