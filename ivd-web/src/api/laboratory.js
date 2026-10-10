import request from '@/utils/request'

export const getLabItems = (params, signal) =>
  request({ url: '/lab/items', params, signal, silentError: true })
export const createLabItem = (data) =>
  request({ url: '/lab/items', method: 'post', data })
export const updateLabItem = (id, data) =>
  request({ url: `/lab/items/${id}`, method: 'put', data })
export const getLabIntervals = (params, signal) =>
  request({ url: '/lab/intervals', params, signal, silentError: true })
export const createLabInterval = (data) =>
  request({ url: '/lab/intervals', method: 'post', data })
export const updateLabInterval = (id, data) =>
  request({ url: `/lab/intervals/${id}`, method: 'put', data })
export const evaluateLabInterval = (data) =>
  request({ url: '/lab/intervals/evaluate', method: 'post', data })
export const getLabDeviceOptions = (keyword) =>
  request({
    url: '/lab/devices/options',
    params: { keyword },
    silentError: true,
  })
