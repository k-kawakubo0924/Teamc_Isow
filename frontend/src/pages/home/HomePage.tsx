import { useState } from 'react'
import { Link } from 'react-router'
import { fetchTimeline } from '../../api/posts'
import { HOME_COLUMNS, loadColumns, saveColumns, type Columns } from './columnSetting'
import { ColumnsSwitcher, EmptyMessage, PostGrid } from './PostGrid'
import './home.css'

type HomeTab = 'recommended' | 'following' | 'latest'

const TABS: { key: HomeTab; label: string }[] = [
  { key: 'recommended', label: 'おすすめ' },
  { key: 'following', label: 'フォロー中' },
  { key: 'latest', label: '新着' },
]

/**
 * ホーム画面（docs/home.md、design/home.png）。
 * 新着メッセージの欄は DM 機能で追加する。
 */
function HomePage() {
  const [tab, setTab] = useState<HomeTab>('recommended')
  const [columns, setColumns] = useState<Columns>(() => loadColumns(HOME_COLUMNS))

  const handleChangeColumns = (next: Columns) => {
    setColumns(next)
    saveColumns(HOME_COLUMNS, next)
  }

  return (
    <main className="home-page">
      <header className="home-header">
        {/* 通知一覧（docs/notification.md）ができるまでは表示のみ */}
        <span className="home-bell" role="img" aria-label="通知">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" aria-hidden="true">
            <path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2H4.5z" />
            <path d="M10 20.5a2 2 0 0 0 4 0" strokeLinecap="round" />
          </svg>
        </span>
        <h1 className="home-logo">ISHO</h1>
      </header>

      <div className="home-toolbar">
        <div className="home-tabs" role="tablist" aria-label="表示する投稿">
          {TABS.map((item) => (
            <button
              key={item.key}
              type="button"
              role="tab"
              id={`home-tab-${item.key}`}
              aria-selected={tab === item.key}
              aria-controls="home-tab-panel"
              className={`home-tab${tab === item.key ? ' home-tab-active' : ''}`}
              onClick={() => setTab(item.key)}
            >
              {item.label}
            </button>
          ))}
        </div>
        {/* 選んだ列は端末ごとに記憶する */}
        <ColumnsSwitcher columns={columns} onChange={handleChangeColumns} />
      </div>

      <section id="home-tab-panel" role="tabpanel" aria-labelledby={`home-tab-${tab}`} className="home-panel">
        {tab === 'following' ? (
          // ホームのフォロー中タブは、並び順を決めてから作る（docs/home.md）。それまでは API を呼ばずにこの表示にする
          <EmptyMessage title="フォロー中のユーザーがいません" />
        ) : (
          // タブを切り替えたら一覧を作り直す（読み込み中の通信は中断され、前のタブの結果は表示されない）
          <PostGrid
            key={tab}
            fetchPage={(page, token, signal) => fetchTimeline(tab, page, token, signal)}
            columns={columns}
            empty={
              <EmptyMessage title="まだ投稿がありません">
                <Link to="/post" className="home-empty-action">
                  投稿する
                </Link>
              </EmptyMessage>
            }
          />
        )}
      </section>
    </main>
  )
}

export default HomePage
