package com.aicoursemaster.mapper;

import com.aicoursemaster.domain.Material;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MaterialMapper {

    int insert(Material material);

    Material selectById(@Param("id") Long id);

    List<Material> selectBySessionId(@Param("sessionId") String sessionId);

    int deleteBySessionId(@Param("sessionId") String sessionId);

    int updateParseResult(@Param("id") Long id,
                          @Param("status") Integer status,
                          @Param("summary") String summary,
                          @Param("keywords") String keywords,
                          @Param("errorMsg") String errorMsg);

    int updateFileName(@Param("id") Long id, @Param("fileName") String fileName);

    int deleteById(@Param("id") Long id);

    long countAll();
}

