package org.labkey.sequenceanalysis.util;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.reference.FastaSequenceIndex;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.ReferenceLibraryHelper;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.run.util.FastaIndexer;
import picard.sam.CreateSequenceDictionary;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by bimber on 7/28/2014.
 */
public class ReferenceLibraryHelperImpl implements ReferenceLibraryHelper
{
    private File _refFasta;
    private Map<String, Integer> _cachedIds = new HashMap<>();
    private Map<String, String> _cachedAccessions = new HashMap<>();

    public ReferenceLibraryHelperImpl(File refFasta)
    {
        _refFasta = refFasta;
    }

    @Override
    public File getReferenceFasta()
    {
        return _refFasta;
    }

    @Override
    public File getIdKeyFile()
    {
        return new File(getReferenceFasta().getParentFile(), FileUtil.getBaseName(FileUtil.getBaseName(getReferenceFasta())) + ".idKey.txt");
    }

    @Override
    public File getFastaIndexFile(boolean createIfDoesntExist)
    {
        File ret = new File(getReferenceFasta().getParentFile(), getReferenceFasta().getName() + ".fai");
        if (!ret.exists() && createIfDoesntExist)
        {
            FastaIndexer indexer = new FastaIndexer(null);
            try
            {
                indexer.execute(_refFasta);
            }
            catch (PipelineJobException e)
            {
                //TODO
            }
        }

        return ret;
    }

    public File getSequenceDictionaryFile(boolean createIfDoesntExist)
    {
        File ret = new File(getReferenceFasta().getParentFile(), getReferenceFasta().getName() + ".dict");
        if (!ret.exists() && createIfDoesntExist)
        {
            CreateSequenceDictionary.main(new String[]{"REFERENCE=" + _refFasta.getPath(), "OUTPUT=" + ret.getPath()});
        }

        return ret;
    }

    @Override
    public Integer resolveSequenceId(String refName)
    {
        if (!_cachedIds.containsKey(refName))
        {
            parseForReference(refName);
        }

       return _cachedIds.get(refName);
    }

    @Override
    public String resolveAccession(String refName)
    {
        if (!_cachedAccessions.containsKey(refName))
        {
            parseForReference(refName);
        }

        return _cachedAccessions.get(refName);
    }

    private void parseForReference(String refName)
    {
        File idKey = getIdKeyFile();
        if (idKey.exists())
        {
            try (BufferedReader reader = new BufferedReader(new FileReader(idKey)))
            {
                String line;
                int rowIdx = 0;
                while ((line = reader.readLine()) != null)
                {
                    rowIdx++;
                    if (rowIdx == 1)
                    {
                        continue;
                    }

                    //might as well cache all sequences as we go in case we need them later
                    String[] tokens = line.split("\t");
                    if (tokens.length > 1)
                    {
                        String lineName = tokens[1];

                        _cachedIds.put(lineName, Integer.parseInt(tokens[0]));

                        if (tokens.length > 2)
                        {
                            _cachedAccessions.put(lineName, tokens[2]);
                        }
                        else
                        {
                            _cachedAccessions.put(lineName, null);
                        }

                        if (refName.equals(lineName))
                        {
                            return;
                        }
                    }
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        //for legacy libraries...
        String[] keys = refName.split("\\|");
        if (keys.length < 2)
        {
            _cachedIds.put(refName, null);
            _cachedAccessions.put(refName, null);
            return;
        }

        _cachedIds.put(refName, Integer.parseInt(keys[0]));
        _cachedAccessions.put(refName, null);
    }
}