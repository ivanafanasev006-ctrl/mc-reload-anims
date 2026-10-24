package com.example.bcaddon.data;

import com.example.bcaddon.BcAddon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads {@code data/<namespace>/weapon_extras/*.json} with the data packs (world load and /reload).
 * Kept as text so the server can send the same files to clients.
 *
 * <p>Note: Better Combat itself reads {@code weapon_attributes} only once, when the server starts
 * (/reload does not reload them); data of the addon does reload.</p>
 */
public final class WeaponExtrasLoader extends SimplePreparableReloadListener<Map<ResourceLocation, String>> {
    public static final String FOLDER = "weapon_extras";

    @Override
    protected Map<ResourceLocation, String> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, String> files = new HashMap<>();
        manager.listResources(FOLDER, id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (BufferedReader reader = resource.openAsReader()) {
                files.put(id, reader.lines().collect(Collectors.joining("\n")));
            } catch (Exception e) {
                BcAddon.LOG.error("[bcaddon] Cannot read {}: {}", id, e.toString());
            }
        });
        return files;
    }

    @Override
    protected void apply(Map<ResourceLocation, String> files, ResourceManager manager, ProfilerFiller profiler) {
        WeaponExtras.load(files);
    }
}
