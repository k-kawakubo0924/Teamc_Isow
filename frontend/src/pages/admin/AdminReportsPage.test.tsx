import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AdminReportDetail, AdminReportList } from '../../api/admin'
import AdminReportDetailPage from './AdminReportDetailPage'
import AdminReportsPage from './AdminReportsPage'

/*
 * 管理画面の通報（docs/admin.md「通報」）の確認。
 * 一覧は対象ごとに1行。詳細は証拠・通報した人・状態の記録。措置の機能はまだないことを詳細に明記する。
 * 証拠の写真は /uploads ではなく、管理者向けの API から認証つきで読み込む
 */

const listReports = vi.fn<(params: { status: string; targetType: string; page: number }) => Promise<AdminReportList>>()
const fetchReport = vi.fn<(id: number) => Promise<AdminReportDetail>>()
const changeReportStatus = vi.fn<(id: number, status: string, memo: string) => Promise<unknown>>()
const fetchEvidenceImage = vi.fn<(caseId: number, fileId: number) => Promise<Blob>>()
vi.mock('../../api/admin', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/admin')>()),
  listReports: (params: { status: string; targetType: string; page: number }) => listReports(params),
  fetchReport: (id: number) => fetchReport(id),
  changeReportStatus: (id: number, status: string, memo: string) => changeReportStatus(id, status, memo),
  fetchEvidenceImage: (caseId: number, fileId: number) => fetchEvidenceImage(caseId, fileId),
}))
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'token', isLoggedIn: true }) }))

const LIST: AdminReportList = {
  items: [
    {
      caseId: 7,
      targetType: 'POST',
      targetId: 70,
      status: 'OPEN',
      summary: '秋のコーデ',
      reportCount: 3,
      reasons: [
        { reason: 'SPAM', count: 2 },
        { reason: 'OTHER', count: 1 },
      ],
      firstReportedAt: '2026-10-10T09:00:00',
      lastReportedAt: '2026-10-12T10:30:00',
      evidencePurged: false,
    },
    {
      caseId: 6,
      targetType: 'MESSAGE',
      targetId: 60,
      status: 'OPEN',
      summary: '@ren_027 のメッセージ',
      reportCount: 1,
      reasons: [{ reason: 'HARASSMENT', count: 1 }],
      firstReportedAt: '2026-10-11T09:00:00',
      lastReportedAt: '2026-10-11T09:00:00',
      evidencePurged: false,
    },
  ],
  page: 0,
  size: 20,
  hasNext: false,
}

function postDetail(overrides: Partial<AdminReportDetail> = {}): AdminReportDetail {
  return {
    caseId: 7,
    targetType: 'POST',
    targetId: 70,
    status: 'OPEN',
    reportCount: 1,
    firstReportedAt: '2026-10-10T09:00:00',
    lastReportedAt: '2026-10-10T09:00:00',
    closedAt: null,
    handledByUsername: null,
    adminMemo: null,
    evidencePurged: false,
    evidencePurgedAt: null,
    evidence: {
      post: {
        postId: 70,
        authorId: 2,
        authorUsername: 'haru_st',
        title: '秋のコーデ',
        fashionCategory: 'きれいめ',
        tags: ['古着'],
        wornItems: null,
        description: '<b>説明</b>\n2行目',
        referenceUrl: null,
        createdAt: '2026-10-09T08:00:00',
        editedAt: null,
      },
      user: null,
      messages: null,
    },
    evidenceFiles: [{ id: 101, messageId: null }],
    currentState: 'DELETED',
    reports: [
      { reporterId: 3, reporterUsername: 'aoi', reason: 'SPAM', comment: '宣伝です', reportedAt: '2026-10-10T09:00:00' },
    ],
    ...overrides,
  }
}

function messageDetail(): AdminReportDetail {
  const messages = Array.from({ length: 11 }, (_, i) => ({
    messageId: 50 + i,
    senderId: i % 2 === 0 ? 2 : 3,
    senderUsername: i % 2 === 0 ? 'ren_027' : 'aoi',
    body: `本文${i + 1}`,
    hasImage: i === 5,
    sentAt: '2026-10-11T09:00:00',
    reported: i === 5,
  }))
  return postDetail({
    caseId: 6,
    targetType: 'MESSAGE',
    targetId: 55,
    evidence: { post: null, user: null, messages },
    evidenceFiles: [{ id: 201, messageId: 55 }],
    currentState: 'EXISTS',
  })
}

beforeEach(() => {
  listReports.mockResolvedValue(LIST)
  fetchEvidenceImage.mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:evidence'), revokeObjectURL: vi.fn() }))
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  listReports.mockReset()
  fetchReport.mockReset()
  changeReportStatus.mockReset()
  fetchEvidenceImage.mockReset()
})

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/admin/reports" element={<AdminReportsPage />} />
        <Route path="/admin/reports/:caseId" element={<AdminReportDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('通報の一覧', () => {
  it('対象ごとに1行で、種類・要約・通報の数・理由の内訳・最新の日時を出し、既定は未対応だけを読む', async () => {
    renderAt('/admin/reports')

    await screen.findByText('秋のコーデ')
    const rows = screen.getAllByRole('row').slice(1)
    expect(rows).toHaveLength(2)
    expect(rows[0].textContent).toContain('投稿')
    expect(rows[0].textContent).toContain('3件')
    expect(rows[0].textContent).toContain('スパム・宣伝 2')
    expect(rows[0].textContent).toContain('2026/10/12 10:30')
    expect(rows[1].textContent).toContain('DM のメッセージ')
    expect(rows[1].textContent).toContain('@ren_027 のメッセージ')
    expect(within(rows[0]).getByRole('link', { name: '秋のコーデ' }).getAttribute('href')).toBe('/admin/reports/7')
    expect(listReports).toHaveBeenCalledWith({ status: 'OPEN', targetType: '', page: 0 })
  })

  it('状態・種類で絞り込める', async () => {
    renderAt('/admin/reports')
    await screen.findByText('秋のコーデ')

    fireEvent.change(screen.getByLabelText('状態'), { target: { value: 'RESOLVED' } })
    fireEvent.change(screen.getByLabelText('対象'), { target: { value: 'USER' } })

    await waitFor(() => expect(listReports).toHaveBeenLastCalledWith({ status: 'RESOLVED', targetType: 'USER', page: 0 }))
  })
})

describe('通報の詳細', () => {
  it('措置の機能がまだないこと（対応済みにしても何も起きないこと）を明記する', async () => {
    fetchReport.mockResolvedValue(postDetail())
    const { container } = renderAt('/admin/reports/7')

    await screen.findByText('秋のコーデ')
    const notice = container.querySelector('.admin-report-notice')?.textContent ?? ''
    expect(notice).toContain('措置')
    expect(notice).toContain('まだありません')
    expect(notice).toContain('「対応済み」にしても')
    expect(notice).toContain('状態を記録するだけ')
  })

  it('通報した時点の投稿の内容と、今の状態（削除された）と、通報した人・理由・詳しい内容を出す。本文は HTML として扱わない', async () => {
    fetchReport.mockResolvedValue(postDetail())
    const { container } = renderAt('/admin/reports/7')

    await screen.findByText('秋のコーデ')
    expect(container.textContent).toContain('@haru_st')
    expect(container.textContent).toContain('削除された')
    expect(container.querySelector('.admin-report-body')?.textContent).toBe('<b>説明</b>\n2行目')
    expect(container.querySelector('.admin-report-body b')).toBeNull()
    const reports = container.querySelector('.admin-report-reports')?.textContent ?? ''
    expect(reports).toContain('aoi')
    expect(reports).toContain('スパム・宣伝')
    expect(reports).toContain('宣伝です')
  })

  it('証拠の写真は管理者向けの API から読み込み、/uploads の URL は使わない', async () => {
    fetchReport.mockResolvedValue(postDetail())
    const { container } = renderAt('/admin/reports/7')

    await waitFor(() => expect(fetchEvidenceImage).toHaveBeenCalledWith(7, 101))
    const image = await waitFor(() => {
      const img = container.querySelector('img.admin-report-evidence-image')
      if (!img) throw new Error('まだ表示されていない')
      return img
    })
    expect(image.getAttribute('src')).toBe('blob:evidence')
    expect(container.innerHTML).not.toContain('/uploads')
  })

  it('DM は11件を並べ、通報されたメッセージがどれかを分かるようにする', async () => {
    fetchReport.mockResolvedValue(messageDetail())
    const { container } = renderAt('/admin/reports/6')

    await screen.findByText('本文1')
    const items = container.querySelectorAll('.admin-report-message')
    expect(items).toHaveLength(11)
    const reported = container.querySelectorAll('.admin-report-message-reported')
    expect(reported).toHaveLength(1)
    expect(reported[0].textContent).toContain('本文6')
    expect(reported[0].textContent).toContain('通報されたメッセージ')
    expect(items[5].getAttribute('aria-current')).toBe('true')
    // この画面を開いたことは記録されることを書く
    expect(container.textContent).toContain('DM の内容を表示したことは、操作ログに記録されます')
    await waitFor(() => expect(fetchEvidenceImage).toHaveBeenCalledWith(6, 201))
  })

  it('保存期間を過ぎて証拠を消した通報は、そのことを出す', async () => {
    fetchReport.mockResolvedValue(
      postDetail({ status: 'RESOLVED', evidence: null, evidenceFiles: [], evidencePurged: true, evidencePurgedAt: '2027-04-01T03:00:00' }),
    )
    const { container } = renderAt('/admin/reports/7')

    await waitFor(() => expect(container.textContent).toContain('保存期間（対応を終えてから180日）を過ぎたため、証拠は削除しました'))
  })

  it('「対応済みにする」でメモと一緒に状態を記録し、表示を変える', async () => {
    fetchReport.mockResolvedValue(postDetail())
    changeReportStatus.mockResolvedValue({
      caseId: 7,
      status: 'RESOLVED',
      closedAt: '2026-10-13T10:00:00',
      handledByUsername: 'admin',
      adminMemo: '投稿者に連絡した',
    })
    const { container } = renderAt('/admin/reports/7')
    await screen.findByText('秋のコーデ')

    fireEvent.change(screen.getByLabelText(/メモ/), { target: { value: '投稿者に連絡した' } })
    fireEvent.click(screen.getByRole('button', { name: '対応済みにする' }))

    await waitFor(() => expect(container.querySelector('.admin-report-status')?.textContent).toContain('対応済み'))
    expect(changeReportStatus).toHaveBeenCalledWith(7, 'RESOLVED', '投稿者に連絡した')
    expect(container.textContent).toContain('admin')
    expect(screen.getByRole('button', { name: '未対応に戻す' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: '対応済みにする' })).toBeNull()
  })
})
