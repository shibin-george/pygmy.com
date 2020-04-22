package pygmy.com.catalog;

import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.post;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONObject;

import config.Config;
import pygmy.com.mutex.MutualExclusionLockManager;
import pygmy.com.ui.UIServer;
import pygmy.com.utils.HttpRESTUtils;
import pygmy.com.wal.CatalogWriteAheadLogger;

public class CatalogServer {

    private static String initialInventoryPath = "";
    private static CatalogDatabase catalogDb = null;

    private static String uiIpAddress = "";
    private static String ipAddress;

    private static MutualExclusionLockManager lockManager = null;

    public static void main(String[] args) throws IOException, InterruptedException {

        InetAddress ip = InetAddress.getLocalHost();
        ipAddress = UIServer.prefixHTTP(ip.getHostAddress() + ":" + Config.CATALOG_SERVER_PORT);
        System.out.println("Catalog Server, running on " + ipAddress + "...");

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

        if (args.length >= 4 && args[3].equals("recovery")) {
            // we need to start in recovery mode since we crashed in previous run
            // resync
        }

        // set up lock-manager for the token-ring distributed
        // mutual exclusion algorithm
        lockManager = new MutualExclusionLockManager();

        if (!catalogDb.init(initialInventoryPath)) {
            System.out.println(getTime() + "INIT of DB from on-disk file failed! Exiting..");
            System.exit(1);
        }

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
                    HttpRESTUtils.httpPost(uiIpAddress + "/catalog/ack/" + params[1], Config.DEBUG);
                }
            });
            ackThread.start();

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
                    HttpRESTUtils.httpPost(uiIpAddress + "/catalog/ack/" + params[1], Config.DEBUG);
                }
            });
            ackThread.start();

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
            int updateBy = updateRequest.getInt("updateBy");

            // some updates may not have order-id tagged if not made via ui-server
            String orderId = updateRequest.optString("orderId", "DEFAULT");

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

            long exitTS = System.currentTimeMillis();
            delayWriter.write(entryTS + "-" + exitTS);
            delayWriter.newLine();
            delayWriter.flush();
            return jsonObject;
        });

        // REST end-point for order-server to add itself to the load-balancer
        post("/uibroadcast", (req, res) -> {
            JSONObject jsonObject = new JSONObject(req.body());

            Iterator<String> keys = jsonObject.keys();

            while (keys.hasNext()) {
                String key = keys.next();
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
                        JSONObject response = new JSONObject(
                                HttpRESTUtils.httpPostJSON(uiIpAddress + "/catalog/add", request, Config.DEBUG));

                        // check if we have the lock
                        if (response.optString("token", "false").equals("true")) {
                            lockManager.setLockToAcquired();
                        }

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
        return ("CatalogServer : tid=" + Thread.currentThread().getId() + " : "
                + sdf.format(resultdate) + " :: ");
    }

}
