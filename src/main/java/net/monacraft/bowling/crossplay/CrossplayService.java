package net.monacraft.bowling.crossplay;

import org.bukkit.plugin.PluginManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Optional Geyser/Floodgate API adapter without a hard runtime dependency. */
public final class CrossplayService {
    private final Logger logger;
    private final Provider floodgate;
    private final Provider geyser;

    public CrossplayService(PluginManager plugins, Logger logger) {
        this.logger = logger;
        this.floodgate = findEnabled(plugins, "floodgate", "Floodgate")
                ? createFloodgateProvider() : null;
        this.geyser = findEnabled(plugins, "Geyser-Spigot", "Geyser")
                ? createGeyserProvider() : null;
    }

    public ClientProfile profile(UUID playerId) {
        ClientProfile value = query(floodgate, playerId);
        if (value != null) return value;
        value = query(geyser, playerId);
        return value == null ? ClientProfile.javaClient() : value;
    }

    public String providerStatus() {
        return "Floodgate=" + (floodgate != null) + ",Geyser=" + (geyser != null);
    }

    private ClientProfile query(Provider provider, UUID playerId) {
        if (provider == null) return null;
        try {
            return provider.profile(playerId);
        } catch (ReflectiveOperationException | LinkageError exception) {
            logger.log(Level.WARNING, "Cross-play API query failed for " + provider.name(), exception);
            return null;
        }
    }

    private Provider createFloodgateProvider() {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method isBedrock = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            Method getPlayer = apiClass.getMethod("getPlayer", UUID.class);
            return new ReflectiveProvider("Floodgate", playerId -> {
                if (!(boolean) isBedrock.invoke(api, playerId)) return null;
                Object player = getPlayer.invoke(api, playerId);
                return bedrockProfile(player, "getInputMode", "getDeviceOs", "getVersion", "Floodgate");
            });
        } catch (ReflectiveOperationException | LinkageError exception) {
            logger.log(Level.WARNING, "Floodgate is enabled but its API could not be initialized", exception);
            return null;
        }
    }

    private Provider createGeyserProvider() {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object api = apiClass.getMethod("api").invoke(null);
            if (api == null) return null;
            Method isBedrock = apiClass.getMethod("isBedrockPlayer", UUID.class);
            Method connection = apiClass.getMethod("connectionByUuid", UUID.class);
            return new ReflectiveProvider("Geyser", playerId -> {
                if (!(boolean) isBedrock.invoke(api, playerId)) return null;
                Object value = connection.invoke(api, playerId);
                return bedrockProfile(value, "inputMode", "platform", "version", "Geyser");
            });
        } catch (ReflectiveOperationException | LinkageError exception) {
            logger.log(Level.WARNING, "Geyser is enabled but its API could not be initialized", exception);
            return null;
        }
    }

    private static ClientProfile bedrockProfile(Object player, String inputMethod,
                                                 String platformMethod, String versionMethod,
                                                 String provider) throws ReflectiveOperationException {
        if (player == null) {
            return new ClientProfile(ClientProfile.Edition.BEDROCK, ClientProfile.InputKind.UNKNOWN,
                    "UNKNOWN", "unknown", provider);
        }
        String input = invokeText(player, inputMethod);
        return new ClientProfile(ClientProfile.Edition.BEDROCK,
                ClientProfile.InputKind.fromApiName(input),
                invokeText(player, platformMethod), invokeText(player, versionMethod), provider);
    }

    private static String invokeText(Object target, String method) throws ReflectiveOperationException {
        try {
            Object value = target.getClass().getMethod(method).invoke(target);
            return value == null ? "UNKNOWN" : String.valueOf(value);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflective) throw reflective;
            throw exception;
        }
    }

    private static boolean findEnabled(PluginManager plugins, String... names) {
        for (String name : names) if (plugins.isPluginEnabled(name)) return true;
        return false;
    }

    private interface Provider {
        String name();
        ClientProfile profile(UUID playerId) throws ReflectiveOperationException;
    }

    @FunctionalInterface
    private interface ProfileLookup {
        ClientProfile profile(UUID playerId) throws ReflectiveOperationException;
    }

    private record ReflectiveProvider(String name, ProfileLookup lookup) implements Provider {
        @Override public ClientProfile profile(UUID playerId) throws ReflectiveOperationException {
            return lookup.profile(playerId);
        }
    }
}
