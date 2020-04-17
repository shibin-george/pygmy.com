package pygmy.com.catalog;

import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.post;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONObject;

import config.Config;
import pygmy.com.wal.CatalogWriteAheadLogger;

public class CatalogServer {

    private static String initialInventoryPath = "";
    private static CatalogDatabase catalogDb = null;

    public static void main(String[] args) throws IOException {
        System.out.println("Starting Catalog Server...");

        // the first argument for the catalog server should be the
        // path from which initial state of inventory can be read from
        if (args.length < 2) {
            System.out.println(getTime() + "No initial inventory or WAL location "
                    + "provided! Exiting..");
            System.exit(1);
        }

        initialInventoryPath = args[0];
        catalogDb = new CatalogDatabase(new CatalogWriteAheadLogger(args[1]));

        BufferedWriter delayWriter =
                new BufferedWriter(new BufferedWriter(new FileWriter(args[1] + ".delay", false)));

        if (!catalogDb.init(initialInventoryPath)) {
            System.out.println(getTime() + "INIT of DB from on-disk file failed! Exiting..");
            System.exit(1);
        }

        // start listening on pre-configured port
        port(Integer.parseInt(Config.CATALOG_SERVER_PORT));

        // expose the endpoints

        // query-by-topic
        get("/query/topic/:topicName", (req, res) -> {
            System.out.println(
                    getTime() + "Searching for books on topic: " + req.params(":topicName"));
            ArrayList<Book> booksByTopic = catalogDb.searchByTopic(req.params(":topicName"));
            res.type("application/json");
            JSONObject jsonObject = new JSONObject();
            JSONArray jsonArray = new JSONArray();
            for (Book book : booksByTopic) {
                jsonArray.put(book.getConciseJSON());
            }
            jsonObject.put("items", jsonArray);

            return jsonObject;
        });

        // query-by-bookId
        get("/query/book/:bookId", (req, res) -> {
            System.out.println(getTime() + "Searching for book: " + req.params(":bookId"));
            Book book = catalogDb.searchById(req.params(":bookId"));

            res.type("application/json");
            if (book == null) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("CatalogStatus", "This item doesn't exist in the inventory.");
                return jsonObject;
            }

            return book.JSONifySelf();
        });

        // update count of a book
        // this endpoint can only increase or decrease the count
        // of a book. The JSON request should have the "updateBy"
        // set to +x or -y. It should also have the "bookId" set
        post("/update", (req, res) -> {

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

            res.type("application/json");
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
