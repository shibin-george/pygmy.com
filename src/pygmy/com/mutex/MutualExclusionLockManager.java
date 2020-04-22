package pygmy.com.mutex;

public class MutualExclusionLockManager {

    private boolean lockAcquired;

    public MutualExclusionLockManager() {
        lockAcquired = false; // start out in "RELEASED" state
    }

    public synchronized void setLockToAcquired() {
        lockAcquired = true;
    }

    public synchronized boolean isLockAcquired() {
        return lockAcquired;
    }

    public synchronized void setLockToReleased() {
        lockAcquired = false;
    }
}
