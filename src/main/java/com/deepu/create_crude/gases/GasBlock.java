package com.deepu.create_crude.gases;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ItemInteractionResult;

import java.util.function.Supplier;

public class GasBlock extends Block {
    public static final int MAX_RADIUS = 10;

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 15);
    public static final IntegerProperty RADIUS = IntegerProperty.create("radius", 0, MAX_RADIUS);
    public static final BooleanProperty SOURCE = BooleanProperty.create("source");
    public static final IntegerProperty PRESSURE = IntegerProperty.create("pressure", 0, 7);

    private final GasProperties properties;
    private final Supplier<SimpleParticleType> particleSupplier;

    public GasBlock(Properties blockProps, GasProperties props, Supplier<SimpleParticleType> particleSupplier) {
        super(blockProps);
        this.properties = props;
        this.particleSupplier = particleSupplier;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ACTIVE, false)
                .setValue(VARIANT, 0)
                .setValue(RADIUS, 0)
                .setValue(SOURCE, false)
                .setValue(PRESSURE, 0));
    }

    public GasProperties getProperties() {
        return properties;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return InteractionResult.FAIL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return ItemInteractionResult.FAIL;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, properties.tickInterval());
        }
    }

   
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean isSource = state.getValue(SOURCE);
        int currentRadius = state.getValue(RADIUS);

        // Dispersal logic: If it's not a source block and it has reached its max capacity or expands out,
        // you can control its natural death via random chance or let it dissipate when radius peaks.
        if (!isSource && currentRadius >= properties.maxRadius() && random.nextInt(4) == 0) {
            level.removeBlock(pos, false);
            return;
        }

        int newRadius = currentRadius;
        if (currentRadius < properties.maxRadius()) {
            newRadius = Math.min(properties.maxRadius(), currentRadius + 1);
        }

        int particleCount = 2 + newRadius * 3;
        double spread = 0.3 + newRadius * 0.6;

        level.sendParticles(particleSupplier.get(),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                particleCount,
                spread, spread * 0.6, spread,
                0.01);

        BlockState newState = state.setValue(RADIUS, newRadius);
        level.setBlock(pos, newState, 3);

        level.scheduleTick(pos, this, properties.tickInterval());
    }


    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, VARIANT, RADIUS, SOURCE,PRESSURE);
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction direction) {
        return adjacentBlockState.is(this) || super.skipRendering(state, adjacentBlockState, direction);
    }
}