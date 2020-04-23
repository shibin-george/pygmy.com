package pygmy.com.mutex;

public class MutualExclusionLockManager {

    private boolean lockAcquired;
    private long lastReleasedTimestamp = 0;
    private long LOCK_TIMEOUT = 0;

    public MutualExclusionLockManager(long timeout) {
        lockAcquired = false; // start out in "RELEASED" state
        lastReleasedTimestamp = System.currentTimeMillis();
        LOCK_TIMEOUT = timeout;
    }

    public synchronized void setLockToAcquired() {
        lockAcquired = true;
    }

    public synchronized boolean isLockAcquired() {
        return lockAcquired;
    }

    public synchronized void setLockToReleased() {
        lockAcquired = false;
        lastReleasedTimestamp = System.currentTimeMillis();
    }

    public boolean hasBeenTooLongSinceRelease() {
        if (System.currentTimeMillis() - lastReleasedTimestamp > LOCK_TIMEOUT)
            return true;

        return false;
    }
}
