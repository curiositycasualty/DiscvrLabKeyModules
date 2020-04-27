package org.labkey.sequenceanalysis.run.analysis;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.util.DepthOfCoverageWrapper;
import org.labkey.sequenceanalysis.run.variant.SNPEffStep;
import org.labkey.sequenceanalysis.run.variant.SnpEffWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LofreqAnalysis extends AbstractCommandPipelineStep<LofreqAnalysis.LofreqWrapper> implements AnalysisStep
{
    public static final String CATEGORY = "Lowfreq VCF";

    public LofreqAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new LofreqWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractAnalysisStepProvider<LofreqAnalysis>
    {
        public Provider()
        {
            super("LofreqAnalysis", "LoFreq Analysis", null, "This will run LowFreq, a tool designed to call low-frequency mutations in a sample, such as viral populations or bacteria.  It is recommended to run GATK's BQSR and IndelRealigner upstream of this tool.", Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam(SNPEffStep.GENE_PARAM, "Gene File", "This is the ID of a GTF or GFF3 file containing genes from this genome.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                    {{
                        put("extensions", Arrays.asList("gtf", "gff"));
                        put("width", 400);
                        put("allowBlank", false);
                    }}, null),
                    ToolParameterDescriptor.create("minCoverage", "Min Coverage For Consensus", "If provided, a consensus will only be called over regions with at least this depth", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 25)

            ), null, "http://csb5.github.io/lofreq/");
        }


        @Override
        public LofreqAnalysis create(PipelineContext ctx)
        {
            return new LofreqAnalysis(this, ctx);
        }
    }


    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();

        File outputVcf = new File(outputDir, FileUtil.getBaseName(inputBam) + ".lofreq.vcf.gz");
        File outputVcfSnpEff = new File(outputDir, FileUtil.getBaseName(inputBam) + ".lofreq.snpeff.vcf.gz");

        //LoFreq
        getWrapper().execute(inputBam, outputVcf, referenceGenome.getWorkingFastaFile(), SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger()));

        //Add depth for downstream use:
        File coverageOut = new File(outputDir, SequenceAnalysisService.get().getUnzippedBaseName(outputVcf.getName()) + ".coverage");

        DepthOfCoverageWrapper wrapper = new DepthOfCoverageWrapper(getPipelineCtx().getLogger());
        wrapper.run(Collections.singletonList(inputBam), coverageOut.getPath(), referenceGenome.getWorkingFastaFile(), null, true);
        if (!coverageOut.exists())
        {
            throw new PipelineJobException("Unable to find file: " + coverageOut.getPath());
        }

        //SnpEff:
        Integer geneFileId = getProvider().getParameterByName(SNPEffStep.GENE_PARAM).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        File snpEffBaseDir = SNPEffStep.checkOrCreateIndex(getPipelineCtx(), referenceGenome, geneFileId);

        SnpEffWrapper snpEffWrapper = new SnpEffWrapper(getPipelineCtx().getLogger());
        snpEffWrapper.runSnpEff(referenceGenome.getGenomeId(), geneFileId, snpEffBaseDir, outputVcf, outputVcfSnpEff, null);

        try
        {
            SequenceAnalysisService.get().ensureVcfIndex(outputVcfSnpEff, getPipelineCtx().getLogger());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        output.addIntermediateFile(outputVcf);
        output.addIntermediateFile(new File(outputVcf.getPath() + ".tbi"));

        int totalVariants = 0;
        int totalGT1 = 0;
        int totalGT50 = 0;
        int totalIndelGT1 = 0;
        try (VCFFileReader reader = new VCFFileReader(outputVcfSnpEff);CloseableIterator<VariantContext> it = reader.iterator())
        {
            while (it.hasNext())
            {
                VariantContext vc = it.next();
                totalVariants++;
                if (vc.hasAttribute("AF") && vc.getAttributeAsDouble("AF", 0.0) > 0.01)
                {
                    totalGT1++;
                    if (vc.hasAttribute("INDEL"))
                    {
                        totalIndelGT1++;
                    }
                }

                if (vc.hasAttribute("AF") && vc.getAttributeAsDouble("AF", 0.0) > 0.5)
                {
                    totalGT50++;
                }
            }
        }

        //generate consensus
        File script = new File(SequenceAnalysisService.get().getScriptPath(SequenceAnalysisModule.NAME, "external/viral_consensus.sh"));
        if (!script.exists())
        {
            throw new PipelineJobException("Unable to find script: " + script.getPath());
        }

        SimpleScriptWrapper consensusWrapper = new SimpleScriptWrapper(getPipelineCtx().getLogger());
        consensusWrapper.setWorkingDir(getPipelineCtx().getWorkingDirectory());

        Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
        if (maxThreads != null)
        {
            consensusWrapper.addToEnvironment("SEQUENCEANALYSIS_MAX_THREADS", maxThreads.toString());
        }

        //Create a BED file with all regions of coverage below MIN_COVERAGE:
        int minCoverage = getProvider().getParameterByName("minCoverage").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        int positionsSkipped = 0;
        int gapIntervals = 0;

        File mask = new File(outputDir, "mask.bed");
        try (CSVReader reader = new CSVReader(Readers.getReader(coverageOut), '\t');CSVWriter writer = new CSVWriter(IOUtil.openFileForBufferedUtf8Writing(mask), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            String[] line;

            Interval intervalOfCurrentGap = null;

            int i = 0;
            while ((line = reader.readNext()) != null)
            {
                i++;
                if (i == 1)
                {
                    continue;
                }

                String[] tokens = line[0].split(":");
                int depth = Integer.parseInt(line[1]);

                if (depth < minCoverage)
                {
                    positionsSkipped++;

                    if (intervalOfCurrentGap != null)
                    {
                        if (intervalOfCurrentGap.getContig().equals(tokens[0]))
                        {
                            //extend
                            intervalOfCurrentGap = new Interval(intervalOfCurrentGap.getContig(), intervalOfCurrentGap.getStart(), Integer.parseInt(tokens[1]));
                        }
                        else
                        {
                            //switched contigs, write and make new:
                            writer.writeNext(new String[]{intervalOfCurrentGap.getContig(), String.valueOf(intervalOfCurrentGap.getStart()-1), String.valueOf(intervalOfCurrentGap.getEnd())});
                            gapIntervals++;
                            intervalOfCurrentGap = new Interval(tokens[0], Integer.parseInt(tokens[1]), Integer.parseInt(tokens[1]));
                        }
                    }
                    else
                    {
                        //Not existing gap, just start one:
                        intervalOfCurrentGap = new Interval(tokens[0], Integer.parseInt(tokens[1]), Integer.parseInt(tokens[1]));
                    }
                }
                else
                {
                    //We just existed a gap, so write:
                    if (intervalOfCurrentGap != null)
                    {
                        writer.writeNext(new String[]{intervalOfCurrentGap.getContig(), String.valueOf(intervalOfCurrentGap.getStart()-1), String.valueOf(intervalOfCurrentGap.getEnd())});
                        gapIntervals++;
                    }

                    intervalOfCurrentGap = null;
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        getPipelineCtx().getLogger().info("Total positions with coverage below threshold (" + minCoverage + "): " + positionsSkipped);
        getPipelineCtx().getLogger().info("Total intervals of these gaps: " + gapIntervals);

        consensusWrapper.execute(Arrays.asList("/bin/bash", script.getPath(), inputBam.getPath(), referenceGenome.getWorkingFastaFile().getPath(), mask.getPath()));

        String description = String.format("Total Variants: %s\nTotal GT 1 PCT: %s\nTotal GT 50 PCT: %s\nTotal Indel GT 1 PCT: %s", totalVariants, totalGT1, totalGT50, totalIndelGT1);
        output.addSequenceOutput(outputVcfSnpEff, "LoFreq: " + rs.getName(), CATEGORY, rs.getReadsetId(), null, referenceGenome.getGenomeId(), description);
        output.addSequenceOutput(coverageOut, "Depth of Coverage: " + rs.getName(), "Depth of Coverage", rs.getReadsetId(), null, referenceGenome.getGenomeId(), description);

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }

    public static class LofreqWrapper extends AbstractCommandWrapper
    {
        public LofreqWrapper(Logger log)
        {
            super(log);
        }

        public File execute(File input, File outputVcf, File fasta, @Nullable Integer threads) throws PipelineJobException
        {
            List<String> args = new ArrayList<>();
            args.add(getExe().getPath());

            args.add(threads == null ? "call" : "call-parallel");
            if (threads != null)
            {
                args.add("--pp-threads");
                args.add(threads.toString());
            }

            args.add("--force-overwrite");

            //the tool does not seem reliable about respecting force-overwrite
            if (outputVcf.exists())
            {
                outputVcf.delete();
                File index = new File(outputVcf.getPath() + ".tbi");
                if (index.exists())
                {
                    index.delete();
                }
            }

            args.add("--call-indels");
            args.add("-f");
            args.add(fasta.getPath());

            args.add("-o");
            args.add(outputVcf.getPath());

            args.add(input.getPath());

            execute(args);

            if (!outputVcf.exists())
            {
                throw new PipelineJobException("Unable to find expected file: " + outputVcf.getPath());
            }

            try
            {
                SequenceAnalysisService.get().ensureVcfIndex(outputVcf, getLogger());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            return outputVcf;
        }

        private File getExe()
        {
            return SequencePipelineService.get().getExeForPackage("LOFREQPATH", "lofreq");
        }
    }
}
