// Local acceptance only. Creates a labeled project and draft requisitions; IDs are returned for exact cleanup.
async (page) => {
  const check=(value,message)=>{if(!value) throw new Error(message)}
  const prior=await page.evaluate(()=>JSON.parse(localStorage.getItem('ivd-research-qa') || 'null'))
  if(prior?.reqIds?.length) throw new Error('Previous QA drafts require cleanup before rerun')
  const name=prior?.name || `科研验收-${Date.now()}`
  const fixture=prior || {name,projectId:null,reqIds:[]}
  const remember=()=>page.evaluate(value=>localStorage.setItem('ivd-research-qa',JSON.stringify(value)),fixture)
  await remember()
  await page.goto('http://127.0.0.1:5173/system/research-projects')
  if(!fixture.projectId) {
  await page.getByRole('button',{name:'新增科研项目',exact:true}).click()
  const editor=page.getByRole('dialog',{name:'新增科研项目',exact:true})
  await editor.getByPlaceholder('请输入科研项目名称').fill(name)
  const saved=page.waitForResponse(r=>r.url().endsWith('/system/research-projects') && r.request().method()==='POST')
  await editor.getByRole('button',{name:'保存',exact:true}).click()
  const result=await (await saved).json();check(result.code===200,'project creation failed')
  fixture.projectId=String(result.data.id);await remember()
  }
  await page.getByRole('row').filter({hasText:name}).waitFor()
  await page.screenshot({path:'output/playwright/research-settings.png',fullPage:true,animations:'disabled'})
  await page.goto('http://127.0.0.1:5173/requisition')
  await page.getByRole('button',{name:'新建申领',exact:true}).click()
  const dialog=page.getByRole('dialog',{name:'新建申领',exact:true})
  await dialog.getByText('无（不关联科研项目）',{exact:true}).waitFor()
  check(await dialog.locator('.research-project-select').innerText().then(text=>!text.includes(name)),'project was selected by default')
  await dialog.locator('.research-project-select .el-select').click()
  await page.getByRole('option',{name,exact:true}).click()
  await dialog.getByText('请选择申领科室',{exact:true}).click()
  await page.getByRole('option',{name:'检验科',exact:true}).click()
  await dialog.locator('.el-select').filter({hasText:'搜索并选择试剂…'}).click()
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
  await dialog.getByRole('button',{name:'加入清单',exact:true}).click()
  await dialog.locator('textarea').fill(name)
  await page.setViewportSize({width:1024,height:900})
  await page.screenshot({path:'output/playwright/research-requisition-select.png',animations:'disabled'})
  const created=page.waitForResponse(r=>r.url().endsWith('/procurement/requisitions') && r.request().method()==='POST')
  await dialog.getByRole('button',{name:'提交申领',exact:true}).click()
  const response=await created;const payload=response.request().postDataJSON();const req=await response.json()
  check(req.code===200,'requisition creation failed')
  fixture.reqIds.push(String(req.data.id));await remember()
  check(String(req.data.researchProjectId)===fixture.projectId && req.data.researchProjectName===name,'project not persisted on requisition')
  check(req.data.status==='DRAFT','QA unexpectedly submitted a workflow')
  await page.getByRole('row').filter({hasText:req.data.reqNo}).getByRole('button',{name:'详情',exact:true}).click()
  await page.getByRole('dialog',{name:'申领明细',exact:true}).getByText(name,{exact:true}).waitFor()
  await page.screenshot({path:'output/playwright/research-requisition-detail.png',animations:'disabled'})
  await page.keyboard.press('Escape')
  const checks=await page.evaluate(async ({fixture,payload})=>{
    const headers={Authorization:`Bearer ${localStorage.getItem('ivd-token')}`,'Content-Type':'application/json'}
    const api=async(path,method='GET',body)=>await (await fetch('/api/v1'+path,{method,headers,...(body?{body:JSON.stringify(body)}:{})})).json()
    const project={name:fixture.name+'-改名',leader:'',remark:fixture.name,sortOrder:0,enabled:false}
    const updated=await api('/system/research-projects/'+fixture.projectId,'PUT',project)
    const detail=await api('/procurement/requisitions/'+fixture.reqIds[0])
    const options=await api('/system/research-projects/options')
    const rejected=await api('/procurement/requisitions','POST',{...payload,researchProjectName:'伪造名称'})
    const legacy=await api('/procurement/requisitions','POST',{...payload,researchProjectId:null,researchProjectName:'伪造名称'})
    if(legacy.code===200) { fixture.reqIds.push(String(legacy.data.id));localStorage.setItem('ivd-research-qa',JSON.stringify(fixture)) }
    const duplicate=await api('/system/research-projects','POST',project)
    return {updated:updated.code,history:detail.data?.researchProjectName,disabledExcluded:!options.data.some(p=>p.id===fixture.projectId),rejectCode:rejected.code,legacyCode:legacy.code,legacyId:legacy.data?.id,legacyProject:legacy.data?.researchProjectId ?? null,legacyName:legacy.data?.researchProjectName ?? null,duplicateCode:duplicate.code,fixture}
  },{fixture,payload})
  check(checks.updated===200 && checks.history===name,'history snapshot changed after rename')
  check(checks.disabledExcluded && checks.rejectCode===40001,'disabled project was accepted')
  check(checks.legacyCode===200 && checks.legacyProject===null && checks.legacyName===null,'optional project compatibility broken')
  check(checks.duplicateCode===40900,'duplicate name not rejected')
  await page.getByRole('button',{name:'新建申领',exact:true}).click()
  await dialog.getByText('无（不关联科研项目）',{exact:true}).waitFor()
  await dialog.locator('.research-project-select .el-select').click()
  check(await page.getByRole('option',{name:fixture.name+'-改名',exact:true}).count()===0,'disabled option still visible')
  await page.keyboard.press('Escape');await dialog.getByRole('button',{name:'取消',exact:true}).click()
  return checks
}
