// Run in a signed-in local test browser; fault injection affects this browser only.
async (page) => {
  await page.unrouteAll({behavior:'ignoreErrors'})
  const check=(condition,message)=>{if(!condition) throw new Error(message)}
  const errors=[];page.on('pageerror',error=>errors.push(error.message))
  await page.goto('http://127.0.0.1:5173/dashboard')
  await page.getByRole('button',{name:/今日消耗金额/}).waitFor()
  const baseline=await page.evaluate(async()=>{
    const headers={Authorization:`Bearer ${localStorage.getItem('ivd-token')}`}
    const summary=await (await fetch('/api/v1/analytics/workbench',{headers})).json()
    const products=await (await fetch('/api/v1/analytics/workbench/products?page=1&pageSize=5',{headers})).json()
    const invalid=await (await fetch('/api/v1/analytics/workbench/products?page=0',{headers})).json()
    const invalidType=await fetch('/api/v1/analytics/workbench/products?page=abc',{headers})
    const anonymous=await fetch('/api/v1/analytics/workbench')
    const expired=await fetch('/api/v1/analytics/workbench',{headers:{Authorization:'Bearer invalid-test-token'}})
    return {summary,products,invalid,invalidType:invalidType.status,anonymous:anonymous.status,expired:expired.status}
  })
  check(baseline.summary.code===200 && baseline.products.code===200,'API request failed')
  check(baseline.invalid.code===40001 && baseline.invalidType===400,'Pagination validation mismatch')
  check(baseline.anonymous===401 && baseline.expired===401,'Unauthenticated request must be 401, not 403')
  check(baseline.summary.data.trend.length===30,'Trend missing dates')
  await page.locator('.product-panel').getByText(`共 ${baseline.products.data.total} 项`,{exact:true}).waitFor()
  check(await page.getByRole('button',{name:/今日销售额/}).count()===0,'Fake sales KPI remains')
  const productPanel=page.locator('.product-panel')
  if(baseline.products.data.total>5) {
    const next=page.waitForResponse(r=>r.url().includes('/analytics/workbench/products?') && r.url().includes('page=2'))
    await page.getByRole('button',{name:'下一页',exact:true}).click();await next
    await productPanel.getByText(/展示 6–10 项/).waitFor()
  }
  const search=page.getByRole('textbox',{name:'搜索商品',exact:true})
  await search.fill('NO_SUCH_BATCH_123')
  await page.getByText('未找到匹配的商品，请调整搜索条件').waitFor()
  await search.fill('')
  await productPanel.getByText('展示 1–5 项',{exact:true}).waitFor()
  await productPanel.locator('.el-table__body-wrapper button').first().click()
  await page.getByRole('dialog',{name:'商品详情'}).getByText(baseline.products.data.records[0].batch,{exact:true}).waitFor()
  await page.keyboard.press('Escape')
  // Stale slow search must not overwrite a later query.
  await page.route('**/api/v1/analytics/workbench/products?**',async route=>{
    if(route.request().url().includes('keyword=SLOW_OLD')) { await page.waitForTimeout(700); try { await route.fulfill({json:{code:200,data:{total:0,page:1,pageSize:5,records:[]}}}) } catch {} }
    else await route.continue()
  })
  await search.fill('SLOW_OLD');await page.waitForTimeout(400);await search.fill('')
  await productPanel.getByText(`共 ${baseline.products.data.total} 项`,{exact:true}).waitFor();await page.waitForTimeout(800)
  check(await productPanel.getByText(`共 ${baseline.products.data.total} 项`,{exact:true}).isVisible(),'Stale search overwrote latest results')
  await page.unroute('**/api/v1/analytics/workbench/products?**')
  // Retain known summary with an explicit stale/error banner; never replace it with zeros.
  await page.route('**/api/v1/analytics/workbench',route=>route.fulfill({status:500,json:{code:50000,message:'验收：模拟读取失败'}}))
  await page.getByRole('button',{name:'刷新数据',exact:true}).click()
  await page.getByText('本次读取失败，以下为上次成功结果，请刷新重试。').waitFor()
  await page.screenshot({path:'output/playwright/workbench-error.png',fullPage:true,animations:'disabled'})
  await page.unroute('**/api/v1/analytics/workbench')
  await page.getByRole('button',{name:'刷新数据',exact:true}).click()
  await page.getByText('本次读取失败，以下为上次成功结果，请刷新重试。').waitFor({state:'hidden'})
  // Forbidden is not unauthenticated: user remains signed in and receives an actionable error.
  await page.route('**/api/v1/analytics/workbench/products?**',route=>route.fulfill({status:403,json:{code:40300,message:'验收：无数据权限'}}))
  await search.fill('FORBIDDEN')
  await productPanel.getByText('验收：无数据权限',{exact:true}).waitFor()
  check(await page.evaluate(()=>!!localStorage.getItem('ivd-token')),'403 removed login state')
  await page.unroute('**/api/v1/analytics/workbench/products?**');await search.fill('')
  await productPanel.getByText(`共 ${baseline.products.data.total} 项`,{exact:true}).waitFor()
  // Initial-load failure shows no made-up zero cards, while independently loaded batches remain usable.
  await page.route('**/api/v1/analytics/workbench',route=>route.fulfill({status:500,json:{code:50000,message:'验收：首次加载失败'}}))
  await page.reload()
  await page.getByText('业务数据未获取，不显示占位统计。').waitFor()
  check(await page.locator('.statistic-card').count()===0,'Initial failure displayed zero cards')
  await page.unroute('**/api/v1/analytics/workbench')
  await page.getByRole('button',{name:'刷新数据',exact:true}).click()
  await page.getByRole('button',{name:/今日消耗金额/}).waitFor()
  await page.locator('.el-message').first().waitFor({state:'hidden'})
  for(const width of [1280,1024]) {
    await page.setViewportSize({width,height:1000});await page.waitForTimeout(350)
    check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Document overflow')
    await page.screenshot({path:`output/playwright/workbench-real-${width}.png`,fullPage:true,animations:'disabled'})
  }
  check(errors.length===0,errors.join(';'))
  await page.evaluate(()=>localStorage.setItem('ivd-token','invalid-test-token'))
  await page.reload()
  await page.getByRole('textbox',{name:'请输入用户名'}).waitFor()
  check(await page.evaluate(()=>!localStorage.getItem('ivd-token')),'Invalid session did not clear login state')
  return {metrics:baseline.summary.data.metrics,alerts:baseline.summary.data.alerts,todoTotal:baseline.summary.data.todoTotal,products:baseline.products.data.total,checked:['real API','pagination','empty search','batch detail','stale response race','500 retry','403 preserves session','1280/1024 layout'],errors}
}
