package net.hyper_pigeon.eldritch_mobs.command.commands.summon;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.hyper_pigeon.eldritch_mobs.EldritchMobsMod;
import net.hyper_pigeon.eldritch_mobs.command.argument_type.LanternArgumentType;
import net.hyper_pigeon.eldritch_mobs.command.argument_type.MobRankArgumentType;
import net.hyper_pigeon.eldritch_mobs.enums.LanternType;
import net.hyper_pigeon.eldritch_mobs.enums.MobRank;
import net.minecraft.command.argument.EntitySummonArgumentType;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public abstract class SummonCommandRoot {

    protected static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.summon.failed"));
    protected static final SimpleCommandExceptionType FAILED_UUID_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.summon.failed.uuid"));
    protected static final SimpleCommandExceptionType INVALID_POSITION_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.summon.invalidPosition"));

    public static final LiteralArgumentBuilder<ServerCommandSource> SUMMON_COMMAND_ROOT = CommandManager.literal("summon")
            .requires(source -> source.hasPermissionLevel(4)).then(
                    CommandManager.argument("mob_rank", MobRankArgumentType.mobRank()).then(
                            CommandManager.argument("entity", EntitySummonArgumentType.entitySummon())
                            .suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
                            .executes(context -> execute(
                                    context.getSource(),
                                    EntitySummonArgumentType.getEntitySummon(context, "entity"),
                                    context.getSource().getPosition(),
                                    new NbtCompound(),
                                    MobRankArgumentType.getMobRank(context, "mob_rank")
                            )).then(
                                    CommandManager.argument("pos", Vec3ArgumentType.vec3())
                                            .executes(context -> execute(
                                                    context.getSource(),
                                                    EntitySummonArgumentType.getEntitySummon(context, "entity"),
                                                    Vec3ArgumentType.getVec3(context, "pos"),
                                                    new NbtCompound(),
                                                    MobRankArgumentType.getMobRank(context, "mob_rank")
                                            )).then(
                                                    CommandManager.argument("nbt", NbtCompoundArgumentType.nbtCompound())
                                                            .executes(context -> execute(
                                                                    context.getSource(),
                                                                    EntitySummonArgumentType.getEntitySummon(context, "entity"),
                                                                    Vec3ArgumentType.getVec3(context, "pos"),
                                                                    NbtCompoundArgumentType.getNbtCompound(context, "nbt"),
                                                                    MobRankArgumentType.getMobRank(context, "mob_rank")
                                                            ))
                                            )
                            )
                    )
            );

    private static int execute(ServerCommandSource source, Identifier entity, Vec3d pos, NbtCompound nbt, MobRank rank) throws CommandSyntaxException {

        // Check that the position is valid.
        if (!World.isValid(new BlockPos(pos))) throw INVALID_POSITION_EXCEPTION.create();

        NbtCompound nbtCompound = nbt.copy();
        nbtCompound.putString("id", entity.toString());

        ServerWorld serverWorld = source.getWorld();

        Entity toSpawn = EntityType.loadEntityWithPassengers(nbtCompound, serverWorld, entityX -> {
            entityX.refreshPositionAndAngles(pos.x, pos.y, pos.z, entityX.getYaw(), entityX.getPitch());
            return entityX;
        });

        // Check that the entity was created correctly.
        if (toSpawn == null) throw FAILED_EXCEPTION.create();

        if (toSpawn instanceof MobEntity mobToSpawn) {
            mobToSpawn.initialize(serverWorld, serverWorld.getLocalDifficulty(toSpawn.getBlockPos()), SpawnReason.COMMAND, null, null);
            if (EldritchMobsMod.ELDRITCH_MODIFIERS.get(toSpawn).getModifiers() != null) {
                EldritchMobsMod.ELDRITCH_MODIFIERS.get(toSpawn).setRank(MobRank.NONE);
                EldritchMobsMod.ELDRITCH_MODIFIERS.get(toSpawn).clearModifiers();
                toSpawn.setCustomName(null);
            }
            EldritchMobsMod.ELDRITCH_MODIFIERS.get(toSpawn).setRank(rank);
            EldritchMobsMod.ELDRITCH_MODIFIERS.get(toSpawn).randomlySetModifiers();
            EldritchMobsMod.ELDRITCH_MODIFIERS.get(toSpawn).setTitle();
        }

        // Check that the entity was spawned correctly.
        if (!serverWorld.spawnNewEntityAndPassengers(toSpawn)) throw FAILED_UUID_EXCEPTION.create();

        source.sendFeedback(Text.translatable("commands.summon.success", toSpawn.getDisplayName()), true);
        return 1;
    }
}
