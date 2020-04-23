package test;

import java.io.IOException;
import java.util.Random;

import org.json.JSONException;
import org.json.JSONObject;

import config.Config;
import pygmy.com.utils.HttpRESTUtils;
import utils.TestUtils;

public class ConsecutiveTests {

    public static void testConsecutiveBuy(String catalogServerURL,
            String uiServerURL, boolean checkOutOfStockBehavior)
            throws JSONException, IOException {

        String[] bookIds = { "xenart177", "67720min", "rpcdummies", "impstudent" };

        Random random = new Random();

        for (String bookId : bookIds) {

            int numBuyRequests = random.nextInt(20) + 5, numPassed = 0;

            System.out.println("Initial stock of: " + bookId);

            // get the initial count in stock
            JSONObject queryResponse = new JSONObject(
                    HttpRESTUtils.httpGet(uiServerURL + "/lookup/" + bookId, Config.DEBUG));

            System.out.println(queryResponse.toString(2));
            int stockCount = queryResponse.getInt("Stock");
            int resetCount = stockCount;

            if (checkOutOfStockBehavior) {
                System.out.println("Starting consecutive-buy test of " + bookId + " to "
                        + "check out-of-stock behavior...");

                // if checking out-of-stock behavior, reduce count of
                // the items by enough for half the buys to fail

                // round off total buy requests to an even number
                numBuyRequests /= 2;
                numBuyRequests *= 2;
                int updateBy = stockCount - numBuyRequests / 2;
                updateBy *= -1; // reduce the stock
                stockCount = TestUtils.updateStock(catalogServerURL, uiServerURL, bookId, updateBy);
            } else {
                System.out.println("Starting consecutive-buy test of " + bookId
                        + " assuming enough stock is avaliable...");

                // if not checking out-of-stock behavior, stock up
                // the items enough for all buys to be successful
                stockCount =
                        TestUtils.updateStock(catalogServerURL, uiServerURL, bookId,
                                numBuyRequests);
            }

            int initialStockCount = stockCount;
            boolean expectedFailure = (stockCount <= 0);

            int iter = numBuyRequests, successfulBuys = 0;
            while (iter-- > 0) {
                JSONObject buyResponse = new JSONObject(
                        HttpRESTUtils.httpPost(uiServerURL + "/buy/" + bookId, Config.DEBUG));
                if (!expectedFailure) {
                    assert buyResponse.getInt("code") == 0;
                    int newCount = buyResponse.getInt("Stock");

                    // the new count should be exactly 1 less than previous count
                    assert newCount == stockCount - 1;
                    stockCount -= 1;
                    successfulBuys += 1;
                    System.out.println("\nSuccessfully bought the book. Stock: " + stockCount);
                } else {
                    assert buyResponse.getInt("code") < 0;
                    System.out.println("\nExpected failure to buy the book. Stock: " + stockCount);
                }

                numPassed += 1;
                // the next iteration should fail if the stock is <= 0
                expectedFailure = (stockCount <= 0);

                System.out
                        .println("Passed " + numPassed + " out of " + numBuyRequests
                                + " buy requests");
            }

            System.out.println("Finishing consecutive-buy test on book: " + bookId + "...");
            System.out.println("Final stock of " + bookId + " after " + successfulBuys
                    + " successful buy requests "
                    + "and " + (numBuyRequests - successfulBuys) + " expected failures");

            // final check of Stock
            queryResponse = new JSONObject(
                    HttpRESTUtils.httpGet(uiServerURL + "/lookup/" + bookId, Config.DEBUG));
            System.out.println(queryResponse.toString(2));

            if (checkOutOfStockBehavior) {
                // exactly half of the number of buys should be successful
                assert successfulBuys == numBuyRequests / 2;
            } else {
                // all the buys should be successful
                assert successfulBuys == numBuyRequests;
            }

            int finalCount = queryResponse.getInt("Stock");
            int expectedCount = initialStockCount - successfulBuys;
            assert finalCount == expectedCount;
            System.out.println(
                    "Final count of " + finalCount + " matches expected count of " + expectedCount);

            if (checkOutOfStockBehavior) {
                System.out.println(
                        "Bringing back stock of book: " + bookId + " to " + resetCount
                                + " units for further tests.");
                TestUtils.updateStock(catalogServerURL, uiServerURL, bookId, resetCount);
            }
        }
    }

}
