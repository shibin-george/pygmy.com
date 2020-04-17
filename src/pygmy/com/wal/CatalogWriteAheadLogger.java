package pygmy.com.wal;

public class CatalogWriteAheadLogger extends WriteAheadLogger {

    /*
     * Each log record is a colon separated list of attributes:
     * 
     * 1) INITDB:<name of the file>:<timestamp>
     * Represents initialization of a catalog server from a on-disk file.
     * 
     * 2) QUERYTOPIC:<topic>:<timestamp>
     * Represents querying of a topic
     * 
     * 3) QUERYBOOK:<book-id>:<timestamp>
     * Represents querying of a book
     * 
     * 4) UPDATE:<book-id>:<count>:<timestamp>:<unique Order Id>
     * Represents updation of count of a book. The count can be a negative or
     * positive integer. The timestamp and the unique order id is also tagged
     * 
     */

    public CatalogWriteAheadLogger(String logFile) {
        super(logFile);
    }

    public boolean writeInitDB(String fileName, long timestamp) {
        if (fileName.contains(":"))
            return false;

        writeToWAL("INITDB:" + fileName + ":" + timestamp);
        return true;
    }

    public boolean writeUpdate(String bookId, int updateBy,
            long timestamp, String orderId) {
        if (bookId.contains(":") || orderId.contains(":"))
            return false;

        writeToWAL("UPDATE:" + bookId + ":" + updateBy +
                ":" + timestamp + ":" + orderId);
        return true;
    }

    public boolean writeQueryTopic(String topic, long timestamp) {
        if (topic.contains(":"))
            return false;

        writeToWAL("QUERYTOPIC:" + topic + ":" + timestamp);
        return true;
    }

    public boolean writeQueryBook(String bookId, long timestamp) {
        if (bookId.contains(":"))
            return false;

        writeToWAL("QUERYBOOK:" + bookId + ":" + timestamp);
        return true;
    }
}
