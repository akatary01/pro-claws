package com.vendistri.operations.features.navigation;

import com.mapbox.navigation.core.MapboxNavigation;
import com.mapbox.navigation.core.reroute.RerouteController;

/** Bridges a public Java getter that Kotlin resolves to Mapbox's private backing property. */
public final class MapboxRerouteObserverBridge {
    private MapboxRerouteObserverBridge() {}

    public static void register(
        MapboxNavigation navigation,
        RerouteController.RerouteStateObserver observer
    ) {
        navigation.getRerouteController().registerRerouteStateObserver(observer);
    }

    public static void unregister(
        MapboxNavigation navigation,
        RerouteController.RerouteStateObserver observer
    ) {
        navigation.getRerouteController().unregisterRerouteStateObserver(observer);
    }
}
