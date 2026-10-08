package com.wms.base.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.base.entity.BaseMaterial;
import com.wms.base.mapper.BaseMaterialMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BaseMaterialService {

    private final BaseMaterialMapper materialMapper;

    public PageResult<BaseMaterial> page(String materialCode, String materialName,
                                         String materialType, Integer status,
                                         long current, long size) {
        LambdaQueryWrapper<BaseMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(materialCode), BaseMaterial::getMaterialCode, materialCode)
                .like(StringUtils.hasText(materialName), BaseMaterial::getMaterialName, materialName)
                .eq(StringUtils.hasText(materialType), BaseMaterial::getMaterialType, materialType)
                .eq(status != null, BaseMaterial::getStatus, status)
                .orderByDesc(BaseMaterial::getCreateTime);
        Page<BaseMaterial> page = materialMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public BaseMaterial getByCode(String materialCode) {
        BaseMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                .eq(BaseMaterial::getMaterialCode, materialCode));
        if (material == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "物料不存在", "MATERIAL_NOT_FOUND");
        }
        return material;
    }

    public void delete(String materialCode) {
        BaseMaterial existing = getByCode(materialCode);
        materialMapper.deleteById(existing.getId());
    }

    /**
     * 从金蝶主数据 upsert 物料，编码与金蝶 FNumber 一致，内码取 FMATERIALID。
     * 逻辑删除的物料在重新同步时会被复活，避免 uk_material_code 冲突。
     *
     * @return INSERTED / UPDATED / SKIPPED
     */
    public String upsertFromKingdee(String materialCode, String materialName, String specification,
                                    String unitCode, boolean batchManaged, boolean serialManaged,
                                    boolean active, Long erpMaterialId) {
        if (!StringUtils.hasText(materialCode)) {
            return "SKIPPED";
        }
        String code = materialCode.trim();
        BaseMaterial existing = materialMapper.selectAnyByCode(code);
        if (existing == null) {
            BaseMaterial material = new BaseMaterial();
            material.setErpMaterialId(erpMaterialId);
            material.setMaterialCode(code);
            material.setMaterialName(defaultName(materialName, code));
            material.setSpecification(trimToNull(specification));
            material.setUnitCode(StringUtils.hasText(unitCode) ? unitCode.trim() : "Pcs");
            material.setCategoryCode("ERP");
            material.setMaterialType("RAW");
            material.setBatchManaged(batchManaged ? 1 : 0);
            material.setSerialManaged(serialManaged ? 1 : 0);
            material.setStatus(active ? 1 : 0);
            materialMapper.insert(material);
            return "INSERTED";
        }
        boolean revived = existing.getDeleted() != null && existing.getDeleted() != 0;
        if (revived) {
            materialMapper.reviveById(existing.getId());
        }
        existing.setErpMaterialId(erpMaterialId);
        existing.setMaterialName(defaultName(materialName, code));
        if (StringUtils.hasText(specification)) {
            existing.setSpecification(specification.trim());
        }
        if (StringUtils.hasText(unitCode)) {
            existing.setUnitCode(unitCode.trim());
        }
        existing.setBatchManaged(batchManaged ? 1 : 0);
        existing.setSerialManaged(serialManaged ? 1 : 0);
        existing.setStatus(active ? 1 : 0);
        existing.setDeleted(0);
        materialMapper.updateById(existing);
        return "UPDATED";
    }

    private static String defaultName(String name, String code) {
        return StringUtils.hasText(name) ? name.trim() : code;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 金蝶全量同步后清理演示/过期本地物料（保留金蝶同步的 ERP 物料）。
     */
    public int removeStaleLocalMaterials(Set<String> activeKingdeeCodes) {
        List<BaseMaterial> materials = materialMapper.selectList(new LambdaQueryWrapper<BaseMaterial>()
                .eq(BaseMaterial::getDeleted, 0));
        int removed = 0;
        for (BaseMaterial material : materials) {
            if (!shouldRemoveStaleMaterial(material, activeKingdeeCodes)) {
                continue;
            }
            materialMapper.deleteById(material.getId());
            removed++;
        }
        return removed;
    }

    private boolean shouldRemoveStaleMaterial(BaseMaterial material, Set<String> activeKingdeeCodes) {
        String code = material.getMaterialCode();
        if (isMockMaterialCode(code)) {
            return true;
        }
        if ("CAT001".equals(material.getCategoryCode()) && "system".equals(material.getCreateBy())) {
            return true;
        }
        if (activeKingdeeCodes == null || activeKingdeeCodes.isEmpty()) {
            return false;
        }
        return !"ERP".equals(material.getCategoryCode()) && !activeKingdeeCodes.contains(code);
    }

    private static boolean isMockMaterialCode(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        return code.matches("MAT-\\d+") || code.matches("MAT00000\\d+");
    }
}
