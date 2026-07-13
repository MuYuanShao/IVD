package com.zlth.ivd.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.system.entity.SysDept;
import com.zlth.ivd.system.entity.SysUser;
import com.zlth.ivd.system.enums.RoleEnum;
import com.zlth.ivd.system.mapper.SysDeptMapper;
import com.zlth.ivd.system.mapper.SysUserMapper;
import com.zlth.ivd.system.service.UserService;
import com.zlth.ivd.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class UserServiceImpl implements UserService {

    private static final String DEFAULT_PASSWORD = "IVD@2026";

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public PageVO<UserVO> list(int page, int pageSize, String keyword, String role, Integer status) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        if (StringUtils.hasText(role)) qw.eq(SysUser::getRole, role);
        if (status != null) qw.eq(SysUser::getStatus, status);
        qw.orderByDesc(SysUser::getCreatedAt);

        Page<SysUser> p = userMapper.selectPage(new Page<>(page, Math.min(pageSize, 200)), qw);

        // batch-fetch dept names
        Set<Long> deptIds = p.getRecords().stream().map(SysUser::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNameMap = new HashMap<>();
        if (!deptIds.isEmpty()) {
            deptMapper.selectBatchIds(deptIds).forEach(d -> deptNameMap.put(d.getId(), d.getDeptName()));
        }

        List<UserVO> records = p.getRecords().stream().map(u -> toVO(u, deptNameMap)).collect(Collectors.toList());
        return new PageVO<>(p.getTotal(), page, pageSize, records);
    }

    @Override
    public UserVO getById(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BizException(40400, "用户不存在: id=" + id);
        String deptName = null;
        if (u.getDeptId() != null) {
            SysDept d = deptMapper.selectById(u.getDeptId());
            if (d != null) deptName = d.getDeptName();
        }
        return toVO(u, Collections.singletonMap(u.getDeptId(), deptName));
    }

    @Override
    public UserVO create(String username, String realName, String role, Long deptId, String phone, String email) {
        // uniqueness check
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (count != null && count > 0) throw new BizException(40001, "用户名已存在: " + username);
        // validate role
        validateRole(role);

        SysUser u = new SysUser();
        u.setUsername(username);
        u.setRealName(realName);
        u.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        u.setRole(role);
        u.setDeptId(deptId);
        u.setPhone(phone);
        u.setEmail(email);
        u.setStatus(1);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(u);

        String deptName = null;
        if (deptId != null) { SysDept d = deptMapper.selectById(deptId); if (d != null) deptName = d.getDeptName(); }
        return toVO(u, Collections.singletonMap(deptId, deptName));
    }

    @Override
    public UserVO update(Long id, String realName, String role, Long deptId, String phone, String email) {
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BizException(40400, "用户不存在: id=" + id);
        if (StringUtils.hasText(role)) validateRole(role);

        if (StringUtils.hasText(realName)) u.setRealName(realName);
        if (StringUtils.hasText(role)) u.setRole(role);
        if (deptId != null) u.setDeptId(deptId);
        if (phone != null) u.setPhone(phone);
        if (email != null) u.setEmail(email);
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(u);

        String deptName = null;
        if (u.getDeptId() != null) { SysDept d = deptMapper.selectById(u.getDeptId()); if (d != null) deptName = d.getDeptName(); }
        return toVO(u, Collections.singletonMap(u.getDeptId(), deptName));
    }

    @Override
    public void delete(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BizException(40400, "用户不存在: id=" + id);
        userMapper.deleteById(id);
    }

    @Override
    public void toggleStatus(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BizException(40400, "用户不存在: id=" + id);
        u.setStatus(u.getStatus() != null && u.getStatus() == 1 ? 0 : 1);
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(u);
    }

    @Override
    public void resetPassword(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BizException(40400, "用户不存在: id=" + id);
        u.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(u);
        log.info("用户 {} 密码已重置", u.getUsername());
    }

    @Override
    public List<UserVO> options(String role) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(role)) qw.eq(SysUser::getRole, role);
        qw.eq(SysUser::getStatus, 1).orderByAsc(SysUser::getRealName);
        return userMapper.selectList(qw).stream().map(u -> {
            UserVO vo = new UserVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setRealName(u.getRealName());
            vo.setRole(u.getRole());
            return vo;
        }).collect(Collectors.toList());
    }

    private void validateRole(String role) {
        try { RoleEnum.valueOf(role); }
        catch (IllegalArgumentException e) { throw new BizException(40001, "无效的角色: " + role); }
    }

    private UserVO toVO(SysUser u, Map<Long, String> deptNameMap) {
        UserVO vo = new UserVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setRealName(u.getRealName());
        vo.setRole(u.getRole());
        vo.setDeptId(u.getDeptId());
        vo.setDeptName(deptNameMap.getOrDefault(u.getDeptId(), "—"));
        vo.setPhone(u.getPhone());
        vo.setEmail(u.getEmail());
        vo.setStatus(u.getStatus());
        vo.setCreatedAt(u.getCreatedAt());
        vo.setUpdatedAt(u.getUpdatedAt());
        // role label
        try { vo.setRoleLabel(RoleEnum.valueOf(u.getRole()).getLabel()); }
        catch (IllegalArgumentException e) { vo.setRoleLabel(u.getRole()); }
        return vo;
    }
}
