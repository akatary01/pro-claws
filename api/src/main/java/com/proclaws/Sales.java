package com.proclaws;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.proclaws.RequestHandler.get;
import static com.proclaws.Utils.API_KEYS_FILE_PATH;
import com.proclaws.Utils.Nayax;
import static com.proclaws.Utils.Nayax.NAYAX_MACHINES_LAST_SALES;
import static com.proclaws.Utils.safeCall;

public class Sales {
    private static final String NAYAX_API_KEY = safeCall(() -> new JSONObject(Files.readString(Paths.get(API_KEYS_FILE_PATH))).getString("nayax"));

    public static ArrayList<MachineSales> getRouteReport(JSONObject route) {
        final ArrayList<MachineSales> sales = new ArrayList<>();
        final JSONArray machines = safeCall(() -> route.getJSONArray("machines"));
        for (int i = 0; i < machines.length(); i++) {
            final int idx = i;
            final JSONObject machine = safeCall(() -> machines.getJSONObject(idx));
            final String machineId = safeCall(() -> machine.getString("id"));

            System.out.println("fetching sales data for machine " + machineId + "...");
            final HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + NAYAX_API_KEY);
           
            try {
                final CloseableHttpResponse salesData = get(
                    NAYAX_MACHINES_LAST_SALES.apply(machineId), new HashMap<>(), headers);
                
                switch (salesData.getStatusLine().getStatusCode()) {
                    case 200: 
                        final MachineSales machineSales = new MachineSales();
                        final JSONArray salesJson = new JSONArray(EntityUtils.toString(salesData.getEntity()));

                        machineSales.id = machineId;
                        for (int j = 0; j < salesJson.length(); j++) {
                            final int jdx = j;
                            final JSONObject sale = safeCall(() -> salesJson.getJSONObject(jdx));
                            final String paymentMethod = safeCall(() -> sale.getString("PaymentMethod"));
            
                            if (paymentMethod.equals(Nayax.CREDIT_CARD)) {
                                machineSales.cashSales += safeCall(() -> sale.getInt("SettlementValue"));
                            } else {
                                // assumption: there is an additional fee of $0.15 per transaction for cashless payments
                                machineSales.cashlessSales += safeCall(() -> sale.getInt("SettlementValue")) - 0.15;
                                machineSales.surplussFee += 0.15;
                            }
                        }
                        sales.add(machineSales);
                    case 403:
                        System.out.println(salesData);
                        break;
                    default:
                        System.out.println("received status code " + salesData.getStatusLine().getStatusCode() + " from Nayax API");
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
        return sales;
    }

    public static class MachineSales {
        public String id;
        public double cashSales;
        public double surplussFee;
        public double cashlessSales;
    }
}
