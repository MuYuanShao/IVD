// Requires an already authenticated test browser. Only reads business data.
async (page) => {
  const errors = []
  const failedApis = []
  const mutations = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('response', response => { if (response.status() >= 400 && response.url().includes('/api/')) failedApis.push({ path: response.url().replace(/^https?:\/\/[^/]+/, '').split('?')[0], status: response.status() }) })
  page.on('request', request => { if (request.url().includes('/api/') && !['GET', 'HEAD', 'OPTIONS'].includes(request.method())) mutations.push(request.method()) })
  const check = (ok, message) => { if (!ok) throw new Error(message) }
  await page.goto('http://127.0.0.1:5173/dashboard')
  await page.getByRole('heading', { name: '待办事项' }).waitFor()
  const links = await page.getByRole('navigation', { name: '二级页面入口' }).getByRole('link').evaluateAll(els => els.map(el => ({ path: el.getAttribute('href'), title: el.textContent.trim() })))
  check(links.some(link => link.path === '/settlement'), 'Settlement menu missing')
  check(links.some(link => link.path === '/dashboard-live'), 'Live dashboard menu missing')
  const routes = []
  for (const link of links) {
    await page.goto(`http://127.0.0.1:5173${link.path}`)
    await page.locator('.scm-business .scm-main').waitFor()
    await page.waitForTimeout(350)
    check(page.url().replace(/^https?:\/\/[^/]+/, '').split('?')[0] === link.path, `Unexpected redirect: ${link.path}`)
    check(await page.locator('.layout-aside').count() === 0, 'Old sidebar still visible')
    check(await page.getByRole('navigation', { name: '二级页面入口' }).locator('[aria-current="page"]').count() === 1, `Secondary active state: ${link.path}`)
    routes.push(link.path)
  }
  const views = []
  for (const width of [1280,1024]) {
    await page.setViewportSize({ width, height: 900 })
    for (const path of ['/dashboard','/catalog','/requisition','/central']) {
      await page.goto(`http://127.0.0.1:5173${path}`)
      await page.locator('.scm-main').waitFor()
      await page.waitForTimeout(500)
      const size = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth }))
      check(size.scroll <= size.width, `Document overflow ${path} at ${width}: ${size.scroll}`)
      await page.screenshot({ path: `output/playwright/business-${path.slice(1)}-${width}.png`, fullPage: true })
      views.push({ path, ...size })
    }
  }
  await page.getByRole('menuitem', { name: '采购申领', exact: true }).hover()
  await page.getByRole('menuitem', { name: '申领管理', exact: true }).click()
  await page.getByRole('navigation', { name: '面包屑' }).getByText('采购申领').waitFor()
  await page.locator('.el-table__expand-icon').first().click()
  await page.locator('.el-table__expanded-cell').first().waitFor()
  await page.mouse.move(990, 880)
  await page.waitForTimeout(400)
  await page.screenshot({ path: 'output/playwright/business-requisition-expanded-1024.png', fullPage: true })
  await page.getByRole('button', { name: '新建申领', exact: true }).click()
  await page.getByRole('dialog', { name: '新建申领' }).waitFor()
  await page.waitForTimeout(400)
  await page.screenshot({ path: 'output/playwright/business-requisition-dialog-1024.png', animations: 'disabled' })
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '用户菜单' }).click()
  await page.getByRole('menuitem', { name: '退出登录', exact: true }).click()
  await page.getByRole('textbox', { name: '请输入用户名' }).waitFor()
  await page.goto('http://127.0.0.1:5173/catalog')
  await page.getByRole('textbox', { name: '请输入用户名' }).waitFor()
  check(page.url().replace(/^https?:\/\/[^/]+/, '').split('?')[0] === '/login', 'Protected route bypassed after logout')
  check(errors.length === 0, `JavaScript errors: ${errors.join('; ')}`)
  check(mutations.length === 0, 'Business data was mutated during UI QA')
  return { routes, views, errors, failedApis, mutations, logoutGuard: 'passed' }
}
