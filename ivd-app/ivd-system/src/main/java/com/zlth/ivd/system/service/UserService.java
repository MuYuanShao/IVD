package com.zlth.ivd.system.service;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.system.vo.UserVO;

import java.util.List;

/**
 * 用户管理服务 — M00-1
 *
 * @author IVD Team
 * @since 2026-07-13
 */
public interface UserService {

    PageVO<UserVO> list(int page, int pageSize, String keyword, String role, Integer status);

    UserVO getById(Long id);

    UserVO create(String username, String realName, String role, Long deptId, String phone, String email);

    UserVO update(Long id, String realName, String role, Long deptId, String phone, String email);

    void delete(Long id);

    void toggleStatus(Long id);

    void resetPassword(Long id);

    List<UserVO> options(String role);
}
