import { cleanup, render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { UserRow, type UserRowItem } from './UserRow'

afterEach(cleanup)

const GENDERS = [{ code: 'FEMALE', label: '女性' }]

function renderRow(heightCm: number | null | undefined, gender: string | null = 'FEMALE') {
  const user: UserRowItem = {
    id: 2,
    username: 'haru_st',
    profileImageUrl: null,
    heightCm: heightCm as number | null,
    gender,
    followingByMe: false,
  }
  // 自分の行として描画する（相談・フォローのボタンを出さず、表示する項目だけを確かめる）
  return render(
    <MemoryRouter>
      <UserRow user={user} isMe genders={GENDERS} pending={false} consultStatus={null} onFollow={() => {}}
        onConsultStatusChanged={() => {}} />
    </MemoryRouter>,
  )
}

describe('ユーザーの行（フォロー中一覧・ユーザーの検索結果）', () => {
  it.each([null, undefined])('身長が未設定（%s）なら、身長を出さずに性別だけを出す', (heightCm) => {
    const { container } = renderRow(heightCm)

    expect(container.querySelector('.follow-details')?.textContent).toBe('女性')
    expect(container.textContent).not.toMatch(/nullcm|undefinedcm/)
  })

  it('身長も性別も未設定なら、その行そのものを出さない', () => {
    const { container } = renderRow(null, null)

    expect(container.querySelector('.follow-details')).toBeNull()
  })

  it('身長が設定されていれば「160cm ・ 女性」', () => {
    const { container } = renderRow(160)

    expect(container.querySelector('.follow-details')?.textContent).toBe('160cm ・ 女性')
  })
})
