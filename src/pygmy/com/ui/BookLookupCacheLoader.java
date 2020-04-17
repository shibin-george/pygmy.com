package pygmy.com.ui;

import org.cache2k.integration.FunctionalCacheLoader;
import org.json.JSONObject;

//the loader that is used in the read-through mode of the cache
public class BookLookupCacheLoader implements FunctionalCacheLoader<String, JSONObject> {

    @Override
    public JSONObject load(String bookId) throws Exception {
        return UIServer.lookupBook(bookId);
    }

}
