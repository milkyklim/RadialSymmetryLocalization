package gui.interactive;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import net.imglib2.Cursor;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.algorithm.localextrema.RefinedPeak;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

import fit.Spot;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ImageProcessor;

public class HelperFunctions {

	public static ArrayList<RefinedPeak<Point>> filterPeaks(final ArrayList<RefinedPeak<Point>> peaks,
			final Rectangle rectangle, final double threshold) {
		final ArrayList<RefinedPeak<Point>> filtered = new ArrayList<>();

		for (final RefinedPeak<Point> peak : peaks)
			if (HelperFunctions.isInside(peak, rectangle) && (-peak.getValue() > threshold)) 
				// I guess the peak.getValue function returns the value in scale-space
				filtered.add(peak);

		return filtered;
	}
	
	// TODO: code might be reused instead of copy\pasting
	public static ArrayList<RefinedPeak<Point>> filterPeaks(final ArrayList<RefinedPeak<Point>> peaks, final double threshold) {
		final ArrayList<RefinedPeak<Point>> filtered = new ArrayList<>();
		
		for (final RefinedPeak<Point> peak : peaks)
			if (-peak.getValue() > threshold)
				filtered.add(peak);
		
		return filtered;
	}
	
	// TODO: Create a more sophisticated way to filter the peaks
	public static ArrayList<Point> resavePeaks(final ArrayList<RefinedPeak<Point>> peaks, final double threshold, int numDimensions) {
		final ArrayList<Point> filtered = new ArrayList<>();
		
		double [] pos = new double[numDimensions];
		long [] iPos = new long [numDimensions];
	
		for (final RefinedPeak<Point> peak : peaks)
			if (-peak.getValue() > threshold){
				peak.localize(pos);
				for (int d = 0; d < numDimensions; d ++)
					iPos[d] = (long)pos[d];		
				filtered.add(new Point(iPos));
				
			}		
		return filtered;
	}

	public static ArrayList<Spot> filterSpots(final ArrayList<Spot> spots, final int minInliers) {
		final ArrayList<Spot> filtered = new ArrayList<>();

		for (final Spot spot : spots)
			if (spot.inliers.size() >= minInliers)
				filtered.add(spot);

		return filtered;
	}

	public static <L extends RealLocalizable> void drawRealLocalizable(final Collection<L> peaks, final ImagePlus imp,
			final double radius, final Color col, final boolean clearFirst) {
		// extract peaks to show
		// we will overlay them with RANSAC result
		Overlay overlay = imp.getOverlay();

		if (overlay == null) {
			// System.out.println("If this message pops up probably something
			// went wrong.");
			overlay = new Overlay();
			imp.setOverlay(overlay);
		}

		if (clearFirst)
			overlay.clear();

		for (final L peak : peaks) {
			final float x = peak.getFloatPosition(0);
			final float y = peak.getFloatPosition(1);

			if (!clearFirst) {
				// +0.5 is to center in on the middle of the detection pixel
				// cross roi
				final Roi lrv = new Roi(x - radius + 0.5, y + 0.5, radius * 2, 0);
				final Roi lrh = new Roi(x + 0.5, y - radius + 0.5, 0, radius * 2);

				lrv.setStrokeColor(col);
				lrh.setStrokeColor(col);
				overlay.add(lrv);
				overlay.add(lrh);
			} else {
				// +0.5 is to center in on the middle of the detection pixel
				final OvalRoi or = new OvalRoi(x - radius + 0.5, y - radius + 0.5, radius * 2, radius * 2);
				or.setStrokeColor(col);
				overlay.add(or);
			}
		}

		// this part might be useful for debugging
		// for lab meeting to show the parameters
		// final OvalRoi sigmaRoi = new OvalRoi(50, 10, 0, 0);
		// sigmaRoi.setStrokeWidth(1);
		// sigmaRoi.setStrokeColor(new Color(255, 255, 255));
		// sigmaRoi.setName("sigma : " + String.format(java.util.Locale.US,
		// "%.2f", sigma));
		//
		// final OvalRoi srRoi = new OvalRoi(72, 26, 0, 0);
		// srRoi.setStrokeWidth(1);
		// srRoi.setStrokeColor(new Color(255, 255, 255));
		// srRoi.setName("support radius : " + supportRadius);
		//
		// final OvalRoi irRoi = new OvalRoi(68, 42, 0, 0);
		// irRoi.setStrokeWidth(1);
		// irRoi.setStrokeColor(new Color(255, 255, 255));
		// irRoi.setName("inlier ratio : " + String.format(java.util.Locale.US,
		// "%.2f", inlierRatio));
		//
		// final OvalRoi meRoi = new OvalRoi(76, 58, 0, 0);
		// meRoi.setStrokeWidth(1);
		// meRoi.setStrokeColor(new Color(255, 255, 255));
		// meRoi.setName("max error : " + String.format(java.util.Locale.US,
		// "%.4f", maxError));
		//
		// // output sigma
		// // Support radius
		// // inlier ratio
		// // Max error
		// overlay.add(sigmaRoi);
		// overlay.add(srRoi);
		// overlay.add(irRoi);
		// overlay.add(meRoi);
		//
		// overlay.setLabelFont(new Font("SansSerif", Font.PLAIN, 16));
		// overlay.drawLabels(true); // allow labels
		// overlay.drawNames(true); // replace numbers with name

		imp.updateAndDraw();
	}

	public static Img<FloatType> toImg(final ImagePlus imagePlus, final long[] dim, final int type) {
		final ImageProcessor ip = imagePlus.getStack().getProcessor(imagePlus.getCurrentSlice());
		final Object pixels = ip.getPixels();

		final Img<FloatType> imgTmp;

		if (type == 0) {
			imgTmp = ArrayImgs.floats(dim);
			final byte[] p = (byte[]) pixels;

			int i = 0;
			for (final FloatType t : imgTmp)
				t.set(UnsignedByteType.getUnsignedByte(p[i++]));

		} else if (type == 1) {
			imgTmp = ArrayImgs.floats(dim);
			final short[] p = (short[]) pixels;

			int i = 0;
			for (final FloatType t : imgTmp)
				t.set(UnsignedShortType.getUnsignedShort(p[i++]));
		} else {
			imgTmp = ArrayImgs.floats((float[]) pixels, dim);
		}

		return imgTmp;
	}

	public static float computeSigma2(final float sigma1, final int stepsPerOctave) {
		final float k = (float) Math.pow(2f, 1f / stepsPerOctave);
		return sigma1 * k;
	}

	public static float computeValueFromScrollbarPosition(final int scrollbarPosition, final float min, final float max,
			final int scrollbarSize) {
		return min + (scrollbarPosition / (float) scrollbarSize) * (max - min);
	}

	public static int computeScrollbarPositionFromValue(final float sigma, final float min, final float max,
			final int scrollbarSize) {
		return Util.round(((sigma - min) / (max - min)) * scrollbarSize);
	}

	/*
	 * sets the calibration for the initial image. Only the relative value
	 * matters. normalize everything with respect to the 1-st coordinate.
	 */
	public static double[] setCalibration(ImagePlus imagePlus, int numDimensions) {
		double[] calibration = new double[numDimensions]; // should always be 2
															// for the
															// interactive mode
		// if there is something reasonable in x-axis calibration use this value
		if ((imagePlus.getCalibration().pixelWidth >= 1e-13) && imagePlus.getCalibration().pixelWidth != Double.NaN) {
			calibration[0] = imagePlus.getCalibration().pixelWidth / imagePlus.getCalibration().pixelWidth;
			calibration[1] = imagePlus.getCalibration().pixelHeight / imagePlus.getCalibration().pixelWidth;
			if (numDimensions == 3)
				calibration[2] = imagePlus.getCalibration().pixelDepth / imagePlus.getCalibration().pixelWidth;
		} else {
			// otherwise set everything to 1.0 trying to fix calibration
			for (int i = 0; i < numDimensions; ++i)
				calibration[i] = 1.0;
		}
		return calibration;
	}

	public static double[] getMinMax(RandomAccessibleInterval<FloatType> rai) {
		Cursor<FloatType> cursor = Views.iterable(rai).cursor();
		double[] minmax = new double[] { Double.MAX_VALUE, -Double.MAX_VALUE };

		while (cursor.hasNext()) {
			FloatType val = cursor.next();
			if (val.getRealDouble() > minmax[1])
				minmax[1] = val.getRealDouble();
			if (val.getRealDouble() < minmax[0])
				minmax[0] = val.getRealDouble();
		}
		return minmax;
	}

	// set the min/max location for the given location
	public static void setMinMaxLocation(double[] location, double[] sigma, long[] min, long[] max) {
		int nunDimensions = location.length;

		for (int d = 0; d < nunDimensions; d++) {
			min[d] = (long) (location[d] - sigma[d] - 1);
			max[d] = (long) (location[d] + sigma[d] + 1);
		}
	}

	public static <T extends Comparable<T> & Type<T>> void computeMinMax(final RandomAccessibleInterval<T> input,
			final T min, final T max) {
		// create a cursor for the image (the order does not matter)
		final Iterator<T> iterator = Views.iterable(input).iterator();

		// initialize min and max with the first image value
		T type = iterator.next();

		min.set(type);
		max.set(type);

		// loop over the rest of the data and determine min and max value
		while (iterator.hasNext()) {
			// we need this type more than once
			type = iterator.next();

			if (type.compareTo(min) < 0)
				min.set(type);

			if (type.compareTo(max) > 0)
				max.set(type);
		}
	}

	public static <T extends RealType<T>> double[] computeMinMax(final RandomAccessibleInterval<T> input) {
		// create a cursor for the image (the order does not matter)
		final Iterator<T> iterator = Views.iterable(input).iterator();

		// initialize min and max with the first image value
		T type = iterator.next();

		double min = type.getRealDouble();
		double max = type.getRealDouble();

		// loop over the rest of the data and determine min and max value
		while (iterator.hasNext()) {
			// we need this type more than once
			double v = iterator.next().getRealDouble();

			if (v < min)
				min = v;

			if (v > max)
				max = v;
		}

		return new double[] { min, max };
	}

	/*
	 * initialize calibration 2D and 3D friendly
	 */
	public static double[] initCalibration(final ImagePlus imp, int numDimensions) {
		double[] calibration = new double[numDimensions];

		try {
			final Calibration cal = imp.getCalibration();
			double[] calValues = new double[numDimensions];

			// image doesn't have the calibration
			if (cal != null) {
				calValues[0] = cal.pixelWidth;
				calValues[1] = cal.pixelHeight;
				if (numDimensions == 3)
					calValues[2] = cal.pixelDepth;
			}

			boolean isFine = true;
			boolean isSame = true; // to check the calibration in all dimensions

			// ? calibration has meaningful values
			for (int d = 0; d < numDimensions; d++) {
				if (Double.isNaN(calValues[d]) || Double.isInfinite(calValues[d]) || calValues[d] <= 0) {
					isFine = false;
				}
				if (d > 0 && (calValues[d - 1] != calValues[d]))
					isSame = false;
			}

			if (!isFine || cal == null || isSame) {
				for (int d = 0; d < numDimensions; d++)
					calibration[d] = 1.0;
			} else {
				IJ.log("WARNING: Pixel calibration might be not symmetric! Please check this (Image > Properties)");
				IJ.log("x: " + calValues[0]);
				IJ.log("y: " + calValues[1]);
				if (numDimensions == 3)
					IJ.log("z: " + calValues[2]);

				int iMax = 0;
				for (int d = 0; d < numDimensions; d++) {
					calibration[d] = calValues[d];
					if (calibration[d] < calibration[iMax])
						iMax = d;
				}

				for (int d = 0; d < numDimensions; d++) {
					if (d != iMax)
						calibration[d] /= calibration[iMax];
				}
				calibration[iMax] = 1.0;
			}
		} catch (Exception e) {
			for (int d = 0; d < numDimensions; d++)
				calibration[d] = 1.0;
		}

		IJ.log("Using relative calibration: " + Util.printCoordinates(calibration));
		return calibration;

	}

	public static <T extends RealType<T>> void printCoordinates(RandomAccessibleInterval<T> img) {
		for (int d = 0; d < img.numDimensions(); ++d) {
			System.out.println("[" + img.min(d) + " " + img.max(d) + "] ");
		}
	}

	// check if peak is inside of the rectangle
	protected static <P extends RealLocalizable> boolean isInside(final P peak, final Rectangle rectangle) {
		if (rectangle == null)
			return true;

		final float x = peak.getFloatPosition(0);
		final float y = peak.getFloatPosition(1);

		boolean res = (x >= (rectangle.x) && y >= (rectangle.y) && x < (rectangle.width + rectangle.x - 1)
				&& y < (rectangle.height + rectangle.y - 1));

		return res;
	}

	/*
	 * Copy peaks found by DoG to lighter ArrayList (!imglib2)
	 */
	public static void copyPeaks(final ArrayList<RefinedPeak<Point>> peaks, final ArrayList<long[]> simplifiedPeaks,
			final int numDimensions, final Rectangle rectangle, final double threshold) {
		for (final RefinedPeak<Point> peak : peaks) {
			if (HelperFunctions.isInside(peak, rectangle) && (-peak.getValue() > threshold)) {
				final long[] coordinates = new long[numDimensions];
				for (int d = 0; d < peak.numDimensions(); ++d)
					coordinates[d] = Util.round(peak.getDoublePosition(d));

				simplifiedPeaks.add(coordinates);
			}
		}
	}

	/*
	 * Copy peaks found by DoG to lighter ArrayList (!imglib2)
	 */
	public static ArrayList<Localizable> localizablePeaks(final ArrayList<RefinedPeak<Point>> peaks) {
		ArrayList< Localizable > integerPeaks = new ArrayList<>();
		for (final RefinedPeak<Point> peak : peaks) {
			int numDimensions = peak.numDimensions();
			final long[] coordinates = new long[numDimensions];
			for (int d = 0; d < peak.numDimensions(); ++d)
				coordinates[d] = Util.round(peak.getDoublePosition(d));
			integerPeaks.add( new Point(coordinates));
		}
		return integerPeaks;
	}	
	
	// used to copy Spots to Peaks
	public static void copyToLocalizable(final ArrayList<Spot> spots, Collection<Localizable> peaks ) {
		for (final Spot spot : spots) {
			peaks.add( new PointSpot( spot ) );
			//Point pos = new Point(spot.getOriginalLocation());
			//peaks.add(pos);
		}
	}
	
	// used to copy Spots to long []
	public static void copyToLong(final ArrayList<Spot> spots, ArrayList<long[]> peaks ) {
		for (final Spot spot : spots)
			peaks.add(spot.getOriginalLocation());
	}

	// used to copy Spots to double []
	public static void copyToDouble(final ArrayList<Spot> spots, ArrayList<double []> peaks ) {
		for (final Spot spot : spots)
			peaks.add(spot.getCenter());
	}
	

	public static class PointSpot extends Point
	{
		final Spot spot;

		public PointSpot( final Spot spot )
		{
			super( spot.getOriginalLocation() );
			this.spot = spot;
		}
		public Spot getSpot() { return spot; }
	}

	/*
	 * used by background subtraction to calculate the boundaries of the spot
	 */
	// FIXME: Actually wrong! check 0 should interval.min(d)
	public static void getBoundaries(long[] peak, long[] min, long[] max, long[] fullImgMax, int supportRadius) {
		for (int d = 0; d < peak.length; ++d) {
			// check that it does not exceed bounds of the underlying image
			min[d] = Math.max(peak[d] - supportRadius, 0);
			max[d] = Math.min(peak[d] + supportRadius, fullImgMax[d]);

		}
	}

}
