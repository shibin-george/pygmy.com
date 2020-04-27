package pygmy.com.order;

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
import java.util.ArrayList;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import config.Config;
import pygmy.com.scheduler.HeartbeatMonitor;
import pygmy.com.scheduler.PygmyJob;
import pygmy.com.scheduler.RoundRobinLoadBalancer;
import pygmy.com.ui.UIServer;
import pygmy.com.utils.HttpRESTUtils;
import pygmy.com.wal.OrderWriteAheadLogger;

public class OrderServer {

    // load balancers
    private static RoundRobinLoadBalancer<String> catalogLoadBalancer = null;

    // heart-beat monitors
    private static HeartbeatMonitor<String, JSONObject, String, JSONObject> catalogHeartbeatMonitor =
            null;

    private static String uiIpAddress, ipAddress;

    private static final int CATALOG_HTTP_REQ_TIMEOUT = 7000;

    public static void main(String[] args) throws IOException, InterruptedException {

        InetAddress ip = InetAddress.getLocalHost();
        ipAddress = UIServer.prefixHTTP(ip.getHostAddress() + ":" + Config.ORDER_SERVER_PORT);
        System.out.println(getTime() + "Order Server, running on " + ipAddress + "...");

        // the first argument for the order server should be the
        // the URL for the CatalogServer
        if (args.length < 2) {
            System.out.println(getTime() + "CatalogServer and/or WAL location"
                    + " not specified! Exiting..");
            System.exit(1);
        }

        uiIpAddress = UIServer.prefixHTTP(args[0] + ":" + Config.UI_SERVER_PORT);

        threadPool(10);

        // start listening on pre-configured port
        port(Integer.parseInt(Config.ORDER_SERVER_PORT));

        // get an instance of WAL logger
        OrderWriteAheadLogger oWALogger = new OrderWriteAheadLogger(args[1]);
        BufferedWriter delayWriter =
                new BufferedWriter(new BufferedWriter(new FileWriter(args[1] + ".delay", false)));

        // set up load-balancer and the heartbeat-monitor
        catalogLoadBalancer = new RoundRobinLoadBalancer<String>(5);
        catalogHeartbeatMonitor =
                new HeartbeatMonitor<String, JSONObject, String, JSONObject>(catalogLoadBalancer,
                        CATALOG_HTTP_REQ_TIMEOUT);

        // expose the endpoints

        // query-by-topic
        post("/buy", (req, res) -> {
            res.type("application/json");

            long entryTS = System.currentTimeMillis();

            JSONObject updateRequest = new JSONObject(req.body());
            String bookId = updateRequest.getString("bookId");
            int updateBy = updateRequest.getInt("updateBy");
            String orderId = updateRequest.getString("orderId");

            // add reply-to url (i.e. own url) so that the catalog-server can ACK
            updateRequest.put("reply-to", ipAddress);

            // add OrderServer timestamp
            long timeStamp = System.currentTimeMillis();

            String jobId = orderId.substring(1);
            updateBook(updateRequest, jobId);

            // spin until the request is processed
            while (!catalogHeartbeatMonitor.isJobComplete(jobId)) {
                // check again after a second
                Thread.sleep(1000);
            }

            Thread.sleep(100);
            JSONObject updateResponse = catalogHeartbeatMonitor.getResponse(jobId);
            catalogHeartbeatMonitor.cleanupJob(jobId);

            // check if update was successful
            if (updateResponse.getInt("Stock") >= 0) {
                updateResponse.put("code", 0);
                updateResponse.put("OrderStatus",
                        "Order approved by OrderServer.");
            } else {
                updateResponse.put("code", -1);
                updateResponse.put("OrderStatus",
                        "Order rejected by CatalogServer.");
            }

            // add the OrderServer timestamp & the orderId to the response packet
            updateResponse.put("OrderServerTimeStamp", timeStamp);
            updateResponse.put("orderId", orderId);

            // log into WAL
            oWALogger.writeBuy(bookId, updateBy, updateRequest.getLong("UIServerTimeStamp"),
                    timeStamp, updateResponse.optLong("CatalogServerTimeStamp", timeStamp),
                    orderId, (updateResponse.getInt("code") == 0 ? "Success" : "Failure"));

            long exitTS = System.currentTimeMillis();
            delayWriter.write(entryTS + "-" + exitTS);
            delayWriter.newLine();
            delayWriter.flush();

            System.out.println(getTime() + "ACKing jobId: " + jobId);
            Thread ackThread = new Thread(new Runnable() {

                @Override
                public void run() {
                    HttpRESTUtils.httpPost(uiIpAddress + "/order/ack/" + jobId, 0, Config.DEBUG);
                }
            });
            ackThread.start();

            updateResponse.put("ServedByOrderServer", ipAddress);
            return updateResponse;
        });

        // REST end-point for catalog-server to mark a Job as completed
        post("/catalog/ack/:jobId", (req, res) -> {
            String jobId = req.params(":jobId");
            catalogHeartbeatMonitor.markJobCompleted(jobId);
            return UIServer.getDummyJSONObject();
        });

        // REST end-point for ui-server to add catalog-server to the load-balancer
        post("/catalog/add", (req, res) -> {
            JSONObject server = new JSONObject(req.body());
            JSONArray catalogServers = server.getJSONArray("catalog-servers");
            for (int i = 0; i < catalogServers.length(); i++) {
                catalogLoadBalancer.add(catalogServers.getString(i));
            }
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

        // now that everything is done (including possibly recovery),
        // introduce self to the front-end server
        introduceSelfToUIServer();

        Thread jobRecoveryThread = new Thread(new Runnable() {

            int RECOVERY_WAKEUP_TIMEOUT_IN_MILLISECONDS = 1000;

            @Override
            public void run() {
                while (true) {
                    retryCatalogServerJobs(catalogHeartbeatMonitor.getIncompleteJobs());

                    try {
                        Thread.sleep(RECOVERY_WAKEUP_TIMEOUT_IN_MILLISECONDS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            private void retryCatalogServerJobs(
                    ArrayList<PygmyJob<String, JSONObject, String>> incompleteJobs) {
                for (PygmyJob<String, JSONObject, String> job : incompleteJobs) {
                    try {
                        if (job.getJobType().equals("UPDATE")) {
                            // re-attempt the job hopefully on a new server
                            updateBook(job.getParameter(), job.getJobId());
                        }
                    } catch (JSONException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        });

        jobRecoveryThread.start();

    }

    public static void updateBook(JSONObject updateRequest, String jobId)
            throws InterruptedException {

        // tell the heart-beat monitor to track this job
        String catalogServer = catalogLoadBalancer.get();
        PygmyJob<String, JSONObject, String> updateJob =
                new PygmyJob<String, JSONObject, String>(jobId, "UPDATE", catalogServer,
                        updateRequest);

        catalogHeartbeatMonitor.markJobStarted(updateJob);

        System.out.println(
                getTime() + "Asking CatalogServer to update count of book: "
                        + updateRequest.getString("bookId"));

        String updateResponse =
                HttpRESTUtils.httpPostJSON(catalogServer + "/update", updateRequest,
                        CATALOG_HTTP_REQ_TIMEOUT, Config.DEBUG);

        if (updateResponse != null) {
            catalogHeartbeatMonitor.addResponse(jobId, new JSONObject(updateResponse));
        }
    }

    private static void introduceSelfToUIServer() throws ConnectException, IOException {

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject request = new JSONObject();
                request.put("URL", ipAddress);

                while (true) {
                    try {
                        HttpRESTUtils.httpPostJSON(uiIpAddress + "/order/add", request,
                                0, Config.DEBUG);

                        // do it again after 20 seconds
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
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
        return ("OrderServer : tid=" + Thread.currentThread().getId() + " : "
                + sdf.format(resultdate) + " :: ");
    }

}
