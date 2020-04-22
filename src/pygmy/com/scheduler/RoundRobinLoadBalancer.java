package pygmy.com.scheduler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class RoundRobinLoadBalancer<T> {

    private BlockingQueue<T> blockingQueue = null;

    private int identifer;
    private HashMap<T, Integer> serverSet;

    public RoundRobinLoadBalancer(int capacity) {
        blockingQueue = new ArrayBlockingQueue<T>(capacity, true);
        serverSet = new HashMap<T, Integer>();
    }

    public synchronized T get() throws InterruptedException {

        // poll from the head of the queue
        T head = blockingQueue.take();

        System.out.println(getTime() + "Forwarding request to " + head);

        // add the head element back to the queue
        blockingQueue.put(head);
        return head;
    }

    public synchronized void remove(T t) {
        System.out.println(getTime() + "Removing server: " + t + " from the load-balancer!");
        // remove all instances of t from the load-balancer's queue
        blockingQueue.removeIf(e -> (t.equals(e)));

        if (serverSet.containsKey(t))
            serverSet.remove(t);
    }

    public synchronized void add(T t) throws InterruptedException {
        if (!serverSet.containsKey(t)) {
            System.out.println(getTime() + "Adding server: " + t + " to the load-balancer!");
            serverSet.put(t, identifer++);
            blockingQueue.put(t);
        }
    }

    public synchronized HashMap<T, Integer> getAllServers() {
        return serverSet;
    }

    // Used to get time in a readable format for logging
    private static String getTime() {
        long milliSeconds = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy; HH:mm:ss.SSS");
        Date resultdate = new Date(milliSeconds);
        return ("LoadBalancer : tid=" + Thread.currentThread().getId() + " : "
                + sdf.format(resultdate) + " :: ");
    }

}
