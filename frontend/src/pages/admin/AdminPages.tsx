/*
 * 管理画面のまだ中身のない画面。今は見出しだけで、中身は後のステップで作る
 * （お知らせ・操作ログはステップ7。マスタ管理は AdminMastersPage。docs/admin.md）
 */

export function AdminAnnouncementsPage() {
  return <AdminPlaceholder title="お知らせ" />
}

export function AdminOperationLogsPage() {
  return <AdminPlaceholder title="操作ログ" />
}

function AdminPlaceholder({ title }: { title: string }) {
  return (
    <>
      <h1 className="admin-page-title">{title}</h1>
      <p className="admin-page-placeholder">この画面は準備中です。</p>
    </>
  )
}
