package org.labkey.cluster;

import org.labkey.api.cluster.ClusterResourceAllocator;
import org.labkey.api.cluster.ClusterService;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.security.User;
import org.labkey.cluster.pipeline.ClusterPipelineJob;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/**
 * Created by bimber on 2/23/2016.
 */
public class ClusterServiceImpl extends ClusterService
{
    private List<ClusterResourceAllocator.Factory> _allocatorList = new ArrayList<>();

    public static ClusterServiceImpl get()
    {
        return (ClusterServiceImpl) ClusterService.get();
    }

    public ClusterServiceImpl()
    {

    }

    public void registerResourceAllocator(ClusterResourceAllocator.Factory allocator)
    {
        _allocatorList.add(allocator);
    }

    public List<ClusterResourceAllocator.Factory> getAllocators(TaskId taskId)
    {
        TreeMap<Integer, List<ClusterResourceAllocator.Factory>> allocators = new TreeMap<>();
        for (ClusterResourceAllocator.Factory allocator : _allocatorList)
        {
            Integer priorty = allocator.getPriority(taskId);
            if (priorty != null)
            {
                if (!allocators.containsKey(priorty))
                {
                    allocators.put(priorty, new ArrayList<>());
                }

                allocators.get(priorty).add(allocator);
            }
        }

        if (allocators.isEmpty())
        {
            return Collections.emptyList();
        }

        List<ClusterResourceAllocator.Factory> ret = new ArrayList<>();
        for (Integer priority : allocators.keySet())
        {
            ret.addAll(allocators.get(priority));
        }

        return ret;
    }

    public String getClusterUser(Container c)
    {
        return ClusterManager.get().getClusterUser(c);
    }

    @Override
    public PipelineJob createClusterRemotePipelineJob(Container c, User u, String jobName, RemoteExecutionEngine engine, ClusterRemoteTask task, File logFile) throws PipelineValidationException
    {
        return ClusterPipelineJob.createJob(c, u, jobName, task, engine, logFile);
    }
}
