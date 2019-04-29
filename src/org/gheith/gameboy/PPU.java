package org.gheith.gameboy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;


public class PPU {
	private TileSet tileset1;
	private TileSet tileset2;
	private Map map;
	private ArrayList<Sprite> sprites;
	private MMU mem;
	private int currentX;
	private int currentY;
	private BufferedImage frame;
	private GameBoyScreen gbs;
	private int scrollX;
	private int scrollY;
	private int cycleCount;
	private boolean drewFrame;
	
	/*
	public static final int OAM_SEARCH_LENGTH = 20;
	public static final int OAM_SEARCH_START = 0;
	public static final int OAM_SEARCH_END = 19;
	public static final int PIXEL_TRANSFER_LENGTH = 43;
	public static final int PIXEL_TRANSFER_START = 20;
	public static final int PIXEL_TRANSFER_END = 62;
	public static final int H_BLANK_LENGTH = 51;
	public static final int H_BLANK_START = 63;
	public static final int H_BLANK_END = 113;
	public static final int V_BLANK = 10;
	public static final int ACTUAL_LINES = 144;
	public static final int V_BLANK_LINES = 10;
	public static final int LINE_LENGTH = 114;
	*/
	
	
	public static final int OAM_SEARCH_LENGTH = 80;
	public static final int OAM_SEARCH_START = 0;
	public static final int OAM_SEARCH_END = 79;
	public static final int PIXEL_TRANSFER_LENGTH = 172;
	public static final int PIXEL_TRANSFER_START = 80;
	public static final int PIXEL_TRANSFER_END = 251;
	public static final int H_BLANK_LENGTH = 204;
	public static final int H_BLANK_START = 252;
	public static final int H_BLANK_END = 455;
	public static final int V_BLANK = 10;
	public static final int ACTUAL_LINES = 144;
	public static final int V_BLANK_LINES = 10;
	public static final int LINE_LENGTH = 456;
	
	
	public PPU(MMU mem, GameBoyScreen gbs) {
		this.mem = mem;
		frame = new BufferedImage(160, 144, BufferedImage.TYPE_3BYTE_BGR);
		currentX = 0;
		currentY = 0;
		this.gbs = gbs;
	}
	
	public boolean drewFrame() {
		return drewFrame;
	}
	
	public void loadTileSets() {
		tileset1 = new TileSet(mem, 0x8000, 256, true);
		tileset2 = new TileSet(mem, 0x8800, 256, false);
	}
	
	
	public void loadMap(boolean useTileSet1) {
		TileSet ts = useTileSet1 ? tileset1 : tileset2;
		map = new Map(mem, 0x9800, ts);
	}
	
	public void tick() {
		if (cycleCount == 0) {
			//System.out.println("executing this thing");
			scrollY = mem.readByte(0xFF42);
		}
		if (cycleCount == OAM_SEARCH_START) {
			if (currentY < ACTUAL_LINES) {
				int status = mem.readByte(0xFF41) & 0x3F;
				mem.writeByte(0xFF41, status | 0x80);
			}
			mem.writeByte(0xFF44, currentY);
			this.loadTileSets();
			this.loadMap(true);
			currentX = 0;
			scrollX = mem.readByte(0xFF43);
		}
		if (cycleCount == PIXEL_TRANSFER_START) {
			int status = mem.readByte(0xFF41) & 0x3F;
			mem.writeByte(0xFF41, status | 0xC0);
		}
		// Lie to the CPU and pretend we're transfering pixels to the LCD
		if (cycleCount >= PIXEL_TRANSFER_START && cycleCount <= PIXEL_TRANSFER_END) {
			
		}
		// Actually transfer pixels
		if (cycleCount == PIXEL_TRANSFER_END && currentY < ACTUAL_LINES) {
			int yPos = currentY + scrollY;
			for (int i = 0; i < 160; i++) {
				int xPos = scrollX + i;
				Tile currentTile = map.getTile(yPos / 8, xPos / 8);
				int pixel = currentTile.getPixel(yPos % 8, xPos % 8);
				switch(pixel) {
				case 0:
					frame.setRGB(i, currentY, Color.WHITE.getRGB());
					break;
				case 1:
					frame.setRGB(i, currentY, Color.LIGHT_GRAY.getRGB());
					break;
				case 2:
					frame.setRGB(i, currentY, Color.DARK_GRAY.getRGB());
					break;
				case 3:
					frame.setRGB(i, currentY, Color.BLACK.getRGB());
					break;
				default:
					frame.setRGB(i, currentY, Color.BLACK.getRGB());
				}
			}
		}
		// H-Blank Interrupt
		if (cycleCount == H_BLANK_START && currentY < ACTUAL_LINES) {
			int status = mem.readByte(0xFF41) & 0x3F;
			mem.writeByte(0xFF41, status | 0xC0);
		}
		
		// Increment currentY
		if (cycleCount == H_BLANK_END) {
			currentY++;
			if (currentY == ACTUAL_LINES + V_BLANK_LINES) {
				currentY = 0;
			}
		}
		
		// Send V-Blank interrupt
		if (currentY == 145 && cycleCount == 0) {
			int status = mem.readByte(0xFF41) & 0x3F;
			mem.writeByte(0xFF41, status | 0x40);
			gbs.drawFrame(frame);
			drewFrame = true;
			int interruptRegister = mem.readByte(0xFF0F) & 0xFE;
			mem.writeByte(0xFF0F, interruptRegister | 0x01); 
			//mem.writeByte(0xFF85, 0xFF);
			//mem.writeByte(0xFF44, 0x90);
		}
		cycleCount++;
		cycleCount %= LINE_LENGTH;
	}
	
	
	public void tickOld() {
		//System.out.println("Current X " + currentX + "\n Current Y " + currentY);
		// Need to reset all values
		drewFrame = false;
		mem.writeByte(0xFF44, currentY);
		if (currentX == 0 && currentY == 0) {
			scrollY = mem.readByte(0xFF42);
			System.out.println("Scroll Y " + scrollY);
			mem.writeByte(0xF085, 0x00);
			loadTileSets();
			loadMap(true);
		}
		// Need to reset 
		if (currentX == 0) {
			scrollX = mem.readByte(0xFF43);
			int status = mem.readByte(0xFF41) & 0x3F;
			mem.writeByte(0xFF41, status | 0x80);
		}
		if (currentX == 80) {
			int status = mem.readByte(0xFF41) & 0x3F;
			mem.writeByte(0xFF41, status | 0xC0);
			
		}
		if (currentX >= 80 && currentX < 80 + 160 && currentY < 144) {
			
			int tileX = (scrollX + currentX - 80) / 8;
			int tileY = (scrollY + currentY) / 8;
			//Tile currentTile = map.getTile(tileX, tileY);
			//int pixel = currentTile.getPixel((currentX - 80 + scrollX) % 8, (scrollY + currentY) % 8);
			Tile currentTile = map.getTile(tileY, tileX);
			int pixel = currentTile.getPixel((scrollY + currentY) % 8, (currentX - 80 + scrollX) % 8);
			switch(pixel) {
			case 0:
				frame.setRGB(currentX - 80, currentY, Color.WHITE.getRGB());
				break;
			case 1:
				frame.setRGB(currentX - 80, currentY, Color.LIGHT_GRAY.getRGB());
				break;
			case 2:
				frame.setRGB(currentX - 80, currentY, Color.DARK_GRAY.getRGB());
				break;
			case 3:
				frame.setRGB(currentX - 80, currentY, Color.BLACK.getRGB());
				break;
			default:
				frame.setRGB(currentX - 80, currentY, Color.BLACK.getRGB());
			}
		}
		if (currentX == 80 + 172) {
			int status = mem.readByte(0xFF41) & 0x3F;
			mem.writeByte(0xFF41, status);
		}
		// Entered V Blank
		if (currentX == 0 && currentY == 146) {
			int status = mem.readByte(0xFF41) & 0x3F;
			mem.writeByte(0xFF41, status | 0x40);
			gbs.drawFrame(frame);
			drewFrame = true;
			int interruptRegister = mem.readByte(0xFF0F) & 0xFE;
			mem.writeByte(0xFF0F, interruptRegister | 0x01); 
			//mem.writeByte(0xFF85, 0xFF);
			//mem.writeByte(0xFF44, 0x90);
			System.out.println("drawing frame");

			
		}
		
		currentX++;
		if (currentX == 456) {
			currentX = 0;
			currentY++;
			//mem.writeByte(0xFF44, currentY);
		}
		if (currentY == 154) {
			currentY = 0;
		}	
	}
	
}
