package org.spectra.cluster.tools;

import org.apache.commons.cli.*;
import org.spectra.cluster.exceptions.MissingParameterException;
import org.spectra.cluster.tools.utils.IProgressListener;
import org.spectra.cluster.tools.utils.ProgressUpdate;
import org.spectra.cluster.util.DefaultParameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.InvalidParameterException;
import java.util.ArrayList;
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
public class SpectraClusterTool implements IProgressListener {

    public static final boolean DELETE_TEMPORARY_CLUSTERING_RESULTS = true;

    public DefaultParameters defaultParameters = new DefaultParameters();

    private boolean verbose;

    public static void main(String[] args) {
        SpectraClusterTool instance = new SpectraClusterTool();
        instance.run(args);
    }

    private void run(String[] args) {
        CommandLineParser parser = new PosixParser();

        try {
            CommandLine commandLine = parser.parse(CliOptions.getOptions(), args);

            // HELP
            if (commandLine.hasOption(CliOptions.OPTIONS.HELP.getValue())) {
                printUsage();
                return;
            }

            // RESULT FILE PATH
            if (!commandLine.hasOption(CliOptions.OPTIONS.OUTPUT_PATH.getValue()))
                throw new MissingParameterException("Missing required option " + CliOptions.OPTIONS.OUTPUT_PATH.getValue());
            File finalResultFile = new File(commandLine.getOptionValue(CliOptions.OPTIONS.OUTPUT_PATH.getValue()));

            if (finalResultFile.exists())
                throw new Exception("Result file " + finalResultFile + " already exists");

            // NUMBER OF ROUNDS
            int rounds = 4;
            if (commandLine.hasOption(CliOptions.OPTIONS.ROUNDS.getValue()))
                rounds = Integer.parseInt(commandLine.getOptionValue(CliOptions.OPTIONS.ROUNDS.getValue()));

            // START THRESHOLD
            float startThreshold = defaultParameters.getThresholdStart();
            if (commandLine.hasOption(CliOptions.OPTIONS.START_THRESHOLD.getValue()))
                startThreshold = Float.parseFloat(commandLine.getOptionValue(CliOptions.OPTIONS.START_THRESHOLD.getValue()));

            // END THRESHOLD
            float endThreshold = 0.99F;
            if (commandLine.hasOption(CliOptions.OPTIONS.END_THRESHOLD.getValue()))
                endThreshold = Float.parseFloat(commandLine.getOptionValue(CliOptions.OPTIONS.END_THRESHOLD.getValue()));

//            // PRECURSOR TOLERANCE
//            if (commandLine.hasOption(CliOptions.OPTIONS.PRECURSOR_TOLERANCE.getValue())) {
//                float precursorTolerance = Float.parseFloat(commandLine.getOptionValue(CliOptions.OPTIONS.PRECURSOR_TOLERANCE.getValue()));
//                Defaults.setDefaultPrecursorIonTolerance(precursorTolerance);
//            }
//
//            // FRAGMENT ION TOLERANCE
//            if (commandLine.hasOption(CliOptions.OPTIONS.FRAGMENT_TOLERANCE.getValue())) {
//                float fragmentTolerance = Float.parseFloat(commandLine.getOptionValue(CliOptions.OPTIONS.FRAGMENT_TOLERANCE.getValue()));
//                Defaults.setFragmentIonTolerance(fragmentTolerance);
//            }
//
//            // BINARY TMP DIR
//            if (commandLine.hasOption(CliOptions.OPTIONS.BINARY_TMP_DIR.getValue())) {
//                File binaryTmpDirectory = new File(commandLine.getOptionValue(CliOptions.OPTIONS.BINARY_TMP_DIR.getValue()));
//                spectraClusterStandalone.setTemporaryDirectory(binaryTmpDirectory);
//            }
//
//            // KEEP BINARY FILES
//            if (commandLine.hasOption(CliOptions.OPTIONS.KEEP_BINARY_FILE.getValue())) {
//                spectraClusterStandalone.setKeepBinaryFiles(true);
//            }
//
//            // FAST MODE
//            if (commandLine.hasOption(CliOptions.OPTIONS.FAST_MODE.getValue())) {
//                spectraClusterStandalone.setUseFastMode(true);
//            }
//
//            // VERBOSE
//            if (commandLine.hasOption(CliOptions.OPTIONS.VERBOSE.getValue())) {
//                spectraClusterStandalone.setVerbose(true);
//            }
//
//            // REMOVE QUANT PEAKS
//            if (commandLine.hasOption(CliOptions.OPTIONS.REMOVE_REPORTER_PEAKS.getValue())) {
//                String quantTypeString = commandLine.getOptionValue(CliOptions.OPTIONS.REMOVE_REPORTER_PEAKS.getValue());
//                RemoveReporterIonPeaksFunction.REPORTER_TYPE reporterType;
//
//                if (quantTypeString.toLowerCase().equals("itraq")) {
//                    reporterType = RemoveReporterIonPeaksFunction.REPORTER_TYPE.ITRAQ;
//                }
//                else if (quantTypeString.toLowerCase().equals("tmt")) {
//                    reporterType = RemoveReporterIonPeaksFunction.REPORTER_TYPE.TMT;
//                }
//                else if (quantTypeString.toLowerCase().equals("all")) {
//                    reporterType = RemoveReporterIonPeaksFunction.REPORTER_TYPE.ALL;
//                }
//                else {
//                    throw new MissingParameterException("Invalid reporter type defined. Valid values are " +
//                            "'ITRAQ', 'TMT', and 'ALL'.");
//                }
//
//                spectraClusterStandalone.setReporterType(reporterType);
//            }
//
//            // FILES TO PROCESS
//            String[] peaklistFilenames = commandLine.getArgs();
//
//            // RE-USE BINARY FILES
//            boolean reUseBinaryFiles = commandLine.hasOption(CliOptions.OPTIONS.REUSE_BINARY_FILES.getValue());
//
//            // if re-use is set, binaryTmpDirectory is required and merging is impossible
//            if (reUseBinaryFiles && !commandLine.hasOption(CliOptions.OPTIONS.BINARY_TMP_DIR.getValue()))
//                throw new MissingParameterException("Missing required option '" + CliOptions.OPTIONS.BINARY_TMP_DIR.getValue() + "' with " + CliOptions.OPTIONS.REUSE_BINARY_FILES.getValue());
//
//            if (reUseBinaryFiles && peaklistFilenames.length > 0)
//                System.out.println("WARNING: " + CliOptions.OPTIONS.REUSE_BINARY_FILES.getValue() + " set, input files will be ignored");
//
//            // make sure input files were set
//            if (!reUseBinaryFiles && peaklistFilenames.length < 1)
//                throw new MissingParameterException("No spectrum files passed. Please list the peak list files to process after the command.");
//
//            // add the filters
//            List<String> addedFilters = new ArrayList<String>();
//            if (commandLine.hasOption(CliOptions.OPTIONS.FILTER.getValue())) {
//                for (String filterName : commandLine.getOptionValues(CliOptions.OPTIONS.FILTER.getValue())) {
//                    ClusteringSettings.SPECTRUM_FILTER filter = ClusteringSettings.SPECTRUM_FILTER.getFilterForName(filterName);
//
//                    if (filter == null) {
//                        throw new InvalidParameterException("Error: Unknown filter name passed: '" + filterName + "'");
//                    }
//
//                    ClusteringSettings.addIntitalSpectrumFilter(filter.filter);
//                    addedFilters.add(filterName);
//                }
//            }
//
//            /**
//             * Advanced options
//             */
//            // MIN NUMBER COMPARISONS
//            Defaults.setMinNumberComparisons(10000);
//            if (commandLine.hasOption(CliOptions.OPTIONS.ADVANCED_MIN_NUMBER_COMPARISONS.getValue())) {
//                int minComparisons = Integer.parseInt(commandLine.getOptionValue(CliOptions.OPTIONS.ADVANCED_MIN_NUMBER_COMPARISONS.getValue()));
//                Defaults.setMinNumberComparisons(minComparisons);
//            }
//
//            // N HIGHEST PEAKS
//            if (commandLine.hasOption(CliOptions.OPTIONS.ADVANCED_NUMBER_PREFILTERED_PEAKS.getValue())) {
//                int nHighestPeaks = Integer.parseInt(commandLine.getOptionValue(CliOptions.OPTIONS.ADVANCED_NUMBER_PREFILTERED_PEAKS.getValue()));
//                ClusteringSettings.setLoadingSpectrumFilter(new HighestNPeakFunction(nHighestPeaks));
//            }
//
//            // MGF COMMENT SUPPORT
//            ClusteringSettings.disableMGFCommentSupport = commandLine.hasOption(CliOptions.OPTIONS.ADVANCED_DISABLE_MGF_COMMENTS.getValue());
//
//            /**
//             * ------ Learn the CDF if set --------
//             */
//            if (commandLine.hasOption(CliOptions.OPTIONS.ADVANCED_LEARN_CDF.getValue())) {
//                String cdfOuputFilename = commandLine.getOptionValue(CliOptions.OPTIONS.ADVANCED_LEARN_CDF.getValue());
//                File cdfOutputFile = new File(cdfOuputFilename);
//
//                if (cdfOutputFile.exists()) {
//                    throw new Exception("CDF output file " + cdfOuputFilename + " already exists.");
//                }
//
//                CdfLearner cdfLearner = new CdfLearner();
//                System.out.println("Learning CDF...");
//                CdfResult cdfResult = cdfLearner.learnCumulativeDistribution(peaklistFilenames, paralellJobs);
//
//                // write it to the file
//                FileWriter writer = new FileWriter(cdfOutputFile);
//                writer.write(cdfResult.toString());
//                writer.close();
//
//                System.out.println("CDF successfully written to " + cdfOuputFilename);
//                return;
//            }
//
//            /**
//             * ------ Load the CDF from file -------
//             */
//            if (commandLine.hasOption(CliOptions.OPTIONS.ADVANCED_LOAD_CDF_FILE.getValue())) {
//                BufferedReader reader = new BufferedReader(
//                        new FileReader(
//                                commandLine.getOptionValue(CliOptions.OPTIONS.ADVANCED_LOAD_CDF_FILE.getValue())));
//
//                StringBuilder cdfString = new StringBuilder();
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    cdfString.append(line);
//                }
//                reader.close();
//
//                Defaults.setCumulativeDistributionFunction(CumulativeDistributionFunction.fromString(cdfString.toString()));
//            }
//
//            /**
//             * ------- THE ACTUAL LOGIC STARTS HERE -----------
//             */
//            printSettings(finalResultFile, paralellJobs, startThreshold, endThreshold, rounds, spectraClusterStandalone.isKeepBinaryFiles(),
//                    spectraClusterStandalone.getTemporaryDirectory(), peaklistFilenames, reUseBinaryFiles, spectraClusterStandalone.isUseFastMode(),
//                    addedFilters);
//
//            spectraClusterStandalone.addProgressListener(this);
//
//            // make sure binary files exist
//            if (reUseBinaryFiles) {
//                File binaryFileDirectory = new File(spectraClusterStandalone.getTemporaryDirectory(), "spectra");
//                if (!binaryFileDirectory.isDirectory()) {
//                    reUseBinaryFiles = false;
//                    System.out.println("No binary files found. Re-creating them...");
//                }
//            }
//
//            if (reUseBinaryFiles) {
//                spectraClusterStandalone.clusterExistingBinaryFiles(
//                        spectraClusterStandalone.getTemporaryDirectory(), thresholds, finalResultFile);
//            }
//            else {
//                List<File> peaklistFiles = new ArrayList<File>(peaklistFilenames.length);
//                for (String filename : peaklistFilenames) {
//                    peaklistFiles.add(new File(filename));
//                }
//
//                spectraClusterStandalone.clusterPeaklistFiles(peaklistFiles, thresholds, finalResultFile);
//            }
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

    private void printSettings(File finalResultFile, int nMajorPeakJobs, float startThreshold,
                               float endThreshold, int rounds, boolean keepBinaryFiles, File binaryTmpDirectory,
                               String[] peaklistFilenames, boolean reUseBinaryFiles, boolean fastMode,
                               List<String> addedFilters) {
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
        System.out.println("Precursor tolerance: " + defaultParameters.getPrecursorIonTolerance());
        System.out.println("Fragment ion tolerance: " + defaultParameters.getFragmentIonTolerance());

        // used filters
        System.out.print("Added filters: ");
        for (int i = 0; i < addedFilters.size(); i++) {
            if (i > 0) {
                System.out.print(", ");
            }
            System.out.print(addedFilters.get(i));
        }
        System.out.println("");

//        // only show certain settings if they were changed
//        if (Defaults.getMinNumberComparisons() != Defaults.DEFAULT_MIN_NUMBER_COMPARISONS)
//            System.out.println("Minimum number of comparisons: " + Defaults.getMinNumberComparisons());

        System.out.println();
    }

    private void printUsage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Spectra Cluster ",
                "Clusters the spectra found in Mass Spectrometry file formats and writes the results in a text-based file.\n",
                CliOptions.getOptions(), "\n\n", true);
    }

    @Override
    public void onProgressUpdate(ProgressUpdate progressUpdate) {
        System.out.println(progressUpdate.getMessage());
    }
}
