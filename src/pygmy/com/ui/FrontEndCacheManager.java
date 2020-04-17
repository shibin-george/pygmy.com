package pygmy.com.ui;

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.event.CacheEntryRemovedListener;
import org.json.JSONObject;

public class FrontEndCacheManager {

    private Cache<String, JSONObject> topicLookupCache, bookLookupCache;

    public FrontEndCacheManager() {
        topicLookupCache = new Cache2kBuilder<String, JSONObject>() {
        }
                .name("tLC")
                .loader(new TopicLookupCacheLoader())
                .build();

        // build book-lookup cache but with limited entries
        bookLookupCache = new Cache2kBuilder<String, JSONObject>() {
        }
                .name("bLC")
                .loader(new BookLookupCacheLoader())
                .entryCapacity(3)
                .addListener(new CacheEntryRemovedListener<String, JSONObject>() {

                    @Override
                    public void onEntryRemoved(Cache<String, JSONObject> arg0,
                            CacheEntry<String, JSONObject> arg1) {
                        System.out.println(UIServer.getTime() +
                                "Removing book: " + arg1.getKey() + " from cache!");

                    }
                })
                .build();
    }

    public JSONObject getTopicLookupResults(String topic) {
        return topicLookupCache.get(topic);
    }

    public JSONObject getBookLookupResults(String bookId) {
        return bookLookupCache.get(bookId);
    }

    public boolean invalidateBookLookupCache(String bookId) {
        return bookLookupCache.containsAndRemove(bookId);
    }

}
