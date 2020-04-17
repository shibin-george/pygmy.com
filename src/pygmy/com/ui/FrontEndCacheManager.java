package pygmy.com.ui;

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.json.JSONObject;

public class FrontEndCacheManager {

    private Cache<String, JSONObject> topicLookupCache, bookLookupCache;

    public FrontEndCacheManager() {
        topicLookupCache = new Cache2kBuilder<String, JSONObject>() {
        }
                .name("tLC").loader(new TopicLookupCacheLoader()).build();

        bookLookupCache = new Cache2kBuilder<String, JSONObject>() {
        }
                .name("bLC").loader(new BookLookupCacheLoader()).build();
    }

    public JSONObject getTopicLookupResults(String topic) {
        return topicLookupCache.get(topic);
    }

    public JSONObject getBookLookupResults(String bookId) {
        return bookLookupCache.get(bookId);
    }

}
