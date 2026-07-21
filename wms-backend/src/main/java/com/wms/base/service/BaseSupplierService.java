package com.wms.base.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.base.entity.BaseSupplier;
import com.wms.base.mapper.BaseSupplierMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.CodeAutoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BaseSupplierService {

    private final BaseSupplierMapper supplierMapper;

    public PageResult<BaseSupplier> page(String supplierCode, String supplierName,
                                         Integer status, long current, long size) {
        LambdaQueryWrapper<BaseSupplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(supplierCode), BaseSupplier::getSupplierCode, supplierCode)
                .like(StringUtils.hasText(supplierName), BaseSupplier::getSupplierName, supplierName)
                .eq(status != null, BaseSupplier::getStatus, status)
                .orderByDesc(BaseSupplier::getCreateTime);
        Page<BaseSupplier> page = supplierMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public BaseSupplier getByCode(String supplierCode) {
        BaseSupplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<BaseSupplier>()
                .eq(BaseSupplier::getSupplierCode, supplierCode));
        if (supplier == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在", "SUPPLIER_NOT_FOUND");
        }
        return supplier;
    }

    public void create(BaseSupplier supplier) {
        supplier.setSupplierCode(CodeAutoGenerator.ensureOrGenerate(supplier.getSupplierCode(), "SUP"));
        if (!StringUtils.hasText(supplier.getSupplierName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "供应商名称不能为空");
        }
        Long count = supplierMapper.selectCount(new LambdaQueryWrapper<BaseSupplier>()
                .eq(BaseSupplier::getSupplierCode, supplier.getSupplierCode()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "供应商编码已存在", "SUPPLIER_CODE_EXISTS");
        }
        supplierMapper.insert(supplier);
    }

    public void update(String supplierCode, BaseSupplier supplier) {
        BaseSupplier existing = getByCode(supplierCode);
        supplier.setId(existing.getId());
        supplier.setSupplierCode(supplierCode);
        supplierMapper.updateById(supplier);
    }

    public void delete(String supplierCode) {
        BaseSupplier existing = getByCode(supplierCode);
        supplierMapper.deleteById(existing.getId());
    }
}
