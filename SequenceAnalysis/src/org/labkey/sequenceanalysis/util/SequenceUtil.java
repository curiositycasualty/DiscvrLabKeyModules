package org.labkey.sequenceanalysis.util;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.Interval;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.CloseableTribbleIterator;
import htsjdk.tribble.FeatureReader;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFFileReader;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 11/20/12
 * Time: 9:49 PM
 */
public class SequenceUtil
{
    public static FILETYPE inferType(File file)
    {
        for (FILETYPE f : FILETYPE.values())
        {
            FileType ft = f.getFileType();
            if (ft.isType(file))
                return f;
        }
        return null;
    }

    public static enum FILETYPE
    {
        fastq(Arrays.asList(".fastq", ".fq"), true),
        fasta(".fasta"),
        bam(".bam"),
        sff(".sff");

        private List<String> _extensions;
        private boolean _allowGzip;

        FILETYPE(String extension)
        {
            this(Arrays.asList(extension), false);
        }

        FILETYPE(List<String> extensions, boolean allowGzip)
        {
            _extensions = extensions;
            _allowGzip = allowGzip;
        }

        public FileType getFileType()
        {
            return new FileType(_extensions, _extensions.get(0), false, _allowGzip ? FileType.gzSupportLevel.SUPPORT_GZ : FileType.gzSupportLevel.NO_GZ);
        }

        public String getPrimaryExtension()
        {
            return _extensions.get(0);
        }
    }

    public static long getLineCount(File f) throws PipelineJobException
    {
        try (BufferedReader reader = new BufferedReader(new FileReader(f)))
        {
            long i = 0;
            while (reader.readLine() != null)
            {
                i++;
            }

            return i;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public static boolean hasLineCount(File f) throws PipelineJobException
    {
        try (BufferedReader reader = new BufferedReader(new FileReader(f)))
        {
            boolean hasLine = false;
            while (reader.readLine() != null)
            {
                hasLine = true;
                break;
            }

            return hasLine;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public static long getAlignmentCount(File bam) throws IOException
    {
        SamReaderFactory fact = SamReaderFactory.makeDefault();
        fact.validationStringency(ValidationStringency.SILENT);
        try (SamReader reader = fact.open(bam))
        {
            try (SAMRecordIterator it = reader.iterator())
            {
                long count = 0;
                while (it.hasNext())
                {
                    it.next();
                    count++;
                }

                return count;
            }
        }
    }

    public static void logAlignmentCount(File bam, Logger log) throws IOException
    {
        SamReaderFactory fact = SamReaderFactory.makeDefault();
        fact.validationStringency(ValidationStringency.SILENT);
        try (SamReader reader = fact.open(bam))
        {
            try (SAMRecordIterator it = reader.iterator())
            {
                long total = 0;
                long primary = 0;
                long unaligned = 0;
                while (it.hasNext())
                {
                    SAMRecord r = it.next();
                    total++;

                    if (r.getReadUnmappedFlag())
                    {
                        unaligned++;
                    }
                    else if (!r.isSecondaryOrSupplementary())
                    {
                        primary++;
                    }
                }

                log.info("file size: " + FileUtils.byteCountToDisplaySize(bam.length()));
                log.info("total alignments: " + total);
                log.info("primary alignments: " + primary);
                log.info("unaligned: " + unaligned);
            }
        }
    }

    public static void writeFastaRecord(Writer writer, String header, String sequence, int lineLength) throws IOException
    {
        writer.write(">" + header + "\n");
        if (sequence != null)
        {
            int len = sequence.length();
            for (int i=0; i<len; i+=lineLength)
            {
                writer.write(sequence.substring(i, Math.min(len, i + lineLength)) + "\n");
            }
        }
    }

    public static void bgzip(File input, File output)
    {
        try (FileInputStream i = new FileInputStream(input); BlockCompressedOutputStream o = new BlockCompressedOutputStream(new FileOutputStream(output), output))
        {
            FileUtil.copyData(i, o);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static SAMFileHeader.SortOrder getBamSortOrder(File bam) throws IOException
    {
        SamReaderFactory fact = SamReaderFactory.makeDefault();
        try (SamReader reader = fact.open(bam))
        {
            return reader.getFileHeader().getSortOrder();
        }
    }

    public static void logFastqBamDifferences(Logger log, File bam) throws IOException
    {
        int totalFirstMateAlignments = 0;
        int totalFirstMatePrimaryAlignments = 0;

        int totalSecondMateAlignments = 0;
        int totalSecondMatePrimaryAlignments = 0;

        SamReaderFactory fact = SamReaderFactory.makeDefault();
        fact.validationStringency(ValidationStringency.SILENT);
        try (SamReader reader = fact.open(bam))
        {
            try (SAMRecordIterator it = reader.iterator())
            {
                while (it.hasNext())
                {
                    SAMRecord r = it.next();
                    if (r.getReadUnmappedFlag())
                    {
                        continue;
                    }

                    //count all alignments
                    if (r.getReadPairedFlag() && r.getSecondOfPairFlag())
                    {
                        totalSecondMateAlignments++;
                    }
                    else
                    {
                        totalFirstMateAlignments++;
                    }

                    //also just primary alignments
                    if (!r.isSecondaryOrSupplementary())
                    {
                        if (r.getReadPairedFlag() && r.getSecondOfPairFlag())
                        {
                            totalSecondMatePrimaryAlignments++;
                        }
                        else
                        {
                            totalFirstMatePrimaryAlignments++;
                        }
                    }
                }

                log.info("File size: " + FileUtils.byteCountToDisplaySize(bam.length()));
                log.info("Total first mate alignments: " + totalFirstMateAlignments);
                log.info("Total first second mate alignments: " + totalSecondMateAlignments);

                log.info("Total first mate primary alignments: " + totalFirstMatePrimaryAlignments);

                log.info("Total second mate primary alignments: " + totalSecondMatePrimaryAlignments);
            }
        }
    }

    public static List<Interval> bedToIntervalList(File input) throws IOException
    {
        List<Interval> ret = new ArrayList<>();

        try (FeatureReader<BEDFeature> reader = AbstractFeatureReader.getFeatureReader(input.getAbsolutePath(), new BEDCodec(), false))
        {
            try (CloseableTribbleIterator<BEDFeature> i = reader.iterator())
            {
                while (i.hasNext())
                {
                    BEDFeature f = i.next();
                    ret.add(new Interval(f.getChr(), f.getStart(), f.getEnd()));
                }
            }
        }

        return ret;
    }

    public static JSONArray getReadGroupsForBam(File bam) throws IOException
    {
        if (bam == null || !bam.exists())
        {
            return null;
        }

        JSONArray ret = new JSONArray();

        SamReaderFactory fact = SamReaderFactory.makeDefault();
        try (SamReader reader = fact.open(bam))
        {
            SAMFileHeader header = reader.getFileHeader();
            if (header != null)
            {
                List<SAMReadGroupRecord> groups = header.getReadGroups();
                for (SAMReadGroupRecord g : groups)
                {
                    JSONObject details = new JSONObject();
                    details.put("platform", g.getPlatform());
                    details.put("platformUnit", g.getPlatformUnit());
                    details.put("description", g.getDescription());
                    details.put("sample", g.getSample());
                    details.put("id", g.getId());
                    details.put("date", g.getRunDate());
                    details.put("readGroupId", g.getReadGroupId());
                    details.put("centerName", g.getSequencingCenter());

                    ret.put(details);
                }
            }
        }

        return ret;
    }

    public static void deleteBamAndIndex(File bam)
    {
        bam.delete();

        File idx = new File(bam.getPath() + ".bai");
        if (idx.exists())
        {
            idx.delete();
        }
    }
}
