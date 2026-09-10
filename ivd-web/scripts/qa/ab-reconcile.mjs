// Read-only database reconciliation for a completed/blocked ab-business-flow run.
import { readFileSync,writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { spawnSync } from 'node:child_process'
const run=process.argv[2]
if(!/^AB-[A-Za-z0-9-]+$/.test(run||'')) throw new Error('Provide the exact AB run identifier')
if(!process.env.IVD_DB_PASSWORD) throw new Error('Set IVD_DB_PASSWORD')
const folder=resolve('output/ab-flow',run)
const evidence=JSON.parse(readFileSync(resolve(folder,'evidence.json'),'utf8'))
const lots=[`${run}-A`,`${run}-B`].map(lot=>`'${lot}'`).join(',')
const queries={
  batches:`SELECT id,batch_no,reagent_id,warehouse_id,stock,unit,unit_price,udi_status FROM reagent_batch WHERE batch_no IN (${lots}) ORDER BY id`,
  originalStock:'SELECT id,stock FROM reagent_batch ORDER BY id',
  ledger:`SELECT id,record_type,batch_no,quantity,unit,amount,device_id,warehouse_id,from_warehouse_id,requisition_id,operator_id FROM consumption_record WHERE batch_no IN (${lots}) ORDER BY id`,
  loads:`SELECT * FROM device_batch_load WHERE batch_no IN (${lots})`,
  statusLogs:`SELECT batch_no,from_status,to_status,operator_id,occurred_at FROM udi_status_log WHERE batch_no IN (${lots}) ORDER BY occurred_at`,
  uniqueCodes:`SELECT id,unique_code,batch_id,batch_no,status FROM udi_unique_code WHERE batch_no IN (${lots}) ORDER BY unique_code`,
  settlements:`SELECT id,settlement_no,settlement_type,source_id,source_no,batch_no,quantity,unit_price,amount,status FROM settlement_record WHERE source_id IN (${Object.values(evidence.branches).flatMap(b=>[b.reqId,b.consumeId].filter(Boolean)).map(id=>{if(!/^\d+$/.test(id))throw new Error('Invalid ID');return id}).join(',')})`,
  catalog:'SELECT id,generic_name,device_bind_mode,enable_unique_code,unit FROM reagent_catalog WHERE id IN (1,4)',
}
const results={}
for(const [name,sql] of Object.entries(queries)) {
  const result=spawnSync('C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe',['--batch','--raw','--default-character-set=utf8mb4','-u','root','-D','ivd','-e',sql],{encoding:'utf8',env:{...process.env,MYSQL_PWD:process.env.IVD_DB_PASSWORD}})
  if(result.status!==0) throw new Error(`${name}: ${result.stderr}`)
  writeFileSync(resolve(folder,`${name}.tsv`),result.stdout)
  const lines=result.stdout.trim().split(/\r?\n/).filter(Boolean)
  const headers=lines.shift()?.split('\t')||[]
  results[name]=lines.map(line=>Object.fromEntries(line.split('\t').map((value,i)=>[headers[i],value==='NULL'?null:value])))
}
const checks=[]
const check=(label,actual,expected)=>checks.push({label,actual,expected,pass:actual===expected})
const baseline=evidence.steps['baseline.inventory'].response.data.records
const now=new Map(results.originalStock.map(row=>[row.id,row.stock]))
check('原有批次库存未被测试改动',baseline.filter(row=>Number(row.stock)!==Number(now.get(String(row.id)))).length,0)
for(const key of ['A','B']) {
  const b=evidence.branches[key],lot=b.config.lot
  const stock=results.batches.filter(row=>row.batch_no===lot).reduce((sum,row)=>sum+Number(row.stock),0)
  const consumed=results.ledger.filter(row=>row.batch_no===lot && row.record_type==='CONSUME').reduce((sum,row)=>sum+Number(row.quantity),0)
  check(`${key}:采购数量=最终库存+实际消耗`,stock+consumed,4)
  check(`${key}:接口库存=数据库库存`,b.latestBatches.reduce((sum,row)=>sum+Number(row.stock),0),stock)
  check(`${key}:验收入库金额`,results.ledger.filter(row=>row.batch_no===lot && row.record_type==='INBOUND').reduce((sum,row)=>sum+Number(row.amount),0),4*b.config.price)
  const reqSettlements=results.settlements.filter(row=>row.source_id===b.reqId && row.settlement_type==='REQUISITION')
  check(`${key}:重复结算未新增记录`,reqSettlements.length,1)
  check(`${key}:结算批号真实一致`,reqSettlements[0]?.batch_no,lot)
  const flow=evidence.steps[`${key}.deptFlow`].response.data.records.find(row=>row.reqNo===b.reqNo)
  check(`${key}:科室流转查询签收状态`,flow?.status,'已签收');check(`${key}:科室流转查询数量`,Number(flow?.qty),3)
  const trace=evidence.steps[`${key}.traceFinal`].response.data.timeline
  check(`${key}:追溯应显示已调拨`,trace.find(row=>row.node==='调拨至科室')?.status,'completed')
  if(key==='A') {
    check('A:强制绑定有设备装载记录',results.loads.filter(row=>row.batch_no===lot).length>0,true)
    check('A:追溯应显示已消耗',trace.find(row=>row.node==='消耗核销')?.status,'completed')
    const s=results.settlements.find(row=>row.source_id===b.consumeId && row.settlement_type==='CONSUMPTION')
    const r=evidence.steps['A.consumptionReport'].response.data.records.find(row=>String(row.settlementId)===b.consumeId)
    check('A:消耗报表结算ID=实际结算ID',String(r?.settlementId),s?.id)
    check('A:消耗报表结算单号=实际单号',r?.settlementNo,s?.settlement_no)
    check('A:消耗报表金额',Number(r?.amount),3200)
  } else {
    check('B:无仪器消耗成功',evidence.steps['B.consume'].response.code,200)
    check('B:按验收量生成唯一码',results.uniqueCodes.filter(row=>row.batch_no===lot).length,4)
  }
  check(`${key}:批次单位已保存`,results.batches.filter(row=>row.batch_no===lot && !row.unit).length,0)
}
writeFileSync(resolve(folder,'reconciliation.json'),JSON.stringify({run,checks,results},null,2))
console.log(JSON.stringify({run,checks,passed:checks.filter(c=>c.pass).length,failed:checks.filter(c=>!c.pass).length},null,2))
