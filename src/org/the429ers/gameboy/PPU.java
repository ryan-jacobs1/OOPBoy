package org.the429ers.gameboy;

import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;


public class PPU implements Serializable, IPPU {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2887802651514454071L;
	private TileSet tileset1;
	private TileSet tileset2;
	private TileMap map;
	private TileMap window;
	private MMU mem;
	private int currentX;
	private int currentY;
	private transient BufferedImage frame;
	private GameBoyScreen gbs;
	private int scrollX;
	private int scrollY;
	private int cycleCount;
	private boolean drewFrame;
	private Map<Integer, ISprite> sprites;
	private Pallette background;
	private Pallette obp0;
	private Pallette obp1;
	private boolean spritesEnabled;
	private boolean windowEnabled;
	private int windowX;
	private int windowY;
	private int LYCompare = -1;
	private boolean largeSpriteMode;
	private TileSetManager tileSetManager;
	private boolean vBlank;
	private boolean hBlank;
	
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
	
	int framesDrawn = 0;
	
	
	public PPU(MMU mem, GameBoyScreen gbs) {
	    mem.setPPU(this);
		this.mem = mem;
		frame = new BufferedImage(160, 144, BufferedImage.TYPE_3BYTE_BGR);
		currentX = 0;
		currentY = 0;
		this.gbs = gbs;
		sprites = new HashMap<Integer, ISprite>();
	}
	
	public PPU() {
		frame = new BufferedImage(160, 144, BufferedImage.TYPE_3BYTE_BGR);
	}
	
	public boolean drewFrame() {
		return drewFrame;
	}
	
	public void loadTileSets() {
		tileset1 = tileSetManager.getTileSet(0, 0);
		tileset2 = tileSetManager.getTileSet(0, 1);
	}
	
	
	public void loadMap(boolean useTileSet0, boolean useMap1) {
		int ts = useTileSet0 ? 0 : 1;
		int address = useMap1 ? 0x9800 : 0x9c00;
		map = new TileMap(mem, address, ts, tileSetManager);
	}
	
	public void setTileSetManager(TileSetManager manager) {
		this.tileSetManager = manager;
	}
	
	public void loadWindow(boolean useTileSet0, boolean useMap1) {
		int ts = useTileSet0 ? 0 : 1;
		int address = useMap1 ? 0x9800 : 0x9c00;
		window = new TileMap(mem, address, ts, tileSetManager);
	}
	
	public void loadPallettes() {
		background = new Pallette(mem.readByte(0xFF47));
		obp0 = new Pallette(mem.readByte(0xFF48));
		obp1 = new Pallette(mem.readByte(0xFF49));
	}
	
	public void setLYCompare(int lyCompare){
	    this.LYCompare = lyCompare;
    }
	
	public void setMMU(MMU mmu) {
		this.mem = mmu;
	}
	
	public void setGBS(GameBoyScreen gbs) {
		this.gbs = gbs;
	}
	
	public void toggleHBlankIndicator() {
		hBlank = false;
	}
	
	public void tick() {
		// Lie to the CPU and pretend we're transfering pixels to the LCD
		if (cycleCount >= PIXEL_TRANSFER_START && cycleCount <= PIXEL_TRANSFER_END) {
					
		}
		/*
		else if (cycleCount == 0) {
			//System.out.println("executing this thing");
			
		}
		
		
		*/
		scrollX = mem.readByte(0xFF43);
		int lcdc = mem.readByte(0xff40);
		spritesEnabled = BitOps.extract(lcdc, 1, 1) == 1;
		
		//spritesEnabled = true;
		if (cycleCount == OAM_SEARCH_START) {
			hBlank = false;
			scrollY = mem.readByte(0xFF42);
			if (currentY < ACTUAL_LINES) {
				int status = mem.readByte(0xFF41) & 0x3F;
				mem.writeByte(0xFF41, status | 0x80);
			}
			mem.writeByte(0xFF44, currentY);
			//int lcdc = mem.readByte(0xff40);
			boolean useTileSet0 = BitOps.extract(lcdc, 4, 4) == 1;
			boolean useWindowTileMap0 = BitOps.extract(lcdc, 6, 6) == 0;
			if (BitOps.extract(lcdc, 2, 2) == 1) {
				//System.out.println("want to be in 8x16 mode");
				largeSpriteMode = true;
			}
			else {
				largeSpriteMode = false;
			}
			boolean useBackgroundMap0 = BitOps.extract(lcdc, 3, 3) == 0;
			this.loadMap(useTileSet0, useBackgroundMap0);
			this.loadTileSets();
			if (currentY == 0) {
				vBlank = false;
				this.loadPallettes();
			}
			//spritesEnabled = BitOps.extract(lcdc, 1, 1) == 1;
			//if (spritesEnabled) {
				loadSprites();
			//}
			windowEnabled = BitOps.extract(lcdc, 5, 5) == 1;
			loadWindow(useTileSet0, useWindowTileMap0);
			windowX = mem.readByte(0xff4b) - 7;
			windowY = mem.readByte(0xff4a);
			currentX = 0;
			scrollX = mem.readByte(0xFF43);
		}
		if (cycleCount == PIXEL_TRANSFER_START) {
			int status = mem.readByte(0xFF41) & 0x3F;
			mem.writeByte(0xFF41, status | 0xC0);
		}
		
		
		
		// Actually transfer pixels
		if (cycleCount >= PIXEL_TRANSFER_START && cycleCount < PIXEL_TRANSFER_START + 160 && currentY < ACTUAL_LINES) {
			int yPos = currentY + scrollY;
			int xPos = scrollX + currentX;
			Tile currentTile;
			Pallette currentPallette;
			int pixel;
			Tile backgroundTile = map.getTile(yPos / 8, xPos / 8);
			if (windowEnabled && currentX >= windowX && currentY >= windowY) {
				Tile windowTile = window.getTile((currentY - windowY) / 8, (currentX - windowX) / 8);
				int windowPixel = windowTile.getPixel((currentY - windowY)  % 8, (currentX - windowX) % 8);
				if (spritesEnabled && sprites.containsKey(currentX + 8)) {
					ISprite currentSprite = sprites.get(currentX + 8);
					int spritePixel = currentSprite.getPixel(currentY - (currentSprite.getSpriteY() - 16), currentX - (currentSprite.getSpriteX() - 8));
					if ((currentSprite.getPriority() == 0 || windowPixel == 0) && spritePixel != 0) {
						//currentTile = currentSprite.getTile();
						currentPallette = currentSprite.usePalletteZero() ? obp0 : obp1;
						pixel = spritePixel;
					}
					else {
						currentTile = windowTile;
						currentPallette = background;
						pixel = windowPixel;
					}
				}
				else {
					currentTile = windowTile;
					currentPallette = background;
					pixel = windowPixel;
				}
			}
			else if (spritesEnabled && sprites.containsKey(currentX + 8)) {
				ISprite currentSprite = sprites.get(currentX + 8);
				int spritePixel = currentSprite.getPixel(currentY - (currentSprite.getSpriteY() - 16), currentX - (currentSprite.getSpriteX() - 8));
				if ((currentSprite.getPriority() == 0 || backgroundTile.getPixel(yPos % 8, xPos % 8) == 0) && spritePixel != 0) {
					//currentTile = currentSprite.getTile();
					currentPallette = currentSprite.usePalletteZero() ? obp0 : obp1;
					pixel = spritePixel;
				}
				else {
					currentTile = backgroundTile;
					currentPallette = background;
					pixel = currentTile.getPixel(yPos % 8, xPos % 8);
				}
			}
			else {
				currentTile = backgroundTile;
				currentPallette = background;
				pixel = currentTile.getPixel(yPos % 8, xPos % 8);
			}
			if (frame == null) {
				frame = new BufferedImage(160, 144, BufferedImage.TYPE_3BYTE_BGR);
			}
			frame.setRGB(currentX, currentY, currentPallette.getColor(pixel).getRGB());
			currentX++;
		}
		// H-Blank Interrupt
		if (cycleCount == H_BLANK_START && currentY < ACTUAL_LINES) {
			if (!vBlank) {
				hBlank = true;
			}
			int status = mem.readByte(0xFF41) & 0x3F;
			mem.writeByte(0xFF41, status | 0xC0);
		}
		
		
		// Increment currentY
		if (cycleCount == H_BLANK_END) {
			currentY++;
			if (currentY == 154) {
				currentY = 0;
			}
		}
		
		drewFrame = false;
		// Send V-Blank interrupt
		if (currentY == 145 && cycleCount == 0) {
			vBlank = true;
			drawFrame();
		}
		
		//send LCDC interrupt
        if (currentY == LYCompare){
            int interruptRegister = mem.readByte(0xFF0F) & 0xFE;
            mem.writeByte(0xFF0F, interruptRegister | 0x02);
        }
		
		cycleCount++;
		cycleCount %= LINE_LENGTH;
	}
	
	
	private void drawFrame() {
		int status = mem.readByte(0xFF41) & 0x3F;
		mem.writeByte(0xFF41, status | 0x40);
		gbs.drawFrame(frame);
		drewFrame = true;
		int interruptRegister = mem.readByte(0xFF0F) & 0xFE;
		mem.writeByte(0xFF0F, interruptRegister | 0x01);
		//mem.writeByte(0xFF85, 0xFF);
		//mem.writeByte(0xFF44, 0x90);
	}
	
	
	public void loadSprites() {
		sprites.clear();
		int spriteCount = 0;
		int spritesFound = 0;
		int memAddress = 0xFE00;
		while (spriteCount < 40 && spritesFound < 10) {
			ISprite s = null;
			if (largeSpriteMode) {
				s = new LargeSprite(mem, memAddress, tileset1);
			}
			else {
				s = new SmallSprite(mem, memAddress, tileset1);
			}
			if (s.inRange(currentY + 16)) {
				spritesFound++;
				for (int i = 0; i < 8; i++) {
					if (!sprites.containsKey(s.getSpriteX() + i)) {
						if (s.getPixel(currentY - (s.getSpriteY() - 16), i) != 0) {
							sprites.put(s.getSpriteX() + i, s);
						}
					}
					else {
						ISprite conflictSprite = sprites.get(s.getSpriteX() + i);
						boolean isTransparent = s.getPixel(currentY - (s.getSpriteY() - 16), i) == 0;
						if (s.getSpriteX() < conflictSprite.getSpriteX() && !isTransparent) {
							sprites.put(s.getSpriteX() + i, s);
						}
					}
				}
			}
			spriteCount++;
			memAddress += 4;
		}
	}
	
	@Override
	public boolean isHBlank() {
		return hBlank;
	}
	
}