/*
 * Copyright 2023 OpenSPG Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 */

package com.antgroup.openspg.server.api.http.server.openapi;

import com.alibaba.fastjson.JSONObject;
import com.antgroup.openspg.builder.model.record.RecordAlterOperationEnum;
import com.antgroup.openspg.common.constants.BuilderConstant;
import com.antgroup.openspg.common.util.DateTimeUtils;
import com.antgroup.openspg.common.util.StringUtils;
import com.antgroup.openspg.common.util.builder.StructureBuilderExtensionHelper;
import com.antgroup.openspg.server.api.facade.dto.common.request.KagStructureBuilderRequest;
import com.antgroup.openspg.server.biz.common.ProjectManager;
import com.antgroup.openspg.server.common.model.bulider.BuilderJob;
import com.antgroup.openspg.server.common.model.datasource.DataSource;
import com.antgroup.openspg.server.common.model.project.Project;
import com.antgroup.openspg.server.common.model.scheduler.SchedulerEnum;
import com.antgroup.openspg.server.common.service.builder.BuilderJobService;
import com.antgroup.openspg.server.common.service.datasource.DataSourceService;
import com.antgroup.openspg.server.core.scheduler.model.service.SchedulerJob;
import com.antgroup.openspg.server.core.scheduler.service.api.SchedulerService;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class StructureBuilderSubmitService {

  private static final String STRUCTURE_BUILD_TYPE = "STRUCTURE_BUILD";

  @Autowired private ProjectManager projectManager;

  @Autowired private DataSourceService dataSourceService;

  @Autowired private BuilderJobService builderJobService;

  @Autowired private SchedulerService schedulerService;

  public BuilderJob submit(KagStructureBuilderRequest request) {
    Project project = projectManager.queryById(request.getProjectId());
    Assert.notNull(project, "project not found");

    DataSource dataSource = dataSourceService.getById(request.getDataSourceId());
    StructureBuilderExtensionHelper.validateMySqlDataSource(dataSource);
    StructureBuilderExtensionHelper.validatePropertyMapping(request.getPropertyMapping());

    String entityTypeFilter =
        StructureBuilderExtensionHelper.resolveEntityTypeFilter(
            project.getNamespace(), request.getEntityType());
    JSONObject extension =
        StructureBuilderExtensionHelper.buildExtension(
            dataSource,
            request.getDatabase(),
            request.getTable(),
            entityTypeFilter,
            request.getPropertyMapping(),
            request.getWhere(),
            request.getBatchSize());

    String userId =
        StringUtils.isBlank(request.getUserNumber()) ? BuilderConstant.SYSTEM : request.getUserNumber();
    Date now = new Date();
    BuilderJob job = new BuilderJob();
    job.setProjectId(request.getProjectId());
    job.setGmtCreate(now);
    job.setGmtModified(now);
    job.setCreateUser(userId);
    job.setModifyUser(userId);
    job.setJobName(
        StringUtils.isBlank(request.getJobName())
            ? "MYSQL_STRUCTURE_"
                + request.getTable()
                + "_"
                + DateTimeUtils.getDate2Str(DateTimeUtils.YYYY_MM_DD_HH_MM_SS2, now)
            : request.getJobName());
    job.setFileUrl("");
    job.setStatus("RUNNING");
    job.setDataSourceType(BuilderConstant.MYSQL.toUpperCase());
    job.setType(STRUCTURE_BUILD_TYPE);
    job.setVersion(BuilderConstant.DEFAULT_VERSION);
    job.setExtension(extension.toJSONString());
    job.setLifeCycle(SchedulerEnum.LifeCycle.ONCE.name());
    job.setAction(RecordAlterOperationEnum.UPSERT.name());

    Long builderJobId = builderJobService.insert(job);
    job.setId(builderJobId);

    SchedulerJob schedulerJob = new SchedulerJob();
    schedulerJob.setProjectId(job.getProjectId());
    schedulerJob.setName(job.getJobName());
    schedulerJob.setCreateUser(job.getCreateUser());
    schedulerJob.setModifyUser(job.getModifyUser());
    schedulerJob.setLifeCycle(SchedulerEnum.LifeCycle.ONCE);
    schedulerJob.setStatus(SchedulerEnum.Status.ENABLE);
    schedulerJob.setTranslateType(SchedulerEnum.TranslateType.KAG_STRUCTURE_BUILDER);
    schedulerJob.setDependence(SchedulerEnum.Dependence.INDEPENDENT);
    schedulerJob.setInvokerId(builderJobId.toString());
    schedulerJob = schedulerService.submitJob(schedulerJob);

    BuilderJob update = new BuilderJob();
    update.setId(builderJobId);
    update.setTaskId(schedulerJob.getId());
    builderJobService.update(update);
    job.setTaskId(schedulerJob.getId());
    return job;
  }
}
