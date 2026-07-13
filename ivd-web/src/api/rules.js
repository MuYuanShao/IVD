import request from '@/utils/request'

/** S2 规则引擎 API */

export function getRulesList() {
  return request({ url: '/rules', method: 'get' })
}

export function getRulesByLayer(layer) {
  return request({ url: `/rules/layer/${layer}`, method: 'get' })
}

export function getRuleByRuleId(ruleId) {
  return request({ url: `/rules/${ruleId}`, method: 'get' })
}

export function updateRule(ruleId, data) {
  return request({ url: `/rules/${ruleId}`, method: 'put', data })
}

export function toggleRule(ruleId, enabled) {
  return request({ url: `/rules/${ruleId}/toggle`, method: 'put', params: { enabled } })
}

export function createRule(data) {
  return request({ url: '/rules', method: 'post', data })
}
