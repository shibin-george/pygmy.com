package pygmy.com.ui;

import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.post;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

import config.Config;
import pygmy.com.utils.HttpRESTUtils;

public class UIServer {

    private static FrontEndCacheManager cacheManager = null;

    // load balancers
    private static RoundRobinLoadBalancer<String> catalogLoadBalancer = null;
    private static RoundRobinLoadBalancer<String> orderLoadBalancer = null;

    private static ArrayList<String> catalogServers = null;
    private static ArrayList<String> orderServers = null;

    public static void main(String args[]) throws IOException {
        System.out.println("Starting UI Server...");

        if (args.length == 0) {
            System.out
                    .println(getTime() + "CatalogServer and OrderServer not specified! Exiting..");
            System.exit(1);
        }

        catalogServers = new ArrayList<String>();
        catalogServers.add(prefixHTTP(args[0] + ":" + Config.CATALOG_SERVER_PORT));
        catalogServers.add(prefixHTTP(args[1] + ":" + Config.CATALOG_SERVER_PORT));

        orderServers = new ArrayList<String>();
        orderServers.add(prefixHTTP(args[2] + ":" + Config.ORDER_SERVER_PORT));
        orderServers.add(prefixHTTP(args[3] + ":" + Config.ORDER_SERVER_PORT));

        catalogLoadBalancer = new RoundRobinLoadBalancer<String>(2, catalogServers);
        orderLoadBalancer = new RoundRobinLoadBalancer<String>(2, orderServers);

        // start listening on pre-configured port
        port(Integer.parseInt(Config.UI_SERVER_PORT));

        BufferedWriter delayWriter =
                new BufferedWriter(new BufferedWriter(new FileWriter("UI.delay", false)));

        // set up caches for storing topic and book lookup results
        cacheManager = new FrontEndCacheManager();

        /*
         * expose the UIServer REST endpoints
         */

        // search (only by topic)
        get("/search/:topic", (req, res) -> {
            String topic = req.params(":topic");
            return cacheManager.getTopicLookupResults(topic);
        });

        // lookup (only by unique id)
        get("/lookup/:bookId", (req, res) -> {
            String bookId = req.params(":bookId");
            return cacheManager.getBookLookupResults(bookId);
        });

        // invalidate endpoint to remove an entry from the cache
        delete("/invalidate/:bookId", (req, res) -> {
            String bookId = req.params(":bookId");
            return cacheManager.invalidateBookLookupCache(bookId);
        });

        // buy (only accepts unique id, count defaults to 1)
        post("/buy/:bookId", (req, res) -> {
            long entryTS = System.currentTimeMillis();

            String bookId = req.params(":bookId");
            int toBuy = 1;

            // get UIServer timestamp
            long timeStamp = System.currentTimeMillis();

            System.out.println(getTime() +
                    "Asking OrderServer to buy book: " + bookId);

            // create a JSON request to send to OrderServer
            JSONObject buyRequest = new JSONObject();
            buyRequest.put("bookId", bookId);
            buyRequest.put("updateBy", -1 * toBuy);
            buyRequest.put("UIServerTimeStamp", timeStamp);

            String orderId = "#OD" + buyRequest.hashCode() + "-" +
                    Thread.currentThread().getId();
            buyRequest.put("orderId", orderId);

            System.out.println("Buy request: " + buyRequest.toString(2));

            JSONObject buyResponse = new JSONObject(
                    HttpRESTUtils.httpPostJSON(orderLoadBalancer.get()
                            + "/buy", buyRequest));

            if (buyResponse.getInt("code") == 0) {
                buyResponse.put("Status", "Successfully bought the book(s)!");
            } else {
                buyResponse.put("Status",
                        "Couldn't buy the book. See OrderStatus/CatalogStatus for more details.");
            }

            buyResponse.put("UIServerTimeStamp", timeStamp);
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

            // get UIServer timestamp
            long timeStamp = System.currentTimeMillis();

            System.out.println(getTime() +
                    "Asking OrderServer to buy book: " + bookId);

            // create a JSON request to send to OrderServer
            buyRequest.put("updateBy", -1 * toBuy);
            buyRequest.remove("count");
            buyRequest.put("UIServerTimeStamp", timeStamp);

            String orderId = "#OD" + buyRequest.hashCode() + "-" +
                    Thread.currentThread().getId();
            buyRequest.put("orderId", orderId);

            System.out.println(getTime() +
                    "Asking OrderServer to buy book: " + bookId);

            JSONObject buyResponse = new JSONObject(
                    HttpRESTUtils.httpPostJSON(orderLoadBalancer.get()
                            + "/buy", buyRequest));

            if (buyResponse.getInt("code") == 0) {
                buyResponse.put("Status", "Successfully bought the book(s)!");
            } else {
                buyResponse.put("Status",
                        "Couldn't buy the book(s). See OrderStatus/CatalogStatus for more details.");
            }

            buyResponse.put("UIServerTimeStamp", timeStamp);
            buyResponse.put("orderId", orderId);

            long exitTS = System.currentTimeMillis();
            delayWriter.write(entryTS + "-" + exitTS);
            delayWriter.newLine();
            delayWriter.flush();

            return buyResponse;
        });
    }

    public static JSONObject lookupTopic(String topic)
            throws JSONException, IOException, InterruptedException {
        System.out.println(getTime() +
                "Asking CatalogServer to search for books on topic: " + topic);
        return new JSONObject(
                HttpRESTUtils.httpGet(catalogLoadBalancer.get()
                        + "/query/topic/" + topic));
    }

    public static JSONObject lookupBook(String bookId)
            throws JSONException, IOException, InterruptedException {
        System.out.println(getTime() +
                "Asking CatalogServer to search for book: " + bookId);
        return new JSONObject(
                HttpRESTUtils.httpGet(catalogLoadBalancer.get()
                        + "/query/book/" + bookId));
    }

    // Used to get time in a readable format for logging
    public static String getTime() {
        long milliSeconds = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy; HH:mm:ss.SSS");
        Date resultdate = new Date(milliSeconds);
        return ("UIServer : tid=" + Thread.currentThread().getId() + " : "
                + sdf.format(resultdate) + " :: ");
    }

    public static String prefixHTTP(String t) {

        if (!t.startsWith("http://")) {
            return "http://" + t;
        }

        return t;
    }

}
