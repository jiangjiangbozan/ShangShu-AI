/**
 * Storage 工具封装
 *
 * 对 uni.getStorageSync / uni.setStorageSync / uni.removeStorageSync
 * 进行类型安全包装，统一异常处理。
 */

/**
 * 读取 Storage
 * @param key 存储键
 * @returns 值，不存在返回 null
 */
export function getStorage<T = string>(key: string): T | null {
  try {
    const value = uni.getStorageSync(key)
    return value === '' ? null : (value as T)
  } catch {
    console.warn(`[Storage] 读取失败: key=${key}`)
    return null
  }
}

/**
 * 写入 Storage
 * @param key   存储键
 * @param value 存储值
 */
export function setStorage(key: string, value: unknown): void {
  try {
    uni.setStorageSync(key, value)
  } catch {
    console.warn(`[Storage] 写入失败: key=${key}`)
  }
}

/**
 * 删除 Storage
 * @param key 存储键
 */
export function removeStorage(key: string): void {
  try {
    uni.removeStorageSync(key)
  } catch {
    console.warn(`[Storage] 删除失败: key=${key}`)
  }
}
