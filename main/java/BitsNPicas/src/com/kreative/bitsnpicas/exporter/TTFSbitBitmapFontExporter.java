package com.kreative.bitsnpicas.exporter;

import com.kreative.bitsnpicas.*;
import com.kreative.bitsnpicas.Font;
import com.kreative.bitsnpicas.truetype.*;
import org.w3c.dom.css.Rect;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.*;

public class TTFSbitBitmapFontExporter implements BitmapFontExporter {
	private int xsize, ysize;
	
	public TTFSbitBitmapFontExporter() {
		this.xsize = 100;
		this.ysize = 100;
	}
	
	public TTFSbitBitmapFontExporter(int size) {
		this.xsize = size;
		this.ysize = size;
	}
	
	public TTFSbitBitmapFontExporter(int xsize, int ysize) {
		this.xsize = xsize;
		this.ysize = ysize;
	}
	
	public byte[] exportFontToBytes(BitmapFont font) throws IOException {
		return createTrueTypeTables(font, xsize, ysize).compile();
	}
	
	public void exportFontToFile(BitmapFont font, File file) throws IOException {
		FileOutputStream fos = new FileOutputStream(file);
		fos.write(createTrueTypeTables(font, xsize, ysize).compile());
		fos.close();
	}
	
	public void exportFontToStream(BitmapFont font, OutputStream os) throws IOException {
		os.write(createTrueTypeTables(font, xsize, ysize).compile());
	}
	
	private static final class ThingsToKeepTrackOf {
		private int numGlyphs = 0;
		private int maxPoints = 0;
		private int maxContours = 0;
		private boolean maxBoundingBox = false;
		private int bbx1 = 0;
		private int bby1 = 0;
		private int bbx2 = 0;
		private int bby2 = 0;
		private int minLSB = 0;
		private int minRSB = 0;
		private int maxAdvance = 0;
		private int maxExtent = 0;
		private int averageWidth = 0;
		private int numAverages = 0;
		private int xHeight = 0;
		private int HHeight = 0;
		private boolean highUnicode = false;
		private Map<Integer,Integer> charToGlyfMap = new HashMap<Integer,Integer>();
		private int currentLocation = 0;
	}
	
	private static final TrueTypeFile createTrueTypeTables(BitmapFont bf, int xsize, int ysize) throws IOException {
		ThingsToKeepTrackOf a = new ThingsToKeepTrackOf();
		bf.autoFillNames();
		
		GlyfTable glyfTable = new GlyfTable();
		LocaTable locaTable = new LocaTable();
		HmtxTable hmtxTable = new HmtxTable();
		PostTable postTable = new PostTable();
		
		EbdtTable ebdtTable = new EbdtTable(SbitTableType.OPENTYPE);
		EblcTable eblcTable = new EblcTable(SbitTableType.OPENTYPE);
		EblcBitmapSize strike = new EblcBitmapSize();
		eblcTable.add(strike);
		
		
		
		if (bf.isItalicStyle()) postTable.italicAngle = PostTable.ITALIC_ANGLE_ISOMETRIC;
		postTable.underlinePosition = -ysize;
		postTable.underlineThickness = ysize;
		
		List<Integer> chars = new ArrayList<Integer>();
		Iterator<Integer> cpi = bf.codePointIterator();
		while (cpi.hasNext()) chars.add(cpi.next());
		Collections.sort(chars, new Comparator<Integer>() {
			@Override
			// Sort negative codepoints (unencoded glyphs) after positive ones and in reverse order.
			public int compare(Integer codepoint1, Integer codepoint2) {
				if (codepoint1 < 0 && codepoint2 >= 0) {
					return 1;
				} else if (codepoint2 < 0 && codepoint1 >= 0) {
					return -1;
				} else if (codepoint1 >= 0) {
					return codepoint1.compareTo(codepoint2);
				} else {
					// they must both be negative, flip the order
					return codepoint2.compareTo(codepoint1);
				}
			}
		});
		
		// Add single undefined glyph
        int undefined = -1;
		a.charToGlyfMap.put(undefined, a.numGlyphs);
		locaTable.add(a.currentLocation);
		hmtxTable.add(new HmtxTableEntry());
		postTable.add(PostTableEntry.forCharacter(undefined));
		a.numGlyphs++;
		
		for (int ch : chars) {
			makeCharacterSbit(bf, ch, a, xsize, ysize, ebdtTable, strike, hmtxTable, postTable);
		}
		
		EblcIndexSubtable1 subtable = (EblcIndexSubtable1) strike.get(strike.size() - 1);
		// add last padding entry
		subtable.add(ebdtTable.getNextKey());
		
		strike.startGlyphIndex = 1;
		strike.endGlyphIndex = a.numGlyphs-1;
		int ppem = bf.getEmAscent() + bf.getEmDescent();
		strike.ppemX = strike.ppemY = ppem;
		strike.bitDepth = 1;
		strike.flags = 1;

		strike.hori = new SbitLineMetrics();
		strike.vert = new SbitLineMetrics();
		
		strike.hori.ascender = bf.getEmAscent();
		strike.hori.descender = -bf.getLineDescent();
		
		// TODO:
		strike.hori.widthMax = 26;
		strike.hori.minOriginSB = -20;
		strike.hori.minAdvanceSB = -1;
		strike.hori.maxBeforeBL = 14;
		strike.hori.minAfterBL = -5;
		
		
		// ebdtTable.recalculate(eblcTable);

		TrueTypeFile ttf = new TrueTypeFile();
		ttf.add(makeHeadTable(bf, a, ysize));
		ttf.add(makeHheaTable(bf, a, ysize));
		ttf.add(makeMaxpTable(a));
		ttf.add(makeOs2Table(bf, a, xsize, ysize));
		ttf.add(hmtxTable);
		ttf.add(makeCmapTable(bf, a));
		ttf.add(locaTable);
		ttf.add(glyfTable);
		ttf.add(makeNameTable(bf));
		ttf.add(postTable);
		ttf.add(ebdtTable);
		ttf.add(eblcTable);
		return ttf;
	}
	
	private static final void makeCharacterSbit(
		BitmapFont bf, int ch, ThingsToKeepTrackOf a, int xsize, int ysize,
		EbdtTable ebdtTable, EblcBitmapSize strike, HmtxTable hmtxTable, PostTable postTable
	) {
		BitmapFontGlyph bg = bf.getCharacter(ch);
		Rectangle r = new Rectangle(
				bg.getGlyphOffset() * xsize,
				-bg.getGlyphDescent() * ysize,
				bg.getGlyphWidth() * xsize, 
				bg.getGlyphHeight() * ysize
		);
		int advance = bf.getCharacter(ch).getCharacterWidth() * xsize;
		if (!a.maxBoundingBox) {
			a.maxBoundingBox = true;
			a.bbx1 = r.x; a.bby1 = r.y; a.bbx2 = r.x+r.width; a.bby2 = r.y+r.height;
			a.minLSB = r.x; a.minRSB = advance-r.x-r.width; a.maxAdvance = advance;
			a.maxExtent = r.x+r.width;
		} else {
			if (r.x < a.bbx1) a.bbx1 = r.x;
			if (r.y < a.bby1) a.bby1 = r.y;
			if (r.x+r.width > a.bbx2) a.bbx2 = r.x+r.width;
			if (r.y+r.height > a.bby2) a.bby2 = r.y+r.height;
			if (r.x < a.minLSB) a.minLSB = r.x;
			if (advance-r.x-r.width < a.minRSB) a.minRSB = advance-r.x-r.width;
			if (advance > a.maxAdvance) a.maxAdvance = advance;
			if (r.x+r.width > a.maxExtent) a.maxExtent = r.x+r.width;
		}
		if (advance > 0) { a.averageWidth += advance; a.numAverages++; }
		if (ch == 'x') a.xHeight = r.y+r.height;
		if (ch == 'H') a.HHeight = r.y+r.height;
		if (ch >= 0x10000) a.highUnicode = true;
		a.charToGlyfMap.put(ch, a.numGlyphs);
		hmtxTable.add(new HmtxTableEntry(advance, r.x));
		
		EbdtEntryFormat1 entry = new EbdtEntryFormat1();
		int bytesPerRow = (int) Math.ceil(bg.getGlyphWidth2D() / 8);
		int totalBytes = bytesPerRow * bg.getGlyphHeight();
		byte[] data = new byte[totalBytes];
		int i = 0;
		for (byte[] row : bg.getGlyph()) {
			for (int col = 0; col < row.length; col += 8) {
				byte outByte = 0;
				for (int c = 0; c < 8; c++) {
					outByte <<= 1;
					if (col+c < row.length && row[col+c] < 0) { // NOTE: < 0 comparison is for signed byte
						outByte |= 1;
					}
				}
				data[i++] = outByte;
			}
		}
		entry.imageData = data;
		
		SbitSmallGlyphMetrics metrics = new SbitSmallGlyphMetrics();
		entry.smallMetrics = metrics;
		metrics.height = bg.getGlyphHeight();
		metrics.width = bg.getGlyphWidth();
		metrics.advance = bg.getCharacterWidth();
		metrics.bearingX = bg.getGlyphOffset();
		metrics.bearingY = bg.getGlyphAscent();
		
		
		EblcIndexSubtable1 subtable;
		if (strike.size() == 0) {
			subtable = new EblcIndexSubtable1();
			subtable.header = new EblcIndexSubtableHeader();
			subtable.header.firstGlyphIndex = a.numGlyphs;
			subtable.header.lastGlyphIndex = a.numGlyphs;
			subtable.header.indexFormat = 1;
			subtable.header.imageFormat = entry.format();
			strike.add(subtable);
		} else {
			subtable = (EblcIndexSubtable1) strike.get(strike.size() - 1);
			subtable.header.lastGlyphIndex = a.numGlyphs;
		}
		
		int entryOffset = ebdtTable.getNextKey();
//		if (entryOffset % 4 != 0) {
//			entryOffset += (4 - entryOffset % 4);
//		}
		subtable.add(entryOffset);
		ebdtTable.put(entryOffset, entry);

		// here
		if (ch < 0) {
			postTable.add(PostTableEntry.forCharacterName(bf.getUnencodedName(ch)));
		} else {
			postTable.add(PostTableEntry.forCharacter(ch));
		}
		a.numGlyphs++;
	}
	
	private static final CmapTable makeCmapTable(BitmapFont bf, ThingsToKeepTrackOf a) {
		List<Integer> chars = new ArrayList<Integer>();
		Iterator<Integer> cpi = bf.codePointIterator();
		while (cpi.hasNext()) chars.add(cpi.next());
		Collections.sort(chars);
		CmapSubtableFormat4 lowTable = new CmapSubtableFormat4();
		CmapSubtableFormat12 highTable = new CmapSubtableFormat12();
		CmapSubtableSequentialEntry currentGroup = null;
		int lastGlyphIndex = 0;
		for (int ch : chars) {
			// Skip unencoded chars
			if (ch < 0) {
				continue;
			}
			
			int gidx = a.charToGlyfMap.get(ch);
			if (currentGroup == null) {
				currentGroup = new CmapSubtableSequentialEntry();
				currentGroup.startCharCode = ch;
				currentGroup.endCharCode = ch;
				currentGroup.glyphIndex = gidx;
				lastGlyphIndex = gidx;
			} else if ((ch == currentGroup.endCharCode + 1) && (gidx == lastGlyphIndex + 1)) {
				currentGroup.endCharCode = ch;
				lastGlyphIndex = gidx;
			} else {
				if (currentGroup.startCharCode < 0x10000) lowTable.add(currentGroup);
				highTable.add(currentGroup);
				currentGroup = new CmapSubtableSequentialEntry();
				currentGroup.startCharCode = ch;
				currentGroup.endCharCode = ch;
				currentGroup.glyphIndex = gidx;
				lastGlyphIndex = gidx;
			}
		}
		if (currentGroup != null) {
			if (currentGroup.startCharCode < 0x10000) lowTable.add(currentGroup);
			highTable.add(currentGroup);
		}
		currentGroup = new CmapSubtableSequentialEntry();
		currentGroup.startCharCode = 0xFFFF;
		currentGroup.endCharCode = 0xFFFF;
		currentGroup.glyphIndex = 0;
		lowTable.add(currentGroup);
		CmapTable cmapTable = new CmapTable();
		cmapTable.subtables.add(lowTable);
		if (a.highUnicode) cmapTable.subtables.add(highTable);
		cmapTable.entries.add(CmapTableEntry.forUnicode(a.highUnicode ? highTable : lowTable));
		cmapTable.entries.add(CmapTableEntry.forWindowsUnicode16(lowTable));
		if (a.highUnicode) cmapTable.entries.add(CmapTableEntry.forWindowsUnicode32(highTable));
		return cmapTable;
	}
	
	private static final HeadTable makeHeadTable(BitmapFont bf, ThingsToKeepTrackOf a, int ysize) {
		Calendar now = new GregorianCalendar();
		double fontVersion;
		try {
			String s = bf.getName(Font.NAME_VERSION);
			if (s == null) fontVersion = 1.0;
			else if (s.startsWith("Version ")) fontVersion = Double.parseDouble(s.substring(8));
			else fontVersion = Double.parseDouble(s);
		} catch (NumberFormatException nfe) {
			fontVersion = 1.0;
		}
		HeadTable headTable = new HeadTable();
		headTable.setFontRevisionDouble(fontVersion);
		headTable.flags = HeadTable.FLAGS_Y_VALUE_OF_ZERO_SPECIFIES_BASELINE | HeadTable.FLAGS_MINIMUM_X_VALUE_IS_LEFT_SIDE_BEARING;
		headTable.unitsPerEm = (bf.getEmAscent() + bf.getEmDescent()) * ysize;
		headTable.setDateCreatedCalendar(now);
		headTable.setDateModifiedCalendar(now);
		headTable.xMin = a.bbx1;
		headTable.yMin = a.bby1;
		headTable.xMax = a.bbx2;
		headTable.yMax = a.bby2;
		headTable.macStyle = bf.getMacStyle();
		// here? fontforge has a different value
		headTable.lowestRecPPEM = bf.getEmAscent() + bf.getEmDescent();
		headTable.fontDirectionHint = HeadTable.FONT_DIRECTION_HINT_LTR_WITH_NEUTRAL;
		headTable.indexToLocFormat = HeadTable.INDEX_TO_LOC_FORMAT_LONG;
		return headTable;
	}
	
	private static final HheaTable makeHheaTable(BitmapFont bf, ThingsToKeepTrackOf a, int ysize) {
		HheaTable hheaTable = new HheaTable();
		hheaTable.ascent = bf.getLineAscent() * ysize;
		hheaTable.descent = -bf.getLineDescent() * ysize;
		hheaTable.lineGap = bf.getLineGap() * ysize;
		hheaTable.advanceWidthMax = a.maxAdvance;
		hheaTable.minLeftSideBearing = a.minLSB;
		hheaTable.minRightSideBearing = a.minRSB;
		hheaTable.xMaxExtent = a.maxExtent;
		if (bf.isItalicStyle()) {
			hheaTable.caretSlopeRise = 2;
			hheaTable.caretSlopeRun = 1;
		}
		hheaTable.numLongHorMetrics = a.numGlyphs;
		return hheaTable;
	}
	
	private static final MaxpTable makeMaxpTable(ThingsToKeepTrackOf a) {
		MaxpTable maxpTable = new MaxpTable();
		maxpTable.numGlyphs = a.numGlyphs;
		maxpTable.maxPoints = a.maxPoints;
		maxpTable.maxContours = a.maxContours;
		return maxpTable;
	}
	
	private static final Os2Table makeOs2Table(BitmapFont bf, ThingsToKeepTrackOf a, int xsize, int ysize) {
		List<Integer> chars = new ArrayList<Integer>();
		Iterator<Integer> cpi = bf.codePointIterator();
		while (cpi.hasNext()) chars.add(cpi.next());
		Os2Table os2Table = new Os2Table();
		os2Table.averageCharWidth = a.averageWidth / a.numAverages;
		os2Table.weightClass = bf.isBoldStyle() ? Os2Table.WEIGHT_CLASS_BOLD : Os2Table.WEIGHT_CLASS_MEDIUM;
		os2Table.widthClass = bf.isCondensedStyle() ? Os2Table.WIDTH_CLASS_CONDENSED : bf.isExtendedStyle() ? Os2Table.WIDTH_CLASS_EXPANDED : Os2Table.WIDTH_CLASS_MEDIUM;
		os2Table.subscriptXSize = (bf.getEmAscent() + bf.getEmDescent()) * xsize;
		os2Table.subscriptYSize = (bf.getEmAscent() + bf.getEmDescent()) * ysize;
		os2Table.subscriptXOffset = 0;
		os2Table.subscriptYOffset = (bf.getEmAscent() + bf.getEmDescent()) * ysize / 2;
		os2Table.superscriptXSize = (bf.getEmAscent() + bf.getEmDescent()) * xsize;
		os2Table.superscriptYSize = (bf.getEmAscent() + bf.getEmDescent()) * ysize;
		os2Table.superscriptXOffset = 0;
		os2Table.superscriptYOffset = (bf.getEmAscent() + bf.getEmDescent()) * ysize / 2;
		os2Table.strikeoutWidth = ysize;
		os2Table.strikeoutPosition = bf.getEmAscent() * ysize / 2;
		os2Table.setUnicodeRanges(chars);
		os2Table.setVendorIDString("KBnP");
		// here: use typo metrics?
		os2Table.fsSelection = (bf.isItalicStyle() ? 1 : 0) | (bf.isBoldStyle() ? 32 : 0);
		os2Table.setCharIndices(chars);
		os2Table.typoAscent = bf.getLineAscent() * ysize;
		os2Table.typoDescent = -bf.getLineDescent() * ysize;
		os2Table.typoLineGap = bf.getLineGap() * ysize;
		os2Table.winAscent = bf.getLineAscent() * ysize;
		os2Table.winDescent = bf.getLineDescent() * ysize;
		os2Table.setCodePages(chars);
		os2Table.xHeight = a.xHeight;
		os2Table.capHeight = a.HHeight;
		return os2Table;
	}
	
	private static final NameTable makeNameTable(BitmapFont bf) {
		List<Integer> nameTypes = new ArrayList<Integer>();
		for (int i : bf.nameTypes()) nameTypes.add(i);
		Collections.sort(nameTypes);
		NameTable nameTable = new NameTable();
		for (int nameID : nameTypes) nameTable.add(NameTableEntry.forUnicode(nameID, bf.getName(nameID)));
		for (int nameID : nameTypes) nameTable.add(NameTableEntry.forMacintosh(nameID, bf.getName(nameID)));
		for (int nameID : nameTypes) nameTable.add(NameTableEntry.forWindows(nameID, bf.getName(nameID)));
		return nameTable;
	}
}
