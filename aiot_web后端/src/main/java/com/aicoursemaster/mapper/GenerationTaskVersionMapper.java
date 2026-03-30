package com.aicoursemaster.mapper;

import com.aicoursemaster.domain.GenerationTaskVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GenerationTaskVersionMapper {

    int insert(GenerationTaskVersion v);

    Integer selectMaxVersionNoByTaskId(@Param("taskId") String taskId);

    List<GenerationTaskVersion> selectByTaskIdOrderByVersionDesc(@Param("taskId") String taskId);

    GenerationTaskVersion selectByTaskIdAndVersionNo(@Param("taskId") String taskId,
                                                   @Param("versionNo") int versionNo);
}
