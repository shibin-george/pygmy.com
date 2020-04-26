package test;

import java.io.IOException;
import java.util.Random;

import org.json.JSONException;
import org.json.JSONObject;

import config.Config;
import pygmy.com.utils.HttpRESTUtils;
import utils.TestUtils;

public class CacheTests {

    public static void reportLatencies(String catalogServerURL, String uiServerURL) {

        String[] bookIds = { "xenart177", "67720min", "rpcdummies", "impstudent", "project3",
                "pioneer", "whytheory" };

        String[] topics = { "distributed-systems", "graduate-school" };

        long cacheMissLatency = 0, cacheHitLatency = 0;
        int numMisses = 0, numHits = 0;

        System.out.println("Starting /lookup latency tests..");
        for (int i = 0; i < 50; i++) {
            for (String bookId : bookIds) {
                TestUtils.invalidateCache(uiServerURL, bookId);

                cacheMissLatency -= System.currentTimeMillis();
                HttpRESTUtils.httpGet(uiServerURL + "/lookup/" + bookId, 0, Config.DEBUG);
                cacheMissLatency += System.currentTimeMillis();

                cacheHitLatency -= System.currentTimeMillis();
                HttpRESTUtils.httpGet(uiServerURL + "/lookup/" + bookId, 0, Config.DEBUG);
                cacheHitLatency += System.currentTimeMillis();

                numMisses++;
                numHits++;
            }
        }

        System.out.println("Average /lookup latency in the event of cache-miss: "
                + (double) cacheMissLatency / (double) numMisses + " milliseconds..");
        System.out.println("Avergae /lookup latency in the event of cache-hit: "
                + (double) cacheHitLatency / (double) numHits + " milliseconds..");

        cacheMissLatency = 0;
        cacheHitLatency = 0;
        numMisses = 0;
        numHits = 0;

        System.out.println("Starting /search latency tests..");
        for (int i = 0; i < 50; i++) {
            for (String topic : topics) {
                TestUtils.invalidateCache(uiServerURL, topic);

                cacheMissLatency -= System.currentTimeMillis();
                HttpRESTUtils.httpGet(uiServerURL + "/search/" + topic, 0, Config.DEBUG);
                cacheMissLatency += System.currentTimeMillis();

                for (int j = 0; j < 10; j++) {
                    cacheHitLatency -= System.currentTimeMillis();
                    HttpRESTUtils.httpGet(uiServerURL + "/search/" + topic, 0, Config.DEBUG);
                    cacheHitLatency += System.currentTimeMillis();
                    numHits++;
                }

                numMisses++;
            }
        }

        System.out.println("Average /search latency in the event of cache-miss: "
                + (double) cacheMissLatency / (double) numMisses + " milliseconds..");
        System.out.println("Avergae /search latency in the event of cache-hit: "
                + (double) cacheHitLatency / (double) numHits + " milliseconds..");

    }

    public static void testConsistency(String catalogServerURL, String uiServerURL)
            throws JSONException, IOException {
        String[] bookIds = { "xenart177", "67720min", "rpcdummies", "impstudent", "project3",
                "pioneer", "whytheory" };

        Random random = new Random();
        for (String bookId : bookIds) {
            for (int j = 0; j < 5; j++) {
                System.out.println("Performing initial /lookup of book: " + bookId);
                JSONObject lookupResponse =
                        new JSONObject(
                                HttpRESTUtils.httpGet(uiServerURL + "/lookup/" + bookId, 0, false));
                int initialCount = lookupResponse.getInt("Stock");
                System.out.println("Stock of book: " + bookId + " reported to be "
                        + initialCount);

                int howMany = random.nextInt(50) + 5;
                System.out.println("Buying " + howMany + " units of book: " + bookId);
                TestUtils.buyStock(catalogServerURL, uiServerURL, bookId, howMany);

                System.out.println("Performing second /lookup of book: " + bookId
                        + " to test cache consistency..");
                lookupResponse =
                        new JSONObject(
                                HttpRESTUtils.httpGet(uiServerURL + "/lookup/" + bookId, 0, false));
                int finalCount = lookupResponse.getInt("Stock");

                assert finalCount == initialCount - howMany;
                System.out.println("Stock of book: " + bookId + " correctly reported to be "
                        + initialCount);
            }
        }
    }

}
