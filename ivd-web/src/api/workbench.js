import request from '@/utils/request'

export const getWorkbench = (signal) =>
  request({
    url: '/analytics/workbench',
    method: 'get',
    signal,
    silentError: true,
  })
export const getWorkbenchProducts = (params, signal) =>
  request({
    url: '/analytics/workbench/products',
    method: 'get',
    params,
    signal,
    silentError: true,
  })
