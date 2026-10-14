import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReportRequest, ReportResult } from '../../api/reports'
import { ReportDialog } from './ReportDialog'

/*
 * 通報の入力（docs/admin.md「通報」）の確認。理由は対象に合ったものだけを出し、「その他」は詳しい内容が必須。
 * 送ったら、相手に伝わらないことと、ブロックもできることを伝える（自動ではブロックしない）
 */

const reportTarget = vi.fn<(request: ReportRequest) => Promise<ReportResult>>()
vi.mock('../../api/reports', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/reports')>()
  return { ...actual, reportTarget: (request: ReportRequest) => reportTarget(request) }
})
const blockUser = vi.fn<(id: number) => Promise<void>>()
vi.mock('../../api/blocks', () => ({ blockUser: (id: number) => blockUser(id) }))
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'test-token' }) }))

afterEach(() => {
  cleanup()
  reportTarget.mockReset()
  blockUser.mockReset()
})

function renderDialog(
  targetType: ReportRequest['targetType'] = 'POST',
  options: { onClose?: () => void; onBlocked?: () => void; blockable?: boolean } = {},
) {
  return render(
    <ReportDialog
      targetType={targetType}
      targetId={10}
      owner={{ id: 2, username: 'haru_st' }}
      blockable={options.blockable ?? true}
      onClose={options.onClose ?? vi.fn()}
      onBlocked={options.onBlocked}
    />,
  )
}

function reasonLabels(): string[] {
  return screen.getAllByRole('radio').map((radio) => radio.closest('label')?.textContent ?? '')
}

describe('理由の選択', () => {
  it('投稿では「他人の写真の無断転載」を出し、「なりすまし」は出さない', () => {
    renderDialog('POST')

    expect(reasonLabels()).toContain('他人の写真の無断転載')
    expect(reasonLabels()).not.toContain('なりすまし')
    expect(reasonLabels()).toContain('その他')
  })

  it('ユーザーでは「なりすまし」を出し、「他人の写真の無断転載」は出さない', () => {
    renderDialog('USER')

    expect(reasonLabels()).toContain('なりすまし')
    expect(reasonLabels()).not.toContain('他人の写真の無断転載')
  })

  it('理由を選ぶまでは送れない。「その他」は詳しい内容を書くまで送れない', () => {
    renderDialog('POST')
    const send = screen.getByRole('button', { name: '通報する' }) as HTMLButtonElement

    expect(send.disabled).toBe(true)
    fireEvent.click(screen.getByLabelText('その他'))
    expect(send.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/詳しい内容/), { target: { value: '詳しく書きました' } })
    expect(send.disabled).toBe(false)
  })

  it('詳しい内容は500文字までで、超えると送れない', () => {
    renderDialog('POST')
    fireEvent.click(screen.getByLabelText('スパム・宣伝'))

    fireEvent.change(screen.getByLabelText(/詳しい内容/), { target: { value: 'あ'.repeat(501) } })

    expect(screen.getByText('501 / 500')).toBeTruthy()
    expect((screen.getByRole('button', { name: '通報する' }) as HTMLButtonElement).disabled).toBe(true)
  })
})

describe('送信', () => {
  it('選んだ理由と詳しい内容を送り、相手に伝わらないことを書いた完了の表示に変わる', async () => {
    reportTarget.mockResolvedValue({ status: 'ACCEPTED' })
    renderDialog('POST')

    fireEvent.click(screen.getByLabelText('他人の写真の無断転載'))
    fireEvent.change(screen.getByLabelText(/詳しい内容/), { target: { value: '私の写真です' } })
    fireEvent.click(screen.getByRole('button', { name: '通報する' }))

    expect(await screen.findByText('通報を受け付けました')).toBeTruthy()
    expect(reportTarget).toHaveBeenCalledWith({
      targetType: 'POST',
      targetId: 10,
      reason: 'UNAUTHORIZED_PHOTO',
      comment: '私の写真です',
    })
    expect(screen.getByRole('dialog').textContent).toContain('通報したことは相手には伝わりません')
    // 自動ではブロックしない
    expect(blockUser).not.toHaveBeenCalled()
  })

  it('同じ対象をすでに通報していれば、そのことを出す', async () => {
    reportTarget.mockResolvedValue({ status: 'ALREADY_REPORTED' })
    renderDialog('USER')

    fireEvent.click(screen.getByLabelText('なりすまし'))
    fireEvent.click(screen.getByRole('button', { name: '通報する' }))

    expect(await screen.findByText('すでに通報済みです')).toBeTruthy()
  })

  it('送れなかったら、理由を出して入力を残す', async () => {
    reportTarget.mockRejectedValue(new Error('通報の上限に達しました。時間をおいて再度お試しください。'))
    renderDialog('POST')

    fireEvent.click(screen.getByLabelText('スパム・宣伝'))
    fireEvent.click(screen.getByRole('button', { name: '通報する' }))

    expect(await screen.findByText('通報の上限に達しました。時間をおいて再度お試しください。')).toBeTruthy()
    expect((screen.getByLabelText('スパム・宣伝') as HTMLInputElement).checked).toBe(true)
  })

  it('完了の後に「ブロックする」を選べ、確かめてからブロックする', async () => {
    reportTarget.mockResolvedValue({ status: 'ACCEPTED' })
    blockUser.mockResolvedValue(undefined)
    const onBlocked = vi.fn()
    renderDialog('POST', { onBlocked })
    fireEvent.click(screen.getByLabelText('スパム・宣伝'))
    fireEvent.click(screen.getByRole('button', { name: '通報する' }))
    await screen.findByText('通報を受け付けました')

    fireEvent.click(screen.getByRole('button', { name: '@haru_st さんをブロックする' }))
    const confirm = screen.getByRole('alertdialog')
    expect(confirm.textContent).toContain('@haru_st さんをブロックしますか？')
    fireEvent.click(within(confirm).getByRole('button', { name: 'ブロックする' }))

    await vi.waitFor(() => expect(onBlocked).toHaveBeenCalled())
    expect(blockUser).toHaveBeenCalledWith(2)
  })

  it('すでにブロックしている相手には、ブロックの案内を出さない', async () => {
    reportTarget.mockResolvedValue({ status: 'ACCEPTED' })
    renderDialog('USER', { blockable: false })
    fireEvent.click(screen.getByLabelText('なりすまし'))
    fireEvent.click(screen.getByRole('button', { name: '通報する' }))
    await screen.findByText('通報を受け付けました')

    expect(screen.queryByRole('button', { name: /ブロックする/ })).toBeNull()
  })
})
