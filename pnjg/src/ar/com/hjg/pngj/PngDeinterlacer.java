package ar.com.hjg.pngj;

import java.util.Random;

// you really dont' want to peek inside this
public class PngDeinterlacer {
	private final ImageInfo imi;
	private int pass; // 1-7
	private int rows, cols; 
	int dY, dX, oY, oX; // current step and offset (in pixles)
	int oXsamples, dXsamples; // step in samples

	// current row in the virtual subsampled image; this increments (by 1) from 0 to rows/dy 7 times
	private int currRowSubimg = -1;
	// in the real image, this will cycle from 0 to im.rows in different steps, 7 times
	private int currRowReal = -1;

	private final int packedValsPerPixel;
	private final int packedMask;
	private final int packedShift;

	PngDeinterlacer(ImageInfo iminfo) {
		this.imi = iminfo;
		pass = 0;
		if (imi.packed) {
			packedValsPerPixel = 8 / imi.bitDepth;
			packedShift = imi.bitDepth;
			if (imi.bitDepth == 1)
				packedMask = 0x80;
			else if (imi.bitDepth == 2)
				packedMask = 0xc0;
			else
				packedMask = 0xf0;
		} else {
			packedMask = packedShift = packedValsPerPixel = 1;// dont care
		}
		setPass(1);
		setRow(0);
	}

	/** this refers to the row currRowSubimg */
	void setRow(int n) {
		currRowSubimg = n;
		currRowReal = n * dY + oY;
		if (currRowReal < 0 || currRowReal >= imi.rows)
			throw new PngjExceptionInternal("bad row - this should not happen");
	}

	/** return false is no more rows  */
	boolean nextRow() {
		if (getCols() == 0|| currRowSubimg >= rows-1) {
			if (pass == 7)
				return false;
			setPass(pass + 1);
			if (getCols() == 0 || getRows()==0)
				return nextRow();
			setRow(0);
		} else {
			setRow(currRowSubimg + 1);
		}
		return true;
	}

	void setPass(int p) {
		if (this.pass == p)
			return;
		pass = p;
		switch (pass) {
		case 1:
			dY = dX = 8;
			oX = oY = 0;
			break;
		case 2:
			dY = dX = 8;
			oX = 4;
			oY = 0;
			break;
		case 3:
			dX = 4;
			dY = 8;
			oX = 0;
			oY = 4;
			break;
		case 4:
			dX = dY = 4;
			oX = 2;
			oY = 0;
			break;
		case 5:
			dX = 2;
			dY = 4;
			oX = 0;
			oY = 2;
			break;
		case 6:
			dX = dY = 2;
			oX = 1;
			oY = 0;
			break;
		case 7:
			dX = 1;
			dY = 2;
			oX = 0;
			oY = 1;
			break;
		default:
			throw new PngjExceptionInternal("bad interlace pass" + pass);
		}
		rows = (imi.rows - oY) / dY + 1;
		if ((rows - 1) * dY + oY >= imi.rows)
			rows--; // can be 0
		cols = (imi.cols - oX) / dX + 1;
		if ((cols - 1) * dX + oX >= imi.cols)
			cols--; // can be 0
		if (cols == 0)
			rows = 0; // really...
		dXsamples = dX * imi.channels;
		oXsamples = oX * imi.channels;
	}

	// notice that this is a "partial" deinterlace, it will be called several times for the same row!
	void deinterlaceInt(int[] src, int[] dst, boolean readInPackedFormat) {
		if (!(imi.packed && readInPackedFormat))
			for (int i = 0, j = oXsamples; i < cols * imi.channels; i += imi.channels, j += dXsamples)
				for (int k = 0; k < imi.channels; k++)
					dst[j + k] = src[i + k];
		else
			deinterlaceIntPacked(src, dst);
	}

	// interlaced+packed = monster; this is very clumsy!
	private void deinterlaceIntPacked(int[] src, int[] dst) {
		int spos, smod, smask; // source byte position, bits to shift to left (01,2,3,4
		int tpos, tmod, p, d;
		spos = 0;
		smask = packedMask;
		smod = -1;
		// can this really work?
		for (int i = 0, j = oX; i < cols; i++, j += dX) {
			spos = i / packedValsPerPixel;
			smod += 1;
			if (smod >= packedValsPerPixel)
				smod = 0;
			smask >>= packedShift; // the source mask cycles
			if (smod == 0)
				smask = packedMask;
			tpos = j / packedValsPerPixel;
			tmod = j % packedValsPerPixel;
			p = src[spos] & smask;
			d = tmod - smod;
			if (d > 0)
				p >>= (d * packedShift);
			else if (d < 0)
				p <<= ((-d) * packedShift);
			dst[tpos] |= p;
		}
	}

	// yes, duplication of code is evil, normally
	void deinterlaceByte(byte[] src, byte[] dst, boolean readInPackedFormat) {
		if (!(imi.packed && readInPackedFormat))
			for (int i = 0, j = oXsamples; i < cols * imi.channels; i += imi.channels, j += dXsamples)
				for (int k = 0; k < imi.channels; k++)
					dst[j + k] = src[i + k];
		else
			deinterlacePackedByte(src, dst);
	}

	private void deinterlacePackedByte(byte[] src, byte[] dst) {
		int spos, smod, smask; // source byte position, bits to shift to left (01,2,3,4
		int tpos, tmod, p, d;
		// what the heck are you reading here? I told you would not enjoy this. Try Dostoyevsky or Simone Weil instead
		spos = 0;
		smask = packedMask;
		smod = -1;
		// Arrays.fill(dst, 0);
		for (int i = 0, j = oX; i < cols; i++, j += dX) {
			spos = i / packedValsPerPixel;
			smod += 1;
			if (smod >= packedValsPerPixel)
				smod = 0;
			smask >>= packedShift; // the source mask cycles
			if (smod == 0)
				smask = packedMask;
			tpos = j / packedValsPerPixel;
			tmod = j % packedValsPerPixel;
			p = src[spos] & smask;
			d = tmod - smod;
			if (d > 0)
				p >>= (d * packedShift);
			else if (d < 0)
				p <<= ((-d) * packedShift);
			dst[tpos] |= p;
		}
	}

	/**
	 * Is current row the last row for the lass pass??
	 */
	boolean isAtLastRow() {
		return pass == 7 && currRowSubimg == rows - 1;
	}

	/**
	 * current row number inside the "sub image"
	 */
	int getCurrRowSubimg() {
		return currRowSubimg;
	}

	/**
	 * current row number inside the "real image"
	 */
	int getCurrRowReal() {
		return currRowReal;
	}

	/**
	 * current pass number (1-7)
	 */
	int getPass() {
		return pass;
	}

	/**
	 * How many rows has the current pass?
	 **/
	int getRows() {
		return rows;
	}

	/**
	 * How many columns (pixels) are there in the current row
	 */
	int getCols() {
		return cols;
	}

	public int getPixelsToRead() {
		return getCols();
	}

	public int getBytesToRead() { // not including filter byte
		return (imi.bitspPixel * getPixelsToRead() + 7) / 8;
	}

	static void test() {
		Random rand = new Random();
		PngDeinterlacer ih = new PngDeinterlacer(new ImageInfo(rand.nextInt(35) + 1, rand.nextInt(52) + 1, 8, true));
		int np = ih.imi.cols * ih.imi.rows;
		System.out.println(ih.imi);
		for (int p = 1; p <= 7; p++) {
			ih.setPass(p);
			for (int row = 0; row < ih.getRows(); row++) {
				ih.setRow(row);
				int b = ih.getCols();
				np -= b;
				System.out.printf("Read %d pixels. Pass:%d Realline:%d cols=%d dX=%d oX=%d last:%b\n", b, ih.pass,
						ih.currRowReal, ih.cols, ih.dX, ih.oX, ih.isAtLastRow());

			}
		}
		if (np != 0)
			throw new PngjExceptionInternal("wtf??" + ih.imi);
	}

	public int getdY() {
		return dY;
	}

	/*
	 * in pixels 
	 */
	public int getdX() {
		return dX;
	}

	public int getoY() {
		return oY;
	}
	/*
	 * in pixels 
	 */
	public int getoX() {
		return oX;
	}

	public static void main(String[] args) {
		test();
	}

}
