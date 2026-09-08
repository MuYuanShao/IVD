// Run via playwright-cli run-code --filename=scripts/qa/workspace-ui.js
async (page) => {
  const errors = []
  const apiRequests = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (msg) => {
    if (['error', 'warning'].includes(msg.type())) errors.push(msg.text())
  })
  page.on('request', (request) => {
    // Vite also serves source modules at /src/api/*.js; these are not backend calls.
    if (/^https?:\/\/[^/]+\/api\//.test(request.url())) apiRequests.push(request.url())
  })
  const check = (ok, message) => {
    if (!ok) throw new Error(message)
  }
  const checks = []
  await page.goto('http://127.0.0.1:5173/workspace/home')
  await page.getByRole('heading', { name: '待办事项' }).waitFor()
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.getByText('近7天', { exact: true }).click()
  check(
    (await page.getByRole('img', { name: /近7天入库/ }).count()) === 1,
    'chart range did not change',
  )
  await page.getByText('近30天', { exact: true }).click()
  checks.push('Chart ranges: 7 / 30 days')
  await page.getByRole('button', { name: /检验科 · 常规耗材申领/ }).click()
  await page.getByRole('dialog', { name: '待办详情' }).waitFor()
  check(
    await page
      .getByRole('dialog')
      .getByText('一次性使用采血管等 8 项')
      .isVisible(),
    'todo detail missing',
  )
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: /关于开展第三季度耗材盘点/ }).click()
  await page.getByRole('dialog').waitFor()
  check(
    await page
      .getByRole('dialog')
      .getByText(/请各科室/)
      .isVisible(),
    'notice content missing',
  )
  await page.keyboard.press('Escape')
  checks.push('Todo and notice detail; Escape closes dialogs')
  await page.getByRole('button', { name: '管理', exact: true }).click()
  const boxes = page.locator('.function-editor').getByRole('checkbox')
  for (const box of await boxes.all()) {
    if (await box.isChecked()) await box.locator('..').click()
  }
  await page.getByRole('button', { name: '完成', exact: true }).click()
  check(
    await page.getByText('点击“管理”添加常用功能').isVisible(),
    'empty shortcuts state missing',
  )
  await page.getByRole('button', { name: '用户菜单' }).click()
  await page.getByRole('menuitem', { name: '重置演示偏好' }).click()
  check(
    (await page.locator('.function-tags button').count()) === 4,
    'reset preferences failed',
  )
  await page.getByRole('button', { name: '用户菜单' }).click()
  await page.getByRole('menuitem', { name: '个人信息' }).click()
  await page.getByRole('dialog', { name: '当前用户' }).waitFor()
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '用户菜单' }).click()
  await page.getByRole('menuitem', { name: '工作台设置' }).click()
  await page.getByRole('dialog').getByText('批次效期', { exact: true }).click()
  await page.keyboard.press('Escape')
  check(
    (await page.locator('.function-tags button').count()) === 5,
    'settings not reflected on dashboard',
  )
  checks.push(
    'Shortcut multiselect, empty state, reset and profile/settings dialogs',
  )
  const search = page.getByRole('textbox', { name: '搜索商品' })
  await search.fill('口罩')
  check(
    (await page.locator('.product-panel .el-table__body tbody tr').count()) ===
      4,
    'product search mismatch',
  )
  await search.fill('NO-SUCH-PRODUCT')
  await page.getByText('未找到匹配的商品，请调整搜索条件').waitFor()
  await search.fill('')
  await page.getByRole('button', { name: '下一页' }).click()
  await page.getByText('展示 6–10 项').waitFor()
  await search.fill('HC000001')
  await page.getByText('展示 1–1 项').waitFor()
  await page
    .locator('.product-panel')
    .getByRole('button', { name: '一次性使用真空采血管', exact: true })
    .click()
  await page.getByRole('dialog', { name: '商品详情' }).waitFor()
  check(
    await page.getByRole('dialog').getByText('LOT20260901').isVisible(),
    'product batch missing',
  )
  await page.keyboard.press('Escape')
  await search.fill('')
  checks.push(
    'Product search, empty results, pagination, page reset, product detail',
  )
  await page.getByRole('menuitem', { name: '设备管理', exact: true }).hover()
  await page.getByRole('menuitem', { name: '设备保养', exact: true }).click()
  await page.getByRole('heading', { name: '设备保养', exact: true }).waitFor()
  await page
    .getByRole('navigation', { name: '二级页面入口' })
    .getByRole('link', { name: '设备信息' })
    .click()
  await page.getByRole('heading', { name: '设备信息', exact: true }).waitFor()
  check(
    await page
      .getByRole('navigation', { name: '面包屑' })
      .getByText('设备管理')
      .isVisible(),
    'breadcrumbs missing group',
  )
  await page.reload()
  await page.getByRole('heading', { name: '设备信息', exact: true }).waitFor()
  await page.getByRole('button', { name: '详情', exact: true }).first().click()
  await page.getByRole('dialog').waitFor()
  await page.keyboard.press('Escape')
  checks.push(
    'Primary dropdown, secondary entry, breadcrumb, direct reload and module detail',
  )
  for (const [path, title] of [
    ['quality/licenses', '证照管理'],
    ['quality/expiry', '批次效期'],
    ['central/query', '库存查询'],
    ['central/department', '科室库存查询'],
    ['trade/purchase', '采购申请'],
    ['integration/status', '接口运行状态'],
    ['analysis/overview', '运营分析'],
  ]) {
    await page.goto(`http://127.0.0.1:5173/workspace/${path}`)
    await page.getByRole('heading', { name: title, exact: true }).waitFor()
  }
  checks.push('All seven menu groups render their mock pages')
  await page.goto('http://127.0.0.1:5173/workspace/home')
  await page.getByRole('heading', { name: '待办事项' }).waitFor()
  const sizes = []
  for (const width of [1440, 1280, 1024]) {
    await page.setViewportSize({ width, height: 1000 })
    await page.evaluate(() => document.fonts.ready)
    await page.waitForTimeout(450)
    await page.evaluate(() => window.scrollTo(0, 0))
    const bounds = await page.evaluate(() => ({
      width: innerWidth,
      scroll: document.documentElement.scrollWidth,
      regions: [
        ...document.querySelectorAll(
          '.statistic-card,.dashboard-column,.user-menu',
        ),
      ].map((el) => {
        const r = el.getBoundingClientRect()
        return { left: r.left, right: r.right, width: r.width }
      }),
    }))
    check(bounds.scroll <= width, `horizontal document overflow at ${width}`)
    check(
      bounds.regions.every(
        (r) => r.left >= 0 && r.right <= width + 1 && r.width > 0,
      ),
      `region clipping at ${width}`,
    )
    await page.screenshot({
      path: `output/playwright/workspace-${width}.png`,
      fullPage: true,
    })
    sizes.push(bounds)
  }
  checks.push(
    '1440 / 1280 / 1024 widths: no document overflow, region bounds valid, screenshots saved',
  )
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.getByRole('button', { name: '向右滚动页面入口' }).click()
  checks.push('Secondary menu scrolling')
  check(apiRequests.length === 0, `Unexpected backend requests: ${apiRequests}`)
  check(errors.length === 0, `Browser warnings/errors: ${errors.join('\n')}`)
  return { checks, sizes, browserErrors: errors, backendRequests: apiRequests }
}
