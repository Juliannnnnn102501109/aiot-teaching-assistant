package com.aicoursemaster.mapper;

import com.aicoursemaster.domain.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SensitiveWordMapper {

    List<SensitiveWord> selectAllEnabled();

    List<SensitiveWord> selectAll();

    SensitiveWord selectById(@Param("id") Long id);

    SensitiveWord selectByWord(@Param("word") String word);

    int insert(SensitiveWord row);

    int update(SensitiveWord row);

    int deleteById(@Param("id") Long id);

    long countAll();
}
