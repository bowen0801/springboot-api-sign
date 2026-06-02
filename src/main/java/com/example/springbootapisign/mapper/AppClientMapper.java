package com.example.springbootapisign.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.springbootapisign.entity.AppClient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 客户端密钥 Mapper 接口
 * 支持多客户端数据库动态查询
 */
@Mapper
public interface AppClientMapper extends BaseMapper<AppClient> {

    /**
     * 根据 AppKey 查询有效的客户端信息
     * 只返回状态为正常（status=1）的客户端
     *
     * @param appKey 客户端唯一标识
     * @return 客户端实体，不存在或已禁用则返回 null
     */
    @Select("SELECT id, app_key, app_secret, app_name, status, limit_count, create_time, update_time " +
            "FROM sys_app_client WHERE app_key = #{appKey} AND status = 1")
    AppClient selectActiveByAppKey(@Param("appKey") String appKey);
}
