package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Voucher;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 */
public interface VoucherMapper extends BaseMapper<Voucher> {
    //添加针对优惠券表的查询
    @Select("SELECT id FROM tb_voucher LIMIT #{offset}, #{limit}")
    List<Long> selectAllIds(@Param("offset") int offset, @Param("limit") int limit);

    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}
