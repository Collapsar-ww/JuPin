package com.jupin.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jupin.pojo.entity.PoolMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PoolMemberMapper extends BaseMapper<PoolMember> {
}
