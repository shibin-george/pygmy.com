package pygmy.com.mutex;

import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class TaskQueue<TaskIDType, TaskParameterType, TaskResponseType> {

    private BlockingQueue<SimpleEntry<TaskIDType, TaskParameterType>> taskQueue = null;
    private HashSet<TaskIDType> pendingTasks = null;
    private HashMap<TaskIDType, TaskResponseType> responseMap;

    public TaskQueue(int capacity) {
        taskQueue =
                new ArrayBlockingQueue<SimpleEntry<TaskIDType, TaskParameterType>>(capacity, true);
        pendingTasks = new HashSet<TaskIDType>();
        responseMap = new HashMap<TaskIDType, TaskResponseType>();
    }

    public synchronized void addTask(TaskIDType id, TaskParameterType task)
            throws InterruptedException {
        pendingTasks.add(id);
        System.out.println(getTime() + "Added task: " + id + " to task queue!");
        taskQueue.put(new SimpleEntry<TaskIDType, TaskParameterType>(id, task));
    }

    public synchronized boolean hasPendingTasks() {
        if (pendingTasks.isEmpty())
            return false;

        return true;
    }

    public synchronized boolean isTaskComplete(TaskIDType orderId) {
        if (pendingTasks.contains(orderId) || !responseMap.containsKey(orderId)) {
            return false;
        }

        return true;
    }

    public synchronized void addResponse(TaskIDType orderId, TaskResponseType response) {
        responseMap.put(orderId, response);
    }

    public synchronized TaskResponseType getResponse(TaskIDType orderId) {
        return responseMap.getOrDefault(orderId, null);
    }

    public synchronized SimpleEntry<TaskIDType, TaskParameterType> getTask() {
        return taskQueue.poll();
    }

    public synchronized void markTaskComplete(TaskIDType key) {
        if (pendingTasks.contains(key)) {
            pendingTasks.remove(key);
        }
    }

    // Used to get time in a readable format for logging
    public static String getTime() {
        long milliSeconds = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy; HH:mm:ss.SSS");
        Date resultdate = new Date(milliSeconds);
        return ("TaskQueue : tid=" + Thread.currentThread().getId() + " : "
                + sdf.format(resultdate) + " :: ");
    }

}
