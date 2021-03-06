package org.labkey.sequenceanalysis.pipeline;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.pipeline.XarGeneratorFactorySettings;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.HasJobParams;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputTracker;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.PrintWriters;

import javax.validation.constraints.Null;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 8/31/2016.
 */
public class SequenceJob extends PipelineJob implements FileAnalysisJobSupport, HasJobParams, SequenceOutputTracker
{
    private TaskId _taskPipelineId;
    private Integer _experimentRunRowId;
    private String _jobName;
    private String _description;
    private SequenceJobSupportImpl _support;
    private File _webserverJobDir;
    private File _parentWebserverJobDir;
    private String _folderPrefix;
    private List<File> _inputFiles;
    private List<SequenceOutputFile> _outputsToCreate = new ArrayList<>();
    private PipeRoot _folderFileRoot;

    transient private JSONObject _params;

    // Default constructor for serialization
    protected SequenceJob()
    {
    }

    protected SequenceJob(SequenceJob parentJob, String jobName, String subdirectory) throws IOException
    {
        super(parentJob);
        _taskPipelineId = parentJob._taskPipelineId;
        _experimentRunRowId = parentJob._experimentRunRowId;
        _jobName = jobName;
        _description = parentJob._description;
        _support = parentJob._support;
        _parentWebserverJobDir = parentJob._webserverJobDir;
        _webserverJobDir = new File(parentJob._webserverJobDir, subdirectory);
        if (!_webserverJobDir.exists())
        {
            _webserverJobDir.mkdirs();
        }

        _folderPrefix = parentJob._folderPrefix;
        _inputFiles = parentJob._inputFiles;
        _folderFileRoot = parentJob._folderFileRoot;

        _params = parentJob.getParameterJson();

        setLogFile(_getLogFile());
    }

    public SequenceJob(String providerName, Container c, User u, @Nullable String jobName, PipeRoot pipeRoot, JSONObject params, TaskId taskPipelineId, String folderPrefix) throws IOException
    {
        super(providerName, new ViewBackgroundInfo(c, u, null), pipeRoot);

        _support = new SequenceJobSupportImpl();

        _jobName = jobName;
        _taskPipelineId = taskPipelineId;
        _folderPrefix = folderPrefix;
        _webserverJobDir = createLocalDirectory(pipeRoot);
        _params = params;
        writeParameters(params);

        _folderFileRoot = c.isWorkbook()? PipelineService.get().findPipelineRoot(c.getParent()) : pipeRoot;

        setLogFile(_getLogFile());
    }

    private File _getLogFile() throws IOException
    {
        return AssayFileWriter.findUniqueFileName((FileUtil.makeLegalName(_jobName) + ".log"), getDataDirectory());
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(_taskPipelineId);
    }

    protected void setInputFiles(Collection<File> inputs)
    {
        _inputFiles = inputs == null ? Collections.emptyList() : new ArrayList<>(inputs);
    }

    @Override
    public List<File> getInputFiles()
    {
        return _inputFiles == null ? Collections.emptyList() : Collections.unmodifiableList(_inputFiles);
    }

    public void setFolderFileRoot(PipeRoot folderFileRoot)
    {
        _folderFileRoot = folderFileRoot;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    protected File createLocalDirectory(PipeRoot pipeRoot) throws IOException
    {
        File webserverOutDir = new File(pipeRoot.getRootPath(), _folderPrefix + "Pipeline");
        if (!webserverOutDir.exists())
        {
            webserverOutDir.mkdir();
        }

        String folderName = FileUtil.makeLegalName(StringUtils.capitalize(_folderPrefix) + "_" + FileUtil.getTimestamp());
        webserverOutDir = AssayFileWriter.findUniqueFileName(folderName, webserverOutDir);
        if (!webserverOutDir.exists())
        {
            webserverOutDir.mkdirs();
        }

        return webserverOutDir;
    }

    protected void writeParameters(JSONObject params) throws IOException
    {
        try (PrintWriter writer = PrintWriters.getPrintWriter(getParametersFile()))
        {
            writer.write(params.isEmpty() ? "" : params.toString(1));
        }
    }

    @Override
    public Map<String, String> getJobParams()
    {
        return getParameters();
    }

    @Override
    public JSONObject getParameterJson()
    {
        if (_params == null)
        {
            try
            {
                File paramFile = getParametersFile();
                if (paramFile.exists())
                {
                    try (BufferedReader reader = Readers.getReader(getParametersFile()))
                    {
                        List<String> lines = IOUtils.readLines(reader);

                        _params = lines.isEmpty() ? new JSONObject() : new JSONObject(StringUtils.join(lines, '\n'));
                    }
                }
                else
                {
                    getLogger().error("parameter file not found: " + paramFile.getPath());
                }
            }
            catch (IOException e)
            {
                getLogger().error("Unable to find file: " + getParametersFile().getPath(), e);
            }
        }

        return _params;
    }

    @Override
    public Map<String, String> getParameters()
    {
        Map<String, String> ret = new HashMap<>();
        JSONObject json = getParameterJson();
        if (json == null)
        {
            getLogger().error("getParameterJson() was null for job: " + getDescription());
            return null;
        }

        for (String key : json.keySet())
        {
            ret.put(key, json.getString(key));
        }

        return ret;
    }

    @Override
    public String getDescription()
    {
        return _jobName;
    }

    public String getJobName()
    {
        return _jobName;
    }

    @Override
    public String getProtocolName()
    {
        return _jobName;
    }

    @Override
    public String getJoinedBaseName()
    {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public List<String> getSplitBaseNames()
    {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public String getBaseName()
    {
        return _webserverJobDir.getName();
    }

    @Override
    public String getBaseNameForFileType(FileType fileType)
    {
        return getBaseName();
    }

    @Override
    public File getDataDirectory()
    {
        return _webserverJobDir;
    }

    public File getWebserverDir(boolean forceParent)
    {
        return forceParent && isSplitJob() ? _parentWebserverJobDir : _webserverJobDir;
    }

    @Override
    public File getAnalysisDirectory()
    {
        return _webserverJobDir;
    }

    @Override
    public File findOutputFile(String name)
    {
        return findFile(name);
    }

    @Override
    public File findInputFile(String name)
    {
        return findFile(name);
    }


    @Override
    public File findOutputFile(@NotNull String outputDir, @NotNull String fileName)
    {
        return AbstractFileAnalysisJob.getOutputFile(outputDir, fileName, getPipeRoot(), getLogger(), getAnalysisDirectory());
    }

    @Override
    public ParamParser createParamParser()
    {
        return PipelineJobService.get().createParamParser();
    }

    @Override
    public File getParametersFile()
    {
        return new File(_parentWebserverJobDir == null ? _webserverJobDir : _parentWebserverJobDir, _folderPrefix + ".json");
    }

    @Nullable
    @Override
    public File getJobInfoFile()
    {
        return new File(_webserverJobDir, FileUtil.makeLegalName(_jobName) + ".job.json");
    }

    @Override
    public FileType.gzSupportLevel getGZPreference()
    {
        return null;
    }

    public SequenceJobSupportImpl getSequenceSupport()
    {
        return _support;
    }

    @Override
    public ActionURL getStatusHref()
    {
        if (_experimentRunRowId != null)
        {
            ExpRun run = ExperimentService.get().getExpRun(_experimentRunRowId.intValue());
            if (run != null)
                return PageFlowUtil.urlProvider(ExperimentUrls.class).getRunGraphDetailURL(run, null);
        }
        return null;
    }

    @Override
    public void clearActionSet(ExpRun run)
    {
        super.clearActionSet(run);

        _experimentRunRowId = run.getRowId();
    }

    public PipeRoot getFolderPipeRoot()
    {
        return _folderFileRoot;
    }

    public Integer getExperimentRunRowId()
    {
        return _experimentRunRowId;
    }

    public void setExperimentRunRowId(Integer experimentRunRowId)
    {
        _experimentRunRowId = experimentRunRowId;
    }

    public File findFile(String name)
    {
        return new File(getAnalysisDirectory(), name);
    }

    protected static XarGeneratorFactorySettings getXarGenerator() throws CloneNotSupportedException
    {
        XarGeneratorFactorySettings settings = new XarGeneratorFactorySettings("xarGeneratorJoin");
        settings.setJoin(true);

        TaskFactory factory = PipelineJobService.get().getTaskFactory(settings.getCloneId());
        if (factory == null)
        {
            PipelineJobService.get().addTaskFactory(settings);
        }

        return settings;
    }

    public void addOutputToCreate(SequenceOutputFile o)
    {
        getLogger().debug("adding sequence output: " + (o.getFile() == null ? o.getName() : o.getFile().getPath()));
        _outputsToCreate.add(o);
    }

    public List<SequenceOutputFile> getOutputsToCreate()
    {
        if (_outputsToCreate == null)
        {
            getLogger().warn("job._outputsToCreate is null");
            _outputsToCreate = new ArrayList<>();
        }

        return _outputsToCreate;
    }
}
