# 科研项目设置与申领关联

## 使用方式

1. 在“系统管理 → 科研项目设置”新增项目。项目名称必填且不可重名；负责人、备注可选，排序值越小越靠前。
2. 在“申领管理 → 新建申领”使用“科研项目”下拉框。默认“无”，可搜索、选择或清空；只显示启用项目。
3. 每次打开新建申领都会重新加载项目设置并重置为无项目。选项加载失败时可以重试，也可以不关联项目继续申领。
4. 在申领详情中查看科研项目。项目名称使用申领创建时保存的快照；项目重命名或停用不改写历史单据。
5. 无需使用的项目可以停用，保留历史关联；不提供物理删除入口。

## 权限与接口

系统管理员（SYSTEM_ADMIN / ADMIN）和检验科主任（LAB_DIRECTOR）可维护项目；所有已登录申领用户可读取启用选项。

| 接口 | 用途 |
| --- | --- |
| GET /api/v1/system/research-projects | 配置列表，包含停用项，管理角色使用 |
| GET /api/v1/system/research-projects/options | 申领下拉项，只包含启用项目 |
| POST /api/v1/system/research-projects | 新增项目 |
| PUT /api/v1/system/research-projects/{id} | 编辑项目及启停状态 |

项目输入为 `name / leader / remark / sortOrder / enabled`，输出 VO 的 `id` 为字符串，避免前端长整型精度损失。配置校验失败为40001，重名为40900，不存在的管理记录为40400。

既有 `POST /api/v1/procurement/requisitions` 新增可空 `researchProjectId`。未传、null 或前端清空均表示不关联。服务端在创建申领的同一事务内锁定、检查项目存在且启用，并生成 `researchProjectName` 快照；不信任客户端传入的项目名称。列表和详情都返回关联ID及快照名称。

采购模块只依赖系统模块的 `ResearchProjectService` 接口，不直接访问项目 Mapper。项目修改与申领创建使用行锁串行化，防止项目状态校验与保存之间被并发停用。

## 数据库升级

新增 Flyway `V20260909001__research_projects.sql`：创建 `research_project` 表，为 `purchase_requisition` 新增 `research_project_id` 和 `research_project_name` 可空列及外键。没有修改任何已执行迁移。旧单两列均为空，详情显示“无”。

## 验证

```powershell
cd D:\IVD\ivd-app
& 'D:\apache-maven-3.9.0\bin\mvn.cmd' -o test -pl ivd-procurement -am '-Dtest=ResearchProjectServiceTest,RequisitionResearchProjectTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

8项回归测试覆盖可选项目、启停校验、重名、参数边界、服务端快照以及拒绝项目时不插入申领。

`scripts/qa/research-project-ui.js` 为本地浏览器验收脚本：需要预先登录测试管理员；会创建带“科研验收”标记的项目和两张草稿单，不审核、不出库。测试ID记录在该测试浏览器的 `ivd-research-qa` localStorage 项并在结果中返回。测试后必须先核对这些确切ID、验收标记和草稿状态，再清理测试明细、草稿及项目，不能按模糊名称批量删除。该脚本不应在生产数据库运行。
