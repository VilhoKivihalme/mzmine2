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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
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
    private DIChromatogram[] mergedCromatograms;
    // Mapping of last data point m/z --> chromatogram
    private Set<DIChromatogram> buildingChromatograms;
    private final int minPeakCount;
    public DIHighestDataPointConnector(RawDataFile dataFile,
            int allScanNumbers[], double minimumTimeSpan, double minimumHeight,int minPeakCount,
            MZTolerance mzTolerance) {

        this.mzTolerance = mzTolerance;
        this.minimumHeight = minimumHeight;
        this.minimumTimeSpan = minimumTimeSpan;
        this.dataFile = dataFile;
        this.allScanNumbers = allScanNumbers;
        this.minPeakCount = minPeakCount;
        // for (int i = 0; i < allScanNumbers.length; i++) {
        // System.out.println(allScanNumbers[i]);
        // }
        // We use LinkedHashSet to maintain a reproducible ordering. If we use
        // plain HashSet, the resulting peak list row IDs will have different
        // order every time the method is invoked.
        buildingChromatograms = new LinkedHashSet<DIChromatogram>();
        points = new DataPoint[allScanNumbers.length][];
    }

    DataPoint[][] points; 
    int pointsIndex =0;
    public void addScan(int scanNumber, DataPoint mzValues[]) {
    	points[pointsIndex]=mzValues;
    	pointsIndex++;
    	System.out.println("Added " + mzValues.length+" values");
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
            double closest=Double.POSITIVE_INFINITY;
            
            for (DIChromatogram testChrom : buildingChromatograms) {
                // System.out.println(testChrom);

                DataPoint lastMzPeak = testChrom.getLastMzPeak();
                Range<Double> toleranceRange = mzTolerance
                        .getToleranceRange(lastMzPeak.getMZ());

                
                /*
                */
   //*/
                if (toleranceRange.contains(mzPeak.getMZ())) {
                    if ((bestChromatogram == null) || (testChrom.getLastMzPeak()
                            .getIntensity() > bestChromatogram.getLastMzPeak()
                                    .getIntensity())) {
                        bestChromatogram = testChrom;

                    }
                }
                
   /*/
                if (toleranceRange.contains(mzPeak.getMZ())) {
                	
                	
                    if ((bestChromatogram == null) || Math.abs( (testChrom.getLastMzPeak()
                            .getMZ() - bestChromatogram.getLastMzPeak()
                                    .getMZ()))<closest) {
                    	if(bestChromatogram!=null){
                        closest = Math.abs( (testChrom.getLastMzPeak()
                                .getMZ() - bestChromatogram.getLastMzPeak()
                                .getMZ()));
                    	}
                        bestChromatogram = testChrom;
             

                    }
                }
//*/
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
    	/*int dataPointsChecked=0;
    	int total=0;
    	int max=0;
    	for(int i =0;i<points.length-1;i++){

			  System.out.println(points[i].length + " "+points[i+1].length);
    		for(int j = 0;j<points[i].length;j++){
    			int matches=0;
				  Range<Double> toleranceRange = mzTolerance
	                        .getToleranceRange(points[i][j].getMZ());
    			for(int k=0;k<points[i+1].length;k++){
    				if(toleranceRange.contains(points[i+1][k].getMZ())){
    					matches++;
    				}
    			}
    			if(matches>max){
    				max=matches;
    			}
    			total+=matches;
    			dataPointsChecked++;
    		}
    	}
    	System.out.println("Out of " + dataPointsChecked + " there was average of "+(total/dataPointsChecked) +" possible connections for each (max: " + max+")");
        */
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
 
        
        DIChromatogram[] chromatograms=buildingChromatograms.toArray(new DIChromatogram[0]);
      

        mergedCromatograms= mergeChromatograms();
       
        return chromatograms;
    }

    public DIChromatogram[] getMergedChromatograms(){
    	return mergedCromatograms;
    }
    
	private DIChromatogram[] mergeChromatograms() {
		Iterator<DIChromatogram> chromIterator;
		//set of lists of chromatograms to be merged
		HashMap<DIChromatogram, ArrayList<DIChromatogram>> lists=null;
        //set of ranges for certain chromatogram
		HashMap<DIChromatogram, Range<Double>> newRanges = null;
		//list of such cromatograms that should not be added because they were merged
		LinkedHashSet<DIChromatogram> mergedNotToAdd =null;
		System.out.println("Starting the merging process..");
		Set<DIChromatogram> merged=null;
        boolean moreMerging=true;
        boolean firstRound=true;
//        while(moreMerging){ //thisshould not be needed and makes the process take really long.
        
        	moreMerging=false; //assume the merging process is done.
        	
        	lists = new HashMap<DIChromatogram, ArrayList<DIChromatogram>>();
        	newRanges = new HashMap<DIChromatogram, Range<Double>>();
        	mergedNotToAdd=new LinkedHashSet<DIChromatogram>();
			if(firstRound){
			    chromIterator = buildingChromatograms.iterator();
			}else{
				chromIterator=merged.iterator();
			}
			int total = buildingChromatograms.size();
			int current = 0;
        // iterate all chromatograms
        while (chromIterator.hasNext()) {
        	current++;
        	if(current%100==0){
        		System.out.println("merged:"+current+"/"+total);
        	}
            DIChromatogram chromatogram = chromIterator.next();

            // init new chromatogram list
            lists.put(chromatogram, new ArrayList<DIChromatogram>());
            newRanges.put(chromatogram, chromatogram.getRawDataPointsMZRange());
            // go through all others
            Iterator<DIChromatogram> another;
        	if(firstRound){
        		another = buildingChromatograms.iterator();
			}else{
				another=merged.iterator();
			}
            while (another.hasNext()) {
            	DIChromatogram d = another.next();
            	
                // do not test current to itself
                if (d != chromatogram) {
                	
                	//if this has not been merged somewhere
                    if (!mergedNotToAdd.contains(d)) {
                    	
                    	//see if these ranges intersect
                        if (d.getRawDataPointsMZRange()
                                .isConnected(newRanges.get(chromatogram))){
                            lists.get(chromatogram).add(d);
                            
                            mergedNotToAdd.add(d);
                            mergedNotToAdd.add(chromatogram); //original is now merged as well, no longer needed.
//                            System.out.println("Removing:" +d.getMZ() +" and " +chromatogram.getMZ());
//                            System.out.println("OLD:"+newRanges.get(chromatogram));
                            newRanges.put(chromatogram,
                                    d.getRawDataPointsMZRange()
                                            .span(newRanges.get(chromatogram)));
                            
                            //we need to start iterating from beginning because new range may now enclose others.
                        
                            if(firstRound){
                            	another = buildingChromatograms.iterator();
		        			}else{
		        				another=merged.iterator();
		        			}
//                            System.out.println("NEW: " +newRanges.get(chromatogram));
//                            System.out.println(newRanges.get(chromatogram));
                            // System.out.prHintln(d.getRawDataPointsMZRange() +
                            // " "
                            // + chromatogram.getRawDataPointsMZRange());

                        }

                    }
                }
            }
        }
        firstRound=false;
        merged = new LinkedHashSet<DIChromatogram>();
        int merges =0;
        DIChromatogram[] keys = lists.keySet().toArray(new DIChromatogram[0]);
        Arrays.sort(keys);
        for (DIChromatogram di : keys) {
            ArrayList<DIChromatogram> list = lists.get(di);

            DIChromatogram combined = di; //if nothing to combine, use the original
            System.out.println(combined +" " +di);
            if(list.size()>0){
//            	System.out.println("Merging: " +(list.size()+1));
            	moreMerging=true;
	            for (DIChromatogram toMerge : list) {
	
//	                System.out.println("Merging " + toMerge + " to " + combined);
	            	merges++;
	                combined = DIChromatogram.merge(combined, toMerge);
	                combined.finishChromatogram();
	                
//	                System.out.println("Result: " +combined);
	            }
            }
            if (!mergedNotToAdd.contains(combined)) {
                merged.add(combined);
            }
        }
        System.out.println("merges: "+merges);
//        }

        // All remaining chromatograms are good, so we can return them
        
        //TODO: now strip the ones that do not have enough peaks.
        ArrayList<DIChromatogram> all = new ArrayList<DIChromatogram>(); 
       for(DIChromatogram merg : merged){
    	   if(merg.getAllDatapoints().size()<minPeakCount){
    		   all.add(merg);
    	   }
       }
       merged.removeAll(all);
       return merged.toArray(new DIChromatogram[0]);
	}

}
