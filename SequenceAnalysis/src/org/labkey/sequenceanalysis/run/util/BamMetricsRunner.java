package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.SAMFormatException;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.metrics.MetricsFile;
import htsjdk.samtools.reference.FastaSequenceIndex;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.samtools.reference.ReferenceSequence;
import org.apache.log4j.Logger;
import org.labkey.api.data.Table;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import picard.analysis.AlignmentSummaryMetrics;
import picard.analysis.AlignmentSummaryMetricsCollector;
import picard.analysis.MetricAccumulationLevel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 9/12/2014.
 */
public class BamMetricsRunner
{
    private Logger _log;
    private Map<String, ReferenceSequence> _references = new HashMap<>();

    public BamMetricsRunner(Logger log)
    {
        _log = log;
    }


    private ReferenceSequence getReferenceSequenceFromFasta(String refName, IndexedFastaSequenceFile indexedRef)
    {
        if (_references.containsKey(refName))
        {
            return _references.get(refName);
        }
        else
        {
            _references.put(refName, indexedRef.getSequence(refName));
            return _references.get(refName);
        }
    }

    public MetricsFile addMetricsForFile(File inputBam, File refFasta) throws PipelineJobException
    {
        File fai = new File(refFasta.getPath() + ".fai");
        try (SAMFileReader sam = new SAMFileReader(inputBam);IndexedFastaSequenceFile indexedRef = new IndexedFastaSequenceFile(refFasta, new FastaSequenceIndex(fai)))
        {
            sam.setValidationStringency(ValidationStringency.SILENT);
            AlignmentSummaryMetricsCollector collector = new AlignmentSummaryMetricsCollector(Collections.singleton(MetricAccumulationLevel.ALL_READS), sam.getFileHeader().getReadGroups(), true, new ArrayList<String>(), 5000, false);

            try (SAMRecordIterator it = sam.iterator())
            {
                int i = 0;
                long startTime = new Date().getTime();

                while (it.hasNext())
                {
                    i++;
                    SAMRecord r = it.next();

                    try
                    {
                        ReferenceSequence ref = r.getReadUnmappedFlag() ? null : getReferenceSequenceFromFasta(r.getReferenceName(), indexedRef);
                        if (ref != null && r.getBaseQualities().length > 0)
                        {
                            collector.acceptRecord(it.next(), ref);
                        }
                    }
                    catch (Exception e)
                    {
                        _log.warn(e.getMessage(), e);
                    }

                    if (i % 250000 == 0)
                    {
                        long newTime = new Date().getTime();
                        _log.info("processed " + i + " reads in " + ((newTime - startTime) / 1000) + " seconds");
                        startTime = newTime;
                    }
                }
            }

            collector.finish();

            MetricsFile metricsFile = new MetricsFile();
            collector.addAllLevelsToFile(metricsFile);

            indexedRef.close();

            return metricsFile;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}