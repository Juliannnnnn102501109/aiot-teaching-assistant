package com.aicoursemaster.mapper;

import com.aicoursemaster.domain.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    int insert(ChatMessage message);

    ChatMessage selectById(@Param("id") Long id);

    List<ChatMessage> selectBySessionId(@Param("sessionId") String sessionId);

    List<ChatMessage> selectLatestBySessionId(@Param("sessionId") String sessionId,
                                              @Param("limit") int limit);

    int deleteBySessionId(@Param("sessionId") String sessionId);

    int deleteByIds(@Param("ids") List<Long> ids);

    Long countByIdsAndSession(@Param("ids") List<Long> ids,
                              @Param("sessionId") String sessionId);

    int updateFeedback(@Param("id") Long id,
                       @Param("score") Integer score,
                       @Param("reason") String reason);

    int updateTokenUsage(@Param("id") Long id,
                         @Param("promptTokens") Integer promptTokens,
                         @Param("completionTokens") Integer completionTokens);
}

