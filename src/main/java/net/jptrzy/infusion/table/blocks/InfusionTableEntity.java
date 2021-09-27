package net.jptrzy.infusion.table.blocks;

import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable;
import net.jptrzy.infusion.table.Main;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

public class InfusionTableEntity extends BlockEntity implements BlockEntityClientSerializable {

    private ItemStack item = new ItemStack(Items.AIR);
    private ItemStack book = new ItemStack(Items.AIR);

    public float bookOpenAngle = 0F;
    public float bookLastOpenAngle = 0F;

    public float bookRot = 0;
    public float bookLastRot = 0;
    public float bookRotDir = 0;
    public float bookRotForce = 0;

    /*
    0 - empty | passive
    1 - with book | passive
    2 - with item in animation or without item in reverse animation
    3 - with item | passive
    4 - on fire in animation
    5 - enchanted book | passive
    */
    private int state = 0;

    private int time = 0;

    public InfusionTableEntity(BlockPos pos, BlockState state) {
        super(Main.INFUSION_TABLE_ENTITY, pos, state);
    }

    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if(!world.isClient()) {
            ItemStack item = player.getStackInHand(hand);
            ItemStack copy;
            if (this.state == 0 && item.isOf(Items.BOOK) && !item.hasEnchantments()) {
                copy = item.copy();
                copy.setCount(1);
                this.book = copy;
                item.decrement(1);
                this.bookOpenAngle = 0;
                this.state = 1;
                this.sync();
            } else if (this.state == 1 && item.hasEnchantments() && !item.isOf(Items.BOOK) && !item.isOf(Items.ENCHANTED_BOOK)) {
                copy = item.copy();
                copy.setCount(1);
                this.item = copy;
                item.decrement(1);
                this.state = 2;
                this.sync();
            }else if(this.state == 3 && item.isOf(Items.FLINT_AND_STEEL)){
                this.state = 4;
                this.sync();
            }
        }
        return ActionResult.SUCCESS;
    }

    public void onExplosion(World world, BlockPos pos, Explosion explosion) {
        onBreak(null, world, pos, null);
    }

    public void onBreak(@Nullable BlockState state, World world, BlockPos pos, @Nullable PlayerEntity player) {
        if (!world.isClient()) {
            if (player == null) {
                if(!this.book.isEmpty()) {
                    dropStack(world, pos, this.book);
                    this.book = new ItemStack(Items.AIR);

                    if (!this.item.isEmpty() && this.state != 5) {
                        dropStack(world, pos, this.item);
                        this.item = new ItemStack(Items.AIR);
                    }
                    cleanUp(world);
                }
                this.sync();
            } else {
                if (this.state == 3) {
                    dropStack(world, pos, this.item);
                    this.item = new ItemStack(Items.AIR);
                    this.state = 2;
                    this.sync();
                } else if (this.state == 1 || this.state == 5) {
                    dropStack(world, pos, this.book);
                    this.book = new ItemStack(Items.AIR);
                    this.state = 0;
                    if(this.state == 5){
                        cleanUp(world);
                    }
                    this.sync();
                }
            }
        }
    }

    public static void dropStack(World world, BlockPos pos, ItemStack stack) {
        float f = EntityType.ITEM.getHeight() / 2.0F;
        double d = (double)((float)pos.getX() + 0.5F);
        double e = (double)((float)pos.getY() + 1F) - (double)f;
        double g = (double)((float)pos.getZ() + 0.5F);
        dropStack(world, () -> { return new ItemEntity(world, d, e, g, stack);}, stack);
    }

    private static void dropStack(World world, Supplier<ItemEntity> itemEntitySupplier, ItemStack stack) {
        if (!world.isClient && !stack.isEmpty() && world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS)) {
            ItemEntity itemEntity = (ItemEntity)itemEntitySupplier.get();
            itemEntity.setToDefaultPickupDelay();
            world.spawnEntity(itemEntity);
        }
    }

    public float bookAngleForce = 0;



    public static void tick(World world, BlockPos pos, BlockState state, InfusionTableEntity entity) {

        entity.bookLastRot = entity.bookRot;

        PlayerEntity playerEntity = world.getClosestPlayer((double)pos.getX() + 0.5D, (double)pos.getY() + 0.5D, (double)pos.getZ() + 0.5D, 3.0D, false);
        if (playerEntity != null) {
            double d = playerEntity.getX() - ((double) pos.getX() + 0.5D);
            double e = playerEntity.getZ() - ((double) pos.getZ() + 0.5D);
            entity.bookRotDir = (float) MathHelper.atan2(e, d);
        }else{
            entity.bookRotDir += 0.02F;
        }

        entity.bookRot = Main.aroundRadial(entity.bookRot);

        entity.bookRotDir = Main.aroundRadial(entity.bookRotDir);

        entity.bookRotForce = entity.bookRotDir - entity.bookRot;

        entity.bookRotForce = Main.aroundRadial(entity.bookRotForce);

        entity.bookRot += entity.bookRotForce * 0.4F;

        entity.bookLastOpenAngle = entity.bookOpenAngle;

        switch (entity.state) {
            case 2:

                if(entity.item.isEmpty()){
                    if(entity.bookOpenAngle > 0F) {
                        entity.bookOpenAngle -= 0.1F;
                    }else if(!world.isClient()){
                        entity.bookOpenAngle = 0;
                        entity.state = 1;
                        entity.sync();
                    }
                }else{
                    if(entity.bookOpenAngle < 1F) {
                        entity.bookOpenAngle += 0.1F;
                    }else if(!world.isClient()){
                        entity.bookOpenAngle = 1;
                        entity.state = 3;
                        entity.sync();
                    }
                }
                break;
            case 4: // TODO
                if(entity.time > 60){
                    if(entity.bookOpenAngle > 0F) {
                        entity.bookOpenAngle -= 0.1F;
                    }else{
                        if(!world.isClient()){
                            entity.state = 5;
                            entity.time = 0;
                            entity.bookOpenAngle = 0;

                            Map<Enchantment, Integer> list =  EnchantmentHelper.fromNbt(entity.item.getEnchantments());
                            for ( Map.Entry<Enchantment, Integer> entry : list.entrySet() ) {
                                entity.book.addEnchantment(entry.getKey(), entry.getValue());
                            }

                            entity.item = new ItemStack(Items.ENCHANTED_BOOK);
                            entity.item.setNbt(entity.book.getNbt());
                            entity.book = entity.item.copy();

                            entity.sync();
                        }
                    }
                }else if(entity.time < 40){
                    Random r = new Random();
                    world.addParticle(ParticleTypes.ENCHANT, (double)pos.getX() + 0.5D, (double)pos.getY() + 1.0D, (double)pos.getZ() + 0.5D,
                            (r.nextBoolean() ? -1 : 1) * r.nextFloat(),
                            1,
                            (r.nextBoolean() ? -1 : 1) * r.nextFloat());
                }
                entity.time += 1;
                break;
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        super.writeNbt(tag);

        tag.put("Item", this.item.writeNbt(new NbtCompound()));
        tag.put("Book", this.book.writeNbt(new NbtCompound()));
        tag.putInt("State", this.state);
        tag.putInt("Time", this.time);
        tag.putFloat("BookOpenAngle", this.bookOpenAngle);

        return tag;
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);

        this.item = ItemStack.fromNbt(tag.getCompound("Item"));
        this.book = ItemStack.fromNbt(tag.getCompound("Book"));
        this.state = tag.getInt("State");
        this.time = tag.getInt("Time");
        this.bookOpenAngle = tag.getFloat("BookOpenAngle");
    }

    @Override
    public void fromClientTag(NbtCompound nbt){ this.readNbt(nbt); }

    @Override
    public NbtCompound toClientTag(NbtCompound nbt){ return this.writeNbt(nbt);}

    public void cleanUp(World world){
        if(!world.isClient()){
            this.state = 0;
            this.bookOpenAngle = 0;
            this.time = 0;

            this.item = new ItemStack(Items.AIR);
            this.book = new ItemStack(Items.AIR);
            this.sync();
        }
    }


    public ItemStack getItem(){return this.item;}
    public ItemStack getBook (){return this.book;}
    public float getBookOpenAngle (){return this.bookOpenAngle;}
}