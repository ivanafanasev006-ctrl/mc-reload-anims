package dev.mcreload.moves.client;

import dev.mcreload.moves.Quests;
import dev.mcreload.moves.Scrolls;
import dev.mcreload.moves.Skill;
import dev.mcreload.moves.net.MovesNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.List;

/**
 * The trainer's page (author, 2026-11-16): his two quests for moves (take one; held, the kills so far and the time left by
 * the clock; done, the scroll; failed, red, put down for another), both changed for an emerald (top right); and the bar's
 * rank, its line of tasks stage by stage.
 *
 * <p>Drawn as vanilla's villager trading is (author, 2026-11-16: "the new villager's menu more vanilla"): a grey panel of
 * the size of the trading one, its two tabs on top as the creative inventory's, on the left the list of what there is as
 * buttons (as the trades are), on the right the one picked: its mobs as their spawn eggs in slots, the time, the scroll it
 * gives, the button for it. Dark text without a shadow, as on vanilla's panels.</p>
 */
public final class TrainerScreen extends Screen {
    private static final ResourceLocation PANEL = new ResourceLocation("textures/gui/demo_background.png");
    private static final ResourceLocation TABS = new ResourceLocation("textures/gui/container/creative_inventory/tabs.png");
    private static final int W = 276, H = 166, LIST_W = 100, DARK = 0x404040, GREY = 0x707070, RED = 0xB02E26, GREEN = 0x2F7A1E;

    private final int villager;
    private CompoundTag data;
    private boolean rankTab;
    private long received;
    /** The entry picked on the left: his offers 0 and 1, the player's quests 2 and 3. */
    private int picked = -1;

    TrainerScreen(int villager, CompoundTag data) {
        super(Component.translatable("kinetrix_combat.trainer.title"));
        this.villager = villager;
        this.data = data;
        this.received = System.currentTimeMillis();
    }

    /** From the server: open the page, or refresh it if it is open (without his offers: keep the old ones). */
    public static void received(int villager, boolean open, CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        if (open) mc.setScreen(new TrainerScreen(villager, data));
        else if (mc.screen instanceof TrainerScreen t) {
            if (!data.contains("offers") && t.data.contains("offers")) data.put("offers", t.data.get("offers"));
            t.data = data;
            t.received = System.currentTimeMillis();
            t.rebuildWidgets();
        }
    }

    /** The rank's tab (the self-test's). */
    void showRank() {
        rankTab = true;
        rebuildWidgets();
    }

    private void act(Quests.Action a, int arg) {
        MovesNet.CHANNEL.sendToServer(new MovesNet.TrainerAct(villager, a.ordinal(), arg));
    }

    private int x0() { return (width - W) / 2; }

    /** The panel's top: below its tabs, the whole in the middle. */
    private int y0() { return Math.max(28, (height - H) / 2 + 12); }

    private ListTag offers() { return data.getList("offers", Tag.TAG_COMPOUND); }

    private ListTag quests() { return data.getList("quests", Tag.TAG_COMPOUND); }

    private CompoundTag pickedTag() {
        if (picked < 0) return null;
        ListTag l = picked < 2 ? offers() : quests();
        int i = picked < 2 ? picked : picked - 2;
        return i < l.size() ? l.getCompound(i) : null;
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void init() {
        int x0 = x0(), y0 = y0();
        if (!rankTab) initMoves(x0, y0);
        else initRank(x0, y0);
    }

    private void initMoves(int x0, int y0) {
        ListTag offers = offers(), quests = quests();
        // what is picked: kept while it is there; else the first quest held, else the first offer
        if (pickedTag() == null) picked = !quests.isEmpty() ? 2 : !offers.isEmpty() ? 0 : -1;
        // the emerald: both offers changed (top right)
        addRenderableWidget(Button.builder(Component.literal("⟳"), b -> act(Quests.Action.REROLL, 0)).bounds(x0 + W - 25, y0 + 4, 18, 14)
                .tooltip(Tooltip.create(Component.translatable("kinetrix_combat.trainer.reroll"))).build());
        for (int i = 0; i < offers.size(); i++) {
            CompoundTag o = offers.getCompound(i);
            addRenderableWidget(new Entry(x0 + 6, y0 + 28 + i * 21, i, Skill.byKey(o.getString("skill")), 0xFFFFFF));
        }
        for (int i = 0; i < quests.size(); i++) {
            CompoundTag q = quests.getCompound(i);
            boolean failed = failed(q), done = q.getBoolean("done") && !failed;
            addRenderableWidget(new Entry(x0 + 6, y0 + 92 + i * 21, 2 + i, Skill.byKey(q.getString("skill")),
                    failed ? 0xFF6060 : done ? 0x80FF80 : 0xFFFFFF));
        }
        CompoundTag t = pickedTag();
        if (t == null) return;
        Button action;
        if (picked < 2) {
            boolean held = false;
            for (Tag q : quests) held |= ((CompoundTag) q).getString("skill").equals(t.getString("skill"));
            int slot = picked;
            action = Button.builder(Component.translatable(held ? "kinetrix_combat.trainer.taken" : "kinetrix_combat.trainer.take"),
                    b -> act(Quests.Action.TAKE, slot)).bounds(x0 + W - 94, y0 + H - 27, 86, 20).build();
            action.active = !held && quests.size() < Quests.HELD;
        } else {
            boolean failed = failed(t), done = t.getBoolean("done") && !failed;
            int idx = picked - 2;
            action = Button.builder(Component.translatable(done ? "kinetrix_combat.trainer.claim"
                            : failed ? "kinetrix_combat.trainer.refresh" : "kinetrix_combat.trainer.drop"),
                    b -> act(done ? Quests.Action.CLAIM : Quests.Action.DROP, idx)).bounds(x0 + W - 94, y0 + H - 27, 86, 20).build();
        }
        addRenderableWidget(action);
    }

    private void initRank(int x0, int y0) {
        int rank = data.getInt("rank"), line = data.getInt("rankLine");
        int bx = x0 + W - 134, by = y0 + H - 27;
        if ((line == 0 && rank == 0) || ((line == 0 || line == 2) && rank == 1)) {
            boolean bronze = rank == 0;
            addRenderableWidget(Button.builder(Component.translatable("kinetrix_combat.trainer.begin"), b -> act(Quests.Action.RANK_BEGIN, 0))
                    .bounds(bx, by, 126, 20).tooltip(Tooltip.create(Component.translatable(bronze
                            ? "kinetrix_combat.trainer.begin_bronze" : "kinetrix_combat.trainer.begin_brass"))).build());
        } else if (line == 1 || line == 3) {
            String key = switch (data.getString("kind")) {
                case "BRING" -> "kinetrix_combat.trainer.hand_in";
                case "KILL" -> "kinetrix_combat.trainer.next";
                default -> "kinetrix_combat.trainer.pay";
            };
            addRenderableWidget(Button.builder(Component.translatable(key), b -> act(Quests.Action.RANK_NEXT, 0)).bounds(bx, by, 126, 20).build());
        }
    }

    /** An entry of the list on the left: a button as vanilla's trades are, the weapon and the move's name on it. */
    private final class Entry extends AbstractButton {
        private final int index, colour;
        private final Skill skill;

        Entry(int x, int y, int index, Skill skill, int colour) {
            super(x, y, LIST_W - 12, 20, skill == null ? Component.empty() : Scrolls.name(skill));
            this.index = index;
            this.skill = skill;
            this.colour = colour;
        }

        @Override
        public void onPress() {
            picked = index;
            rebuildWidgets();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            Component name = getMessage();
            setMessage(Component.empty());
            super.renderWidget(g, mx, my, pt);
            setMessage(name);
            if (skill != null) g.renderItem(SkillPreview.icon(skill.weapon), getX() + 2, getY() + 2);
            g.drawString(font, font.plainSubstrByWidth(name.getString(), width - 22), getX() + 20, getY() + 6, colour);
            if (index == picked) {
                // the one picked, framed in white
                g.fill(getX(), getY(), getX() + width, getY() + 1, 0xFFFFFFFF);
                g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, 0xFFFFFFFF);
                g.fill(getX(), getY(), getX() + 1, getY() + height, 0xFFFFFFFF);
                g.fill(getX() + width - 1, getY(), getX() + width, getY() + height, 0xFFFFFFFF);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x0 = x0(), y0 = y0();
        tab(g, x0, y0, 0, !rankTab, Scrolls.of(Skill.SWORD_SPIN));
        tab(g, x0, y0, 1, rankTab, new ItemStack(Scrolls.BRONZE.get()));
        g.blitNineSliced(PANEL, x0, y0, W, H, 4, 4, 248, 166, 0, 0);
        // the tab in front, joined to the panel
        if (!rankTab) tab(g, x0, y0, 0, true, Scrolls.of(Skill.SWORD_SPIN));
        else tab(g, x0, y0, 1, true, new ItemStack(Scrolls.BRONZE.get()));
        g.drawString(font, Component.translatable(rankTab ? "kinetrix_combat.trainer.tab.rank" : "kinetrix_combat.trainer.tab.moves"),
                x0 + 8, y0 + 6, DARK, false);
        if (!rankTab) renderMoves(g, x0, y0);
        else renderRank(g, x0, y0);
        super.render(g, mx, my, pt);
        // the tabs' names on hovering them
        for (int i = 0; i < 2; i++) {
            int tx = x0 + i * 29, ty = y0 - 28;
            if (mx >= tx && mx < tx + 26 && my >= ty && my < y0) {
                g.renderTooltip(font, Component.translatable(i == 0 ? "kinetrix_combat.trainer.tab.moves" : "kinetrix_combat.trainer.tab.rank"), mx, my);
            }
        }
    }

    /** A tab on top of the panel, as the creative inventory's ({@code front}: the open one, joined to the panel). */
    private void tab(GuiGraphics g, int x0, int y0, int i, boolean front, ItemStack icon) {
        int x = x0 + i * 29, y = y0 - 28;
        g.blit(TABS, x, y, i == 0 ? 0 : 26, front ? 32 : 0, 26, 32);
        g.renderItem(icon, x + 5, y + 9);
    }

    /** A slot as vanilla's (18 x 18: shaded in, the light from the top left), with an item in it. */
    private void slot(GuiGraphics g, int x, int y, ItemStack item, String count) {
        g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        g.fill(x, y, x + 17, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + 17, 0xFF373737);
        g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        if (item.isEmpty()) return;
        g.renderItem(item, x + 1, y + 1);
        if (count != null) g.renderItemDecorations(font, item, x + 1, y + 1, count);
    }

    private static Component mob(String id) {
        return EntityType.byString(id).map(EntityType::getDescription).orElse(Component.literal(id));
    }

    /** A mob shown as its spawn egg (a skull for one without). */
    private static ItemStack egg(String id) {
        SpawnEggItem e = EntityType.byString(id).map(SpawnEggItem::byId).orElse(null);
        return new ItemStack(e != null ? e : Items.SKELETON_SKULL);
    }

    private boolean failed(CompoundTag q) {
        long left = q.getLong("left") - (System.currentTimeMillis() - received);
        return q.getBoolean("failed") || (!q.getBoolean("done") && left < 0);
    }

    private void renderMoves(GuiGraphics g, int x0, int y0) {
        ListTag offers = offers(), quests = quests();
        // the emerald the change costs, left of its button
        g.renderItem(new ItemStack(Items.EMERALD), x0 + W - 44, y0 + 3);
        g.drawString(font, Component.translatable("kinetrix_combat.trainer.list.offers"), x0 + 7, y0 + 18, DARK, false);
        g.drawString(font, Component.translatable("kinetrix_combat.trainer.list.yours", quests.size(), Quests.HELD), x0 + 7, y0 + 82, DARK, false);
        if (quests.isEmpty()) g.drawString(font, Component.translatable("kinetrix_combat.trainer.list.none"), x0 + 7, y0 + 96, GREY, false);
        // the line between the list and the one picked
        g.fill(x0 + LIST_W, y0 + 18, x0 + LIST_W + 1, y0 + H - 8, 0xFF8B8B8B);
        g.fill(x0 + LIST_W + 1, y0 + 18, x0 + LIST_W + 2, y0 + H - 8, 0xFFFFFFFF);
        CompoundTag t = pickedTag();
        int x = x0 + LIST_W + 8, right = x0 + W - 8;
        if (t == null) {
            drawWrapped(g, Component.translatable("kinetrix_combat.trainer.list.pick"), x, y0 + 40, right - x, GREY);
            return;
        }
        boolean quest = picked >= 2, failed = quest && failed(t), done = quest && t.getBoolean("done") && !failed;
        Skill k = Skill.byKey(t.getString("skill"));
        // the move: its weapon, its name, what it is
        if (k != null) {
            slot(g, x, y0 + 18, SkillPreview.icon(k.weapon), null);
            g.drawString(font, font.plainSubstrByWidth(Scrolls.name(k).getString(), right - x - 22), x + 22, y0 + 19, DARK, false);
            g.drawString(font, Component.translatable(k.perk ? "kinetrix_combat.trainer.list.perk" : "kinetrix_combat.weapon." + k.weapon.name().toLowerCase()),
                    x + 22, y0 + 28, GREY, false);
        }
        // the mobs to kill, each as its egg
        int y = y0 + 42;
        g.drawString(font, Component.translatable("kinetrix_combat.trainer.list.kill"), x, y, DARK, false);
        y += 10;
        for (Tag tg : t.getList("goals", Tag.TAG_COMPOUND)) {
            CompoundTag gl = (CompoundTag) tg;
            int need = gl.getInt("need"), have = gl.getInt("have");
            slot(g, x, y, egg(gl.getString("mob")), null);
            g.drawString(font, font.plainSubstrByWidth(mob(gl.getString("mob")).getString(), right - x - 56), x + 22, y + 5, DARK, false);
            String n = quest ? Math.min(have, need) + "/" + need : "×" + need;
            g.drawString(font, n, right - font.width(n), y + 5, quest && have >= need ? GREEN : DARK, false);
            y += 20;
        }
        // the time, and how it stands
        Component time;
        int colour = GREY;
        if (!quest) time = Component.translatable("kinetrix_combat.trainer.time", clock(t.getInt("minutes") * 60_000L));
        else if (failed) {
            time = Component.translatable("kinetrix_combat.trainer.failed");
            colour = RED;
        } else if (done) {
            time = Component.translatable("kinetrix_combat.trainer.ready");
            colour = GREEN;
        } else time = Component.translatable("kinetrix_combat.trainer.left", clock(t.getLong("left") - (System.currentTimeMillis() - received)));
        drawWrapped(g, time, x, y + 2, right - x, colour);
        // the reward: the move's scroll
        if (k != null) {
            Component reward = Component.translatable("kinetrix_combat.trainer.list.reward");
            g.drawString(font, reward, x, y0 + H - 21, DARK, false);
            slot(g, x + font.width(reward) + 4, y0 + H - 26, Scrolls.of(k), null);
        }
    }

    private void drawWrapped(GuiGraphics g, Component c, int x, int y, int w, int colour) {
        List<FormattedCharSequence> lines = font.split(c, w);
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), x, y + i * 9, colour, false);
    }

    private static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }

    private void renderRank(GuiGraphics g, int x0, int y0) {
        int rank = data.getInt("rank"), line = data.getInt("rankLine");
        // the three ranks down the left, each with its scroll; the player's own marked
        g.drawString(font, Component.translatable("kinetrix_combat.trainer.list.ranks"), x0 + 7, y0 + 18, DARK, false);
        ItemStack[] scrolls = {new ItemStack(Scrolls.COPPER.get()), new ItemStack(Scrolls.BRONZE.get()), new ItemStack(Scrolls.BRASS.get())};
        int[] colours = {0xA8452C, 0x7A4A1A, 0x8A6A00};
        for (int i = 0; i < 3; i++) {
            int y = y0 + 30 + i * 30;
            if (i == rank) g.fill(x0 + 5, y - 2, x0 + LIST_W - 4, y + 20, 0xFFA8A8A8);
            slot(g, x0 + 7, y, scrolls[i], null);
            g.drawString(font, Component.translatable("kinetrix_combat.rank.title." + i), x0 + 28, y + 1, colours[i], false);
            g.drawString(font, Component.translatable("kinetrix_combat.rank.short." + i), x0 + 28, y + 10, i == rank ? DARK : GREY, false);
        }
        g.fill(x0 + LIST_W, y0 + 18, x0 + LIST_W + 1, y0 + H - 8, 0xFF8B8B8B);
        g.fill(x0 + LIST_W + 1, y0 + 18, x0 + LIST_W + 2, y0 + H - 8, 0xFFFFFFFF);
        int x = x0 + LIST_W + 8, right = x0 + W - 8, w = right - x;
        if (rank >= 2) {
            drawWrapped(g, Component.translatable("kinetrix_combat.trainer.top"), x, y0 + 30, w, GREEN);
            return;
        }
        if (line == 2 && rank == 0) {
            drawWrapped(g, Component.translatable("kinetrix_combat.trainer.read_bronze"), x, y0 + 30, w, DARK);
            return;
        }
        if (line == 0 || line == 2) {
            boolean bronze = rank == 0;
            drawWrapped(g, Component.translatable(bronze ? "kinetrix_combat.trainer.line_bronze" : "kinetrix_combat.trainer.line_brass"), x, y0 + 20, w, DARK);
            g.drawString(font, Component.translatable("kinetrix_combat.trainer.list.price"), x, y0 + 48, DARK, false);
            ItemStack price = bronze ? new ItemStack(Items.GOLD_INGOT, 32) : new ItemStack(Items.DIAMOND, 16);
            slot(g, x, y0 + 58, price, String.valueOf(price.getCount()));
            g.drawString(font, price.getHoverName(), x + 22, y0 + 63, DARK, false);
            g.drawString(font, Component.translatable("kinetrix_combat.trainer.list.gives"), x, y0 + 84, DARK, false);
            slot(g, x, y0 + 94, scrolls[bronze ? 1 : 2], null);
            drawWrapped(g, scrolls[bronze ? 1 : 2].getHoverName(), x + 22, y0 + 95, w - 22, DARK);
            return;
        }
        int stage = data.getInt("rankStage");
        String kind = data.getString("kind");
        g.drawString(font, Component.translatable("kinetrix_combat.trainer.stage", stage + 1, Quests.line(line).size(),
                Component.translatable("kinetrix_combat.trainer.kind." + kind.toLowerCase())), x, y0 + 20, DARK, false);
        CompoundTag what = data.getCompound("what"), have = data.getCompound("have");
        int y = y0 + 32;
        for (String id : what.getAllKeys()) {
            if (y > y0 + H - 50) break;
            int need = what.getInt(id), got = have.getInt(id);
            ItemStack icon = switch (kind) {
                case "KILL" -> egg(id);
                case "BRING" -> new ItemStack(Quests.item(id));
                default -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            };
            Component name = switch (kind) {
                case "KILL" -> mob(id);
                case "BRING" -> icon.getHoverName();
                default -> Component.translatable("kinetrix_combat.trainer.levels");
            };
            slot(g, x, y, icon, null);
            g.drawString(font, font.plainSubstrByWidth(name.getString(), w - 64), x + 22, y + 5, DARK, false);
            String n = Math.min(got, need) + "/" + need;
            g.drawString(font, n, right - font.width(n), y + 5, got >= need ? GREEN : RED, false);
            y += 20;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // the tabs
        int x0 = x0(), y0 = y0();
        for (int i = 0; i < 2; i++) {
            int tx = x0 + i * 29, ty = y0 - 28;
            if (button == 0 && mx >= tx && mx < tx + 26 && my >= ty && my < y0 && rankTab != (i == 1)) {
                rankTab = i == 1;
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
