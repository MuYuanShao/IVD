// Real local acceptance: invokes only documented app APIs. Keeps checkpoints; never retries a write.
import { mkdirSync, writeFileSync, readFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'

const runId = process.env.IVD_AB_RUN || `AB-${new Date().toISOString().replace(/[-:.TZ]/g,'').slice(0,14)}`
if (!/^AB-[A-Za-z0-9-]+$/.test(runId)) throw new Error('Invalid run identifier')
const folder = resolve('output/ab-flow', runId)
mkdirSync(folder, { recursive: true })
const statePath = resolve(folder,'evidence.json')
const state = existsSync(statePath) ? JSON.parse(readFileSync(statePath,'utf8')) : {
  runId, startedAt:new Date().toISOString(), steps:{}, checks:[], branches:{},
  design:{purchase:4,requisition:3,consumption:1,warehouse:1,deptWarehouse:2,deptId:2,groupWarehouseA:3}
}
const save = () => writeFileSync(statePath, JSON.stringify(state,null,2))
let token
async function request(key,path,method='GET',body) {
  if (state.steps[key]) {
    if (state.steps[key].status==='pending' || state.steps[key].status==='uncertain') throw new Error(`Uncertain previous request ${key}; reconcile before continuing`)
    return state.steps[key].response
  }
  state.steps[key]={path,method,body,status:'pending',startedAt:new Date().toISOString()};save()
  try {
    const response=await fetch(`http://127.0.0.1:8080/api/v1${path}`,{method,headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(20000)})
    const raw=await response.text();let result
    try { result=JSON.parse(raw) } catch { result={code:response.status,message:raw.slice(0,1000)} }
    state.steps[key]={...state.steps[key],status:'completed',http:response.status,response:result,endedAt:new Date().toISOString()};save()
    return result
  } catch (error) {
    state.steps[key].status=method==='GET'?'failed':'uncertain';state.steps[key].error=error.message;save();throw error
  }
}
function success(result,step) { if(result.code!==200) throw new Error(`${step}: code=${result.code}, ${result.message}`);return result.data }
function check(label,actual,expected) { state.checks.push({label,actual,expected,pass:actual===expected});save() }
const records = result => success(result,'list').records || []
async function batches(branch,label) {
  const rows=records(await request(`${branch.key}.${label}.stock`,`/inventory?page=1&pageSize=100&keyword=${encodeURIComponent(branch.lot)}`)).filter(r=>r.batchNo===branch.lot)
  state.branches[branch.key].latestBatches=rows;save();return rows
}
async function ledger(branch,label) {
  const rows=records(await request(`${branch.key}.${label}.ledger`,`/consumption?page=1&pageSize=100&reagentId=${branch.reagentId}`)).filter(r=>r.batchNo===branch.lot)
  state.branches[branch.key].latestLedger=rows;save();return rows
}
async function runBranch(b) {
  state.branches[b.key] ||= {config:b};save()
  const branch=state.branches[b.key]
  try {
    const orders=success(await request(`${b.key}.purchase`,'/procurement/orders/manual','POST',{
      supplierId:1,settlementMode:'调拨结算',remark:`${runId} ${b.key} QA`,items:[{reagentId:b.reagentId,supplierId:1,quantity:4}]
    }),'purchase')
    if(orders.length!==1) throw new Error('Unexpected split order count')
    const po=orders[0];branch.poId=String(po.id);branch.poNo=po.poNo;save()
    check(`${b.key}:采购金额`,Number(po.totalAmount),4*b.price)
    const detail=success(await request(`${b.key}.purchaseDetail`,`/procurement/orders/${po.id}`),'order detail')
    const line=detail.items.find(i=>String(i.reagentId)===String(b.reagentId));if(!line) throw new Error('Order line absent')
    success(await request(`${b.key}.receive`,'/procurement/receive','POST',{
      orderId:String(po.id),itemId:String(line.id),qtyReceived:4,udiDi:b.udiDi,batchNo:b.lot,productionDate:'2026-09-01',expiryDate:'2027-09-01',warehouseId:1
    }),'acceptance')
    const afterReceive=await batches(b,'afterReceive');const central=afterReceive.find(r=>String(r.warehouseId)==='1')
    if(!central) throw new Error('Accepted batch absent from inventory')
    branch.centralBatchId=String(central.id);save();check(`${b.key}:验收后中心库`,Number(central.stock),4)
    await ledger(b,'afterReceive')
    await request(`${b.key}.traceAfterReceive`,`/inventory/trace?udiDi=${b.udiDi}&lotNo=${b.lot}`)
    const req=success(await request(`${b.key}.requisition`,'/procurement/requisitions','POST',{
      fromDeptId:2,toWarehouseId:2,urgency:'NORMAL',remark:`${runId} ${b.key} QA`,researchProjectId:null,items:[{reagentId:b.reagentId,qtyRequested:3}]
    }),'requisition')
    branch.reqId=String(req.id);branch.reqNo=req.reqNo;save()
    success(await request(`${b.key}.submit`,`/procurement/requisitions/${req.id}/submit`,'PUT'),'submit')
    const reviewed=success(await request(`${b.key}.review`,`/procurement/requisitions/${req.id}/review`,'PUT',{action:'APPROVE',comment:runId}),'review')
    check(`${b.key}:审核状态`,reviewed.status,'REVIEWED')
    const reqLine=reviewed.items.find(i=>String(i.reagentId)===String(b.reagentId))
    const picked=success(await request(`${b.key}.pick`,`/procurement/requisitions/${req.id}/pick-and-ship`,'POST',{
      targetWarehouseId:2,items:[{reqItemId:String(reqLine.id),sourceBatchId:String(central.id),pickQty:3}]
    }),'pick')
    check(`${b.key}:拣货后状态`,picked.status,'SHIPPED')
    const inTransit=await batches(b,'afterPick');check(`${b.key}:发货后中心库`,Number(inTransit.find(r=>String(r.warehouseId)==='1')?.stock),1)
    check(`${b.key}:签收前科室实物库存`,inTransit.filter(r=>String(r.warehouseId)==='2').reduce((sum,r)=>sum+Number(r.stock),0),0)
    success(await request(`${b.key}.signoff`,`/settlement/receive?requisitionId=${req.id}`,'POST'),'department signoff and settlement')
    const received=success(await request(`${b.key}.reqAfterSignoff`,`/procurement/requisitions/${req.id}`),'signed requisition')
    check(`${b.key}:签收后状态`,received.status,'FULFILLED')
    let current=(await batches(b,'afterSignoff')).find(r=>String(r.warehouseId)==='2')
    if(!current) throw new Error('Department batch absent after signoff')
    check(`${b.key}:科室签收数量`,Number(current.stock),3)
    const settlements=records(await request(`${b.key}.reqSettlement`,`/settlement?page=1&pageSize=100&settlementType=REQUISITION`)).filter(r=>String(r.sourceId)===String(req.id))
    branch.reqSettlements=settlements;save()
    check(`${b.key}:申领结算记录数`,settlements.length,1)
    check(`${b.key}:申领结算金额`,settlements.reduce((sum,r)=>sum+Number(r.amount),0),3*b.price)
    check(`${b.key}:结算真实批号`,settlements[0]?.batchNo,b.lot)
    const duplicate=await request(`${b.key}.duplicateSettlement`,`/settlement/requisition?requisitionId=${req.id}`,'POST')
    check(`${b.key}:重复结算被拒绝`,duplicate.code===40900,true)
    if(b.deviceId) {
      const transfer=await request(`${b.key}.groupTransfer`,`/inventory/transfer?batchId=${current.id}&targetWarehouseId=3&qty=3&reason=${runId}`,'POST')
      if(transfer.code===200) current=(await batches(b,'afterGroupTransfer')).find(r=>String(r.warehouseId)==='3')
      else { branch.groupTransferFailure=transfer;save() }
      const noDevice=await request(`${b.key}.requiredDevice`,`/consumption/consume?batchId=${current.id}&qty=1&purpose=ROUTINE&remark=${runId}`,'POST')
      check(`${b.key}:强制绑定缺少仪器被拒绝`,noDevice.code!==200,true)
    }
    branch.consumeBatchId=String(current.id);save()
    const consumed=await request(`${b.key}.consume`,`/consumption/consume?batchId=${current.id}&qty=1&purpose=ROUTINE&remark=${runId}${b.deviceId?`&deviceId=${b.deviceId}`:''}`,'POST')
    if(consumed.code!==200) { branch.consumptionBlocked=consumed;save();throw new Error(`consumption blocked: ${consumed.code} ${consumed.message}`) }
    const logs=await ledger(b,'afterConsume');const source=logs.find(r=>r.recordType==='CONSUME')
    if(!source) throw new Error('Consumption returned success but record missing')
    branch.consumeId=String(source.id);save()
    check(`${b.key}:消耗数量`,Number(source.quantity),1);check(`${b.key}:消耗金额`,Number(source.amount),b.price)
    success(await request(`${b.key}.consumeSettlement`,`/settlement/consumption?consumptionRecordId=${source.id}`,'POST'),'consumption settlement')
    const cs=records(await request(`${b.key}.consumptionSettlements`,`/settlement?page=1&pageSize=100&settlementType=CONSUMPTION`)).filter(r=>String(r.sourceId)===String(source.id))
    branch.consumeSettlements=cs;save();check(`${b.key}:消耗结算金额`,cs.reduce((sum,r)=>sum+Number(r.amount),0),b.price)
    branch.completed=true
  } catch(error) { branch.blocker=error.message;console.log(`${b.key}: ${error.message}`) }
  finally {
    await batches(b,'final');await ledger(b,'final')
    await request(`${b.key}.traceFinal`,`/inventory/trace?udiDi=${b.udiDi}&lotNo=${b.lot}`)
    await request(`${b.key}.udi`,`/inventory/udi/reagent/${b.reagentId}`)
    await request(`${b.key}.deptInventory`,`/inventory/dept?page=1&pageSize=100&deptId=2&keyword=${b.lot}`)
    await request(`${b.key}.deptFlow`,`/procurement/dept/requisitions?page=1&pageSize=100&deptId=2`)
    await request(`${b.key}.consumptionReport`,`/settlement/consumption-details?page=1&pageSize=100&reagentId=${b.reagentId}`)
    save()
  }
}
if(!process.env.IVD_QA_PASSWORD) throw new Error('Set IVD_QA_PASSWORD for the local test account')
const login=await (await fetch('http://127.0.0.1:8080/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:process.env.IVD_QA_USERNAME||'admin',password:process.env.IVD_QA_PASSWORD})})).json()
if(login.code!==200) throw new Error('Login failed')
token=login.data.accessToken
await request('baseline.catalog','/catalog?page=1&pageSize=100')
await request('baseline.inventory','/inventory?page=1&pageSize=100')
await request('baseline.settlementStats','/settlement/stats')
for(const b of [
  {key:'A',reagentId:4,name:'CEA检测试剂盒',mode:'FORCE',price:3200,udiDi:'07613339001234',deviceId:1,lot:`${runId}-A`},
  {key:'B',reagentId:1,name:'CK-MB检测试剂盒',mode:'FLEXIBLE',price:1200,udiDi:'06927634501234',lot:`${runId}-B`},
]) await runBranch(b)
await request('final.inventoryStats','/inventory/stats')
await request('final.settlementStats','/settlement/stats')
state.finishedAt=new Date().toISOString();save()
console.log(JSON.stringify({runId,evidence:statePath,branches:state.branches,failedChecks:state.checks.filter(c=>!c.pass)},null,2))
