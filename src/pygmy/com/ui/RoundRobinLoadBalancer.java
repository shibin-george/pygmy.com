package pygmy.com.ui;

import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class RoundRobinLoadBalancer<T> {

    private BlockingQueue<T> blockingQueue = null;

    public RoundRobinLoadBalancer(int capacity, Collection<T> c) {
        blockingQueue = new ArrayBlockingQueue<T>(2, true, c);
    }

    public T get() throws InterruptedException {

        // poll from the head of the queue
        T head = blockingQueue.take();

        System.out.println(UIServer.getTime() + "RoundRobinLoadBalancer :: "
                + "Forwarding request to " + head);

        // add the head element back to the queue
        blockingQueue.put(head);
        return head;
    }

}
