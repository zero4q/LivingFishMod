package livingfish.entities;

import java.util.List;

import io.netty.buffer.Unpooled;
import livingfish.init.ModConfigs;
import livingfish.init.ModItems;
import livingfish.init.ModNetwork;
import livingfish.items.Fishingrod;
import livingfish.network.SToCMessage;
import livingfish.utils.FishUtils;
import livingfish.utils.MathUtils;
import livingfish.utils.PlayerUtils;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class EntityIronFishHook extends EntityFishHook {
    private static final DataParameter<Integer> DATA_HOOKED_ENTITY = EntityDataManager.<Integer>createKey(EntityIronFishHook.class, DataSerializers.VARINT);
    private BlockPos pos;
    private Block inTile;
    private boolean inGround;
    public EntityPlayer angler;
    private int ticksInGround;
    private int ticksInAir;
    private int ticksCatchable;
    private int ticksCaughtDelay;
    private int ticksCatchableDelay;
    private float fishApproachAngle;
    private int fishPosRotationIncrements;
    private double fishX;
    private double fishY;
    private double fishZ;
    private double fishYaw;
    private double fishPitch;
    @SideOnly(Side.CLIENT)
    private double clientMotionX;
    @SideOnly(Side.CLIENT)
    private double clientMotionY;
    @SideOnly(Side.CLIENT)
    private double clientMotionZ;
    public Entity caughtEntity;
    
    public EntityFish fishOnHook;
    public ItemStack fishingrod;
    public boolean hasBait;
    public boolean hasFishBones;


    public EntityIronFishHook(World world) {
        super(world);
        this.pos = new BlockPos(-1, -1, -1);
        this.setSize(0.25F, 0.25F);
        this.ignoreFrustumCheck = true;
    }
    
    @SideOnly(Side.CLIENT)
    public EntityIronFishHook(World world, double x, double y, double z, EntityPlayer angler) {
        this(world);
        this.setPosition(x, y, z);
        this.ignoreFrustumCheck = true;
        this.angler = angler;
        angler.fishEntity = this;
    }
    
    @SideOnly(Side.CLIENT)
    public boolean isInRangeToRenderDist(double distance) {
        double d0 = this.getEntityBoundingBox().getAverageEdgeLength() * 4.0D;

        if (Double.isNaN(d0)) {
            d0 = 4.0D;
        }

        d0 = d0 * 64.0D;
        return distance < d0 * d0;
    }
    
    /**
     * Set the position and rotation values directly without any clamping.
     */
    @SideOnly(Side.CLIENT)
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        this.fishX = x;
        this.fishY = y;
        this.fishZ = z;
        this.fishYaw = (double)yaw;
        this.fishPitch = (double)pitch;
        this.fishPosRotationIncrements = posRotationIncrements;
        
        this.motionX = this.clientMotionX;
        this.motionY = this.clientMotionY;
        this.motionZ = this.clientMotionZ;
    }

    /**
     * Updates the velocity of the entity to a new value.
     */
    @SideOnly(Side.CLIENT)
    public void setVelocity(double x, double y, double z) {
        this.motionX = x;
        this.motionY = y;
        this.motionZ = z;
        this.clientMotionX = this.motionX;
        this.clientMotionY = this.motionY;
        this.clientMotionZ = this.motionZ;
    }
    
    public EntityIronFishHook(World world, EntityPlayer angler, ItemStack fishingrod, boolean hasBait) {
        super(world);
        this.pos = new BlockPos(-1, -1, -1);
        this.ignoreFrustumCheck = true;
        this.angler = angler;
        this.angler.fishEntity = this;
        
        this.fishingrod	= fishingrod;
        this.setBait(hasBait);
        
        this.setSize(0.25F, 0.25F);
        this.setLocationAndAngles(angler.posX, angler.posY + (double)angler.getEyeHeight(), angler.posZ, angler.rotationYaw, angler.rotationPitch);
        this.posX -= (double)(MathHelper.cos(this.rotationYaw * 0.017453292F) * 0.16F);
        this.posY -= 0.10000000149011612D;
        this.posZ -= (double)(MathHelper.sin(this.rotationYaw * 0.017453292F) * 0.16F);
        this.setPosition(this.posX, this.posY, this.posZ);
        float f = 0.4F;
        this.motionX = (double)(-MathHelper.sin(this.rotationYaw * 0.017453292F) * MathHelper.cos(this.rotationPitch * 0.017453292F) * 0.4F);
        this.motionZ = (double)(MathHelper.cos(this.rotationYaw * 0.017453292F) * MathHelper.cos(this.rotationPitch * 0.017453292F) * 0.4F);
        this.motionY = (double)(-MathHelper.sin(this.rotationPitch * 0.017453292F) * 0.4F);
        this.handleHookCasting(this.motionX, this.motionY, this.motionZ, 1.5F, 1.0F);
    }
    
    public void setBait(boolean hasBait) {
    	this.hasBait = hasBait;
    }

    protected void entityInit() {
        this.getDataManager().register(DATA_HOOKED_ENTITY, Integer.valueOf(0));
    }

    public void notifyDataManagerChange(DataParameter<?> key) {
        if (DATA_HOOKED_ENTITY.equals(key)) {
            int i = ((Integer)this.getDataManager().get(DATA_HOOKED_ENTITY)).intValue();

            if (i > 0 && this.caughtEntity != null) {
                this.caughtEntity = null;
            }
        }

        super.notifyDataManagerChange(key);
    }
    
    public void handleHookCasting(double motionX, double motionY, double motionZ, float p_146035_7_, float p_146035_8_)
    {
        float f = MathHelper.sqrt_double(motionX * motionX + motionY * motionY + motionZ * motionZ);
        motionX = motionX / (double)f;
        motionY = motionY / (double)f;
        motionZ = motionZ / (double)f;
        motionX = motionX + this.rand.nextGaussian() * 0.007499999832361937D * (double)p_146035_8_;
        motionY = motionY + this.rand.nextGaussian() * 0.007499999832361937D * (double)p_146035_8_;
        motionZ = motionZ + this.rand.nextGaussian() * 0.007499999832361937D * (double)p_146035_8_;
        motionX = motionX * (double)p_146035_7_;
        motionY = motionY * (double)p_146035_7_;
        motionZ = motionZ * (double)p_146035_7_;
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        float f1 = MathHelper.sqrt_double(motionX * motionX + motionZ * motionZ);
        this.rotationYaw = (float)(MathHelper.atan2(motionX, motionZ) * (180D / Math.PI));
        this.rotationPitch = (float)(MathHelper.atan2(motionY, (double)f1) * (180D / Math.PI));
        this.prevRotationYaw = this.rotationYaw;
        this.prevRotationPitch = this.rotationPitch;
        this.ticksInGround = 0;
    }
    
    @Override
    public void onUpdate() {
    	
    	if (this.worldObj.isRemote)
        {
            int i = ((Integer)this.getDataManager().get(DATA_HOOKED_ENTITY)).intValue();

            if (i > 0 && this.caughtEntity == null) {
                this.caughtEntity = this.worldObj.getEntityByID(i - 1);
            }
        }
    	
        if (!this.worldObj.isRemote) {
        	if (this.angler == null) {
        		this.setDead();
        		return;
        	}
            ItemStack stack = this.angler.getHeldItemMainhand();
            if (this.angler.isDead || !this.angler.isEntityAlive() || stack == null || stack.getItem() != ModItems.fishingrod || this.getDistanceSqToEntity(this.angler) > 1024.0D) {
                if (this.angler instanceof EntityPlayerMP) {
                    PacketBuffer out = new PacketBuffer(Unpooled.buffer());
                    out.writeInt(1);
                    out.writeInt(this.getEntityId());
                    SToCMessage message = new SToCMessage(out);
                    TargetPoint point = new TargetPoint(this.angler.dimension, this.angler.posX, this.angler.posY, this.angler.posZ, 64.0D);
                    ModNetwork.networkWrapper.sendToAllAround(message, point);
                }
                this.angler.fishEntity = null;
                this.setDead();
                return;
            }
        }
        
        if (this.caughtEntity != null)
        {
            if (!this.caughtEntity.isDead)
            {
                this.posX = this.caughtEntity.posX;
                double d17 = (double)this.caughtEntity.height;
                this.posY = this.caughtEntity.getEntityBoundingBox().minY + d17 * 0.8D;
                this.posZ = this.caughtEntity.posZ;
                return;
            }

            this.caughtEntity = null;
        }
        
        if (this.fishPosRotationIncrements > 0)
        {
            double d3 = this.posX + (this.fishX - this.posX) / (double)this.fishPosRotationIncrements;
            double d4 = this.posY + (this.fishY - this.posY) / (double)this.fishPosRotationIncrements;
            double d6 = this.posZ + (this.fishZ - this.posZ) / (double)this.fishPosRotationIncrements;
            double d8 = MathHelper.wrapDegrees(this.fishYaw - (double)this.rotationYaw);
            this.rotationYaw = (float)((double)this.rotationYaw + d8 / (double)this.fishPosRotationIncrements);
            this.rotationPitch = (float)((double)this.rotationPitch + (this.fishPitch - (double)this.rotationPitch) / (double)this.fishPosRotationIncrements);
            --this.fishPosRotationIncrements;
            this.setPosition(d3, d4, d6);
            this.setRotation(this.rotationYaw, this.rotationPitch);
        } else {
            this.hookBehaviour();
        }
        
        if (!this.worldObj.isRemote && !this.hasFishBones && this.isInWater() && ModConfigs.fishbonesChance >= 0 && MathUtils.getRandom(ModConfigs.fishbonesChance) == 0) {
        	this.hasFishBones = true;
        }
        
    	this.catchFish();
    	
    	if (!this.worldObj.isRemote) {
    		if (this.angler instanceof EntityPlayerMP) {
                PacketBuffer out = new PacketBuffer(Unpooled.buffer());
                out.writeInt(0);
                out.writeInt(this.angler.getEntityId());
                out.writeInt(this.getEntityId());
                SToCMessage message = new SToCMessage(out);
                TargetPoint point = new TargetPoint(this.angler.dimension, this.angler.posX, this.angler.posY, this.angler.posZ, 64.0D);
                ModNetwork.networkWrapper.sendToAllAround(message, point);
            }
    	}
    	
    	this.onEntityUpdate();
    }
    
    public void catchFish() {
        if (!this.worldObj.isRemote) {
    		if (this.fishOnHook != null) {
    			this.setPosition(this.fishOnHook.posX, this.fishOnHook.posY, this.fishOnHook.posZ);
    		}
        }
    }
    
    public void setFishOnHook(EntityFish fish) {
    	this.fishOnHook = fish;
    }

    protected boolean canBeHooked(Entity entity) {
    	Entity onHook = this.fishOnHook;
        return (!(entity instanceof EntityFish) || entity == onHook) && (entity.canBeCollidedWith() || entity instanceof EntityItem);
    }
    
    public void hookBehaviour() {

        
            if (this.inGround) {
            	this.vanillaOnGroundBehavior();
            } else {
                ++this.ticksInAir;
            }
            

            if (!this.worldObj.isRemote) {
                this.vanillaCollition();
            }
            
            

            if (!this.inGround) {
            	this.VanillaMovement();
            }
    }
    
    public void vanillaOnGroundBehavior() {
    	//to long on ground
        if (this.worldObj.getBlockState(this.pos).getBlock() == this.inTile) {
            ++this.ticksInGround;
            if (this.ticksInGround == 1200) {
                this.setDead();
            }
            return;
        }
        

        this.inGround = false;
        this.motionX *= (double)(this.rand.nextFloat() * 0.2F);
        this.motionY *= (double)(this.rand.nextFloat() * 0.2F);
        this.motionZ *= (double)(this.rand.nextFloat() * 0.2F);
        this.ticksInGround = 0;
        this.ticksInAir = 0;
    }
    
    public void vanillaCollition() {
    	Vec3d vec3d1 = new Vec3d(this.posX, this.posY, this.posZ);
        Vec3d vec3d = new Vec3d(this.posX + this.motionX, this.posY + this.motionY, this.posZ + this.motionZ);
        RayTraceResult raytraceresult = this.worldObj.rayTraceBlocks(vec3d1, vec3d);
        vec3d1 = new Vec3d(this.posX, this.posY, this.posZ);
        vec3d = new Vec3d(this.posX + this.motionX, this.posY + this.motionY, this.posZ + this.motionZ);

        if (raytraceresult != null)
        {
            vec3d = new Vec3d(raytraceresult.hitVec.xCoord, raytraceresult.hitVec.yCoord, raytraceresult.hitVec.zCoord);
        }

        Entity entity = null;
        List<Entity> list = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, this.getEntityBoundingBox().addCoord(this.motionX, this.motionY, this.motionZ).expandXyz(1.0D));
        double d0 = 0.0D;

        for (int j = 0; j < list.size(); ++j)
        {
            Entity entity1 = (Entity)list.get(j);

            if (this.canBeHooked(entity1) && (entity1 != this.angler || this.ticksInAir >= 5))
            {
                AxisAlignedBB axisalignedbb1 = entity1.getEntityBoundingBox().expandXyz(0.30000001192092896D);
                RayTraceResult raytraceresult1 = axisalignedbb1.calculateIntercept(vec3d1, vec3d);

                if (raytraceresult1 != null)
                {
                    double d1 = vec3d1.squareDistanceTo(raytraceresult1.hitVec);

                    if (d1 < d0 || d0 == 0.0D)
                    {
                        entity = entity1;
                        d0 = d1;
                    }
                }
            }
        }

        if (entity != null)
        {
            raytraceresult = new RayTraceResult(entity);
        }

        if (raytraceresult != null)
        {
            if (raytraceresult.entityHit != null)
            {
                this.caughtEntity = raytraceresult.entityHit;
                this.getDataManager().set(DATA_HOOKED_ENTITY, Integer.valueOf(this.caughtEntity.getEntityId() + 1));
            }
            else
            {
                this.inGround = true;
            }
        }
    }
    
    public void VanillaMovement() {
    	this.moveEntity(this.motionX, this.motionY, this.motionZ);
        float f2 = MathHelper.sqrt_double(this.motionX * this.motionX + this.motionZ * this.motionZ);
        this.rotationYaw = (float)(MathHelper.atan2(this.motionX, this.motionZ) * (180D / Math.PI));

        for (this.rotationPitch = (float)(MathHelper.atan2(this.motionY, (double)f2) * (180D / Math.PI)); this.rotationPitch - this.prevRotationPitch < -180.0F; this.prevRotationPitch -= 360.0F)
        {
            ;
        }

        while (this.rotationPitch - this.prevRotationPitch >= 180.0F)
        {
            this.prevRotationPitch += 360.0F;
        }

        while (this.rotationYaw - this.prevRotationYaw < -180.0F)
        {
            this.prevRotationYaw -= 360.0F;
        }

        while (this.rotationYaw - this.prevRotationYaw >= 180.0F)
        {
            this.prevRotationYaw += 360.0F;
        }

        this.rotationPitch = this.prevRotationPitch + (this.rotationPitch - this.prevRotationPitch) * 0.2F;
        this.rotationYaw = this.prevRotationYaw + (this.rotationYaw - this.prevRotationYaw) * 0.2F;
        float f3 = 0.92F;

        if (this.onGround || this.isCollidedHorizontally)
        {
            f3 = 0.5F;
        }

        int k = 5;
        double d5 = 0.0D;

        for (int l = 0; l < 5; ++l)
        {
            AxisAlignedBB axisalignedbb = this.getEntityBoundingBox();
            double d9 = axisalignedbb.maxY - axisalignedbb.minY;
            double d10 = axisalignedbb.minY + d9 * (double)l / 5.0D;
            double d11 = axisalignedbb.minY + d9 * (double)(l + 1) / 5.0D;
            AxisAlignedBB axisalignedbb2 = new AxisAlignedBB(axisalignedbb.minX, d10, axisalignedbb.minZ, axisalignedbb.maxX, d11, axisalignedbb.maxZ);

            if (this.worldObj.isAABBInMaterial(axisalignedbb2, Material.WATER))
            {
                d5 += 0.2D;
            }
        }

        if (!this.worldObj.isRemote && d5 > 0.0D)
        {
            //VanillaFishing
        }

        double d7 = d5 * 2.0D - 1.0D;
        this.motionY += 0.03999999910593033D * d7;

        if (d5 > 0.0D)
        {
            f3 = (float)((double)f3 * 0.9D);
            this.motionY *= 0.8D;
        }

        this.motionX *= (double)f3;
        this.motionY *= (double)f3;
        this.motionZ *= (double)f3;
        this.setPosition(this.posX, this.posY, this.posZ);
    }

    public void writeEntityToNBT(NBTTagCompound compound) {
        compound.setInteger("xTile", this.pos.getX());
        compound.setInteger("yTile", this.pos.getY());
        compound.setInteger("zTile", this.pos.getZ());
        ResourceLocation resourcelocation = (ResourceLocation)Block.REGISTRY.getNameForObject(this.inTile);
        compound.setString("inTile", resourcelocation == null ? "" : resourcelocation.toString());
        compound.setByte("inGround", (byte)(this.inGround ? 1 : 0));
    }

    public void readEntityFromNBT(NBTTagCompound compound) {
        this.pos = new BlockPos(compound.getInteger("xTile"), compound.getInteger("yTile"), compound.getInteger("zTile"));

        if (compound.hasKey("inTile", 8)) {
            this.inTile = Block.getBlockFromName(compound.getString("inTile"));
        } else {
            this.inTile = Block.getBlockById(compound.getByte("inTile") & 255);
        }

        this.inGround = compound.getByte("inGround") == 1;
    }

    public int handleIronHookRetraction(double modifier) {    	
        if (this.worldObj.isRemote) {
        	return 0;
        } else {
        	
        	if (this.fishOnHook != null) {
            	
            	double distanceSq = MathUtils.getDistanceSqEntitytoEntity(this.angler, this.fishOnHook, false);
            	if (distanceSq > 2.2D) {
            		MathUtils.pullEntitytoEntity(this.angler, modifier, this.fishOnHook);
            		return 0;
            	}
            	
            	if (!this.fishOnHook.isDead) {
            		if (this.angler.inventory.hasItemStack(new ItemStack(Items.WATER_BUCKET))) {
                		int slot = PlayerUtils.getSlotFor(new ItemStack(Items.WATER_BUCKET), this.angler);
                		Item fishBucket = FishUtils.getFishBucketByFish(this.fishOnHook);
                		if (fishBucket != null) {
                			this.angler.inventory.setInventorySlotContents(slot, new ItemStack(fishBucket));
                		}
                	} else {
                		this.fishOnHook.dropFewItems(false, 0);
                	}
            		this.fishOnHook.setDead();
            	} else {
            		this.fishOnHook = null;
            	}
            	
        	}
        	
        	if(this.fishOnHook == null && this.caughtEntity == null && this.hasFishBones) {
        		if (this.hasBait) {
        			this.hasBait = false;
        			this.removeBait();
        		}
        		ItemStack caughtStack = new ItemStack(ModItems.fishbones);
                EntityItem entityitem = new EntityItem(this.worldObj, this.posX, this.posY, this.posZ, caughtStack);
                double d0 = this.angler.posX - this.posX;
                double d1 = this.angler.posY - this.posY;
                double d2 = this.angler.posZ - this.posZ;
                double d3 = (double)MathHelper.sqrt_double(d0 * d0 + d1 * d1 + d2 * d2);
                double d4 = 0.1D;
                entityitem.motionX = d0 * 0.1D;
                entityitem.motionY = d1 * 0.1D + (double)MathHelper.sqrt_double(d3) * 0.08D;
                entityitem.motionZ = d2 * 0.1D;
                this.worldObj.spawnEntityInWorld(entityitem);
                this.angler.worldObj.spawnEntityInWorld(new EntityXPOrb(this.angler.worldObj, this.angler.posX, this.angler.posY + 0.5D, this.angler.posZ + 0.5D, this.rand.nextInt(6) + 1));
        	}
        	
        	
            int i = 0;
            if (this.caughtEntity != null) {
                if (this.caughtEntity instanceof EntityFish) {
            		i = 1;
            	} else if (this.caughtEntity instanceof Entity) {
                	this.bringInHookedEntity();
                    this.worldObj.setEntityState(this, (byte)31);
                    if (this.caughtEntity instanceof EntityItem) {
                		i = 3;
                	}
                    i = 5;
                } else if (this.inGround) {
                    i = 2;
                }
            }
            
            if (this.angler instanceof EntityPlayerMP) {
                PacketBuffer out = new PacketBuffer(Unpooled.buffer());
                out.writeInt(1);
                out.writeInt(this.getEntityId());
                SToCMessage message = new SToCMessage(out);
                TargetPoint point = new TargetPoint(this.angler.dimension, this.angler.posX, this.angler.posY, this.angler.posZ, 64.0D);
                ModNetwork.networkWrapper.sendToAllAround(message, point);
            }
            
            this.setDead();
            this.angler.fishEntity = null;
            this.fishOnHook = null;
            return i;
        }
    }
    
    @SideOnly(Side.CLIENT)
    public void handleStatusUpdate(byte id)
    {
        if (id == 31 && this.worldObj.isRemote && this.caughtEntity instanceof EntityPlayer && ((EntityPlayer)this.caughtEntity).isUser())
        {
            this.bringInHookedEntity();
        }

        super.handleStatusUpdate(id);
    }

    protected void bringInHookedEntity() {
        double d0 = this.angler.posX - this.posX;
        double d1 = this.angler.posY - this.posY;
        double d2 = this.angler.posZ - this.posZ;
        double d3 = (double)MathHelper.sqrt_double(d0 * d0 + d1 * d1 + d2 * d2);
        double d4 = 0.1D;
        this.caughtEntity.motionX += d0 * 0.1D;
        this.caughtEntity.motionY += d1 * 0.1D + (double)MathHelper.sqrt_double(d3) * 0.08D;
        this.caughtEntity.motionZ += d2 * 0.1D;
    }
    
    public void setDead() {
        super.setDead();

        if (this.angler != null) {
            this.angler.fishEntity = null;
        }
    }
    
    public EntityPlayer getAngler() {
        return this.angler;
    }
    
    public void removeBait() {
    	Fishingrod.removeBait(this.fishingrod);
    	this.hasBait = false;
    }
    
    public void playCoughtSond() {
        this.playSound(SoundEvents.ENTITY_BOBBER_SPLASH, 0.25F, 1.0F + (this.rand.nextFloat() - this.rand.nextFloat()) * 0.4F);
    }
    
}