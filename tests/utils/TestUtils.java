package utils;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import config.Config;
import pygmy.com.utils.HttpRESTUtils;

public class TestUtils {

    public static int updateStock(String catalogServerURL, String uiServerURL,
            String bookId, int updateBy)
            throws JSONException, IOException {

        // get the initial count in stock
        JSONObject queryResponse = new JSONObject(
                HttpRESTUtils.httpGet(uiServerURL + "/lookup/" + bookId, 0, Config.DEBUG));
        int initialCount = queryResponse.getInt("Stock");

        // send a JSON POST request to catalog-server to add to stock
        JSONObject updateRequest = new JSONObject();
        updateRequest.put("bookId", bookId);
        updateRequest.put("updateBy", updateBy);

        JSONObject updateResponse = new JSONObject(
                HttpRESTUtils.httpPostJSON(catalogServerURL + "/update", updateRequest,
                        0, Config.DEBUG));
        int finalCount = updateResponse.getInt("Stock");

        assert finalCount == initialCount + updateBy;
        System.out.println("Updated stock of book: " + bookId + " to " + finalCount);
        return finalCount;
    }

    public static int buyStock(String catalogServerURL, String uiServerURL,
            String bookId, int howMany)
            throws JSONException, IOException {

        // get the initial count in stock
        JSONObject queryResponse = new JSONObject(
                HttpRESTUtils.httpGet(uiServerURL + "/lookup/" + bookId, 0, Config.DEBUG));
        int initialCount = queryResponse.getInt("Stock");

        // send a JSON POST request to catalog-server to add to stock
        JSONObject updateRequest = new JSONObject();
        updateRequest.put("bookId", bookId);
        updateRequest.put("count", howMany);

        JSONObject updateResponse = new JSONObject(
                HttpRESTUtils.httpPostJSON(uiServerURL + "/multibuy", updateRequest,
                        0, Config.DEBUG));
        int finalCount = updateResponse.getInt("Stock");

        assert finalCount == initialCount - howMany;
        return finalCount;
    }

    public static void invalidateCache(String uiServerURL, String bookId) {
        HttpRESTUtils.httpPost(uiServerURL + "/invalidate/" + bookId, 0, Config.DEBUG);
    }

}
