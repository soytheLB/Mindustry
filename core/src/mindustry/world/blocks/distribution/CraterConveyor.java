package mindustry.world.blocks.distribution;

import arc.*;
import arc.func.*;
import arc.math.*;
import arc.util.*;
import mindustry.ui.*;
import arc.math.geom.*;
import mindustry.type.*;
import mindustry.world.*;
import arc.graphics.g2d.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.graphics.*;
import arc.scene.ui.layout.*;
import mindustry.world.meta.*;
import mindustry.entities.type.*;

import static mindustry.Vars.*;

public class CraterConveyor extends BaseConveyor{
    private TextureRegion start, end, crater;

    public CraterConveyor(String name){
        super(name);
        compressable = true;
        entityType = CraterConveyorEntity::new;
    }

    @Override
    public void load(){
        int i;
        for(i = 0; i < regions.length; i++){
            for(int j = 0; j < 4; j++){
                regions[i][j] = Core.atlas.find(name + "-" + i + "-" + 0);
            }
        }

        start  = Core.atlas.find(name + "-5-0");
        end    = Core.atlas.find(name + "-6-0");
        crater = Core.atlas.find("crater");
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(BlockStat.maxUnits, 1, StatUnit.none);
        stats.add(BlockStat.boostEffect, "$blocks.itemcapacity");
    }

    @Override
    public void draw(Tile tile){
        super.draw(tile);

        // draws the markings over either end of the track
        if(isStart(tile) && isEnd(tile)) return;
        if(isStart(tile))  Draw.rect(start, tile.drawx(), tile.drawy(), tile.rotation() * 90);
        if(isEnd(tile))      Draw.rect(end, tile.drawx(), tile.drawy(), tile.rotation() * 90);
    }

    @Override
    public void drawLayer(Tile tile){
        CraterConveyorEntity entity = tile.ent();

        if(entity.crater == null) return;

        // draw crater
        Draw.rect(crater, entity.crater.x, entity.crater.y, entity.crater.rotation);

        // find dominant(/only) item
        if(entity.crater.item == null){
            entity.crater.item = tile.entity.items.take();
            tile.entity.items.add(entity.crater.item, 1);
        }

        // draw resource
        float size = itemSize / 1.5f;
        Draw.rect(entity.crater.item.icon(Cicon.medium), entity.crater.x, entity.crater.y, size, size, 0);

        // draw amount
        Fonts.outline.draw(tile.entity.items.total() + "", entity.crater.x, entity.crater.y - 1,
        Pal.accent, 0.25f * 0.5f / Scl.scl(1f), false, Align.center);
    }


    @Override
    public void update(Tile tile){
        CraterConveyorEntity entity = tile.ent();

        // only update once per frame
        if(entity.lastFrameUpdated == Core.graphics.getFrameId()) return;
        entity.lastFrameUpdated = Core.graphics.getFrameId();

        if(entity.crater == null){
            if(entity.items.total() > 0 && Core.graphics.getFrameId() > entity.lastFrameSpawned){
                entity.crater = new Crater(tile);
                Effects.effect(Fx.plasticburn, tile.drawx(), tile.drawy());
            }
        }else{
            if(entity.items.total() == 0){
                Effects.effect(Fx.plasticburn, tile.drawx(), tile.drawy());
                entity.crater = null;
            }else{

                if(shouldLaunch(tile)){
                    Tile destination = tile.front();
                    destination.block().update(destination);

                    if(entity.crater.dst(tile) < 1.25f){
                        entity.crater.face = tile.rotation() * 90 - 90;
                        if(!(destination.block() instanceof CraterConveyor)){
                            while(entity.items.total() > 0 && entity.crater.item != null && offloadDir(tile, entity.crater.item)) entity.items.remove(entity.crater.item, 1);

                        }
                    }

                    if(entity.crater.dst(tile) < 0.1f){
                        if(destination.block() instanceof CraterConveyor){
                            CraterConveyorEntity e = destination.ent();

                            if(e.crater == null){
                                e.crater = entity.crater;
                                entity.crater = null;

                                entity.lastFrameSpawned = Core.graphics.getFrameId() + 10;

                                e.items.addAll(entity.items);
                                entity.items.clear();
                            }
                        }
                    }
                }
            }
        }

        if(entity.crater != null){
            entity.crater.x = Mathf.lerpDelta(entity.crater.x, tile.drawx(), speed);
            entity.crater.y = Mathf.lerpDelta(entity.crater.y, tile.drawy(), speed);
            entity.crater.rotation = Mathf.slerpDelta(entity.crater.rotation, entity.crater.face, speed * 2);
        }
    }

    public class CraterConveyorEntity extends BaseConveyorEntity{
        float lastFrameUpdated = -1;
        float lastFrameSpawned = -1;
        float lastFrameChanged = -1;
        Crater crater;
    }

    protected class Crater implements Position{
        float rotation;
        float face;
        float x;
        float y;
        Item item;

        Crater(Tile tile){
            x = tile.drawx();
            y = tile.drawy();
            rotation = tile.rotation() * 90 - 90;
            face = rotation;
        }

        @Override
        public float getX(){
            return x;
        }

        @Override
        public float getY(){
            return y;
        }
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        CraterConveyorEntity entity = tile.ent();

        if(!isStart(tile) && !source.block().compressable) return false;
        if(entity.items.total() > 0 && !entity.items.has(item)) return false;
        if(entity.items.total() >= getMaximumAccepted(tile, item)) return false;

        return true;
    }

    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        super.handleItem(item, tile, source);
        ((CraterConveyorEntity)tile.entity).lastFrameChanged = Core.graphics.getFrameId();
    }

    @Override
    public void handleStack(Item item, int amount, Tile tile, Unit source){
        super.handleStack(item, amount, tile, source);
        ((CraterConveyorEntity)tile.entity).lastFrameChanged = Core.graphics.getFrameId();
    }

    @Override
    public int getMaximumAccepted(Tile tile, Item item){
        return Mathf.round(super.getMaximumAccepted(tile, item) * tile.entity.timeScale);
    }

    public boolean shouldLaunch(Tile tile){
        CraterConveyorEntity entity = tile.ent();

        // its not a start tile so it should be moving
        if(!isStart(tile)) return true;

        // its considered full
        if(entity.items.total() >= getMaximumAccepted(tile, entity.crater.item)) return true;

        // inactivity tracker
        if(Core.graphics.getFrameId() > entity.lastFrameChanged + 120) return true;

        return false;
    }

    @Override
    public boolean blends(Tile tile, int rotation, int otherx, int othery, int otherrot, Block otherblock) {
        return otherblock.outputsItems() && blendsArmored(tile, rotation, otherx, othery, otherrot, otherblock) && otherblock.compressable;
    }

    /**
     * Check if there is no crater conveyor blending with this block
     * (should probably use blendbits, if only i understood those)
     */
    private boolean isStart(Tile tile){
        Tile[] inputs = new Tile[]{tile.back(), tile.left(), tile.right()};
        for(Tile input : inputs){
            if(input != null && input.getTeam() == tile.getTeam() && input.block().compressable && input.front() == tile) return false;
        }

        return true;
    }

    /**
     * Check if this tile is considered the end of the line
     */
    private boolean isEnd(Tile tile){
        if(tile.front() == null) return true;
        if(tile.getTeam() != tile.front().getTeam()) return true;
        return !tile.front().block().compressable;
    }

}
