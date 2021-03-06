package org.the429ers.gameboy;

import javax.sound.sampled.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.Random;

class SoundChip implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -3888000280667367472L;
    public SquareWave square1 = new SquareWave();
    public SquareWave square2 = new SquareWave();
    public WaveChannel waveChannel = new WaveChannel();
    public Noise noiseChannel = new Noise();
    
    public static final int SAMPLE_RATE = 131072;
    public static final int SAMPLES_PER_FRAME = SAMPLE_RATE/60;
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(SAMPLE_RATE,  8, 2, false, false);
    
    public static final int SQUARE1 = 0;
    public static final int SQUARE2 = 1;
    public static final int WAVE = 2;
    public static final int NOISE = 3;
    
    private transient SourceDataLine sourceDL;
    
    byte[] masterBuffer = new byte[6 * SAMPLES_PER_FRAME];
    byte[] tempBuffer = new byte[3 * SAMPLES_PER_FRAME];
    
    long totalSamplesWritten = 0;
    
    boolean[] leftEnabled = new boolean[4];
    boolean[] rightEnabled = new boolean[4];
    {
        Arrays.fill(leftEnabled, true);
        Arrays.fill(rightEnabled, true);
    }
    
    public void setSourceDL(SourceDataLine sourceDL){
        this.sourceDL = sourceDL;
    }
    
    public SourceDataLine getSourceDL(){
        return this.sourceDL;
    }

    public SoundChip() {
        try {
            sourceDL = AudioSystem.getSourceDataLine(AUDIO_FORMAT);
            sourceDL.open(AUDIO_FORMAT);
            sourceDL.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }
    
    public SoundChip(SourceDataLine sourceDL) {
        this.sourceDL = sourceDL;
    }
    
    //handle the NR51 register
    public void handleStereo(int val) {
        for(int i = 0; i < 4; i++){
            leftEnabled[i] = ((val >> i) & 1) == 1;
        }
        for(int i = 0; i < 4; i++){
            rightEnabled[i] = ((val >> (4 + i)) & 1) == 1;
        }
    }

    public void tick() {
        if (sourceDL == null) {
            try {
                sourceDL = AudioSystem.getSourceDataLine(AUDIO_FORMAT);
                sourceDL.open(AUDIO_FORMAT);
                sourceDL.start();
            } catch (LineUnavailableException e) {
                e.printStackTrace();
            }
        }
        long residualSamples = totalSamplesWritten - sourceDL.getLongFramePosition();
        int samplesToWrite = Math.max(0, (int)(3 * SAMPLES_PER_FRAME - residualSamples)); //try to keep 3 frames buffered at all times
        samplesToWrite = Math.min(sourceDL.available() / 2, Math.min(3 * SAMPLES_PER_FRAME, samplesToWrite)); //never want to block here
        totalSamplesWritten += samplesToWrite;
        if(totalSamplesWritten < sourceDL.getLongFramePosition()) {
            sourceDL.drain();
            totalSamplesWritten = sourceDL.getLongFramePosition();
        }
        
        Arrays.fill(masterBuffer, (byte) 0);
        
        boolean channelEnabled = square1.tick(tempBuffer, samplesToWrite);
        
        if(channelEnabled) {
            if(leftEnabled[SQUARE1]) {
                for (int i = 0; i < samplesToWrite; i++) {
                    masterBuffer[2*i] += (tempBuffer[i]);
                }
            }
            if(rightEnabled[SQUARE1]){
                for (int i = 0; i < samplesToWrite; i++) {
                    masterBuffer[2*i + 1] += (tempBuffer[i]);
                }
            }
        }
        
        channelEnabled = square2.tick(tempBuffer, samplesToWrite);
        
        if(channelEnabled) {
            if(leftEnabled[SQUARE2]) {
                for (int i = 0; i < samplesToWrite; i++) {
                    masterBuffer[2*i] += (tempBuffer[i]);
                }
            }
            if(rightEnabled[SQUARE2]){
                for (int i = 0; i < samplesToWrite; i++) {
                    masterBuffer[2*i + 1] += (tempBuffer[i]);
                }
            }
        }

        channelEnabled = waveChannel.tick(tempBuffer, samplesToWrite);

        if(channelEnabled) {
            if(leftEnabled[WAVE]) {
                for (int i = 0; i < samplesToWrite; i++) {
                    masterBuffer[2*i] += (tempBuffer[i]);
                }
            }
            if(rightEnabled[WAVE]){
                for (int i = 0; i < samplesToWrite; i++) {
                    masterBuffer[2*i + 1] += (tempBuffer[i]);
                }
            }
        }

        channelEnabled = noiseChannel.tick(tempBuffer, samplesToWrite);

        if(channelEnabled) {
            if(leftEnabled[NOISE]) {
                for (int i = 0; i < samplesToWrite; i++) {
                    masterBuffer[2*i] += (tempBuffer[i]);
                }
            }
            if(rightEnabled[NOISE]){
                for (int i = 0; i < samplesToWrite; i++) {
                    masterBuffer[2*i + 1] += (tempBuffer[i]);
                }
            }
        }
        
        sourceDL.write(masterBuffer, 0, samplesToWrite * 2);
    }
}

public interface SoundChannel {
    void handleByte(int location, int toWrite);
    boolean tick(byte[] soundBuffer, int samplesToWrite);
}

class SquareWave implements SoundChannel, Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 7107235725378560961L;
    protected int duty = 0;
    protected int lengthLoad = 0;
    protected int startingVolume = 0;
    protected boolean envelopeAdd = false;
    protected int envelopePeriod = 0;
    protected int frequency = 0;
    protected boolean playing = false;
    protected boolean lengthEnabled = false;
    protected int lengthCounter = 0;

    protected int currentVolume = 0;
    protected long ticks = 0;
    protected int offset = 0;

    private byte[] transition = new byte[SAMPLE_RATE];

    public static final int SAMPLE_RATE = SoundChip.SAMPLE_RATE;

    public static int getWaveform(int duty) {
        switch(duty){
            case 0:
                return 0b00100000;
            case 1:
                return 0b00110000;
            case 2:
                return 0b00111100;
            case 3:
                return 0b01111110;
            default:
                throw new IllegalArgumentException("duty must be in [0,4)");
        }
    }

    //tick is 30 hz
    public boolean tick(byte[] soundBuffer, int samplesToWrite) {
        if(!this.playing){
            return false;
        }
        
        ticks++;
        
        if (lengthEnabled) {
            lengthCounter-= 4;
            if (lengthCounter <= 0) {
                this.playing = false;
            }
        }

        if(envelopePeriod != 0 && ticks % envelopePeriod == 0) {
            this.currentVolume += (envelopeAdd? 1: -1);
            if(this.currentVolume < 0) this.currentVolume = 0;
            if(this.currentVolume > 15) this.currentVolume = 15;
        }

        int waveForm = getWaveform(this.duty);
        double chunkSize = (2048.0 - frequency) / 8;

        int waveLength = (int)(8 * chunkSize);

        if(waveLength == 0) return false;

        for(int i = 0; i < samplesToWrite; i++){
            int samplesFromStart = (i + offset) % waveLength;
            int loc = (int)(samplesFromStart / chunkSize);
            soundBuffer[i] = (((waveForm >> loc) & 1) == 1)? (byte)(currentVolume): (byte)0;
        }
        
        offset = (offset + samplesToWrite) % waveLength;
        
        return true;
    }

    @Override
    //location is 0, 1, 2, 3, 4
    public void handleByte(int location, int toWrite) {
        int newFrequency = frequency;
        
        switch(location){
            case 0:
                //do nothing
                break;
            case 1:
                this.duty = (toWrite >> 6) & 0x3;
                this.lengthLoad = 64 - (toWrite & 0x3f);
                break;
            case 2:
                this.startingVolume = (toWrite >> 4) & 0xf;
                this.currentVolume = this.startingVolume;
                this.envelopeAdd = ((toWrite >> 3) & 1) == 1;
                this.envelopePeriod = toWrite & 0x7;
                break;
            case 3:
                newFrequency = (newFrequency >> 8) << 8;
                newFrequency |= toWrite & 0xff;
                break;
            case 4:
                this.playing |= (toWrite >> 7) == 1;
                this.lengthEnabled = ((toWrite >> 6) & 1) == 1;
                newFrequency &= 0xff;
                newFrequency |= ((toWrite & 0x7) << 8);
        }
        
        if(newFrequency != frequency) {
            //if the frequency has changed, update the offset so it's (approximately) at the same spot in the wave
            double oldChunkSize = (2048.0 - frequency) / 8;
            double oldWaveLength = (8 * oldChunkSize);
            double newChunkSize = (2048.0 - newFrequency) / 8;
            double newWaveLength = (8 * newChunkSize);
            
            offset = (int)(offset * newWaveLength / oldWaveLength);
            
            frequency = newFrequency;
        }

        if(this.lengthEnabled){
            this.lengthCounter = this.lengthLoad;
            //System.out.println(this.lengthCounter);
        }
    }
}

class WaveChannel implements SoundChannel, Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 7638828751434539339L;
    protected boolean dacPower = false;
    protected int lengthLoad = 0;
    protected int volumeCode = 0;
    protected int frequency = 0;
    protected boolean playing = false;
    protected boolean lengthEnabled = false;
    protected int lengthCounter = 0;
    
    protected int offset;
    protected byte[] samples = new byte[32];

    public static final int SAMPLE_RATE = SoundChip.SAMPLE_RATE;
    
    public void handleWaveByte(int location, int toWrite) {
        if(location > 15 || location < 0){
            throw new IllegalArgumentException("only 16 wave bytes");
        }
        
        samples[2 * location] = (byte)(toWrite >> 4);
        samples[2 * location + 1] = (byte)(toWrite & 0xf);
    }

    @Override
    public void handleByte(int location, int toWrite) {
        int newFrequency = this.frequency;
        switch(location){
            case 0:
                this.dacPower = ((toWrite >> 7) & 1) == 1;
                break;
            case 1:
                this.lengthLoad = 256 - toWrite;
                break;
            case 2:
                this.volumeCode = (toWrite >> 5) & 3;
                break;
            case 3:
                newFrequency = (newFrequency >> 8) << 8;
                newFrequency |= toWrite & 0xff;
                break;
            case 4:
                this.playing |= (toWrite >> 7) == 1;
                this.lengthEnabled = ((toWrite >> 6) & 1) == 1;
                newFrequency &= 0xff;
                newFrequency |= ((toWrite & 0x7) << 8);
        }

        if(newFrequency != frequency) {
            //if the frequency has changed, update the offset so it's (approximately) at the same spot in the wave
            double oldChunkSize = (2048.0 - frequency) / 16;
            double oldWaveLength = (32 * oldChunkSize);
            double newChunkSize = (2048.0 - newFrequency) / 16;
            double newWaveLength = (32 * newChunkSize);

            offset = (int)(offset * newWaveLength / oldWaveLength);

            frequency = newFrequency;
        }
        if(this.lengthEnabled){
            this.lengthCounter = this.lengthLoad;
            //System.out.println(this.lengthCounter);
        }
    }
    
    private byte volumeAdjust(byte toAdjust) {
        switch(volumeCode) {
            case 0:
                return 0;
            case 1:
                return toAdjust;
            case 2:
                return (byte)(toAdjust / 2);
            case 3:
                return (byte)(toAdjust / 4);
        }
        throw new InvalidParameterException("invalid volume code");
    }

    @Override
    public boolean tick(byte[] soundBuffer, int samplesToWrite) {
        if(!this.playing || !this.dacPower){
            return false;
        }
        
        if (lengthEnabled) {
            lengthCounter-= 4;
            if (lengthCounter <= 0) {
                this.playing = false;
            }
        }
        
        double chunkSize = (2048.0 - frequency) / 16;

        int waveLength = (int)(32 * chunkSize);

        if(waveLength == 0) return false;

        //System.out.println(Arrays.toString(samples));
        for(int i = 0; i < samplesToWrite; i++){
            int samplesFromStart = (i + offset) % waveLength;
            int loc = (int)(samplesFromStart / chunkSize);
            soundBuffer[i] = volumeAdjust(samples[loc]);
        }
        
        offset = (offset + samplesToWrite) % waveLength;
        
        return true;
    }
}

class Noise implements SoundChannel, Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 4853112434355414007L;
    protected int lengthLoad = 0;
    protected int startingVolume = 0;
    protected boolean envelopeAdd = false;
    protected int envelopePeriod = 0;
    protected boolean playing = false;
    protected boolean lengthEnabled = false;
    protected int lengthCounter = 0;
    protected int shiftClock = 0;
    protected int widthMode = 0;
    protected int divisorCode = 0;
    
    protected int currentVolume = 0;
    protected long ticks = 0;
    Random rand = new Random();
    
    @Override
    public void handleByte(int location, int toWrite) {
        switch(location){
            case 0:
                //do nothing
                break;
            case 1:
                this.lengthLoad = 64 - (toWrite & 0x3f);
                break;
            case 2:
                this.startingVolume = (toWrite >> 4) & 0xf;
                this.currentVolume = this.startingVolume;
                this.envelopeAdd = ((toWrite >> 3) & 1) == 1;
                this.envelopePeriod = toWrite & 0x7;
                break;
            case 3:
                this.shiftClock = (toWrite >> 4) & 0xf;
                this.widthMode = (toWrite >> 3) & 1;
                this.divisorCode = toWrite & 0x7;
                break;
            case 4:
                this.playing |= (toWrite >> 7) == 1;
                this.lengthEnabled = ((toWrite >> 6) & 1) == 1;
        }

        if(this.lengthEnabled){
            this.lengthCounter = this.lengthLoad;
        }
    }

    @Override
    public boolean tick(byte[] soundBuffer, int samplesToWrite) {
        if(!this.playing){
            return false;
        }

        ticks++;

        if (lengthEnabled) {
            lengthCounter-= 4;
            if (lengthCounter <= 0) {
                this.playing = false;
            }
        }

        if(envelopePeriod != 0 && ticks % envelopePeriod == 0) {
            this.currentVolume += (envelopeAdd? 1: -1);
            if(this.currentVolume < 0) this.currentVolume = 0;
            if(this.currentVolume > 15) this.currentVolume = 15;
        }
        
        if(this.currentVolume == 0){
            return false;
        }

        long chunkSize;
        double frequency;
        if(divisorCode != 0) {
            frequency = ((524288.0 / divisorCode) / (2 << shiftClock));
        }else{
            frequency = ((524288.0 * 2) / (2 << shiftClock));
        }
        
        chunkSize = (long)(SoundChip.SAMPLE_RATE / frequency);
        if (chunkSize == 0) chunkSize = 1;

        int i, j = 0;
        for(i = 0; i < samplesToWrite - chunkSize; i+= chunkSize){
            byte chunkVal = (byte)(rand.nextInt(2) * currentVolume);
            for(j = 0; j < chunkSize; j++){
                soundBuffer[i + j] = chunkVal;
            }
        }
        
        //fill up remainder
        byte remainderVal = (byte)(rand.nextInt(2) * currentVolume);
        for(int k = i + j; k < samplesToWrite; k++){
            soundBuffer[k] = remainderVal;
        }

        return true;
    }
}