/*
 * Copyright 2012, United States Geological Survey or
 * third-party contributors as indicated by the @author tags.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/  >.
 *
 */
package asl.metadata;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import asl.metadata.meta_new.ChannelMeta;
import asl.metadata.meta_new.StationMeta;

/**
 * MetaGenerator - Holds metadata for all networks x stations x channels x
 * epochs Currently reads metadata in from network dataless seed files
 *
 * @author Mike Hagerty hagertmb@bc.edu
 *
 */
public class MetaGenerator {
	private static final Logger logger = LoggerFactory
			.getLogger(asl.metadata.MetaGenerator.class);

	/**
	 * Each datalessDir/XX.dataless file is read into a separate SeedVolume
	 * keyed by network (e.g., XX)
	 */
	protected Hashtable<NetworkKey, SeedVolume> volumes = null;

	/**
	 * Private class meant to enable mock test class to inherit from this without running other function
	 */
	protected MetaGenerator() {
        volumes = new Hashtable<>();
	}

	/**
	 * Look in datalessDir for all files of form XX.dataless
	 * where XX = network {II, IU, NE, etc.}
	 *
	 * @param datalessDir	path to dataless seed files, read from config.xml
	 * @param networkSubset the network subset to parse
	 */
	public MetaGenerator(String datalessDir, List<String> networkSubset) {
		volumes = new Hashtable<>();

		File dir = new File(datalessDir);
		if (!dir.exists()) {
			logger.error("Path '" + dir + "' does not exist.");
			System.exit(0);
		} else if (!dir.isDirectory()) {
			logger.error("Path '" + dir + "' is not a directory.");
			System.exit(0);
		}

		/* Create List of network subset IDs '<ID>.dataless' */
		final List<String> networkExt = new ArrayList<>();
		if (networkSubset != null) {
			String ext = "";
			for (String key : networkSubset) {
				ext = key + ".dataless";
				networkExt.add(ext);
			}
		}
		FilenameFilter textFilter = (dir1, name) -> {
      if (!networkExt.isEmpty()) {
        return networkExt.contains(name);
      } else {
        return name.endsWith(".dataless") && (name.length() == 11) || name.endsWith(".dataless") && (name.length() == 10);
      }
    };

		String[] files = dir.list(textFilter);
		if (files == null) {
			logger.error("== No dataless files exist!");
			System.exit(0);
		}
		for (String fileName : files) {
			String datalessFile = dir + "/" + fileName;
			System.out.format(
					"== MetaGenerator: rdseed -f [datalessFile=%s]\n",
					datalessFile);
			ProcessBuilder pb = new ProcessBuilder("rdseed", "-s", "-f",
					datalessFile);

			ArrayList<String> strings = new ArrayList<>();

			SeedVolume volume = null;

			try {
				Process process = pb.start();
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(process.getInputStream()));
				String line = null;
				while ((line = reader.readLine()) != null) {
					strings.add(line);
				}
				process.waitFor();
			}
			// Need to catch both IOException and InterruptedException
			catch (IOException e) {
				// System.out.println("Error: IOException Description: " +
				// e.getMessage());
				logger.error("IOException:", e);
			} catch (InterruptedException e) {
				// System.out.println("Error: InterruptedException Description: "
				// + e.getMessage());
				logger.error("InterruptedException:", e);
			}



			try {
				volume = buildVolumesFromStringData(strings);
			} catch (Exception e) {
				logger.error("== processing dataless volume for file=[{}]",	fileName);
			}

			if (volume == null) {
				logger.error("== processing dataless volume==null! for file=[{}]", fileName);
				System.exit(0);
			} else {
				addVolume(volume);
			}

		} // end for loop over XX.dataless files
	}

	SeedVolume buildVolumesFromStringData(List<String> strings) throws DatalessParseException {
		Dataless dataless = new Dataless(strings);
		dataless.processVolume();
		return dataless.getVolume();
	}

	protected void addVolume(SeedVolume volume) {
		NetworkKey networkKey = volume.getNetworkKey();
		if (volumes.containsKey(networkKey)) {
			logger.error("== Attempting to load volume networkKey=[{}] --> Already loaded!",
					networkKey);
		} else {
			volumes.put(networkKey, volume);
		}
	}

	/**
	 * Return a list of all stations contained in all volumes
	 */
	public List<Station> getStationList() {
		if (volumes == null) {
			return null;
		}
		ArrayList<Station> allStations = new ArrayList<>();
		for (NetworkKey key : volumes.keySet()) {
			SeedVolume volume = volumes.get(key);
			List<Station> stations = volume.getStationList();
			allStations.addAll(stations);
		}
		return allStations;
	}

	/**
	 * Return a list of all stations matching parameters.
	 *
	 * @param networks network restrictions. Can be null
	 * @param stations station restrictions. Can be null
	 * @return the station list
	 */
	public List<Station> getStationList(String[] networks, String[] stations) {

		logger.info("Generating list of stations for: {}  | {}", networks, stations);
		if (volumes == null) {
			return null;
		}

		List<Station> allStations = new ArrayList<>();

		if(networks != null && stations != null){
			for (String network : networks) {
				SeedVolume volume = volumes.get(new NetworkKey(network));
				for(String station : stations){
					if(volume.hasStation(new StationKey(network, station))) {
						allStations.add(new Station(network, station));
					}
				}
			}
		}
		else if(networks != null && stations == null){
			//Only Network restrictions
			for (String network : networks) {
				SeedVolume volume = volumes.get(new NetworkKey(network));
				allStations.addAll(volume.getStationList());
			}
		}
		else if(networks == null && stations != null){
			//Possible condition, not sure the situation this makes sense.
			for (NetworkKey networkKey : volumes.keySet()) {
				SeedVolume volume = volumes.get(networkKey);
				for(String station : stations){
					if(volume.hasStation(new StationKey(networkKey.network, station))) {
						allStations.add(new Station(networkKey.network, station));
					}
				}
			}
		}
		else if(networks == null && stations == null){
			allStations = getStationList();
		}
		return allStations;
	}

	/**
	 * loadDataless() reads in the entire dataless seed file (all stations)
	 * getStationData returns the metadata for a single station for all epochs
	 * It is called by
	 * {@link asl.metadata.MetaGenerator#getStationMeta(Station, LocalDateTime)}
	 * below.
	 *
	 * @param station
	 *            the station
	 * @return the station data - this can be null if seed files are
	 *         malformatted
	 */
	private StationData getStationData(Station station) {
		SeedVolume volume = volumes.get(new NetworkKey(station.getNetwork()));
		if (volume == null) {
			logger.error(
					"== getStationData() - Volume==null for Station=[{}]  Check the volume label in Blockette 10 Field 9. Must be formatted like IU* to work.\n",
					station);
			return null; //No volume so nothing can be returned.
		}
		StationData stationData = volume.getStation(new StationKey(station));
		if (stationData == null) {
			logger.error("stationData is null ==> This COULD be caused by incorrect network code INSIDE seedfile ...");
		}
		return stationData;
	}

	/**
	 * getStationMeta Calls getStationData to get the metadata for all epochs
	 * for this station, Then scans through the epochs to find and return the
	 * requested epoch metadata.
	 *
	 * @param station The station for which metadata is requested
	 * @param timestamp The (epoch) timestamp for which metadata is requested
	 *
	 *            ChannelData - Contains all Blockettes for a particular
	 *            channel, for ALL epochs EpochData - Constains all Blockettes
	 *            for a particular channel, for the REQUESTED epoch only.
	 *            ChannelMeta - Our (minimal) internal format of the channel
	 *            response. Contains the first 3 (0, 1, 2) response stages for
	 *            the REQUESTED epoch only. ChannelMeta.setDayBreak() = true if
	 *            we detect a change in metadata on the requested timestamp day.
	 */

	public StationMeta getStationMeta(Station station, LocalDateTime timestamp){

		StationData stationData = getStationData(station);
		//This can happen if the file DATALESS.IW_LKWY.seed doesn't match
		if (stationData == null) {
			logger.error("== getStationMeta request:\t\t[{}]\t[{}]\tNOT FOUND!",
					station, timestamp.format(DateTimeFormatter.ISO_ORDINAL_DATE));
			return null; // the name INSIDE the dataless (= US_LKWY) ... so the
							// keys don't match
		}
		// Scan stationData for the correct station blockette (050) for this
		// timestamp - return null if it isn't found
		Blockette blockette = stationData.getBlockette(timestamp);

		if (blockette == null) {
			logger.error("== getStationMeta request:\t\t[{}]\t[{}]\tNOT FOUND!",
					station, timestamp.format(DateTimeFormatter.ISO_ORDINAL_DATE));
			return null;
		} else { // Uncomment to print out a Blockette050 each time
					// getStationMeta is called
					// blockette.print();
			logger.info("== MetaGenerator getStationMeta request:\t\t[{}]\t[{}]",
					station, EpochData.epochToDateString(timestamp));
		}

		StationMeta stationMeta = null;
		try {
			stationMeta = new StationMeta(blockette, timestamp);
		} catch (WrongBlocketteException e) {
			logger.error("Could not create new StationMeta(blockette) !!");
			System.exit(0); //TODO: Fix this System.exit(0) This shouldn't happen.
		}

		// Get this StationData's ChannelKeys and sort:
		Hashtable<ChannelKey, ChannelData> channels = stationData.getChannels();
		TreeSet<ChannelKey> keys = new TreeSet<>();
		keys.addAll(channels.keySet());
		for (ChannelKey key : keys) {
            // System.out.println("==Channel:"+key );
            ChannelData channel = channels.get(key);
            // ChannelMeta channelMeta = new ChannelMeta(key,timestamp);
            ChannelMeta channelMeta = new ChannelMeta(key, timestamp,
                    station);

            // See if this channel contains the requested epoch time and if
            // so return the key
            // (=Epoch Start timestamp)
            // channel.printEpochs();
            LocalDateTime epochTimestamp = channel.containsEpoch(timestamp);
            if (epochTimestamp != null) {
                EpochData epochData = channel.getEpoch(epochTimestamp);

                // If the epoch is closed, check that the end time is at
                // least 24 hours later than the requested time
                if (epochData.getEndTime() != null) {
                    if (epochData.getEndTime().compareTo(timestamp.plusDays(1)) < 0){
                        // set channelMeta.dayBreak = true
                        channelMeta.setDayBreak();
                    }
                }
                channelMeta.processEpochData(epochData);
                stationMeta.addChannel(key, channelMeta);
            }
        }

		return stationMeta;
	}
}
