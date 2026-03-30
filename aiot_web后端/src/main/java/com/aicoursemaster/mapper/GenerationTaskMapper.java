package com.aicoursemaster.mapper;

import com.aicoursemaster.domain.GenerationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GenerationTaskMapper {

    int insert(GenerationTask task);

    GenerationTask selectLatestBySessionId(@Param("sessionId") String sessionId);

    GenerationTask selectByTaskId(@Param("taskId") String taskId);

    int updateByTaskId(GenerationTask task);

    int updateTaskIdById(@Param("id") Long id, @Param("taskId") String taskId);

    long countAll();
}

