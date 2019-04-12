package org.spectra.cluster.tools;

import org.junit.Ignore;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SpectraClusterToolTest {
    @Ignore
    @Test
    public void testLocalBenchmark() throws Exception {
        Path resultFile = Paths.get("/tmp/test.clustering");

        if (Files.exists(resultFile))
            Files.delete(resultFile);

        // create the args
        String[] args = {
              "-o", resultFile.toAbsolutePath().toString(),
              "-p", "1", // precursor tolerance
              "-f", "low", // fragment tolerance
              "-mc", "0", // minimum comparisons (auto)
              "-s", "1", // start threshold
              "-e", "0.99", // end-threshold
              "-r", "5", // rounds
              "/home/jg/Projects/Testfiles/melanoma_heterogeneity/Melanom_metastasen_Griss_P-B_1_151120061620.fdr01.msgf.mgf",
                "/home/jg/Projects/Testfiles/melanoma_heterogeneity/Melanom_metastasen_Griss_P-B_2_151120164543.fdr01.msgf.mgf"
        };

        // start the clustering
        SpectraClusterTool.main(args);
    }
}