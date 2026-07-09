package com.deepu.create_crude;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.ItemInteractionResult;

public class GasBlock extends Block {
    public static final int MAX_RADIUS = 4;
    public static final int MAX_LIFETIME = 100;   // ~5 seconds (20 ticks per second)
    public static final int EXPAND_INTERVAL = 20; // 1 second

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 15);
    public static final IntegerProperty RADIUS = IntegerProperty.create("radius", 0, MAX_RADIUS);
    public static final IntegerProperty LIFETIME = IntegerProperty.create("lifetime", 0, MAX_LIFETIME);
    public static final BooleanProperty SOURCE = BooleanProperty.create("source");

    public GasBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition
                .any()
                .setValue(ACTIVE, false)
                .setValue(VARIANT, 0)
                .setValue(RADIUS, 0)
                .setValue(LIFETIME, MAX_LIFETIME)
                .setValue(SOURCE, false));
    }

    // No collision
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world,
                                        BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    // No interactions
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        return InteractionResult.FAIL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        return ItemInteractionResult.FAIL;
    }

    // Called when the block is placed
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!level.isClientSide) {
            // Start expanding after 1 second
            level.scheduleTick(pos, this, EXPAND_INTERVAL);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean isSource = state.getValue(SOURCE);
        int interval = EXPAND_INTERVAL + random.nextInt(10);
        if (level instanceof ServerLevel serverLevel) {
            int count = isSource ? 3 : 1; // source emits more per tick
            serverLevel.sendParticles(ModParticles.GAS_CLOUD.get(),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                count,
                0.3, 0.2, 0.3,   // spread within the block
                0.01);           // speed
        }

        if (!isSource) {
            int currentLifetime = state.getValue(LIFETIME);
            if (currentLifetime <= 0) {
                level.removeBlock(pos, false);
                return;
            }
        }

        int currentRadius = state.getValue(RADIUS);
        if (isSource || currentRadius < MAX_RADIUS) {
            for (Direction dir : Direction.values()) {
                BlockPos target = pos.relative(dir);
                BlockState targetState = level.getBlockState(target);
                if (targetState.isAir() || targetState.canBeReplaced()) {
                    level.setBlock(target, this.defaultBlockState()
                            .setValue(RADIUS, isSource ? 1 : currentRadius + 1)
                            .setValue(ACTIVE, state.getValue(ACTIVE))
                            .setValue(VARIANT, state.getValue(VARIANT))
                            .setValue(LIFETIME, MAX_LIFETIME)
                            .setValue(SOURCE, false), 3);
                }
            }
        }

        if (!isSource) {
            int currentLifetime = state.getValue(LIFETIME);
            int newLifetime = Math.max(0, currentLifetime - interval);
            level.setBlock(pos, state.setValue(LIFETIME, newLifetime), 3);
        }

        level.scheduleTick(pos, this, isSource ? EXPAND_INTERVAL : interval);
    }
    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, VARIANT, RADIUS, LIFETIME,SOURCE);
    }
    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction direction) {
        return adjacentBlockState.is(this) || super.skipRendering(state, adjacentBlockState, direction);
    }
}