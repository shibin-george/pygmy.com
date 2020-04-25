package pygmy.com.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

public class HttpRESTUtils {

    private static int HTTP_CONNECT_TIMEOUT = 2000 /* milliseconds */;

    public static String httpGet(String urlString, int timeout, boolean log) {

        try {
            URL url = new URL(urlString);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // set timeout
            conn.setConnectTimeout(HTTP_CONNECT_TIMEOUT);
            if (timeout != 0)
                conn.setReadTimeout(timeout);

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
            if (log)
                e.printStackTrace();
            return null;
        }
    }

    public static String httpPost(String urlString, int timeout, boolean log) {

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // set connection type to POST; by default, this is GET
            conn.setRequestMethod("POST");

            conn.setRequestProperty("Accept", "application/json");

            // set timeout
            conn.setConnectTimeout(HTTP_CONNECT_TIMEOUT);
            if (timeout != 0)
                conn.setReadTimeout(timeout);

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
            if (log)
                e.printStackTrace();
            return null;
        }
    }

    public static String httpPostJSON(String urlString,
            JSONObject jsonObject, int timeout, boolean log) {

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // set connection type to POST; by default, this is GET
            conn.setRequestMethod("POST");

            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            // set timeout
            conn.setConnectTimeout(HTTP_CONNECT_TIMEOUT);
            if (timeout != 0)
                conn.setReadTimeout(timeout);

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
            if (log)
                e.printStackTrace();
            return null;
        }
    }
}
