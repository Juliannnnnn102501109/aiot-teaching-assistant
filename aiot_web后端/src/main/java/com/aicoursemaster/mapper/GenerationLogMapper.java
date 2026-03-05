package com.aicoursemaster.mapper;

import com.aicoursemaster.domain.GenerationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GenerationLogMapper {

    int insert(GenerationLog log);

    List<GenerationLog> selectByTaskId(@Param("taskId") String taskId);
}

