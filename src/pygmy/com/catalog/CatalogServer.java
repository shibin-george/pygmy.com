package pygmy.com.catalog;

import static spark.Spark.awaitStop;
import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.stop;
import static spark.Spark.threadPool;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONObject;

import config.Config;
import pygmy.com.mutex.MutualExclusionLockManager;
import pygmy.com.mutex.TaskQueue;
import pygmy.com.ui.UIServer;
import pygmy.com.utils.HttpRESTUtils;
import pygmy.com.wal.CatalogWriteAheadLogger;

public class CatalogServer {

    private static String initialInventoryPath = "";
    private static CatalogDatabase catalogDb = null;

    private static String uiIpAddress = "";
    private static String ipAddress;

    private static MutualExclusionLockManager lockManager = null;
    private static TaskQueue<String, JSONObject, JSONObject> updateTaskQueue = null;
    private static Thread executorThread;

    private static int MAX_JOBS_IN_QUEUE = 5;

    private static HashSet<String> catalogReplicas = null;

    private static boolean helpingInRecovery = false;

    private static int HTTP_ALIVE_TIMEOUT = 1000;

    public static void main(String[] args) throws IOException, InterruptedException {

        InetAddress ip = InetAddress.getLocalHost();
        ipAddress = UIServer.prefixHTTP(ip.getHostAddress() + ":" + Config.CATALOG_SERVER_PORT);
        System.out.println(getTime() + "Catalog Server, running on " + ipAddress + "...");

        if (args.length < 3) {
            System.out.println(getTime() + "No initial inventory or WAL location or UIServer "
                    + "provided! Exiting..");
            System.exit(1);
        }

        // the first argument for the catalog server should be the
        // path from which initial state of inventory can be read from
        initialInventoryPath = args[0];

        // set up the WAL
        catalogDb = new CatalogDatabase(new CatalogWriteAheadLogger(args[1]));

        // set up delay-writer for perf measurements
        BufferedWriter delayWriter =
                new BufferedWriter(new BufferedWriter(new FileWriter(args[1] + ".delay", false)));

        // UIServer!
        uiIpAddress = UIServer.prefixHTTP(args[2] + ":" + Config.UI_SERVER_PORT);

        boolean needRecovery = false;
        if (args.length >= 4 && args[3].equals("recovery"))
            needRecovery = true;

        // set up lock-manager for the token-ring distributed
        // mutual exclusion algorithm
        lockManager = new MutualExclusionLockManager(1000/* milliseconds */);

        // set up task-queue for update-tasks
        updateTaskQueue = new TaskQueue<String, JSONObject, JSONObject>(MAX_JOBS_IN_QUEUE);

        // set containing the replicas of catalog-server
        catalogReplicas = new HashSet<String>();

        if (!needRecovery && !catalogDb.init(initialInventoryPath)) {
            System.out.println(getTime() + "INIT of DB from on-disk file failed! Exiting..");
            System.exit(1);
        }

        threadPool(10);

        // start listening on pre-configured port
        port(Integer.parseInt(Config.CATALOG_SERVER_PORT));

        // expose the endpoints

        // query-by-topic
        get("/query/topic/:topicName", (req, res) -> {
            res.type("application/json");

            String[] params = req.params(":topicName").split("@");
            String topic = params[0];
            System.out.println(
                    getTime() + "Searching for books on topic: " + topic);
            ArrayList<Book> booksByTopic = catalogDb.searchByTopic(topic);

            JSONObject jsonObject = new JSONObject();
            JSONArray jsonArray = new JSONArray();
            for (Book book : booksByTopic) {
                jsonArray.put(book.getConciseJSON());
            }
            jsonObject.put("items", jsonArray);

            System.out.println(getTime() + "ACKing jobId: " + params[1]);
            Thread ackThread = new Thread(new Runnable() {

                @Override
                public void run() {
                    HttpRESTUtils.httpPost(uiIpAddress + "/catalog/ack/" + params[1], 0,
                            Config.DEBUG);
                }
            });
            ackThread.start();

            jsonObject.put("ServedByCatalogServer", ipAddress);
            return jsonObject;
        });

        // query-by-bookId
        get("/query/book/:bookId", (req, res) -> {
            res.type("application/json");

            String[] params = req.params(":bookId").split("@");
            String bookId = params[0];
            System.out.println(getTime() + "Searching for book: " + bookId);
            Book book = catalogDb.searchById(bookId);

            JSONObject jsonObject = null;

            if (book == null) {
                jsonObject = new JSONObject();
                jsonObject.put("CatalogStatus", "This item doesn't exist in the inventory.");
            } else {
                jsonObject = book.JSONifySelf();
            }

            System.out.println(getTime() + "ACKing jobId: " + params[1]);
            Thread ackThread = new Thread(new Runnable() {

                @Override
                public void run() {
                    HttpRESTUtils.httpPost(uiIpAddress + "/catalog/ack/" + params[1], 0,
                            Config.DEBUG);
                }
            });
            ackThread.start();

            jsonObject.put("ServedByCatalogServer", ipAddress);
            return jsonObject;
        });

        // update count of a book
        // this endpoint can only increase or decrease the count
        // of a book. The JSON request should have the "updateBy"
        // set to +x or -y. It should also have the "bookId" set
        post("/update", (req, res) -> {
            res.type("application/json");

            long entryTS = System.currentTimeMillis();

            JSONObject updateRequest = new JSONObject(req.body());
            String bookId = updateRequest.getString("bookId");

            // some updates may not have order-id tagged if not made via ui-server
            String orderId = updateRequest.optString("orderId", "DEFAULT");

            updateTaskQueue.addTask(orderId, updateRequest);

            while (!updateTaskQueue.isTaskComplete(orderId)) {
                Thread.sleep(1000);
            }

            Thread.sleep(100);
            JSONObject response = updateTaskQueue.getResponse(orderId);

            long exitTS = System
                    .currentTimeMillis();
            delayWriter.write(entryTS + "-" + exitTS);
            delayWriter.newLine();
            delayWriter.flush();

            boolean replyTo = !updateRequest.optString("reply-to", "none").equals("none");

            // invalidate cache
            HttpRESTUtils.httpPost(uiIpAddress + "/invalidate/" + bookId, 0, Config.DEBUG);

            System.out.println(
                    getTime() + "ACKing jobId: " + orderId.substring(1));

            Thread ackThread = new Thread(new Runnable() {

                @Override
                public void run() {
                    if (replyTo)
                        HttpRESTUtils.httpPost(
                                updateRequest.getString("reply-to") + "/catalog/ack/"
                                        + orderId.substring(1),
                                0, Config.DEBUG);
                }
            });
            ackThread.start();

            response.put("ServedByCatalogServer", ipAddress);
            return response;
        });

        // REST endpoint for replication
        post("/replicate", (req, res) -> {
            res.type("application/json");

            JSONObject replicateRequest = new JSONObject(req.body());

            String orderId = replicateRequest.optString("orderId", "DEFAULT");
            System.out.println(getTime() + "Replicating order: " + orderId + "..");
            return updateBook(orderId, replicateRequest);
        });

        // REST endpoint for acquiring token/lock
        post("/lock", (req, res) -> {
            res.type("application/json");

            lockManager.setLockToAcquired();
            JSONObject response = new JSONObject();
            response.put("token", "acquired");

            if (Config.DEBUG_LOCK)
                System.out.println(getTime() + "Acquired token from replica server!");

            return response;
        });

        // REST end-point for order-server to add itself to the load-balancer
        post("/uibroadcast", (req, res) -> {
            JSONObject jsonObject = new JSONObject(req.body());

            Iterator<String> keys = jsonObject.keys();

            HashSet<String> replicas = new HashSet<String>();

            while (keys.hasNext()) {
                String key = keys.next();
                // System.out.println(key + " : " + jsonObject.getInt(key));
                replicas.add(key);
            }

            setReplicas(replicas);
            return UIServer.getDummyJSONObject();
        });

        // REST end-point for initiating recovery
        get("/recovery/initiate", (req, res) -> {
            res.type("application/json");

            // set state to helpingInRecovery so that no tasks
            // are executed at this time
            helpingInRecovery = true;

            System.out.println("Initiating recovery of faulty replica server..");

            JSONObject recoveryResponse = new JSONObject();
            recoveryResponse.put("WAL", args[1]);

            return recoveryResponse;
        });

        // REST end-point for completing recovery
        // query-by-topic
        get("/recovery/complete", (req, res) -> {
            res.type("application/json");

            // release the lock so that no orders can be executed while
            // another replica is being recovered from failure
            helpingInRecovery = false;

            catalogDb.prettyPrintCatalog();
            System.out.println("Recovered faulty replica server..");

            // since we recovered the faulty replica server,
            // we acquire the lock
            lockManager.setLockToAcquired();

            Thread.sleep(2000);

            return UIServer.getDummyJSONObject();
        });

        // REST end-point for heartbeat
        get("/heartbeat", (req, res) -> {
            res.type("application/json");

            // System.out.println("HeartBeat from replica server..");

            return UIServer.getDummyJSONObject();
        });

        // REST end-point for heartbeat
        get("/heartbeat", (req, res) -> {
            res.type("application/json");

            // System.out.println("HeartBeat from replica server..");

            return UIServer.getDummyJSONObject();
        });

        // REST end-point for graceful-shutdown
        get("/stop", (req, res) -> {
            res.type("application/json");
            stop();
            awaitStop();
            return "Stopped!";
        });

        Thread.sleep(2000);

        // introduce self to the front-end server
        introduceSelfToUIServer();

        // check if we need to recover from a previous failure
        if (needRecovery) {
            // we need to start in recovery mode since we crashed in previous run
            // resync!
            String otherReplica = null;
            while ((otherReplica = getOtherServer()) == null) {
                Thread.sleep(1000);
            }

            String response = HttpRESTUtils.httpGet(otherReplica + "/recovery/initiate",
                    0, Config.DEBUG);
            if (response == null) {
                System.out.println("Exiting since recovery is not possible");
                System.exit(1);
            }

            JSONObject recoveryResponse = new JSONObject(response);
            if (recoveryResponse.optString("WAL", "none").equals("none")) {
                // the other replica ditched us or probably crashed
                // ABORT
                System.out.println("Exiting since recovery is not possible");
                System.exit(1);
            }

            catalogDb.replayFromWAL(recoveryResponse.optString("WAL"));

            catalogDb.prettyPrintCatalog();

            // now that recovery is complete, convey the same to the other replica
            HttpRESTUtils.httpGet(otherReplica + "/recovery/complete", 0, Config.DEBUG);

            System.out.println("Recovery complete..");
        }

        // start executor thread
        startExecutorThread();
    }

    private static synchronized void setReplicas(HashSet<String> replicas) {
        catalogReplicas.clear();
        catalogReplicas.addAll(replicas);
    }

    private static void startExecutorThread() {
        executorThread = new Thread(new Runnable() {

            @Override
            public void run() {
                while (true) {
                    // check if we have the lock
                    // if we are not helping another replica in recovery process,
                    // we can execute tasks
                    if (lockManager.isLockAcquired() && !helpingInRecovery) {
                        // System.out.println("have lock");
                        int jobsDone = 0;
                        while (jobsDone < 1) {
                            SimpleEntry<String, JSONObject> task = updateTaskQueue.getTask();
                            if (task == null) {
                                // no more tasks in queue
                                break;
                            }

                            System.out.println(getTime() + "Executing task: " + task.getKey());

                            String replicaServer = getOtherServer();
                            if (replicaServer != null) {
                                // replicate this update on the replica-server
                                HttpRESTUtils.httpPostJSON(replicaServer + "/replicate",
                                        task.getValue(), 0, Config.DEBUG);

                                System.out.println(getTime() + "Replicated order: " + task.getKey()
                                        + " on replica server..");
                            }

                            JSONObject response = updateBook(task.getKey(), task.getValue());
                            updateTaskQueue.addResponse(task.getKey(), response);
                            updateTaskQueue.markTaskComplete(task.getKey());
                            jobsDone++;
                        }

                        // done with updates, now release the lock
                        String replicaServer = getOtherServer();
                        if (replicaServer != null && transferToken(replicaServer)) {
                            if (Config.DEBUG_LOCK)
                                System.out.println(getTime() + "Token released to replica server: "
                                        + replicaServer + "..");
                        }
                    } else if (!lockManager.isLockAcquired()) {
                        // System.out.println("no lock");
                        // we don't have the lock yet
                        // check if it has been too long since we last had the lock
                        // also check if we have pending tasks
                        if (lockManager.hasBeenTooLongSinceRelease() &&
                                updateTaskQueue.hasPendingTasks()) {
                            // check if the replica is up and running
                            String replicaServer = getOtherServer();
                            if (replicaServer != null && isAlive(replicaServer)) {
                                // ok! the replica is up and running.
                                // we will wait for some more time
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                // hmmmm.. looks like no other server
                                // take over the lock
                                System.out.println(getTime()
                                        + "Forcibly acquiring the token since replica server seems to be down..");
                                lockManager.setLockToAcquired();
                            }
                        }
                    }
                }
            }

        });

        executorThread.start();
    }

    private static boolean isAlive(String replicaServer) {
        String response = HttpRESTUtils.httpGet(replicaServer + "/heartbeat",
                HTTP_ALIVE_TIMEOUT, Config.DEBUG);

        if (response == null)
            return false;

        return true;
    }

    private static boolean transferToken(String replicaServer) {

        // first, set the state to release
        lockManager.setLockToReleased();

        // now, we transfer the token
        String response =
                HttpRESTUtils.httpPost(replicaServer + "/lock", HTTP_ALIVE_TIMEOUT, Config.DEBUG);

        if (response == null)
            return false;

        JSONObject lockResponse = new JSONObject(response);

        // check if they have the lock
        if (lockResponse.optString("token", "false").equals("acquired")) {
            return true;
        }

        return false;
    }

    private static synchronized String getOtherServer() {
        for (String server : catalogReplicas) {
            if (!server.equals(ipAddress))
                return server;
        }
        return null;
    }

    private static JSONObject updateBook(String orderId, JSONObject updateRequest) {
        String bookId = updateRequest.getString("bookId");
        int updateBy = updateRequest.getInt("updateBy");

        // try to update the database in a thread-safe manner
        String signature = catalogDb.updateCountById(bookId, updateBy, orderId);

        String[] split = signature.split("_");
        int newCount = Integer.parseInt(split[0]);
        long timestamp = Long.parseLong(split[1]);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("bookId", bookId);
        jsonObject.put("Stock", newCount);
        jsonObject.put("CatalogServerTimeStamp", timestamp);

        if (timestamp == Long.MIN_VALUE) {
            jsonObject.put("CatalogStatus", "Write to WAL failed.");
            jsonObject.put("CatalogServerTimeStamp", System.currentTimeMillis());
        } else if (newCount == Integer.MIN_VALUE)
            jsonObject.put("CatalogStatus", "This item doesn't exist in the inventory.");
        else if (newCount >= 0)
            jsonObject.put("CatalogStatus", "Order approved by CatalogServer.");
        else
            jsonObject.put("CatalogStatus", "Not enough items in stock.");

        return jsonObject;
    }

    private static void introduceSelfToUIServer() throws ConnectException, IOException {

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject request = new JSONObject();
                request.put("URL", ipAddress);

                while (true) {
                    try {
                        JSONObject response = new JSONObject(
                                HttpRESTUtils.httpPostJSON(uiIpAddress + "/catalog/add", request,
                                        0, Config.DEBUG));

                        // check if we have the lock
                        if (response.optString("token", "false").equals("true")) {
                            System.out.println(getTime() + "Got token from the UIServer!");
                            lockManager.setLockToAcquired();
                        }

                        // do it again after 20 seconds
                        Thread.sleep(10000);
                    } catch (Exception e) {
                    }
                }
            }
        });

        thread.start();
    }

    // Used to get time in a readable format for logging
    public static String getTime() {
        long milliSeconds = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy; HH:mm:ss.SSS");
        Date resultdate = new Date(milliSeconds);
        return ("CatalogServer : tid=" + Thread.currentThread().getId() + " : "
                + sdf.format(resultdate) + " :: ");
    }

}
