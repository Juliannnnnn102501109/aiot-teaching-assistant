package com.aicoursemaster.mapper;

import com.aicoursemaster.domain.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSessionMapper {

    ChatSession selectById(@Param("id") String id);

    int insert(ChatSession session);

    int updateTitleAndStatus(ChatSession session);

    int updateResultAndStatus(ChatSession session);

    Long countActiveByUserId(@Param("userId") Long userId);

    ChatSession selectByIdAndUserId(@Param("id") String id, @Param("userId") Long userId);

    int deleteByIdAndUserId(@Param("id") String id, @Param("userId") Long userId);

    List<ChatSession> selectByUserId(@Param("userId") Long userId);
}

