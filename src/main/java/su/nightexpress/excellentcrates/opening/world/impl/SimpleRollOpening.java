package su.nightexpress.excellentcrates.opening.world.impl;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.excellentcrates.CratesPlugin;
import su.nightexpress.excellentcrates.api.crate.Reward;
import su.nightexpress.excellentcrates.crate.cost.Cost;
import su.nightexpress.excellentcrates.crate.impl.CrateSource;
import su.nightexpress.excellentcrates.opening.OpeningUtils;
import su.nightexpress.excellentcrates.opening.world.WorldOpening;
import su.nightexpress.excellentcrates.util.pos.WorldPos;
import su.nightexpress.nightcore.util.EntityUtil;
import su.nightexpress.nightcore.util.LocationUtil;
import su.nightexpress.nightcore.util.sound.VanillaSound;

public class SimpleRollOpening extends WorldOpening {

    private final int    spinsRequired;
    private final long   spinInterval;
    private final Reward reward;

    private long spinCount;
    private long finishDelay;

    private Item     rewardDisplay;
    private Location displayLocation;

    public SimpleRollOpening(@NotNull CratesPlugin plugin,
                             @NotNull Player player,
                             @NotNull CrateSource source,
                             @Nullable Cost cost,
                             int spinsRequired,
                             long spinInterval,
                             long finishDelay) {
        super(plugin, player, source, cost);
        this.spinsRequired = spinsRequired;
        this.spinInterval = spinInterval;
        this.finishDelay = finishDelay;

        this.reward = source.getCrate().rollReward(player);
    }

    private void onFirstTick() {
        Block block = this.source.getBlock();

        if (block == null) {
            Location playerLoc = this.player.getEyeLocation().clone();
            Vector direction = playerLoc.getDirection();

            for (int i = 0; i < 3; i++) {
                playerLoc.add(direction);
            }

            this.displayLocation = LocationUtil.setCenter3D(playerLoc);
        }
        else {
            // 在Folia环境中，block.getBoundingBox().getHeight()需要区域线程访问
            // 使用区域调度器在方块所在的位置上执行任务
            Location blockLocation = block.getLocation();
            this.plugin.getNightScheduler().runTaskAtLocation(() -> {
                double offset = Math.max(0, this.crate.getHologramYOffset());
                double height = block.getBoundingBox().getHeight() + offset;
                
                Location center = LocationUtil.setCenter2D(block.getLocation()).add(0, height, 0);
                WorldPos blockPos = WorldPos.from(block);
                this.hideHologram(blockPos);
                
                this.displayLocation = center;
            }, blockLocation);
        }
    }

    @Override
    public long getInterval() {
        return 1L;
    }

    @Override
    public void instaRoll() {
        this.setRefundable(false);
        this.spinCount = this.spinsRequired - 1;
        this.finishDelay = 0L;

        this.spin();

        // Display roll visuals only when instal roll was called in the middle of the opening process to "finish" the visual part.
        if (this.tickCount > 0) {
            this.displayReward();
        }

        this.stop();
    }

    @Override
    protected void onStart() {

    }

    @Override
    protected void onTick() {
        if (this.isSpinsCompleted()) {
            if (this.finishDelay > 0) {
                this.finishDelay--;
            }
            return;
        }

        if (this.tickCount == 0) {
            this.onFirstTick();
            this.setRefundable(false);
        }

        if (this.tickCount % this.spinInterval == 0) {
            this.spin();
            this.displayReward();
        }
    }

    @Override
    protected void onComplete() {

    }

    @Override
    protected void onStop() {
        this.addReward(this.reward);

        if (this.rewardDisplay != null) {
            // 在Folia环境中，实体移除必须在正确的区域线程中执行
            if (this.rewardDisplay.getLocation().getWorld() != null) {
                this.plugin.getNightScheduler().runTaskAtLocation(() -> {
                    try {
                        this.rewardDisplay.remove();
                    } catch (Exception e) {
                        // 捕获移除实体时的异常，防止崩溃
                        this.plugin.error("Failed to remove reward display: " + e.getMessage());
                    }
                }, this.rewardDisplay.getLocation());
            }
            this.rewardDisplay = null;
        }

        Block block = this.source.getBlock();
        if (block != null) {
            WorldPos blockPos = WorldPos.from(block);
            this.showHologram(blockPos);
        }

        super.onStop();
    }

    @Override
    public boolean isCompleted() {
        return this.isSpinsCompleted() && this.finishDelay <= 0;
    }

    private void spin() {
        this.spinCount++;
    }

    private boolean isSpinsCompleted() {
        return this.spinCount >= this.spinsRequired;
    }

    private void displayReward() {
        Reward reward = this.isSpinsCompleted() ? this.reward : this.crate.rollReward(this.player);

        // 检查displayLocation是否已设置且世界有效
        if (this.displayLocation == null || this.displayLocation.getWorld() == null) {
            return; // 如果位置未设置或世界无效，跳过显示
        }

        if (this.rewardDisplay == null) {
            // 使用区域调度器在正确的位置生成物品实体
            this.plugin.getNightScheduler().runTaskAtLocation(() -> {
                try {
                    this.rewardDisplay = player.getWorld().spawn(this.displayLocation, Item.class, item -> item.setVelocity(new Vector()));
                    this.rewardDisplay.setPersistent(false);
                    this.rewardDisplay.setCustomNameVisible(true);
                    this.rewardDisplay.setGravity(false);
                    this.rewardDisplay.setPickupDelay(Integer.MAX_VALUE);
                    this.rewardDisplay.setUnlimitedLifetime(true);
                    this.rewardDisplay.setInvulnerable(true);
                    //this.rewardDisplay.setBillboard(Display.Billboard.CENTER);
                    //this.rewardDisplay.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.35f, 0.35f, 0.35f), new AxisAngle4f()));
                } catch (Exception e) {
                    // 捕获生成实体时的异常，防止崩溃
                    this.plugin.error("Failed to spawn reward display: " + e.getMessage());
                }
            }, this.displayLocation);
        }
        
        if (this.rewardDisplay != null) {
            ItemStack itemStack = reward.getPreviewItem();
            // 在Folia环境中，设置实体物品堆栈必须在正确的区域线程中执行
            if (this.rewardDisplay.getLocation().getWorld() != null) {
                this.plugin.getNightScheduler().runTaskAtLocation(() -> {
                    try {
                        this.rewardDisplay.setItemStack(itemStack);
                        EntityUtil.setCustomName(this.rewardDisplay, reward.getName());
                    } catch (Exception e) {
                        // 捕获设置物品堆栈时的异常，防止崩溃
                        this.plugin.error("Failed to set reward display item: " + e.getMessage());
                    }
                }, this.rewardDisplay.getLocation());
            }
        }

        // 播放音效，使用区域调度器确保在正确的位置
        this.plugin.getNightScheduler().runTaskAtLocation(() -> {
            VanillaSound.of(Sound.UI_BUTTON_CLICK, 0.5f).play(this.displayLocation);
            VanillaSound.of(Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f).play(this.displayLocation);

            if (this.isSpinsCompleted()) {
                VanillaSound.of(Sound.ENTITY_GENERIC_EXPLODE, 0.7f).play(this.displayLocation);
                OpeningUtils.createFirework(this.displayLocation);
            }
        }, this.displayLocation);
    }
}