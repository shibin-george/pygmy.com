package pygmy.com.order;

import static spark.Spark.port;
import static spark.Spark.post;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONObject;

import config.Config;
import pygmy.com.scheduler.HeartbeatMonitor;
import pygmy.com.scheduler.RoundRobinLoadBalancer;
import pygmy.com.ui.UIServer;
import pygmy.com.utils.HttpRESTUtils;
import pygmy.com.wal.OrderWriteAheadLogger;

public class OrderServer {

    private static String catalogServerURL;

    // load balancers
    private static RoundRobinLoadBalancer<String> catalogLoadBalancer = null;

    // heart-beat monitors
    private static HeartbeatMonitor<String, String, String, JSONObject> catalogHeartbeatMonitor =
            null;

    private static String uiIpAddress, ipAddress;

    public static void main(String[] args) throws IOException, InterruptedException {

        InetAddress ip = InetAddress.getLocalHost();
        ipAddress = UIServer.prefixHTTP(ip.getHostAddress() + ":" + Config.ORDER_SERVER_PORT);
        System.out.println("Order Server, running on " + ipAddress + "...");

        // the first argument for the order server should be the
        // the URL for the CatalogServer
        if (args.length < 2) {
            System.out.println(getTime() + "CatalogServer and/or WAL location"
                    + " not specified! Exiting..");
            System.exit(1);
        }

        uiIpAddress = UIServer.prefixHTTP(args[0] + ":" + Config.UI_SERVER_PORT);

        // start listening on pre-configured port
        port(Integer.parseInt(Config.ORDER_SERVER_PORT));

        // get an instance of WAL logger
        OrderWriteAheadLogger oWALogger = new OrderWriteAheadLogger(args[1]);
        BufferedWriter delayWriter =
                new BufferedWriter(new BufferedWriter(new FileWriter(args[1] + ".delay", false)));

        // set up load-balancer and the heartbeat-monitor
        catalogLoadBalancer = new RoundRobinLoadBalancer<String>(5);
        catalogHeartbeatMonitor =
                new HeartbeatMonitor<String, String, String, JSONObject>(catalogLoadBalancer);

        // expose the endpoints

        // query-by-topic
        post("/buy", (req, res) -> {

            long entryTS = System.currentTimeMillis();

            JSONObject updateRequest = new JSONObject(req.body());
            String bookId = updateRequest.getString("bookId");
            int updateBy = updateRequest.getInt("updateBy");
            String orderId = updateRequest.getString("orderId");

            // add OrderServer timestamp
            long timeStamp = System.currentTimeMillis();

            System.out.println(
                    getTime() + "Querying CatalogServer for book: " + bookId);

            JSONObject queryResponse = new JSONObject(
                    HttpRESTUtils.httpGet(catalogServerURL + "/query/book/" + bookId,
                            Config.DEBUG));

            res.type("application/json");
            JSONObject updateResponse = null;

            if (queryResponse.optInt("Stock", Integer.MIN_VALUE) + updateBy >= 0) {

                System.out.println(
                        getTime() + "Asking CatalogServer to update count of book: " + bookId);

                // enough items in stock; proceed to buy
                updateResponse = new JSONObject(HttpRESTUtils
                        .httpPostJSON(catalogServerURL + "/update", updateRequest, Config.DEBUG));

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
            } else {
                // /query shows that we don't have enough items in stock
                // don't proceed with /update
                updateResponse = new JSONObject();
                updateResponse.put("code", -1);
                updateResponse.put("bookId", bookId);
                updateResponse.put("OrderStatus",
                        "Order rejected by OrderServer.");
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

            return updateResponse;
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

        Thread.sleep(1000);

        // now that everything is done (including possibly recovery),
        // introduce self to the front-end server
        introduceSelfToUIServer();

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
                                Config.DEBUG);

                        // do it again after 20 seconds
                        Thread.sleep(20000);
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
