package dev.mcreload.moves.client;

import dev.mcreload.moves.Quests;
import dev.mcreload.moves.Scrolls;
import dev.mcreload.moves.Skill;
import dev.mcreload.moves.net.MovesNet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The trainer's page (author, 2026-11-16): his two quests for moves (take one; held, the kills so far and the time left by
 * the clock; done, the scroll; failed, red, put down for another), both changed for an emerald (top right); and the bar's
 * rank, its line of tasks stage by stage.
 */
public final class TrainerScreen extends Screen {
    private final int villager;
    private CompoundTag data;
    private boolean rankTab;
    private long received;

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

    private int left() { return Math.max(10, width / 2 - 170); }
    private int right() { return Math.min(width - 10, width / 2 + 170); }

    @Override
    protected void init() {
        int l = left(), r = right(), mid = (l + r) / 2;
        Button moves = Button.builder(Component.translatable("kinetrix_combat.trainer.tab.moves"), b -> {
            rankTab = false;
            rebuildWidgets();
        }).bounds(mid - 122, 20, 120, 20).build();
        Button ranks = Button.builder(Component.translatable("kinetrix_combat.trainer.tab.rank"), b -> {
            rankTab = true;
            rebuildWidgets();
        }).bounds(mid + 2, 20, 120, 20).build();
        moves.active = rankTab;
        ranks.active = !rankTab;
        addRenderableWidget(moves);
        addRenderableWidget(ranks);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(mid - 50, height - 25, 100, 20).build());
        if (!rankTab) initMoves(l, r);
        else initRank(l, r);
    }

    private void initMoves(int l, int r) {
        // the emerald: both quests changed (top right)
        addRenderableWidget(Button.builder(Component.literal("⟳ 1"), b -> act(Quests.Action.REROLL, 0)).bounds(r - 40, 36, 40, 14)
                .tooltip(Tooltip.create(Component.translatable("kinetrix_combat.trainer.reroll"))).build());
        ListTag offers = data.getList("offers", Tag.TAG_COMPOUND), quests = data.getList("quests", Tag.TAG_COMPOUND);
        int w = (r - l - 8) / 2;
        for (int i = 0; i < offers.size(); i++) {
            CompoundTag o = offers.getCompound(i);
            boolean held = false;
            for (Tag t : quests) held |= ((CompoundTag) t).getString("skill").equals(o.getString("skill"));
            int slot = i;
            Button take = Button.builder(Component.translatable(held ? "kinetrix_combat.trainer.taken" : "kinetrix_combat.trainer.take"),
                    b -> act(Quests.Action.TAKE, slot)).bounds(l + i * (w + 8) + w - 64, 50 + 58, 60, 18).build();
            take.active = !held && quests.size() < Quests.HELD;
            addRenderableWidget(take);
        }
        for (int i = 0; i < quests.size(); i++) {
            CompoundTag q = quests.getCompound(i);
            boolean done = q.getBoolean("done"), failed = q.getBoolean("failed");
            int idx = i, y = 150 + i * 46;
            Component label = Component.translatable(done && !failed ? "kinetrix_combat.trainer.claim"
                    : failed ? "kinetrix_combat.trainer.refresh" : "kinetrix_combat.trainer.drop");
            addRenderableWidget(Button.builder(label, b -> act(done && !failed ? Quests.Action.CLAIM : Quests.Action.DROP, idx))
                    .bounds(r - 90, y + 4, 86, 18).build());
        }
    }

    private void initRank(int l, int r) {
        int rank = data.getInt("rank"), line = data.getInt("rankLine");
        int y = 110;
        if ((line == 0 && rank == 0) || ((line == 0 || line == 2) && rank == 1)) {
            boolean bronze = rank == 0;
            Component price = Component.translatable(bronze ? "kinetrix_combat.trainer.begin_bronze" : "kinetrix_combat.trainer.begin_brass");
            addRenderableWidget(Button.builder(price, b -> act(Quests.Action.RANK_BEGIN, 0)).bounds((l + r) / 2 - 100, y, 200, 20).build());
        } else if (line == 1 || line == 3) {
            String kind = data.getString("kind");
            String key = switch (kind) {
                case "BRING" -> "kinetrix_combat.trainer.hand_in";
                case "KILL" -> "kinetrix_combat.trainer.next";
                default -> "kinetrix_combat.trainer.pay";
            };
            addRenderableWidget(Button.builder(Component.translatable(key), b -> act(Quests.Action.RANK_NEXT, 0))
                    .bounds((l + r) / 2 - 100, height - 50, 200, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 6, 0xFFD8A0);
        int l = left(), r = right();
        if (!rankTab) {
            g.renderItem(new ItemStack(Items.EMERALD), r - 58, 35);
            renderMoves(g, l, r);
        }
        else renderRank(g, l, r);
        super.render(g, mx, my, pt);
    }

    private static Component mob(String id) {
        return EntityType.byString(id).map(EntityType::getDescription).orElse(Component.literal(id));
    }

    private void renderMoves(GuiGraphics g, int l, int r) {
        ListTag offers = data.getList("offers", Tag.TAG_COMPOUND), quests = data.getList("quests", Tag.TAG_COMPOUND);
        g.drawString(font, Component.translatable("kinetrix_combat.trainer.offers"), l, 42, 0xC0C0C0);
        int w = (r - l - 8) / 2;
        for (int i = 0; i < offers.size(); i++) {
            CompoundTag o = offers.getCompound(i);
            Skill k = Skill.byKey(o.getString("skill"));
            int x = l + i * (w + 8), y = 52;
            CombatScreen.frame(g, x, y, w, 78, 0xFF8A6A40);
            if (k == null) continue;
            g.renderItem(SkillPreview.weapon(k.weapon), x + 4, y + 4);
            g.drawString(font, font.plainSubstrByWidth(Scrolls.name(k).getString(), w - 26), x + 23, y + 8, 0xFFFFD860);
            int yy = y + 24;
            for (Tag t : o.getList("goals", Tag.TAG_COMPOUND)) {
                CompoundTag gl = (CompoundTag) t;
                g.drawString(font, Component.translatable("kinetrix_combat.trainer.kill", gl.getInt("need"), mob(gl.getString("mob"))), x + 6, yy, 0xE0E0E0);
                yy += 10;
            }
            g.drawString(font, Component.translatable("kinetrix_combat.trainer.time", clock(o.getInt("minutes") * 60_000L)), x + 6, yy, 0xA0A0A0);
        }
        g.drawString(font, Component.translatable("kinetrix_combat.trainer.yours", quests.size(), Quests.HELD), l, 138, 0xC0C0C0);
        if (quests.isEmpty()) g.drawString(font, Component.translatable("kinetrix_combat.trainer.none").withStyle(ChatFormatting.DARK_GRAY), l, 152, 0x808080);
        for (int i = 0; i < quests.size(); i++) {
            CompoundTag q = quests.getCompound(i);
            Skill k = Skill.byKey(q.getString("skill"));
            boolean done = q.getBoolean("done"), failed = q.getBoolean("failed");
            long left = q.getLong("left") - (System.currentTimeMillis() - received);
            if (!done && left < 0) failed = true;
            int y = 150 + i * 46;
            CombatScreen.frame(g, l, y, r - l, 42, failed ? 0xFFE03A3A : done ? 0xFF4CD15A : 0xFF8A6A40);
            if (k != null) {
                g.renderItem(SkillPreview.weapon(k.weapon), l + 4, y + 4);
                g.drawString(font, Scrolls.name(k), l + 23, y + 4, failed ? 0xFFE03A3A : 0xFFFFD860);
            }
            int x = l + 23;
            for (Tag t : q.getList("goals", Tag.TAG_COMPOUND)) {
                CompoundTag gl = (CompoundTag) t;
                Component c = Component.translatable("kinetrix_combat.trainer.progress", mob(gl.getString("mob")), gl.getInt("have"), gl.getInt("need"));
                g.drawString(font, c, x, y + 16, gl.getInt("have") >= gl.getInt("need") ? 0x80FF80 : 0xE0E0E0);
                x += font.width(c) + 10;
            }
            Component time = failed ? Component.translatable("kinetrix_combat.trainer.failed")
                    : done ? Component.translatable("kinetrix_combat.trainer.ready") : Component.translatable("kinetrix_combat.trainer.left", clock(left));
            g.drawString(font, time, l + 23, y + 28, failed ? 0xFFE03A3A : done ? 0x80FF80 : 0xA0A0A0);
        }
    }

    private static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }

    private void renderRank(GuiGraphics g, int l, int r) {
        int rank = data.getInt("rank"), line = data.getInt("rankLine");
        int[] colours = {0xFFE38C70, 0xFFD39A5A, 0xFFF2CF5E};
        int mid = (l + r) / 2;
        g.drawCenteredString(font, Component.translatable("kinetrix_combat.trainer.rank", Component.translatable("kinetrix_combat.rank.name." + rank)),
                mid, 48, colours[Math.max(0, Math.min(2, rank))]);
        g.drawCenteredString(font, Component.translatable("kinetrix_combat.rank.cost." + rank), mid, 60, 0xA0A0A0);
        if (rank >= 2) {
            g.drawCenteredString(font, Component.translatable("kinetrix_combat.trainer.top"), mid, 90, 0x80FF80);
            return;
        }
        if (line == 2 && rank == 0) {
            g.drawCenteredString(font, Component.translatable("kinetrix_combat.trainer.read_bronze"), mid, 90, 0xFFD860);
            return;
        }
        if (line == 0 || line == 2) {
            g.drawCenteredString(font, Component.translatable(rank == 0 ? "kinetrix_combat.trainer.line_bronze" : "kinetrix_combat.trainer.line_brass"),
                    mid, 90, 0xE0E0E0);
            return;
        }
        int stage = data.getInt("rankStage");
        String kind = data.getString("kind");
        g.drawCenteredString(font, Component.translatable("kinetrix_combat.trainer.stage", stage + 1, Quests.line(line).size(),
                Component.translatable("kinetrix_combat.trainer.kind." + kind.toLowerCase())), mid, 84, 0xFFD860);
        CompoundTag what = data.getCompound("what"), have = data.getCompound("have");
        int y = 100;
        for (String id : what.getAllKeys()) {
            int need = what.getInt(id), got = have.getInt(id);
            Component name = switch (kind) {
                case "KILL" -> mob(id);
                case "BRING" -> new ItemStack(Quests.item(id)).getHoverName();
                default -> Component.translatable("kinetrix_combat.trainer.levels");
            };
            if (kind.equals("BRING")) g.renderItem(new ItemStack(Quests.item(id)), l + 20, y - 4);
            g.drawString(font, name, l + 40, y, 0xE0E0E0);
            g.drawString(font, Math.min(got, need) + " / " + need, r - 70, y, got >= need ? 0x80FF80 : 0xFF9090);
            y += 18;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
