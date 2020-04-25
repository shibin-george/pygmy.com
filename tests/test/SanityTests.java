package test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import config.Config;
import pygmy.com.utils.HttpRESTUtils;
import utils.TestUtils;

public class SanityTests {

    public static void testSearchAndLookup(String catalogServerURL, String uiServerURL)
            throws JSONException, IOException {
        HashMap<String, String> bookTitle = new HashMap<String, String>();
        bookTitle.put("67720min", "How to get a good grade in 677 in 20 minutes a day.");
        bookTitle.put("rpcdummies", "RPCs for Dummies.");
        bookTitle.put("xenart177", "Xen and the Art of Surviving Graduate School.");
        bookTitle.put("impstudent", "Cooking for the Impatient Graduate Student.");
        bookTitle.put("pioneer", "Spring in the Pioneer Valley.");
        bookTitle.put("whytheory", "Why theory classes are so hard.");
        bookTitle.put("project3", "How to finish Project 3 on time");

        HashMap<String, String> bookTopic = new HashMap<String, String>();
        bookTopic.put("impstudent", "graduate-school");
        bookTopic.put("xenart177", "graduate-school");
        bookTopic.put("67720min", "distributed-systems");
        bookTopic.put("rpcdummies", "distributed-systems");
        bookTopic.put("pioneer", "graduate-school");
        bookTopic.put("whytheory", "graduate-school");
        bookTopic.put("project3", "distributed-systems");

        System.out.println("\nTesting /search endpoint..");

        // search for topic: distributed-systems
        JSONArray searchResponse = new JSONObject(
                HttpRESTUtils.httpGet(uiServerURL + "/search/distributed-systems", 0, Config.DEBUG))
                        .getJSONArray("items");

        assert searchResponse.length() == 3;
        System.out.println("/search/distributed-systems fetched correct number of books!");

        for (int i = 0; i < searchResponse.length(); i++) {
            // System.out.println(searchResponse.getJSONObject(i).toString(2));
            assert searchResponse.getJSONObject(i).get("Topic").equals("distributed-systems");
            assert searchResponse.getJSONObject(i).get("Topic")
                    .equals(bookTopic.get(searchResponse.getJSONObject(i).get("ID")));
            assert searchResponse.getJSONObject(i).get("Name")
                    .equals(bookTitle.get(searchResponse.getJSONObject(i).get("ID")));
        }
        System.out.println("/search/distributed-systems fetched all the correct books!");

        // search for topic: distributed-systems
        searchResponse = new JSONObject(
                HttpRESTUtils.httpGet(uiServerURL + "/search/graduate-school", 0, Config.DEBUG))
                        .getJSONArray("items");

        assert searchResponse.length() == 4;
        System.out.println("/search/graduate-school fetched correct number of books!");

        for (int i = 0; i < searchResponse.length(); i++) {
            assert searchResponse.getJSONObject(i).get("Topic").equals("graduate-school");
            assert searchResponse.getJSONObject(i).get("Topic")
                    .equals(bookTopic.get(searchResponse.getJSONObject(i).get("ID")));
            assert searchResponse.getJSONObject(i).get("Name")
                    .equals(bookTitle.get(searchResponse.getJSONObject(i).get("ID")));
        }
        System.out.println("/search/graduate-school fetched all the correct books!");

        System.out.println("\nTesting /lookup endpoint..");
        // search for topic: distributed-systems
        JSONObject lookupResponse = new JSONObject(
                HttpRESTUtils.httpGet(uiServerURL + "/lookup/impstudent", 0, Config.DEBUG));

        assert lookupResponse.get("ID").equals("impstudent");
        assert lookupResponse.get("Topic")
                .equals(bookTopic.get(lookupResponse.get("ID")));
        assert lookupResponse.get("Name")
                .equals(bookTitle.get(lookupResponse.get("ID")));
        System.out.println("/lookup/impstudent fetched all the correct details for the book!");

        // search for topic: distributed-systems
        lookupResponse = new JSONObject(
                HttpRESTUtils.httpGet(uiServerURL + "/lookup/xenart177", 0, Config.DEBUG));

        assert lookupResponse.get("ID").equals("xenart177");
        assert lookupResponse.get("Topic")
                .equals(bookTopic.get(lookupResponse.get("ID")));
        assert lookupResponse.get("Name")
                .equals(bookTitle.get(lookupResponse.get("ID")));
        System.out.println("/lookup/xenart177 fetched all the correct details for the book!");

        // search for topic: distributed-systems
        lookupResponse = new JSONObject(
                HttpRESTUtils.httpGet(uiServerURL + "/lookup/67720min", 0, Config.DEBUG));

        assert lookupResponse.get("ID").equals("67720min");
        assert lookupResponse.get("Topic")
                .equals(bookTopic.get(lookupResponse.get("ID")));
        assert lookupResponse.get("Name")
                .equals(bookTitle.get(lookupResponse.get("ID")));
        System.out.println("/lookup/67720min fetched all the correct details for the book!");

        // search for topic: distributed-systems
        lookupResponse = new JSONObject(
                HttpRESTUtils.httpGet(uiServerURL + "/lookup/rpcdummies", 0, Config.DEBUG));

        assert lookupResponse.get("ID").equals("rpcdummies");
        assert lookupResponse.get("Topic")
                .equals(bookTopic.get(lookupResponse.get("ID")));
        assert lookupResponse.get("Name")
                .equals(bookTitle.get(lookupResponse.get("ID")));
        System.out.println("/lookup/rpcdummies fetched all the correct details for the book!");

        System.out.println("/search and /lookup endpoints are working perfectly fine!");
    }

    public static void testMultiBuyAndUpdate(String catalogServerURL, String uiServerURL)
            throws JSONException, IOException {

        String[] bookIds = { "xenart177", "67720min", "rpcdummies", "impstudent" };

        for (String bookId : bookIds) {

            // get the initial count in stock
            JSONObject lookupResponse = new JSONObject(
                    HttpRESTUtils.httpGet(uiServerURL + "/lookup/" + bookId, 0, Config.DEBUG));

            int initialStock = lookupResponse.getInt("Stock");

            System.out.println("Initial stock of " + bookId + " is " + initialStock);
            Random random = new Random();
            int updateBy = random.nextInt(500);
            System.out.println(
                    "Now updating stock of " + bookId + " to " + (initialStock + updateBy));

            TestUtils.updateStock(catalogServerURL, uiServerURL, bookId, updateBy);

            // lookup again
            lookupResponse = new JSONObject(
                    HttpRESTUtils.httpGet(uiServerURL + "/lookup/" + bookId, 0, Config.DEBUG));

            assert lookupResponse.getInt("Stock") == initialStock + updateBy;
            System.out.println(
                    "Stock of " + bookId + " after update operation is as expected: "
                            + (initialStock + updateBy));

            // send a multi-buy request to UIServer
            System.out.println(
                    "Now buying " + updateBy + " units of " + bookId);
            JSONObject buyRequest = new JSONObject();
            buyRequest.put("bookId", bookId);
            buyRequest.put("count", updateBy);

            JSONObject buyResponse = new JSONObject(
                    HttpRESTUtils.httpPostJSON(uiServerURL + "/multibuy", buyRequest,
                            0, Config.DEBUG));

            assert buyResponse.getInt("Stock") == initialStock;
            System.out.println(
                    "Stock of " + bookId + " after buy operation is as expected: "
                            + initialStock);
        }

        System.out.println("/multibuy and /update endpoints are working perfectly fine!");
    }

}
