package com.aicoursemaster.mapper;

import com.aicoursemaster.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User selectById(@Param("id") Long id);

    User selectByUsername(@Param("username") String username);

    User selectByUsernameExcludeId(@Param("username") String username, @Param("excludeId") Long excludeId);

    int insert(User user);

    int updatePassword(@Param("id") Long id, @Param("password") String password);

    int updateLastLoginAt(@Param("id") Long id);

    int updateProfileSelective(User user);
}
