/*
 * Copyright 2006-2015 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * St, Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.masslistmethods.dichromatogrambuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.common.collect.Range;

import net.sf.mzmine.datamodel.DataPoint;
import net.sf.mzmine.datamodel.RawDataFile;
import net.sf.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import net.sf.mzmine.util.DataPointSorter;
import net.sf.mzmine.util.SortingDirection;
import net.sf.mzmine.util.SortingProperty;

public class DIHighestDataPointConnector {

    private final MZTolerance mzTolerance;
    private final double minimumTimeSpan, minimumHeight;
    private final RawDataFile dataFile;
    private final int allScanNumbers[];

    // Mapping of last data point m/z --> chromatogram
    private Set<DIChromatogram> buildingChromatograms;

    public DIHighestDataPointConnector(RawDataFile dataFile,
            int allScanNumbers[], double minimumTimeSpan, double minimumHeight,
            MZTolerance mzTolerance) {

        this.mzTolerance = mzTolerance;
        this.minimumHeight = minimumHeight;
        this.minimumTimeSpan = minimumTimeSpan;
        this.dataFile = dataFile;
        this.allScanNumbers = allScanNumbers;
        // for (int i = 0; i < allScanNumbers.length; i++) {
        // System.out.println(allScanNumbers[i]);
        // }

        // We use LinkedHashSet to maintain a reproducible ordering. If we use
        // plain HashSet, the resulting peak list row IDs will have different
        // order every time the method is invoked.
        buildingChromatograms = new LinkedHashSet<DIChromatogram>();

    }

    public void addScan(int scanNumber, DataPoint mzValues[]) {

        // System.out.println("Added scan " + scanNumber);
        // Sort m/z peaks by descending intensity
        Arrays.sort(mzValues, new DataPointSorter(SortingProperty.Intensity,
                SortingDirection.Descending));

        // Set of already connected chromatograms in each iteration
        Set<DIChromatogram> connectedChromatograms = new LinkedHashSet<DIChromatogram>();

        // TODO: these two nested cycles should be optimized for speed
        for (DataPoint mzPeak : mzValues) {

            // Search for best chromatogram, which has highest last data point
            DIChromatogram bestChromatogram = null;
            // System.out.println("CURRENT
            // SIZE:"+connectedChromatograms.size()+" "
            // +buildingChromatograms.size());
            for (DIChromatogram testChrom : buildingChromatograms) {
                // System.out.println(testChrom);

                DataPoint lastMzPeak = testChrom.getLastMzPeak();
                Range<Double> toleranceRange = mzTolerance
                        .getToleranceRange(lastMzPeak.getMZ());

                if (toleranceRange.contains(mzPeak.getMZ())) {
                    if ((bestChromatogram == null) || (testChrom.getLastMzPeak()
                            .getIntensity() > bestChromatogram.getLastMzPeak()
                                    .getIntensity())) {
                        bestChromatogram = testChrom;

                    }
                }

            }

            // If we found best chromatogram, check if it is already connected.
            // In such case, we may discard this mass and continue. If we
            // haven't found a chromatogram, we may create a new one.
            if (bestChromatogram != null) {
                if (connectedChromatograms.contains(bestChromatogram)) {
                    continue;
                }
            } else {
                bestChromatogram = new DIChromatogram(dataFile, allScanNumbers);
            }

            // Add this mzPeak to the chromatogram
            bestChromatogram.addMzPeak(scanNumber, mzPeak);

            // Move the chromatogram to the set of connected chromatograms
            connectedChromatograms.add(bestChromatogram);

        }

        // Process those chromatograms which were not connected to any m/z peak
        for (DIChromatogram testChrom : buildingChromatograms) {

            // Skip those which were connected
            if (connectedChromatograms.contains(testChrom)) {
                continue;
            }

            // Check if we just finished a long-enough segment
            if (testChrom.getBuildingSegmentLength() >= minimumTimeSpan) {
                testChrom.commitBuildingSegment();

                // Move the chromatogram to the set of connected chromatograms
                connectedChromatograms.add(testChrom);
                continue;
            }

            // Check if we have any committed segments in the chromatogram
            if (testChrom.getNumberOfCommittedSegments() > 0) {
                testChrom.removeBuildingSegment();

                // Move the chromatogram to the set of connected chromatograms
                connectedChromatograms.add(testChrom);
                continue;
            }

        }

        // All remaining chromatograms in buildingChromatograms are discarded
        // and buildingChromatograms is replaced with connectedChromatograms
        buildingChromatograms = connectedChromatograms;

    }

    public DIChromatogram[] finishChromatograms() {

        // Iterate through current chromatograms and remove those which do not
        // contain any committed segment nor long-enough building segment

        Iterator<DIChromatogram> chromIterator = buildingChromatograms
                .iterator();
        while (chromIterator.hasNext()) {

            DIChromatogram chromatogram = chromIterator.next();

            if (chromatogram.getBuildingSegmentLength() >= minimumTimeSpan) {
                chromatogram.commitBuildingSegment();
                chromatogram.finishChromatogram();
            } else {
                if (chromatogram.getNumberOfCommittedSegments() == 0) {
                    // System.out.println("removing " +
                    // chromIterator.toString());
                    chromIterator.remove();
                    continue;
                } else {

                    // System.out.println("removing segment");
                    chromatogram.removeBuildingSegment();
                    chromatogram.finishChromatogram();
                }
            }

            // Remove chromatograms smaller then minimum height
            // if (chromatogram.getHeight() < minimumHeight)
            // chromIterator.remove();

        }

        HashMap<DIChromatogram, ArrayList<DIChromatogram>> lists = new HashMap<DIChromatogram, ArrayList<DIChromatogram>>();
        HashMap<DIChromatogram, Range<Double>> newRanges = new HashMap<DIChromatogram, Range<Double>>();
        LinkedHashSet<DIChromatogram> mergedNotToAdd = new LinkedHashSet<DIChromatogram>();
        chromIterator = buildingChromatograms.iterator();

        // iterate all chromatograms
        while (chromIterator.hasNext()) {
            DIChromatogram chromatogram = chromIterator.next();

            // init new chromatogram list
            lists.put(chromatogram, new ArrayList<DIChromatogram>());
            newRanges.put(chromatogram, chromatogram.getRawDataPointsMZRange());
            // go through all others
            Iterator<DIChromatogram> another = buildingChromatograms.iterator();
            while (another.hasNext()) {
            	DIChromatogram d = another.next();
                // do not test current to itself
                if (d != chromatogram) {

                    if (!mergedNotToAdd.contains(d)) {//if this has not been merged somewhere
                        if (d.getRawDataPointsMZRange()
                                .isConnected(newRanges.get(chromatogram))){//newRanges.get(chromatogram))) {
//                            System.out.println("TRUE!");
                            lists.get(chromatogram).add(d);
                            
                            mergedNotToAdd.add(d);
                            mergedNotToAdd.add(chromatogram); //original is now merged as well, no longer needed.
                            System.out.println("OLD:"+newRanges.get(chromatogram));
                            newRanges.put(chromatogram,
                                    d.getRawDataPointsMZRange()
                                            .span(newRanges.get(chromatogram)));
                            another = buildingChromatograms.iterator();
                            System.out.println("NEW: " +newRanges.get(chromatogram));
//                            System.out.println(newRanges.get(chromatogram));
                            // System.out.prHintln(d.getRawDataPointsMZRange() +
                            // " "
                            // + chromatogram.getRawDataPointsMZRange());

                        }

                    }
                }
            }
        }

        ArrayList<DIChromatogram> merged = new ArrayList<DIChromatogram>();

        DIChromatogram[] keys = lists.keySet().toArray(new DIChromatogram[0]);
        Arrays.sort(keys);
        for (DIChromatogram di : keys) {
            ArrayList<DIChromatogram> list = lists.get(di);

            DIChromatogram combined = di; //if nothing to combine, use the original
            if(list.size()>0){
            	System.out.println("Merging: " +list.size());
            	merged.add(di);
	            for (DIChromatogram toMerge : list) {
	
	                System.out.println("Merging " + toMerge + " to " + combined);
	
	                combined = DIChromatogram.merge(combined, toMerge);
	                combined.finishChromatogram();
	
	                System.out.println("Result: " +combined);
	            }
            }
//            if (!mergedNotToAdd.contains(combined)) {
                merged.add(combined);
//            }
        }
        

        // All remaining chromatograms are good, so we can return them
        DIChromatogram[] chromatograms = merged.toArray(new DIChromatogram[0]);
        return chromatograms;
    }

}
