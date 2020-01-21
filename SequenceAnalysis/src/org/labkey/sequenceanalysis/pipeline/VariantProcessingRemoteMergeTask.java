package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.util.FileType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class VariantProcessingRemoteMergeTask extends WorkDirectoryTask<VariantProcessingRemoteMergeTask.Factory>
{
    private static final String ACTION_NAME = "Merging VCFs";

    protected VariantProcessingRemoteMergeTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(VariantProcessingRemoteMergeTask.class);
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return PipelineJob.TaskStatus.running.toString();
        }

        public List<String> getProtocolActionNames()
        {
            List<String> allowableNames = new ArrayList<>();
            allowableNames.add(ACTION_NAME);

            return allowableNames;
        }

        @Override
        public boolean isJoin()
        {
            return true;
        }

        @Override
        public boolean isParticipant(PipelineJob job) throws IOException
        {
            if (job instanceof VariantProcessingJob)
            {
                if (!((VariantProcessingJob)job).isDoScatterByContig())
                {
                    job.getLogger().info("skipping VCF merge task");
                    return false;
                }
            }

            return super.isParticipant(job);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new VariantProcessingRemoteMergeTask(this, job);
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    private VariantProcessingJob getPipelineJob()
    {
        return (VariantProcessingJob)getJob();
    }

    @Override
    public @NotNull RecordedActionSet run() throws PipelineJobException
    {
        SequenceTaskHelper.logModuleVersions(getJob().getLogger());

        TaskFileManagerImpl manager = new TaskFileManagerImpl(getPipelineJob(), _wd.getDir(), _wd);

        List<String> contigs = getPipelineJob().getAllContigs();
        getJob().setStatus(PipelineJob.TaskStatus.running, "Combining Per Contig VFCs: " + contigs.size());

        RecordedAction action = new RecordedAction(ACTION_NAME);

        Map<String, File> finalVcfs = getPipelineJob().getFinalVCFs();
        List<File> toConcat = new ArrayList<>();
        for (String contig : getPipelineJob().getAllContigs())
        {
            if (!finalVcfs.containsKey(contig))
            {
                throw new PipelineJobException("Missing VCF for contig: " + contig);
            }

            File vcf = finalVcfs.get(contig);
            if (!vcf.exists())
            {
                throw new PipelineJobException("Missing VCF: " + vcf.getPath());
            }

            toConcat.add(vcf);
            manager.addInput(action, "Input VCF", vcf);
            manager.addIntermediateFile(vcf);
            manager.addIntermediateFile(new File(vcf.getPath() + ".tbi"));
        }

        String basename = SequenceAnalysisService.get().getUnzippedBaseName(toConcat.get(0).getName());
        File combined = SequenceAnalysisService.get().combineVcfs(toConcat, getPipelineJob().getAnalysisDirectory(), basename, getJob().getLogger());
        manager.addOutput(action, "Merged VCF", combined);

        SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> handler = getPipelineJob().getHandler();
        if (handler instanceof SequenceOutputHandler.TracksVCF)
        {
            Set<SequenceOutputFile> outputs = new HashSet<>();
            toConcat.forEach(f -> outputs.addAll(getPipelineJob().getOutputsToCreate().stream().filter(x -> f.equals(x.getFile())).collect(Collectors.toSet())));
            getJob().getLogger().debug("Total component outputs created: " + outputs.size());
            getPipelineJob().getOutputsToCreate().removeAll(outputs);
            getJob().getLogger().debug("Total SequenceOutputFiles on job after remove: " + getPipelineJob().getOutputsToCreate().size());

            SequenceOutputFile finalOutput = ((SequenceOutputHandler.TracksVCF)getPipelineJob().getHandler()).createFinalSequenceOutput(getJob(), combined, outputs);
            manager.addSequenceOutput(finalOutput);
        }
        else
        {
            throw new PipelineJobException("Handler does not support TracksVCF: " + handler.getName());
        }

        manager.deleteIntermediateFiles();
        manager.cleanup(Collections.singleton(action));

        return new RecordedActionSet(action);
    }
}