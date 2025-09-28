package com.proclaws;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.proclaws.RequestHandler.get;
import com.proclaws.Utils.Nayax;
import static com.proclaws.Utils.Nayax.NAYAX_MACHINES_LAST_SALES;
import static com.proclaws.Utils.safeCall;

public class Sales {
    private static final String NAYAX_API_KEY = "YOUR_API_KEY";

    public static ArrayList<MachineSales> getRouteReport(JSONObject route) {
        final ArrayList<MachineSales> sales = new ArrayList<>();
        final JSONArray machines = safeCall(() -> route.getJSONArray("machines"));
        for (int i = 0; i < machines.length(); i++) {
            final int idx = i;
            final JSONObject machine = safeCall(() -> machines.getJSONObject(idx));

            final String machineId = safeCall(() -> machine.getString("id"));

            final HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", NAYAX_API_KEY);
            final CloseableHttpResponse salesData = safeCall(() -> get(
                NAYAX_MACHINES_LAST_SALES.apply(machineId), new HashMap<>(), headers));

            JSONArray salesJson = safeCall(() -> new JSONArray(EntityUtils.toString(salesData.getEntity())));
            
            final MachineSales machineSales = new MachineSales();
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
