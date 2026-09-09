import request from '@/utils/request'

export const getResearchProjects = () =>
  request({ url: '/system/research-projects', method: 'get' })
export const getResearchProjectOptions = (signal) =>
  request({
    url: '/system/research-projects/options',
    method: 'get',
    signal,
    silentError: true,
  })
export const createResearchProject = (data) =>
  request({ url: '/system/research-projects', method: 'post', data })
export const updateResearchProject = (id, data) =>
  request({ url: `/system/research-projects/${id}`, method: 'put', data })
