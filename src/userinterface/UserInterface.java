package userinterface;

import java.io.IOException;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;

import config.Config;
import pygmy.com.utils.HttpRESTUtils;

public class UserInterface {

    private static String uiServerURL;
    private static Scanner scanner;

    public static void main(String args[]) {
        if (args.length == 0) {
            System.out
                    .println("UIServer not specified! Exiting..");
            System.exit(1);
        }

        uiServerURL = args[0] + ":" + Config.UI_SERVER_PORT;

        if (!uiServerURL.startsWith("http://")) {
            uiServerURL = "http://" + uiServerURL;
        }

        try {
            scanner = new Scanner(System.in);
            int op = 0;
            while (true) {
                showEndpoints();
                op = Integer.parseInt(scanner.nextLine());
                switch (op) {
                case 1: // search

                    String topicName = chooseFromTopics();
                    System.out.println(new JSONObject(
                            HttpRESTUtils.httpGet(uiServerURL + "/search/" + topicName))
                                    .toString(2));
                    break;
                case 2: // lookup
                    String lookupBookId = chooseBookId();
                    System.out.println(new JSONObject(
                            HttpRESTUtils.httpGet(uiServerURL + "/lookup/" + lookupBookId))
                                    .toString(2));
                    break;
                case 3: // buy
                    String buyBookId = chooseBookId();
                    int numItems = chooseCount();
                    if (numItems == 1) {
                        System.out.println(new JSONObject(
                                HttpRESTUtils.httpPost(uiServerURL + "/buy/" + buyBookId))
                                        .toString(2));
                    } else {
                        JSONObject buyRequest = new JSONObject();
                        buyRequest.put("bookId", buyBookId);
                        buyRequest.put("count", numItems);
                        System.out.println(new JSONObject(
                                HttpRESTUtils.httpPostJSON(uiServerURL + "/multibuy", buyRequest))
                                        .toString(2));
                    }
                    break;
                case 0:
                default:
                    System.exit(1);
                }
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input! Exiting..");
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int chooseCount() {
        scanner = new Scanner(System.in);
        System.out.println("\nEnter count to buy:");
        int count = Integer.parseInt(scanner.nextLine());
        return count;
    }

    private static String chooseBookId() {
        scanner = new Scanner(System.in);
        System.out.println("\nEnter the exact ID of the book:");
        String bookId = scanner.nextLine();
        return bookId;
    }

    private static String chooseFromTopics() {
        scanner = new Scanner(System.in);
        System.out.println("\nEnter:\n"
                + "1 to search(\"distributed systems\")\n"
                + "2 to search(\"graduate school\")");
        int input = Integer.parseInt(scanner.nextLine());
        switch (input) {
        case 1:
            return "distributed-systems";
        case 2:
            return "graduate-school";
        default:
            System.out.println("Invalid input! Exiting..");
            System.exit(1);
        }
        return "";
    }

    private static void showEndpoints() {
        System.out.println("\nEnter:\n"
                + "1 for search(Topic Name)\n"
                + "2 for lookup(Book ID)\n"
                + "3 for buy(Book ID)\n"
                + "0 to exit.");
    }

}
