package com.energy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.energy.common.BusinessException;
import com.energy.context.PermissionUtil;
import com.energy.context.UserContext;
import com.energy.entity.Strategy;
import com.energy.mapper.StrategyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StrategyService {

    @Autowired
    private StrategyMapper strategyMapper;

    private void applyOwnerFilter(LambdaQueryWrapper<Strategy> wrapper) {
        if (!PermissionUtil.isAdmin()) {
            wrapper.eq(Strategy::getOwnerId, UserContext.getUserId());
        }
    }

    public Page<Strategy> page(long current, long size, String strategyType) {
        LambdaQueryWrapper<Strategy> wrapper = new LambdaQueryWrapper<>();
        applyOwnerFilter(wrapper);
        if (strategyType != null && !strategyType.isEmpty()) {
            wrapper.eq(Strategy::getStrategyType, strategyType);
        }
        wrapper.orderByDesc(Strategy::getPriority);
        return strategyMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public void add(Strategy strategy) {
        PermissionUtil.requireWrite();
        if (!PermissionUtil.isAdmin() || strategy.getOwnerId() == null) {
            strategy.setOwnerId(UserContext.getUserId());
        }
        strategy.setId(null);
        strategyMapper.insert(strategy);
    }

    public void update(Strategy strategy) {
        PermissionUtil.requireWrite();
        Strategy exist = strategyMapper.selectById(strategy.getId());
        if (exist == null) {
            throw new BusinessException("策略不存在");
        }
        if (!PermissionUtil.isAdmin() && !exist.getOwnerId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权操作该策略");
        }
        strategyMapper.updateById(strategy);
    }

    public void delete(Long id) {
        PermissionUtil.requireWrite();
        Strategy exist = strategyMapper.selectById(id);
        if (exist != null && !PermissionUtil.isAdmin() && !exist.getOwnerId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权操作该策略");
        }
        strategyMapper.deleteById(id);
    }

    public void toggleEnabled(Long id, Integer enabled) {
        PermissionUtil.requireWrite();
        Strategy strategy = new Strategy();
        strategy.setId(id);
        strategy.setEnabled(enabled);
        strategyMapper.updateById(strategy);
    }
}