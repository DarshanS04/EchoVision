import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestScrape {
    public static void main(String[] args) {
        try {
            String ytUrl = "https://www.youtube.com/watch?v=7wtfhZwyrcc"; // Believer
            String urlStr = "https://api.song.link/v1-alpha.1/links?url=" + ytUrl;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder sb = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                sb.append(inputLine);
            }
            in.close();
            
            String json = sb.toString();
            System.out.println("JSON Response: " + json.substring(0, Math.min(200, json.length())));
            
            // Extract spotify track ID
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"spotify\":\"https://open.spotify.com/track/([a-zA-Z0-9]+)\"").matcher(json);
            if (m.find()) {
                System.out.println("SPOTIFY ID: " + m.group(1));
            } else {
                java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("spotify.com/track/([a-zA-Z0-9]+)").matcher(json);
                if (m2.find()) {
                    System.out.println("SPOTIFY ID 2: " + m2.group(1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
