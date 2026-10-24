package com.example.bcaddon.data;

import com.example.bcaddon.AddonConfig;
import com.example.bcaddon.BcAddon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Extra per-weapon data of the addon: {@code data/<namespace>/weapon_extras/*.json}.
 *
 * <p>Better Combat reads {@code weapon_attributes} with GSON into fixed classes, so keys it does not
 * know are silently dropped there. An addon keeps its own data in its own folder and matches it to
 * items by id ({@code "items"}) or by the Better Combat weapon category ({@code "categories"}), which
 * covers every mod whose weapons inherit from {@code bettercombat:sword}, {@code bettercombat:axe}...</p>
 *
 * <pre>{
 *   "categories": ["sword", "katana"],      // and/or "items": ["minecraft:iron_sword"]
 *   "guard_ticks": 6,                       // parry window of the guard
 *   "heavy_attack": {                       // replaces these fields of the current combo step
 *     "animation": "bettercombat:two_handed_slash_vertical_right",
 *     "damage_multiplier": 1.6, "upswing": 0.6, "angle": 0, "hitbox": "VERTICAL_PLANE"
 *   }
 * }</pre>
 *
 * <p>The server loads the files ({@link WeaponExtrasLoader}) and sends them to every client
 * ({@code net.SyncExtras}), because the heavy attack is chosen on both sides.</p>
 */
public record WeaponExtras(@Nullable Integer guardTicks, @Nullable HeavyAttack heavy) {

    private static volatile Map<ResourceLocation, String> files = Map.of();
    private static volatile Map<ResourceLocation, WeaponExtras> byItem = Map.of();
    private static volatile Map<String, WeaponExtras> byCategory = Map.of();

    /** Data for this weapon: by item id first, then by its Better Combat category. */
    @Nullable
    public static WeaponExtras of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        WeaponExtras extras = byItem.get(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        if (extras != null) return extras;
        WeaponAttributes attributes = WeaponRegistry.getAttributes(stack);
        String category = attributes != null ? attributes.category() : null;
        return category != null ? byCategory.get(category) : null;
    }

    public int guardWindow() {
        return guardTicks != null ? guardTicks : AddonConfig.GUARD_TICKS.get();
    }

    /** The files as loaded (file id to JSON text), for sending to clients. */
    public static Map<ResourceLocation, String> files() {
        return files;
    }

    /** Replaces all data with these files: a server (re)load, or the copy a client got from the server. */
    public static void load(Map<ResourceLocation, String> raw) {
        Map<ResourceLocation, WeaponExtras> items = new HashMap<>();
        Map<String, WeaponExtras> categories = new HashMap<>();
        for (Map.Entry<ResourceLocation, String> file : raw.entrySet()) {
            try {
                JsonObject json = JsonParser.parseString(file.getValue()).getAsJsonObject();
                WeaponExtras extras = new WeaponExtras(
                        json.has("guard_ticks") ? json.get("guard_ticks").getAsInt() : null,
                        json.has("heavy_attack") ? HeavyAttack.parse(json.getAsJsonObject("heavy_attack")) : null);
                for (JsonElement item : array(json, "items")) items.put(new ResourceLocation(item.getAsString()), extras);
                for (JsonElement category : array(json, "categories")) categories.put(category.getAsString(), extras);
            } catch (Exception e) {
                BcAddon.LOG.error("[bcaddon] Bad weapon_extras file {}: {}", file.getKey(), e.toString());
            }
        }
        files = Map.copyOf(raw);
        byItem = Map.copyOf(items);
        byCategory = Map.copyOf(categories);
        BcAddon.LOG.info("[bcaddon] weapon_extras: {} files, {} items, {} categories", raw.size(), items.size(), categories.size());
    }

    private static JsonArray array(JsonObject json, String key) {
        return json.has(key) ? json.getAsJsonArray(key) : new JsonArray();
    }

    /** Fields of an attack that replace those of the current combo step; missing ones are kept. */
    public record HeavyAttack(@Nullable String animation, @Nullable Double damageMultiplier, @Nullable Double upswing,
                              @Nullable Double angle, @Nullable WeaponAttributes.HitBoxShape hitbox) {

        static HeavyAttack parse(JsonObject json) {
            return new HeavyAttack(
                    json.has("animation") ? json.get("animation").getAsString() : null,
                    json.has("damage_multiplier") ? json.get("damage_multiplier").getAsDouble() : null,
                    json.has("upswing") ? json.get("upswing").getAsDouble() : null,
                    json.has("angle") ? json.get("angle").getAsDouble() : null,
                    json.has("hitbox") ? WeaponAttributes.HitBoxShape.valueOf(json.get("hitbox").getAsString()) : null);
        }

        public WeaponAttributes.Attack applyTo(WeaponAttributes.Attack base) {
            return new WeaponAttributes.Attack(
                    base.conditions(),
                    hitbox != null ? hitbox : base.hitbox(),
                    damageMultiplier != null ? damageMultiplier : base.damageMultiplier(),
                    angle != null ? angle : base.angle(),
                    upswing != null ? upswing : base.upswing(),
                    animation != null ? animation : base.animation(),
                    base.swingSound(),
                    base.impactSound());
        }
    }
}
