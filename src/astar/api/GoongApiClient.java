package astar.api;

import astar.model.LatLng;
import astar.util.JSON.JSONArray;
import astar.util.JSON.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class GoongApiClient {
    public static String fetchDirections(String key, LatLng o, LatLng d) throws Exception {
        String url = String.format("https://rsapi.goong.io/Direction?origin=%f,%f&destination=%f,%f&vehicle=car&api_key=%s",
                o.lat, o.lng, d.lat, d.lng, key);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "AStarGoongApp/1.0");

        int code = c.getResponseCode();
        InputStream is = code == 200 ? c.getInputStream() : c.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String ln;
        while ((ln = br.readLine()) != null) sb.append(ln);
        if (code != 200) throw new RuntimeException("Goong error " + code + ": " + sb);
        return sb.toString();
    }

    public static List<LatLng> parseWaypoints(String json) {
        List<LatLng> pts = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray routes = root.getJSONArray("routes");
            if (routes.length() == 0) return pts;
            JSONArray legs = routes.getJSONObject(0).getJSONArray("legs");
            for (int i = 0; i < legs.length(); i++) {
                JSONArray steps = legs.getJSONObject(i).getJSONArray("steps");
                for (int j = 0; j < steps.length(); j++) {
                    JSONObject step = steps.getJSONObject(j);
                    JSONObject sl = step.getJSONObject("start_location");
                    pts.add(new LatLng(sl.getDouble("lat"), sl.getDouble("lng")));
                    if (j == steps.length() - 1) {
                        JSONObject el = step.getJSONObject("end_location");
                        pts.add(new LatLng(el.getDouble("lat"), el.getDouble("lng")));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pts;
    }
}
