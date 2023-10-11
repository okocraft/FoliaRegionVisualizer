package net.okocraft.foliaregionvisualizer.util;

import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class CraftBukkitUtils {

    private static final Class<?> CRAFT_WORLD_CLASS;

    private static final MethodHandle GET_HANDLE_METHOD;

    static {
        try {
            CRAFT_WORLD_CLASS = getCraftWorldClass();
            GET_HANDLE_METHOD = getHandleMethod();
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static @Nullable ServerLevel getServerLevel(@NotNull World world) {
        if (!CRAFT_WORLD_CLASS.isInstance(world)) {
            return null;
        }

        try {
            return (ServerLevel) GET_HANDLE_METHOD.invoke(world);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static @NotNull String getReasonForNotObtaining(@NotNull World world) {
        if (!CRAFT_WORLD_CLASS.isInstance(world)) {
            return "Not craft world: " + world.getClass().getName();
        }

        try {
            var ignored = (ServerLevel) GET_HANDLE_METHOD.invoke(world);
            return "Nothing is wrong... Why?";
        } catch (Throwable e) {
            if (e instanceof ClassCastException) {
                return "Cannot cast level to ServerLevel";
            } else {
                return "Exception occurred: " + e.getClass().getName() + "(message: " + e.getMessage() + ")";
            }
        }
    }

    private static @NotNull Class<?> getCraftWorldClass() throws Throwable {
        var cbPackage = Bukkit.getServer().getClass().getPackage().getName();
        return Class.forName(cbPackage + ".CraftWorld");
    }

    private static @NotNull MethodHandle getHandleMethod() throws Throwable {
        return MethodHandles.lookup().findVirtual(CRAFT_WORLD_CLASS, "getHandle", MethodType.methodType(ServerLevel.class));
    }

    private CraftBukkitUtils() {
        throw new UnsupportedOperationException();
    }

}
