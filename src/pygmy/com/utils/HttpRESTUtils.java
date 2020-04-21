package pygmy.com.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

public class HttpRESTUtils {

    public static String httpGet(String urlString) {

        try {
            URL url = new URL(urlString);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // buffer the result into string
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            String responseLine;
            StringBuffer response = new StringBuffer();

            while ((responseLine = reader.readLine()) != null) {
                response.append(responseLine);
            }

            reader.close();
            conn.disconnect();

            return response.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String httpPost(String urlString) {

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // set connection type to POST; by default, this is GET
            conn.setRequestMethod("POST");

            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() != 200) {
                throw new IOException(conn.getResponseMessage());
            }

            // buffer the result into string
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            String responseLine;
            StringBuffer response = new StringBuffer();

            while ((responseLine = reader.readLine()) != null) {
                response.append(responseLine);
            }

            reader.close();
            conn.disconnect();

            return response.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String httpPostJSON(String urlString,
            JSONObject jsonObject) throws IOException, ConnectException {

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // set connection type to POST; by default, this is GET
            conn.setRequestMethod("POST");

            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            /*
             * if (conn.getResponseCode() != 200) {
             * throw new IOException(conn.getResponseMessage());
             * }
             */
            // write the JSON request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonObject.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
                os.flush();
            }

            // buffer the result into string
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            String responseLine;
            StringBuffer response = new StringBuffer();

            while ((responseLine = reader.readLine()) != null) {
                response.append(responseLine);
            }

            reader.close();
            conn.disconnect();

            return response.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
