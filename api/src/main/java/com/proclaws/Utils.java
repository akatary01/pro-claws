package com.proclaws;

import java.util.concurrent.Callable;
import java.util.function.Function;

import org.json.JSONArray;
import org.json.JSONObject;

public class Utils {
    /** Constants **/
    public static final String BASE_DIR = System.getProperty("user.dir") + "/api";
    /** Dirs **/
    public static final String ASSETS_DIR = BASE_DIR + "/assets";
    /** Files **/
    public static final String ROUTES_FILE_PATH = ASSETS_DIR + "/routes.json";
    public static final String CONFIG_FILE_PATH = ASSETS_DIR + "/config.json";
    public static final String API_KEYS_FILE_PATH = ASSETS_DIR + "/api_keys.json";

    /** Nayax **/
    public static final class Nayax {
        public static final String API_BASE_URL = "https://lynx.nayax.com/operational/v1";
        public static final String NAYAX_MACHINES = API_BASE_URL + "/machines";
        // Lambda that takes a machineId and returns URL string
        public static final Function<String, String> NAYAX_MACHINES_LAST_SALES =
            (machineId) -> NAYAX_MACHINES + "/" + machineId + "/lastSales";
        public static final String CREDIT_CARD = "credit card";
    }

    /** Functions **/ 
    public static <T> T safeCall(Callable<T> action) {
        try { return action.call(); } catch (Exception e) {}
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
