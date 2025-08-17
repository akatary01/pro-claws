package com.proclaws;

import java.util.concurrent.Callable;

import org.json.JSONArray;
import org.json.JSONObject;

public class Utils {
    /** Constants **/
    public static final String BASE_DIR = System.getProperty("user.dir");
    /** Dirs **/
    public static final String ASSETS_DIR = BASE_DIR + "/assets";
    /** Files **/
    public static final String ROUTES_FILE_PATH = ASSETS_DIR + "/routes.json";

    /** Functions **/ 
    public static <T> T safeCall(Callable<T> action) {
        try { action.call(); } catch (Exception e) {}
        return null;
    }
    
    public static JSONArray filterJsonArray(JSONArray array, String key, Object value) {
        final JSONArray filtered = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            final int idx = i;
            final JSONObject obj = safeCall(() -> array.getJSONObject(idx));
            if (obj.has(key) && safeCall(() -> obj.get(key)).equals(value)) {
                filtered.put(obj);
            }
        }
        return filtered;
    }
}
