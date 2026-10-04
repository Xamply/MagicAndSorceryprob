package com.cesar.magicandsorcery.client.audio;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ScaledSoundInstance implements SoundInstance {
    protected final SoundInstance delegate;
    protected final float volumeScale;

    public ScaledSoundInstance(SoundInstance delegate, float volumeScale) {
        this.delegate = delegate;
        this.volumeScale = volumeScale;
    }

    public static SoundInstance wrap(SoundInstance sound, float volumeScale) {
        if (sound instanceof TickableSoundInstance tickable) {
            return new ScaledTickable(tickable, volumeScale);
        }
        return new ScaledSoundInstance(sound, volumeScale);
    }

    @Override
    public float getVolume() {
        return delegate.getVolume() * volumeScale;
    }

    @Override
    public ResourceLocation getLocation() {
        return delegate.getLocation();
    }

    @Nullable
    @Override
    public WeighedSoundEvents resolve(SoundManager soundManager) {
        return delegate.resolve(soundManager);
    }

    @Override
    public Sound getSound() {
        return delegate.getSound();
    }

    @Override
    public SoundSource getSource() {
        return delegate.getSource();
    }

    @Override
    public boolean isLooping() {
        return delegate.isLooping();
    }

    @Override
    public boolean isRelative() {
        return delegate.isRelative();
    }

    @Override
    public int getDelay() {
        return delegate.getDelay();
    }

    @Override
    public float getPitch() {
        return delegate.getPitch();
    }

    @Override
    public double getX() {
        return delegate.getX();
    }

    @Override
    public double getY() {
        return delegate.getY();
    }

    @Override
    public double getZ() {
        return delegate.getZ();
    }

    @Override
    public Attenuation getAttenuation() {
        return delegate.getAttenuation();
    }

    @Override
    public boolean canStartSilent() {
        return delegate.canStartSilent();
    }

    @Override
    public boolean canPlaySound() {
        return delegate.canPlaySound();
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return delegate.getStream(soundBuffers, sound, looping);
    }

    public static class ScaledTickable extends ScaledSoundInstance implements TickableSoundInstance {
        private final TickableSoundInstance tickableDelegate;

        public ScaledTickable(TickableSoundInstance delegate, float volumeScale) {
            super(delegate, volumeScale);
            this.tickableDelegate = delegate;
        }

        @Override
        public boolean isStopped() {
            return tickableDelegate.isStopped();
        }

        @Override
        public void tick() {
            tickableDelegate.tick();
        }
    }
}
