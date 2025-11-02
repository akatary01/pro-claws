package com.proclaws;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import org.json.JSONArray;
import org.json.JSONObject;

import com.proclaws.Sales.MachineSales;
import static com.proclaws.Utils.ROUTES_FILE_PATH;
import static com.proclaws.Utils.filterJsonArray;
import static com.proclaws.Utils.safeCall;

import io.javalin.Javalin;
import io.javalin.http.Context;


public class Api {
    private static final int PORT = 7070;
    private static final Scheduler scheduler = new Scheduler();

    public static void main(String[] args) {
        final Javalin api = Javalin.create(/*config*/)
            .get("/scheduleComissionReports", ctx -> scheduleCommisionReports(ctx));
        api.start(PORT);
        System.out.println("API started on port " + PORT);
    }

    private static void scheduleCommisionReports(Context ctx) {
        scheduler.cancelAllTasks(0, MILLISECONDS);

        final JSONArray routes = filterJsonArray(safeCall(() -> new JSONArray(Files.readString(Paths.get(ROUTES_FILE_PATH)))), "sendReports", true);
        for (int i = 0; i < routes.length(); i++) {
            final int idx = i;
            final JSONObject route = safeCall(() -> routes.getJSONObject(idx));
            final UUID id = UUID.fromString(safeCall(() -> route.getString("id")));

            final LocalDateTime start = LocalDateTime.parse(safeCall(() -> route.getString("firstServiceDate")));

            // TODO: Implement the actual report generation logic
            scheduler.scheduleTask(id, () -> {
                final String routeId = safeCall(() -> route.getString("id"));
                System.out.println("generating report for route " + routeId + " at " + LocalDateTime.now());
                
                // fetch sales data for all machines in the route
                final ArrayList<MachineSales> sales = Sales.getRouteReport(route);
                final double totalCash = sales.stream().mapToDouble(ms -> ms.cashSales).sum();
                final double totalCashless = sales.stream().mapToDouble(ms -> ms.cashlessSales).sum();
                final double totalSurplussFee = sales.stream().mapToDouble(ms -> ms.surplussFee).sum();

                final double commission = (totalCashless + totalCash) * safeCall(() -> route.getJSONObject("location").getDouble("commissionRate")); // 2% commission on cashless sales
                
                final double preCommissionRevenue = totalCash + totalCashless + totalSurplussFee;
                final double postCommisionRevenue = preCommissionRevenue - commission;
                
                System.out.println(String.format(
                """
                Route %s Report:
                    Total Cash Sales: $%.2f
                    Total Cashless Sales: $%.2f
                    Total Surpluss Fees: $%.2f
                    Commission: $%.2f
                    Pre-Commission Revenue: $%.2f
                    Post-Commission Revenue: $%.2f""",
                    routeId, totalCash, totalCashless, totalSurplussFee, commission, preCommissionRevenue, postCommisionRevenue));
                System.out.println("sales " + sales);

                // TODO: generate report 
                final String subject = "Sales Report for Route " + routeId;
                final String salesReport = "Yusuf is small.";
                
                // send report via email
                safeCall(() -> {
                    final String email = safeCall(() -> route.getString("email")); 
                    if (email == null || email.isEmpty()) {
                        System.out.println(String.format("skipping route %s: no email provided", routeId));
                        return null;
                    }
                    Email.send(email, subject, salesReport, new byte[0]);
                    return null;
                });
            }, start, 7, DAYS);

            // Cancel the task after 365 * 2 days = 2 years
            scheduler.cancelTask(id, 365*2, DAYS);
        }
    }
}