package pygmy.com.wal;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

public class WriteAheadLogger {

    private Object writeLock = null;

    String logFile = null;
    BufferedWriter writer = null;

    public WriteAheadLogger(String logFile) {

        writeLock = new Object();
        this.logFile = logFile;
        try {
            writer = new BufferedWriter(new FileWriter(logFile, false));
            writer.close();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void writeToWAL(String toWrite) {
        synchronized (writeLock) {
            try {
                writer = new BufferedWriter(new FileWriter(logFile, true));
                writer.write(toWrite);
                writer.newLine();
                writer.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

}
