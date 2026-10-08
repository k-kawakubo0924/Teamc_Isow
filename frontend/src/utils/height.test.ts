import { describe, expect, it } from 'vitest'
import { formatHeight } from './height'

describe('formatHeight（身長の表示）', () => {
  it('設定されていれば「160cm」の形', () => {
    expect(formatHeight(160)).toBe('160cm')
  })

  it('未設定（null）なら null を返し、身長の部分を出さない', () => {
    expect(formatHeight(null)).toBeNull()
  })

  it('項目がない応答（undefined）でも「undefinedcm」にしない', () => {
    expect(formatHeight(undefined)).toBeNull()
  })

  it('数値でない値も出さない', () => {
    expect(formatHeight(Number.NaN)).toBeNull()
  })
})
