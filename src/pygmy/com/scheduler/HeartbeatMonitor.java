package pygmy.com.scheduler;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class HeartbeatMonitor<JobIDType, JobParameterType, LoadBalancerType, JobResponseType> {

    private HashMap<JobIDType, Long> requestStartTimeStateMachine;
    private HashMap<JobIDType, PygmyJob<JobIDType, JobParameterType, LoadBalancerType>> jobMap;
    private RoundRobinLoadBalancer<LoadBalancerType> loadBalancer;
    private HashMap<JobIDType, JobResponseType> responseMap;

    private Long JOB_TIMEOUT_IN_MILLISECONDS = (long) 2000;

    public HeartbeatMonitor(RoundRobinLoadBalancer<LoadBalancerType> loadBalancer, long timeout) {
        this.loadBalancer = loadBalancer;
        requestStartTimeStateMachine = new HashMap<JobIDType, Long>();
        jobMap = new HashMap<JobIDType, PygmyJob<JobIDType, JobParameterType, LoadBalancerType>>();
        responseMap =
                new HashMap<JobIDType, JobResponseType>();
        JOB_TIMEOUT_IN_MILLISECONDS = timeout;
    }

    public synchronized void markJobStarted(
            PygmyJob<JobIDType, JobParameterType, LoadBalancerType> job) {
        requestStartTimeStateMachine.put(job.getJobId(), System.currentTimeMillis());
        jobMap.put(job.getJobId(), job);
    }

    public synchronized void markJobCompleted(JobIDType s) {
        if (requestStartTimeStateMachine.containsKey(s)) {
            System.out.println(
                    getTime() + "Marking Job: " + s + " as complete!");
            requestStartTimeStateMachine.remove(s);
        }
    }

    public synchronized boolean isJobComplete(JobIDType s) {

        if (requestStartTimeStateMachine.containsKey(s) || !responseMap.containsKey(s)) {
            return false;
        }

        return true;
    }

    public synchronized ArrayList<PygmyJob<JobIDType, JobParameterType, LoadBalancerType>> getIncompleteJobs() {

        Long currentTime = System.currentTimeMillis();
        ArrayList<PygmyJob<JobIDType, JobParameterType, LoadBalancerType>> incompleteJobs =
                new ArrayList<PygmyJob<JobIDType, JobParameterType, LoadBalancerType>>();

        // iterate through all the jobs in the hashmap and transfer
        // these jobs that were started more than 5 seconds ago
        // and return these to the requester.
        Iterator<Entry<JobIDType, Long>> it =
                requestStartTimeStateMachine.entrySet().iterator();
        HashSet<LoadBalancerType> faultyServers = new HashSet<LoadBalancerType>();
        while (it.hasNext()) {
            Map.Entry<JobIDType, Long> entry = it.next();
            if (currentTime - entry.getValue() > JOB_TIMEOUT_IN_MILLISECONDS) {
                // System.out.println("crap on a cracker " + (currentTime - entry.getValue()));
                faultyServers.add((LoadBalancerType) jobMap.get(entry.getKey()).getServedBy());
                incompleteJobs.add(jobMap.get(entry.getKey()));
            }
        }

        for (LoadBalancerType server : faultyServers) {
            System.out.println(getTime() + "Server: " + server
                    + " has faulted. Asking LoadBalancer to remove this server..");
            loadBalancer.remove(server);
        }

        return incompleteJobs;
    }

    public synchronized JobResponseType getResponse(String jobId) {
        if (responseMap.containsKey(jobId)) {
            return responseMap.get(jobId);
        }
        return null;
    }

    public synchronized void cleanupJob(JobIDType jobId) {
        if (responseMap.containsKey(jobId))
            responseMap.remove(jobId);
        if (jobMap.containsKey(jobId))
            jobMap.remove(jobId);
    }

    public synchronized void addResponse(JobIDType jobId, JobResponseType response) {
        responseMap.put(jobId, response);
    }

    // Used to get time in a readable format for logging
    private static String getTime() {
        long milliSeconds = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy; HH:mm:ss.SSS");
        Date resultdate = new Date(milliSeconds);
        return ("HeartbeatMonitor : tid=" + Thread.currentThread().getId() + " : "
                + sdf.format(resultdate) + " :: ");
    }

}
