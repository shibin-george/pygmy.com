package pygmy.com.scheduler;

public class PygmyJob<JobIDType, JobParameterType, LoadBalancerType> {

    private String jobType = "";
    private JobIDType jobId = null;
    private LoadBalancerType servedBy = null;
    private JobParameterType parameter = null;

    public PygmyJob(JobIDType jId, String type, LoadBalancerType server,
            JobParameterType p) {
        jobId = jId;
        jobType = type;
        servedBy = server;
        parameter = p;
    }

    public JobIDType getJobId() {
        return jobId;
    }

    public String getJobType() {
        return this.jobType;
    }

    public LoadBalancerType getServedBy() {
        return this.servedBy;
    }

    public JobParameterType getParameter() {
        return this.parameter;
    }

}
