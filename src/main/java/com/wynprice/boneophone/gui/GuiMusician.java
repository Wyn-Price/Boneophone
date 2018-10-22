package com.wynprice.boneophone.gui;

import com.google.common.collect.Lists;
import com.wynprice.boneophone.SkeletalBand;
import com.wynprice.boneophone.entity.MusicalSkeleton;
import com.wynprice.boneophone.midi.MidiStream;
import com.wynprice.boneophone.network.C4SkeletonChangeType;
import com.wynprice.boneophone.network.C6SkeletonChangeChannel;
import com.wynprice.boneophone.network.C8SkeletonChangeTrack;
import com.wynprice.boneophone.types.ConductorType;
import com.wynprice.boneophone.types.MusicianType;
import com.wynprice.boneophone.types.MusicianTypeFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiPageButtonList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.Entity;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class GuiMusician extends GuiScreen {

    private final int entityID;
    private final Supplier<MusicianTypeFactory> typeGetter;
    private final IntSupplier channelSupplier;
    private final IntSupplier trackIDSupplier;
    private GuiTextField channelField;

    private GuiSelectList musicianTypes;
    private GuiSelectList trackList;

    public GuiMusician(int entityID, Supplier<MusicianTypeFactory> typeGetter, IntSupplier channelSupplier, IntSupplier trackIDSupplier) {
        this.entityID = entityID;
        this.typeGetter = typeGetter;
        this.channelSupplier = channelSupplier;
        this.trackIDSupplier = trackIDSupplier;
    }

    @Override
    public void initGui() {

        List<MusicianTypeEntry> typeList = Lists.newArrayList();
        MusicianTypeFactory activeType = this.typeGetter.get();
        MusicianTypeEntry active = null;
        for (MusicianTypeFactory type : SkeletalBand.MUSICIAN_REGISTRY) {
            typeList.add(type == activeType ? active = new MusicianTypeEntry(type) : new MusicianTypeEntry(type));
        }

        this.musicianTypes = new GuiSelectList(20, 20, this.width / 2 - 30, 20, 5, () -> typeList);
        this.musicianTypes.setActive(active);

        List<TrackEntry> trackList = Lists.newArrayList();
        int trackID = this.trackIDSupplier.getAsInt();
        TrackEntry activeTrack = null;
        for (Entity entity : Minecraft.getMinecraft().world.loadedEntityList) {
            if(entity instanceof MusicalSkeleton) {
                MusicianType type = ((MusicalSkeleton)entity).musicianType;
                if(type instanceof ConductorType) {
                    ConductorType con = (ConductorType) type;
                    List<MidiStream.MidiTrack> tracks = con.currentlyPlaying.getTracks();
                    for (int i = 0; i < tracks.size(); i++) {
                        MidiStream.MidiTrack track = tracks.get(i);
                        TrackEntry entry = new TrackEntry(i, track.name, track.totalNotes, con.assignedMap.getOrDefault(i, 0));
                        trackList.add(i == trackID ? activeTrack = entry : entry);
                    }
                }
            }
        }

        this.trackList = new GuiSelectList(this.width / 2 + 10, 50, this.width / 2 - 30, 20, 5, () -> trackList);
        this.trackList.setActive(activeTrack);

        this.channelField = new GuiTextField(0, mc.fontRenderer, this.width / 2 + 10, 21, this.width / 2 - 30, 18);
        this.channelField.setValidator(s -> (s != null && s.isEmpty()) || StringUtils.isNumeric(s));
        this.channelField.setText(String.valueOf(this.channelSupplier.getAsInt()));
        this.channelField.setGuiResponder(new GuiPageButtonList.GuiResponder() {
            @Override
            public void setEntryValue(int id, boolean value) {
            }

            @Override
            public void setEntryValue(int id, float value) {
            }

            @Override
            public void setEntryValue(int id, String value) {
                if(!value.isEmpty()) {
                    SkeletalBand.NETWORK.sendToServer(new C6SkeletonChangeChannel(GuiMusician.this.entityID, Integer.valueOf(value)));
                }
            }
        });

        super.initGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.musicianTypes.render(mouseX, mouseY);
        this.trackList.render(mouseX, mouseY);
        this.channelField.drawTextBox();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.musicianTypes.mouseClicked(mouseX, mouseY, mouseButton);
        this.trackList.mouseClicked(mouseX, mouseY, mouseButton);
        this.channelField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        this.musicianTypes.handleMouseInput();
        this.trackList.handleMouseInput();
    }

    @Override
    public void handleKeyboardInput() throws IOException {
        super.handleKeyboardInput();
        this.musicianTypes.handleKeyboardInput();
        this.trackList.handleKeyboardInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        this.channelField.textboxKeyTyped(typedChar, keyCode);
    }

    private class MusicianTypeEntry implements GuiSelectList.SelectListEntry {

        private final MusicianTypeFactory entry;

        private MusicianTypeEntry(MusicianTypeFactory entry) {
            this.entry = entry;
        }

        @Override
        public void draw(int x, int y) {
            mc.fontRenderer.drawString(this.getSearch(), x + 21, y + 6, -1);
        }

        @Override
        public String getSearch() {
            return Objects.requireNonNull(entry.getRegistryName()).toString();
        }

        @Override
        public void onClicked(int relMouseX, int relMouseY) {
            SkeletalBand.NETWORK.sendToServer(new C4SkeletonChangeType(GuiMusician.this.entityID, this.entry));
        }
    }

    private class TrackEntry implements GuiSelectList.SelectListEntry {

        private final int id;
        private final int assigned;
        private final String name;

        private TrackEntry(int id, String name, int totalNotes, int assigned) {
            this.id = id;
            this.name = (name.isEmpty() ? "Unknown Track" :  name) + " (" + totalNotes + " Notes)";
            this.assigned = assigned;
        }

        @Override
        public void draw(int x, int y) {
            mc.fontRenderer.drawString(this.getSearch(), x + 21, y + 6, -1);
            mc.fontRenderer.drawString(String.valueOf(this.assigned), x + GuiMusician.this.width / 2 - 40 - mc.fontRenderer.getStringWidth(String.valueOf(this.assigned)), y + 6, -1);
        }

        @Override
        public String getSearch() {
            return this.name;
        }

        @Override
        public void onClicked(int relMouseX, int relMouseY) {
            SkeletalBand.NETWORK.sendToServer(new C8SkeletonChangeTrack(GuiMusician.this.entityID, this.id));
            for (Entity entity : Minecraft.getMinecraft().world.loadedEntityList) {
                if(entity instanceof MusicalSkeleton) {
                    MusicianType type = ((MusicalSkeleton)entity).musicianType;
                    if(type instanceof ConductorType) {
                        ((ConductorType) type).assignedMap.put(this.id, ((ConductorType) type).assignedMap.getOrDefault(this.id, 0) + 1);
                    }
                }
            }
        }
    }
}