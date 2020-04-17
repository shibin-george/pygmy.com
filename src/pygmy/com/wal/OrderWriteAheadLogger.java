package pygmy.com.wal;

public class OrderWriteAheadLogger extends WriteAheadLogger {

    /*
     * Each log record is a colon separated list of attributes:
     * 
     * 1)
     * BUY:<book-id>:<count>:<ui-timestamp>:<order-timestamp>:<catalog-timestamp>:<
     * unique
     * Order id>
     * 
     */

    public OrderWriteAheadLogger(String logFile) {
        super(logFile);
    }

    public boolean writeBuy(String bookId, int count, long uiTimestamp, long orderTimestamp,
            long catalogTimestamp, String orderId, String status) {
        if (bookId.contains(":") || orderId.contains(":"))
            return false;

        writeToWAL("BUY:" + bookId + ":" + count +
                ":" + uiTimestamp + ":" + orderTimestamp +
                ":" + catalogTimestamp + ":" + orderId + ":" + status);
        return true;
    }

}
