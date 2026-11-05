import request from './request'

/**
 * 文件上传接口
 *
 * <p>对应后端 FileController。
 *
 * <p><b>为什么上传走单独的实例</b>：
 * 上传是 multipart/form-data，Content-Type 不能是 JSON，
 * 且大文件耗时长，超时时间要单独放宽。
 */

/**
 * 上传图片
 *
 * @param {File} file 图片文件
 * @param {string} bizType 业务类型，目前固定 'feedback'
 * @returns {Promise<{url: string, filename: string, size: number}>} 上传结果
 */
export function uploadImage(file, bizType = 'feedback') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bizType', bizType)

  return request.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000 // 上传放宽到 60 秒
  })
}
