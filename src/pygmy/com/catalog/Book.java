package pygmy.com.catalog;

import org.json.JSONObject;

public class Book {

    String bookName, topicName, uniqueId;
    int cost, count;

    public Book(String bName, String tName, String uniqueId, int cost, int count) {
        bookName = bName;
        topicName = tName;
        this.cost = cost;
        this.count = count;
        this.uniqueId = uniqueId;
    }

    public String getTopic() {
        return topicName;
    }

    public int getCost() {
        return cost;
    }

    public int getStock() {
        return count;
    }

    public String getName() {
        return bookName;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    // should only be called this in a thread-safe manner!
    public void setStock(int c) {
        this.count = c;
    }

    public JSONObject getConciseJSON() {
        JSONObject resultJsonObject = new JSONObject();
        resultJsonObject.put("Name", getName());
        resultJsonObject.put("Topic", getTopic());
        resultJsonObject.put("ID", getUniqueId());
        return resultJsonObject;
    }

    public JSONObject JSONifySelf() {
        JSONObject resultJsonObject = new JSONObject();
        resultJsonObject.put("Name", getName());
        resultJsonObject.put("Topic", getTopic());
        resultJsonObject.put("ID", getUniqueId());
        resultJsonObject.put("Cost", getCost());
        resultJsonObject.put("Stock", getStock());
        return resultJsonObject;
    }

}
