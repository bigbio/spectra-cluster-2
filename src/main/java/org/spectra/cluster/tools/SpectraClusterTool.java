package org.spectra.cluster.tools;

import io.github.bigbio.pgatk.io.mapcache.IMapStorage;
import io.github.bigbio.pgatk.io.objectdb.ObjectsDB;
import io.github.bigbio.pgatk.io.properties.IPropertyStorage;
import io.github.bigbio.pgatk.io.properties.PropertyStorageFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.PosixParser;
import org.spectra.cluster.binning.IClusterBinner;
import org.spectra.cluster.binning.SimilarSizedClusterBinner;
import org.spectra.cluster.consensus.AverageConsensusSpectrumBuilder;
import org.spectra.cluster.engine.GreedyClusteringEngine;
import org.spectra.cluster.exceptions.MissingParameterException;
import org.spectra.cluster.filter.binaryspectrum.HighestPeakPerBinFunction;
import org.spectra.cluster.io.cluster.ClusterStorageFactory;
import org.spectra.cluster.io.cluster.ObjectDBGreedyClusterStorage;
import org.spectra.cluster.io.result.IClusteringResultWriter;
import org.spectra.cluster.io.result.MspWriter;
import org.spectra.cluster.io.spectra.MzSpectraReader;
import org.spectra.cluster.model.cluster.GreedySpectralCluster;
import org.spectra.cluster.model.cluster.ICluster;
import org.spectra.cluster.model.cluster.IClusterProperties;
import org.spectra.cluster.normalizer.BasicIntegerNormalizer;
import org.spectra.cluster.normalizer.MaxPeakNormalizer;
import org.spectra.cluster.tools.utils.IProgressListener;
import org.spectra.cluster.tools.utils.ProgressUpdate;
import org.spectra.cluster.util.ClusteringParameters;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * This code is licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * ==Overview==
 *
 * @author ypriverol on 18/10/2018.
 */
@Slf4j
public class SpectraClusterTool implements IProgressListener {

    public static void main(String[] args) {
        SpectraClusterTool instance = new SpectraClusterTool();
        instance.run(args);
    }

    private void run(String[] args) {
        CommandLineParser parser = new PosixParser();

        try {
            // parse the command line
            CommandLine commandLine = parser.parse(CliOptions.getOptions(), args);

            // HELP
            if (commandLine.hasOption(CliOptions.OPTIONS.HELP.getValue())) {
                printUsage();
                return;
            }

            // create the object to hold the parameters
            ClusteringParameters clusteringParameters = new ClusteringParameters();

            // if a config file is set, merge the config file parameters first
            if (commandLine.hasOption(CliOptions.OPTIONS.CONFIG_FILE.getValue())) {
                String configFilePath = commandLine.getOptionValue(CliOptions.OPTIONS.CONFIG_FILE.getValue());

                clusteringParameters.mergeParameters(configFilePath);
                log.info("Configuration loaded from " + configFilePath);
            }

            // merge all other command line parameters overwriting the config file values
            clusteringParameters.mergeCommandLineArgs(commandLine);

            // make sure that the set parameters are valid
            checkParameterValidity(clusteringParameters);

            // all remaining parameters are treated as input files
            String[] peakFiles = commandLine.getArgs();

            if (peakFiles.length < 1) {
                printUsage();
                throw new MissingParameterException("Missing input files");
            }

            // create the temporary directory in case it wasn't set
            if (clusteringParameters.getBinaryDirectory() == null)
                clusteringParameters.setBinaryDirectory(createTempFolderPath(clusteringParameters.getOutputFile(), "binary-clustering-files"));

            // start the clustering
            runClustering(peakFiles, clusteringParameters);

            // exit nicely
            System.exit(0);
        } catch (MissingParameterException e) {
            System.out.println("Error: " + e.getMessage() + "\n\n");
            printUsage();

            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error: " + e.getMessage());

            System.exit(1);
        }
    }

    /**
     * Run the clustering process using the defined parameters.
     *
     * @param clusteringParameters
     * @throws Exception
     */
    private void runClustering(String[] peakFiles, ClusteringParameters clusteringParameters) throws Exception {
        // create the storage to load the spectra
        IPropertyStorage propertyStorage = PropertyStorageFactory
                .buildDynamicLevelDBPropertyStorage(new File(clusteringParameters.getBinaryDirectory()));

        File clusterStorageDir = createUniqueDirectory(new File(clusteringParameters.getBinaryDirectory(), "loaded-clusters"));
        IMapStorage<ICluster> clusterStorage = ClusterStorageFactory.buildTemporaryDynamicStorage(clusterStorageDir, GreedySpectralCluster.class);

        // load the spectra
        IClusterProperties[] loadedClusters = loadInputFiles(peakFiles, clusteringParameters, propertyStorage, clusterStorage);

        // run the clustering in parallel
        File clusteringTmpDir = createUniqueDirectory(new File(clusteringParameters.getBinaryDirectory(), "clustering-files"));

        IClusterBinner clusterBinner = new SimilarSizedClusterBinner(
                2 * clusteringParameters.getIntPrecursorTolerance(),
                1_000, !clusteringParameters.isIgnoreCharge());

        LocalParallelBinnedClusteringTool clusteringTool = new LocalParallelBinnedClusteringTool(
                clusteringParameters.getNThreads(), clusteringTmpDir, clusterBinner, GreedySpectralCluster.class);

        LocalDateTime startTime = LocalDateTime.now();
        log.debug("Starting clustering...");

        clusteringTool.runClustering(loadedClusters, clusterStorage, clusteringParameters);

        // some nice output
        LocalDateTime clusteringCompleteTime = LocalDateTime.now();
        log.debug(String.format("Clustering completed in in %d seconds",
                Duration.between(startTime, clusteringCompleteTime).getSeconds()));

        log.info("Result file written to " + clusteringParameters.getOutputFile());

        // create the MSP file
        if (clusteringParameters.isOutputMsp()) {
            Path mspFile = Paths.get(clusteringParameters.getOutputFile().toString() + ".msp");
            IClusteringResultWriter writer = new MspWriter(new AverageConsensusSpectrumBuilder(clusteringParameters));

            // open the result file again
            ObjectDBGreedyClusterStorage resultReader = new ObjectDBGreedyClusterStorage(
                    new ObjectsDB(clusteringParameters.getOutputFile().getAbsolutePath(), false));

            log.info("Saving clustering results as MSP file at " + mspFile.toString());
            writer.writeResult(mspFile, resultReader, propertyStorage);
        }

        // TODO: Create .clustering output file

        // close the storage
        clusterStorage.close();
        propertyStorage.close();
    }

    /**
     * Loads all spectra from the defined peak list files as IClusters. The clusters are stored in the defined clusterStorage.
     * The cluster's properties are stored in the propertyStorage. The cluster's basic properties are returned as an array.
     *
     * @param peakFiles Files to load.
     * @param clusteringParameters Clustering parameters to use.
     * @param propertyStorage The property storage.
     * @param clusterStorage Storage to store the clusters in.
     * @return The IClusterProperties of the loaded clusters.
     */
    private IClusterProperties[] loadInputFiles(String[] peakFiles, ClusteringParameters clusteringParameters,
                                                IPropertyStorage propertyStorage, IMapStorage<ICluster> clusterStorage)
                                                throws Exception {
        log.debug(String.format("Loading spectra from %d input files", peakFiles.length));
        LocalDateTime startTime = LocalDateTime.now();

        // convert the input files to file objects
        File[] inputFiles = Arrays.stream(peakFiles)
                .map(File::new)
                .toArray(File[]::new);

        // load the spectra using an MzSpectraReader
        MzSpectraReader reader = new MzSpectraReader( clusteringParameters.createMzBinner(), new MaxPeakNormalizer(),
                new BasicIntegerNormalizer(), new HighestPeakPerBinFunction(), clusteringParameters.createLoadingFilter(),
                GreedyClusteringEngine.COMPARISON_FILTER, clusteringParameters.createGreedyClusteringEngine(), inputFiles);

        // create the iterator to load the clusters
        Iterator<ICluster> iterator = reader.readClusterIterator(propertyStorage);

        List<IClusterProperties> loadedClusters = new ArrayList<>(1000);

        while (iterator.hasNext()) {
            ICluster cluster = iterator.next();

            // all clusters are primarily stored in the cluster storage
            clusterStorage.put(cluster.getId(), cluster);

            // only retain the basic properties
            loadedClusters.add(cluster.getProperties());
        }

        // some nice output
        LocalDateTime loadingCompleteTime = LocalDateTime.now();
        log.debug(String.format("Loaded %d spectra in %d seconds", loadedClusters.size(),
                Duration.between(startTime, loadingCompleteTime).getSeconds()));

        return loadedClusters.toArray(new IClusterProperties[0]);
    }

    /**
     * Create a new unique directory name. In case the target name already exists, a new directory with the name
     * {target name}-{N} is created.
     *
     * @param targetDirectoryName The target directory name / path.
     * @return The File object representing the finally created unique directory.
     * @throws IOException Thrown if creating the directory failed.
     */
    private File createUniqueDirectory(File targetDirectoryName) throws IOException {
        int iteration = 1;
        String orgPath = targetDirectoryName.getAbsolutePath();

        while (targetDirectoryName.exists()) {
            targetDirectoryName = new File(orgPath + "-" + String.valueOf(iteration));
            iteration++;
        }

        // now that the directory does not yet exist, create it
        if (!targetDirectoryName.mkdir()) {
            throw new IOException("Failed to create directory " + targetDirectoryName.getAbsolutePath());
        }

        return targetDirectoryName;
    }

    /**
     * Ensures that the set user parameters are valid. In case they are not, an Exception is thrown.
     *
     * @param clusteringParameters The set parameters
     * @throws Exception In case something is invalid.
     * @throws MissingParameterException
     */
    private void checkParameterValidity(ClusteringParameters clusteringParameters) throws Exception, MissingParameterException {
        // RESULT FILE PATH
        if (clusteringParameters.getOutputFile() == null)
            throw new MissingParameterException("Missing required option " +
                    CliOptions.OPTIONS.OUTPUT_PATH.getValue());

        // ensure that the output file does not exist
        if (clusteringParameters.getOutputFile().exists())
            throw new Exception("Result file " + clusteringParameters.getOutputFile().getAbsolutePath() + " already exists");

        // check whether the fragment tolerance is valid
        if (!"high".equalsIgnoreCase(clusteringParameters.getFragmentIonPrecision()) &&
            !"low".equalsIgnoreCase(clusteringParameters.getFragmentIonPrecision())) {
            throw new Exception("Invalid fragment precision set. Allowed values are 'low' and 'high'");
        }
    }

    /**

     TODO: update print function
    private void printSettings(File finalResultFile, int nMajorPeakJobs, float startThreshold,
                               float endThreshold, int rounds, boolean keepBinaryFiles, File binaryTmpDirectory,
                               String[] peaklistFilenames, boolean reUseBinaryFiles, boolean fastMode,
                               List<String> addedFilters, String fragmentPrecision) {
        System.out.println("Spectra Cluster API Version 2.0");
        System.out.println("Created by Yasset Perez-Riverol & Johannes Griss\n");

        System.out.println("-- Settings --");
        System.out.println("Number of threads: " + String.valueOf(nMajorPeakJobs));
        System.out.println("Thresholds: " + String.valueOf(startThreshold) + " - " + String.valueOf(endThreshold) + " in " + rounds + " rounds");
        System.out.println("Keeping binary files: " + (keepBinaryFiles ? "true" : "false"));
        System.out.println("Binary file directory: " + binaryTmpDirectory);
        System.out.println("Result file: " + finalResultFile);
        System.out.println("Reuse binary files: " + (reUseBinaryFiles ? "true" : "false"));
        System.out.println("Input files: " + peaklistFilenames.length);
        System.out.println("Using fast mode: " + (fastMode ? "yes" : "no"));

        System.out.println("\nOther settings:");
        System.out.println("Precursor tolerance: " + clusteringParameters.getPrecursorIonTolerance());
        System.out.println("Fragment ion precision: " + fragmentPrecision);

        // used filters
        System.out.print("Added filters: ");
        for (int i = 0; i < addedFilters.size(); i++) {
            if (i > 0) {
                System.out.print(", ");
            }
            System.out.print(addedFilters.get(i));
        }
        System.out.println();

//        // only show certain settings if they were changed
//        if (Defaults.getMinNumberComparisons() != Defaults.DEFAULT_MIN_NUMBER_COMPARISONS)
//            System.out.println("Minimum number of comparisons: " + Defaults.getMinNumberComparisons());

        System.out.println();
    }*/

    private void printUsage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Spectra Cluster ",
                "Clusters the spectra found in Mass Spectrometry file formats and writes the results in a text-based file.\n",
                CliOptions.getOptions(), "\n\n", true);
    }

    private String createTempFolderPath(File outputFile, String tempFolder) {
        //check directory
        String finalPath;
        File directory = new File(outputFile.getParentFile(), tempFolder);
        if(directory.exists())
            finalPath = directory.getAbsolutePath();
        else{
            directory.mkdirs();
            finalPath = directory.getAbsolutePath();
        }
        return finalPath;
    }

    @Override
    public void onProgressUpdate(ProgressUpdate progressUpdate) {
        System.out.println(progressUpdate.getMessage());
    }
}
