package pygmy.com.ui;

import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import config.Config;
import pygmy.com.scheduler.HeartbeatMonitor;
import pygmy.com.scheduler.PygmyJob;
import pygmy.com.scheduler.RoundRobinLoadBalancer;
import pygmy.com.utils.HttpRESTUtils;

public class UIServer {

    private static FrontEndCacheManager cacheManager = null;

    // load balancers
    private static RoundRobinLoadBalancer<String> catalogLoadBalancer = null,
            orderLoadBalancer = null;

    // heartbeat monitors
    private static HeartbeatMonitor<String, String, String, JSONObject> catalogHeartbeatMonitor =
            null;
    private static HeartbeatMonitor<String, JSONObject, String, JSONObject> orderHeartbeatMonitor =
            null;

    // own IP address
    private static String ipAddress;

    // the first CatalogServer that contacts UIServer is handed the lock
    private static boolean tokenHandedOut = false;

    public static void main(String args[]) throws IOException, InterruptedException {

        InetAddress ip = InetAddress.getLocalHost();
        ipAddress = prefixHTTP(ip.getHostAddress() + ":" + Config.UI_SERVER_PORT);

        System.out.println(getTime() + "UI Server, running on " + ipAddress + "...");

        catalogLoadBalancer = new RoundRobinLoadBalancer<String>(5);
        orderLoadBalancer = new RoundRobinLoadBalancer<String>(5);

        catalogHeartbeatMonitor =
                new HeartbeatMonitor<String, String, String, JSONObject>(catalogLoadBalancer, 2000);
        orderHeartbeatMonitor =
                new HeartbeatMonitor<String, JSONObject, String, JSONObject>(orderLoadBalancer,
                        20000);

        threadPool(10);

        // start listening on pre-configured port
        port(Integer.parseInt(Config.UI_SERVER_PORT));

        BufferedWriter delayWriter =
                new BufferedWriter(new BufferedWriter(new FileWriter("UI.delay", false)));

        // set up caches for storing topic and book lookup results
        cacheManager = new FrontEndCacheManager(catalogHeartbeatMonitor);

        /*
         * expose the UIServer REST endpoints
         */

        // search (only by topic)
        get("/search/:topic", (req, res) -> {
            String topic = req.params(":topic");
            String jobId = req.hashCode() + "-" +
                    System.currentTimeMillis() + "-" + Thread.currentThread().getId();
            return cacheManager.getTopicLookupResults(topic, jobId);
        });

        // lookup (only by unique id)
        get("/lookup/:bookId", (req, res) -> {
            String bookId = req.params(":bookId");
            String jobId = req.hashCode() + "-" +
                    System.currentTimeMillis() + "-" + Thread.currentThread().getId();
            return cacheManager.getBookLookupResults(bookId, jobId);
        });

        // invalidate endpoint to remove an entry from the cache
        post("/invalidate/:bookId", (req, res) -> {
            String bookId = req.params(":bookId");
            return cacheManager.invalidateBookLookupCache(bookId);
        });

        // buy (only accepts unique id, count defaults to 1)
        post("/buy/:bookId", (req, res) -> {
            long entryTS = System.currentTimeMillis();

            String bookId = req.params(":bookId");
            int toBuy = 1;

            System.out.println(getTime() +
                    "Asking OrderServer to buy book: " + bookId);

            // create a JSON request to send to OrderServer
            JSONObject buyRequest = new JSONObject();
            buyRequest.put("bookId", bookId);
            buyRequest.put("updateBy", -1 * toBuy);
            buyRequest.put("UIServerTimeStamp", entryTS);

            String orderId = "#OD" + buyRequest.hashCode() + "-" +
                    entryTS + "-" + Thread.currentThread().getId();
            buyRequest.put("orderId", orderId);

            String jobId = orderId.substring(1);
            buyBook(buyRequest, jobId);

            // spin until the request is processed
            while (!orderHeartbeatMonitor.isJobComplete(jobId)) {
                // check again after a second
                Thread.sleep(1000);
            }

            Thread.sleep(100);
            JSONObject buyResponse = orderHeartbeatMonitor.getResponse(jobId);
            orderHeartbeatMonitor.cleanupJob(jobId);

            if (buyResponse.getInt("code") == 0) {
                buyResponse.put("Status", "Successfully bought the book(s)!");
            } else {
                buyResponse.put("Status",
                        "Couldn't buy the book. See OrderStatus/CatalogStatus for more details.");
            }

            buyResponse.put("UIServerTimeStamp", entryTS);
            buyResponse.put("orderId", orderId);

            long exitTS = System.currentTimeMillis();
            delayWriter.write(entryTS + "-" + exitTS);
            delayWriter.newLine();
            delayWriter.flush();

            return buyResponse;
        });

        // buy (only accepts unique id, and the count to buy)
        post("/multibuy", (req, res) -> {
            long entryTS = System.currentTimeMillis();

            JSONObject buyRequest = new JSONObject(req.body());
            String bookId = buyRequest.getString("bookId");
            int toBuy = buyRequest.getInt("count");

            // create a JSON request to send to OrderServer
            buyRequest.put("updateBy", -1 * toBuy);
            buyRequest.remove("count");
            buyRequest.put("UIServerTimeStamp", entryTS);

            String orderId = "#OD" + buyRequest.hashCode() + "-" +
                    entryTS + "-" + Thread.currentThread().getId();
            buyRequest.put("orderId", orderId);
            buyRequest.put("replyTo", ipAddress);

            System.out.println(getTime() +
                    "Asking OrderServer to buy book: " + bookId);

            String jobId = orderId.substring(1);
            buyBook(buyRequest, jobId);

            // spin until the request is processed
            while (!orderHeartbeatMonitor.isJobComplete(jobId)) {
                // check again after a second
                Thread.sleep(1000);
            }

            Thread.sleep(100);
            JSONObject buyResponse = orderHeartbeatMonitor.getResponse(jobId);
            orderHeartbeatMonitor.cleanupJob(jobId);

            if (buyResponse.getInt("code") == 0) {
                buyResponse.put("Status", "Successfully bought the book(s)!");
            } else {
                buyResponse.put("Status",
                        "Couldn't buy the book(s). See OrderStatus/CatalogStatus for more details.");
            }

            buyResponse.put("UIServerTimeStamp", entryTS);
            buyResponse.put("orderId", orderId);

            long exitTS = System.currentTimeMillis();
            delayWriter.write(entryTS + "-" + exitTS);
            delayWriter.newLine();
            delayWriter.flush();

            return buyResponse;
        });

        // REST end-point for catalog-server to mark a Job as completed
        post("/catalog/ack/:jobId", (req, res) -> {
            String jobId = req.params(":jobId");
            catalogHeartbeatMonitor.markJobCompleted(jobId);
            return getDummyJSONObject();
        });

        // REST end-point for order-server to mark a Job as completed
        post("/order/ack/:jobId", (req, res) -> {
            String jobId = req.params(":jobId");
            orderHeartbeatMonitor.markJobCompleted(jobId);
            return getDummyJSONObject();
        });

        // REST end-point for catalog-server to add itself to the load-balancer
        post("/catalog/add", (req, res) -> {
            JSONObject server = new JSONObject(req.body());
            catalogLoadBalancer.add(server.getString("URL"));

            JSONObject response = getDummyJSONObject();
            if (!tokenHandedOut) {
                // first catalog-server to approach us
                // Hand out the token to it
                response.put("token", "true");
                System.out.println(getTime() +
                        "Token handed out to " + server.getString("URL"));
                tokenHandedOut = true;
            }

            // inform order-servers about all of the catalog-server replicas
            JSONObject introResponse = new JSONObject();
            JSONArray cServerArray = new JSONArray();
            for (String cServer : catalogLoadBalancer.getAllServers().keySet()) {
                cServerArray.put(cServer);
            }
            introResponse.put("catalog-servers", cServerArray);
            for (String oServer : orderLoadBalancer.getAllServers().keySet()) {
                System.out.println("Sending cservers to " + oServer);
                HttpRESTUtils.httpPostJSON(oServer + "/catalog/add", introResponse, Config.DEBUG);
            }

            // inform all catalog-servers about all catalog-servers
            JSONObject catalogBroadcast = new JSONObject();
            HashMap<String, Integer> cServerMap = catalogLoadBalancer.getAllServers();
            for (String cServer : cServerMap.keySet()) {
                catalogBroadcast.put(cServer, cServerMap.get(cServer));
            }
            for (String cServer : catalogLoadBalancer.getAllServers().keySet()) {
                System.out.println("Sending cservers to " + cServer);
                HttpRESTUtils.httpPostJSON(cServer + "/uibroadcast", catalogBroadcast,
                        Config.DEBUG);
            }

            return response;
        });

        // REST end-point for order-server to add itself to the load-balancer
        post("/order/add", (req, res) -> {
            JSONObject server = new JSONObject(req.body());
            orderLoadBalancer.add((String) server.get("URL"));
            return getDummyJSONObject();
        });

        Thread jobRecoveryThread = new Thread(new Runnable() {

            int RECOVERY_WAKEUP_TIMEOUT_IN_MILLISECONDS = 1000;

            @Override
            public void run() {
                while (true) {
                    retryCatalogServerJobs(catalogHeartbeatMonitor.getIncompleteJobs());
                    retryOrderServerJobs(orderHeartbeatMonitor.getIncompleteJobs());

                    try {
                        Thread.sleep(RECOVERY_WAKEUP_TIMEOUT_IN_MILLISECONDS);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

            private void retryOrderServerJobs(
                    ArrayList<PygmyJob<String, JSONObject, String>> incompleteJobs) {
                for (PygmyJob<String, JSONObject, String> job : incompleteJobs) {
                    try {
                        if (job.getJobType().equals("BUY")) {
                            buyBook(job.getParameter(), job.getJobId());
                        }
                    } catch (JSONException | IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            private void retryCatalogServerJobs(
                    ArrayList<PygmyJob<String, String, String>> incompleteJobs) {
                for (PygmyJob<String, String, String> job : incompleteJobs) {
                    try {
                        if (job.getJobType().equals("LOOKUP_TOPIC")) {
                            // re-attempt the job hopefully on a new server
                            lookupTopic(job.getParameter(), job.getJobId());
                        } else if (job.getJobType().equals("LOOKUP_BOOK")) {
                            // re-attempt the job hopefully on a new server
                            lookupBook(job.getParameter(), job.getJobId());
                        }
                    } catch (JSONException | IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        });

        jobRecoveryThread.start();
    }

    public static void lookupTopic(String topic, String jobId)
            throws JSONException, IOException, InterruptedException {
        System.out.println(getTime() +
                "Asking CatalogServer to search for books on topic: " + topic);

        String catalogServer = catalogLoadBalancer.get();
        PygmyJob<String, String, String> lookupTopicJob =
                new PygmyJob<String, String, String>(jobId, "LOOKUP_TOPIC", catalogServer, topic);

        // add the job to catalog-heartbeat monitor
        catalogHeartbeatMonitor.markJobStarted(lookupTopicJob);

        String response = HttpRESTUtils.httpGet(catalogServer
                + "/query/topic/" + topic + "@" + jobId, Config.DEBUG);

        if (response != null) {
            catalogHeartbeatMonitor.addResponse(jobId, new JSONObject(response));
        }
    }

    public static void lookupBook(String bookId, String jobId)
            throws JSONException, IOException, InterruptedException {
        System.out.println(getTime() +
                "Asking CatalogServer to search for book: " + bookId);

        String catalogServer = catalogLoadBalancer.get();
        PygmyJob<String, String, String> lookupBookJob =
                new PygmyJob<String, String, String>(jobId, "LOOKUP_BOOK", catalogServer, bookId);

        catalogHeartbeatMonitor.markJobStarted(lookupBookJob);

        String response = HttpRESTUtils.httpGet(catalogServer
                + "/query/book/" + bookId + "@" + jobId, Config.DEBUG);

        if (response != null) {
            catalogHeartbeatMonitor.addResponse(jobId, new JSONObject(response));
        }
    }

    public static void buyBook(JSONObject buyRequest, String jobId)
            throws InterruptedException, ConnectException, JSONException, IOException {

        // tell the heart-beat monitor to track this job
        String orderServer = orderLoadBalancer.get();
        PygmyJob<String, JSONObject, String> buyJob =
                new PygmyJob<String, JSONObject, String>(jobId, "BUY", orderServer,
                        buyRequest);

        orderHeartbeatMonitor.markJobStarted(buyJob);

        String response = HttpRESTUtils.httpPostJSON(orderServer
                + "/buy", buyRequest, Config.DEBUG);

        if (response != null) {
            orderHeartbeatMonitor.addResponse(jobId, new JSONObject(response));
        }
    }

    // Used to get time in a readable format for logging
    public static String getTime() {
        long milliSeconds = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy; HH:mm:ss.SSS");
        Date resultdate = new Date(milliSeconds);
        return ("UIServer : tid=" + Thread.currentThread().getId() + " : "
                + sdf.format(resultdate) + " :: ");
    }

    public static String prefixHTTP(String url) {
        if (!url.startsWith("http://")) {
            return "http://" + url;
        }
        return url;
    }

    public static JSONObject getDummyJSONObject() {
        JSONObject object = new JSONObject();
        object.put("dummy", "dummy");
        return object;
    }

}
