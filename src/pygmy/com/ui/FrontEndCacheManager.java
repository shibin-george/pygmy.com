package pygmy.com.ui;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.event.CacheEntryRemovedListener;
import org.json.JSONException;
import org.json.JSONObject;

import pygmy.com.scheduler.HeartbeatMonitor;

public class FrontEndCacheManager {

    private Cache<String, JSONObject> topicLookupCache, bookLookupCache;

    private HeartbeatMonitor<String, String, String, JSONObject> catalogHeartbeatMonitor = null;

    public FrontEndCacheManager(HeartbeatMonitor<String, String, String, JSONObject> cMonitor) {

        catalogHeartbeatMonitor = cMonitor;

        // build topic-lookup cache
        topicLookupCache = new Cache2kBuilder<String, JSONObject>() {
        }
                .name("tLC")
                .build();

        // build book-lookup cache but with limited entries
        bookLookupCache = new Cache2kBuilder<String, JSONObject>() {
        }
                .name("bLC")
                .entryCapacity(3)
                .addListener(new CacheEntryRemovedListener<String, JSONObject>() {

                    @Override
                    public void onEntryRemoved(Cache<String, JSONObject> arg0,
                            CacheEntry<String, JSONObject> arg1) {
                        System.out.println(getTime() +
                                "Removing book: " + arg1.getKey() + " from cache!");

                    }
                })
                .build();
    }

    public JSONObject getTopicLookupResults(String topic, String jobId) {
        JSONObject topicLookupResult = null;
        try {
            if (topicLookupCache.peek(topic) == null) {
                UIServer.lookupTopic(topic, jobId);
                while (!catalogHeartbeatMonitor.isJobComplete(jobId)) {
                    // check again after a short spin
                    Thread.sleep(100);
                }

                Thread.sleep(100);
                topicLookupCache.put(topic, catalogHeartbeatMonitor.getResponse(jobId));
                catalogHeartbeatMonitor.cleanupJob(jobId);
            } else {
                System.out.println(getTime() + "Result for this query is already in Cache!");
            }

            topicLookupResult = topicLookupCache.get(topic);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return topicLookupResult;
    }

    public JSONObject getBookLookupResults(String bookId, String jobId) {
        JSONObject bookLookupResult = null;
        try {
            if (bookLookupCache.peek(bookId) == null) {
                UIServer.lookupBook(bookId, jobId);
                while (!catalogHeartbeatMonitor.isJobComplete(jobId)) {
                    // check again after a short spin
                    Thread.sleep(100);
                }

                Thread.sleep(100);
                bookLookupCache.put(bookId, catalogHeartbeatMonitor.getResponse(jobId));
                catalogHeartbeatMonitor.cleanupJob(jobId);
            } else {
                System.out.println(getTime() + "Result for this query is already in Cache!");
            }

            bookLookupResult = bookLookupCache.get(bookId);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bookLookupResult;
    }

    public synchronized boolean invalidateBookLookupCache(String bookId) {
        return bookLookupCache.containsAndRemove(bookId);
    }

    // Used to get time in a readable format for logging
    private static String getTime() {
        long milliSeconds = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy; HH:mm:ss.SSS");
        Date resultdate = new Date(milliSeconds);
        return ("FrontEndCacheManager : tid=" + Thread.currentThread().getId() + " : "
                + sdf.format(resultdate) + " :: ");
    }
}
