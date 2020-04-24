package test;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Random;

import org.json.JSONException;
import org.json.JSONObject;

import config.Config;
import pygmy.com.utils.HttpRESTUtils;
import utils.TestUtils;

public class ConcurrentTests {

    static Object concurrentTestLock = new Object();

    // 3 threads will concurrently make buy() requests to the UI-server
    static int numThreads = 3;
    static int[] numBuyRequests;
    static int[] successfulBuys;
    static int totalBuyRequests = 0;

    static HashMap<Integer, ArrayList<SimpleEntry<Long, Integer>>> timeStampedEntries;

    static int stockCount = 0;

    private static class BuyTask implements Runnable {

        String catalogServerURL = "";
        String uiServerURL = "";
        String bookId = "";

        public BuyTask(String uiURL, String bId) {
            uiServerURL = uiURL;
            bookId = bId;
        }

        @Override
        public void run() {
            int id = Integer.parseInt(Thread.currentThread().getName());
            int iter = numBuyRequests[id];

            timeStampedEntries.put(id, new ArrayList<SimpleEntry<Long, Integer>>());

            Random random = new Random();

            while (iter-- > 0) {
                JSONObject buyResponse;
                try {
                    buyResponse = new JSONObject(
                            HttpRESTUtils.httpPost(uiServerURL + "/buy/" + bookId, Config.DEBUG));

                    // all successful buy requests must come tagged with
                    // CatalogServer's unique timestamp
                    if (buyResponse.getInt("code") == 0) {
                        long ts = buyResponse.getLong("CatalogServerTimeStamp");
                        int stock = buyResponse.getInt("Stock");

                        // add the stock at this timestamp to the list
                        timeStampedEntries.get(id).add(new SimpleEntry<Long, Integer>(ts, stock));
                    }

                    // Thread.sleep(random.nextInt(100));
                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

    }

    public static void testConcurrentBuy(String catalogServerURL,
            String uiServerURL) throws JSONException, IOException {

        // specify bookId here
        String bookId = "impstudent";

        Random random = new Random();

        numBuyRequests = new int[numThreads];
        successfulBuys = new int[numThreads];

        for (int i = 0; i < numThreads; i++) {
            numBuyRequests[i] = random.nextInt(10);
            successfulBuys[i] = 0;
            totalBuyRequests += numBuyRequests[i];
        }

        System.out.println("Initial stock of:" + bookId);

        // get the initial count in stock
        JSONObject queryResponse = new JSONObject(
                HttpRESTUtils.httpGet(uiServerURL + "/lookup/" + bookId, Config.DEBUG));

        System.out.println(queryResponse.toString(2));

        System.out.println("Starting concurrent-buy test ");

        // if not checking out-of-stock behavior, stock up
        // the items enough for all buys to be successful
        stockCount =
                TestUtils.updateStock(catalogServerURL, uiServerURL, bookId,
                        totalBuyRequests);

        timeStampedEntries =
                new HashMap<Integer, ArrayList<SimpleEntry<Long, Integer>>>();

        Thread[] threads = new Thread[numThreads];

        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(new BuyTask(uiServerURL, bookId), i + "");
        }

        for (int i = 0; i < numThreads; i++) {
            threads[i].start();
        }

        for (int i = 0; i < numThreads; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        // now that all threads have finished execution, check
        // their timeStampedEntries.
        // If we sort the timestamps in increaseing order, the stock
        // count should uniformly decrease by 1.
        ArrayList<SimpleEntry<Long, Integer>> mergedEntries =
                new ArrayList<SimpleEntry<Long, Integer>>();

        for (int i = 0; i < numThreads; i++) {
            mergedEntries.addAll(timeStampedEntries.get(i));
        }

        Collections.sort(mergedEntries, new Comparator<SimpleEntry<Long, Integer>>() {

            @Override
            public int compare(SimpleEntry<Long, Integer> o1, SimpleEntry<Long, Integer> o2) {
                return (int) (o1.getKey() - o2.getKey());
            }

        });

        assert mergedEntries.size() == totalBuyRequests;
        System.out.println("All buy() requests passed as expected.");
        for (int i = 0; i < numThreads; i++) {
            System.out.println("Thread " + (i + 1) + " executed " + numBuyRequests[i]
                    + " requests successfully.");
        }

        int prev = stockCount;
        for (SimpleEntry<Long, Integer> entry : mergedEntries) {
            // every stock entry (sorted by increasing order of timestamp)
            // should be exactly one less than the previous one
            assert entry.getValue() == (prev - 1);
            prev = entry.getValue();
        }

        System.out.println("All threads had consistent view of the stock.");

        int finalCount = mergedEntries.get(totalBuyRequests - 1).getValue();
        int expectedCount = stockCount - totalBuyRequests;
        assert finalCount == expectedCount;
        System.out.println(
                "Final count of " + finalCount + " matches expected count of " + expectedCount);

        System.out
                .println("All threads had consistent view of the stock! Exiting concurrent test..");
    }

}
