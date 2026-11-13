package org.Lyosha008.radlink;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public class CustomProjectile extends Snowball {
    public CustomProjectile(Level level, LivingEntity owner) {super(level, owner);}

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        Entity entity = hitResult.getEntity();
        executeHitScript(entity.blockPosition(), entity);
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        executeHitScript(hitResult.getBlockPos(), null);
        super.onHitBlock(hitResult);
    }

    private void executeHitScript(BlockPos pos, Entity entity) {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        CustomDefinitions.CustomScriptDefinition script = Radlink.getItemScriptForElement(
            BuiltInRegistries.ITEM.getKey(getItem().getItem()).toString());
        if (script != null && "right_click".equalsIgnoreCase(script.trigger().trim())
            && ScriptEngine.checkProjectileHitCondition(
            script.ifCondition(), serverLevel, pos, entity)) {
            ScriptEngine.executeActions(script.thenActions(), serverLevel, pos, entity);
        }
    }
}
