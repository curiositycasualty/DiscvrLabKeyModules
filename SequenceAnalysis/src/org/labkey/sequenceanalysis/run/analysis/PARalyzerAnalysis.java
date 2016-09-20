package org.labkey.sequenceanalysis.run.analysis;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.run.alignment.FastaToTwoBitRunner;
import org.labkey.sequenceanalysis.run.util.PARalyzerRunner;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 7/3/2014
 * Time: 11:29 AM
 */
public class PARalyzerAnalysis extends AbstractCommandPipelineStep<PARalyzerRunner> implements AnalysisStep
{
    public PARalyzerAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new PARalyzerRunner(ctx.getLogger()));
    }

    public static class Provider extends AbstractAnalysisStepProvider<PARalyzerAnalysis>
    {
        public Provider()
        {
            super("PARalyzer", "Run PARalyzer", null, "This step will run PARalyzer to generate a high resolution map of interaction sites between RNA-binding proteins and their targets. The algorithm utilizes the deep sequencing reads generated by PAR-CLIP (Photoactivatable-Ribonucleoside-Enhanced Crosslinking and Immunoprecipitation) data.", Arrays.asList(
                    ToolParameterDescriptor.create("MINIMUM_READ_COUNT_PER_GROUP", "Minimum Read Count Per Group", "Minimum number of reads required to call a group", "ldk-integerfield", null, 10),
                    ToolParameterDescriptor.create("MINIMUM_READ_COUNT_PER_CLUSTER", "Minimum Read Count Per Cluster", "Minimum number of reads required to call a cluster", "ldk-integerfield", null, 1),
                    ToolParameterDescriptor.create("MINIMUM_READ_COUNT_FOR_KDE", "Minimum Read Count For KDE", "Minimum read depth at a location to make a KDE estimate", "ldk-integerfield", null, 5),
                    ToolParameterDescriptor.create("MINIMUM_CLUSTER_SIZE", "Minimum Cluster Size", "Minimum length required for a cluster to be reported", "ldk-integerfield", null, 8),
                    ToolParameterDescriptor.create("MINIMUM_CONVERSION_LOCATIONS_FOR_CLUSTER", "Minimum Conversion Locations For Cluster", "Minimum number of separate locations to have a reported conversion for a cluster to be reported", "ldk-integerfield", null, 1),
                    ToolParameterDescriptor.create("MINIMUM_CONVERSION_COUNT_FOR_CLUSTER", "Minimum Conversion Count For Cluster", "Minimum number of conversion events within a region to report a cluster", "ldk-integerfield", null, 1),
                    ToolParameterDescriptor.create("MINIMUM_READ_COUNT_FOR_CLUSTER_INCLUSION", "Minimum Read Count For Cluster Inclusion", "Minimum read depth for a location to be included within a cluster", "ldk-integerfield", null, 5),
                    ToolParameterDescriptor.create("MINIMUM_READ_LENGTH", "Minimum Read Length", "Minimum length of mapped read to be included in the analysis", "ldk-integerfield", null, 13),
                    ToolParameterDescriptor.create("MAXIMUM_NUMBER_OF_NON_CONVERSION_MISMATCHES", "Maximum Number of Non-conversion Mismatches", "Maximum number of non-conversion mismatches of a mapped read to be included in the analysis", "ldk-integerfield", null, 0)
            ), null, null);
        }

        @Override
        public PARalyzerAnalysis create(PipelineContext ctx)
        {
            return new PARalyzerAnalysis(this, ctx);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();
        output.addInput(inputBam, "BAM File");

        Map<String, String> toolParams = new HashMap<>();
        for (ToolParameterDescriptor pd : (List<ToolParameterDescriptor>)getProvider().getParameters())
        {
            String val = pd.extractValue(getPipelineCtx().getJob(), getProvider());
            if (!StringUtils.isEmpty(val))
            {
                toolParams.put(pd.getName(), val);
            }
        }

        FastaToTwoBitRunner runner = new FastaToTwoBitRunner(getPipelineCtx().getLogger());
        File twoBit = runner.createIndex(referenceGenome, outputDir, getPipelineCtx());
        if (!twoBit.exists())
        {
            throw new PipelineJobException("unable to find expected 2bit file: " + twoBit.getPath());
        }

        File clusterFile = new PARalyzerRunner(getPipelineCtx().getLogger()).execute(inputBam, outputDir, twoBit, toolParams);
        output.addOutput(clusterFile, "Cluster File");
        output.addSequenceOutput(clusterFile, rs.getName() + " Clusters", "Cluster File", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);

        //write parameter file
        output.addIntermediateFile(new File(outputDir, FileUtil.getBaseName(inputBam) + ".paralyzer.ini"));

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }
}
