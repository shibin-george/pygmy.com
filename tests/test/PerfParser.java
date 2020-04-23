package test;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

public class PerfParser {

    public static void main(String[] args) {
        long totalDelay = 0;
        int numBuys = 0;
        ArrayList<Integer> list = new ArrayList<Integer>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(args[0]));
            String line = null;
            while ((line = reader.readLine()) != null) {
                String[] s = line.split("-");
                totalDelay += Long.parseLong(s[1]) - Long.parseLong(s[0]);

                numBuys += 1;
            }
            reader.close();

            System.out.println(args[0] + "->" + (double) (totalDelay) / (double) (numBuys)
                    + " milliseconds.");

        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
