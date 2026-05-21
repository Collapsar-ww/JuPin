package com.jupin.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jupin.pojo.entity.CarPool;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PoolMapper extends BaseMapper<CarPool> {
}
