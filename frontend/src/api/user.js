import request from './request'

/**
 * 用户查询接口
 *
 * <p>对应后端 UserController，仅提供只读的下拉框数据源。
 */

/**
 * 按角色查询用户列表
 *
 * @param {string} role 角色代码，如 DEV / PM / CS；不传返回全部启用用户
 * @returns {Promise<Array<{id:number, displayName:string,
 *   username:string, dept:string}>>} 用户选项列表
 */
export function fetchUserOptions(role) {
  return request.get('/users', { params: role ? { role } : {} })
}
