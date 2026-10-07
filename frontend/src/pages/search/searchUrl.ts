/**
 * 検索の条件（docs/search.md）。検索画面・検索結果画面の URL のクエリに入れて受け渡す
 * （結果画面から戻ったときに、選んだ条件が残るようにするため）。
 *
 * type が users のときはユーザーの検索で、categoryId・ageGroup は使わない（同時には選べない）。
 */
export type SearchConditions = {
  type: 'posts' | 'users'
  /** キーワード。空文字は指定なし */
  q: string
  /** ファッションの種類の ID（GET /api/masters の fashionCategories） */
  categoryId: number | null
  /** 投稿者の年代（GET /api/masters の ageGroups の code） */
  ageGroup: string | null
}

/** URL のクエリから条件を読む。不正な値は指定なしとして扱う */
export function readConditions(params: URLSearchParams): SearchConditions {
  const type = params.get('type') === 'users' ? 'users' : 'posts'
  const categoryId = params.get('categoryId')
  return {
    type,
    q: params.get('q') ?? '',
    categoryId: type === 'posts' && categoryId !== null && /^\d+$/.test(categoryId) ? Number(categoryId) : null,
    ageGroup: type === 'posts' ? params.get('ageGroup') || null : null,
  }
}

/** 条件を URL のクエリにする。指定なしの項目は入れない */
export function toParams(conditions: SearchConditions): URLSearchParams {
  const params = new URLSearchParams()
  if (conditions.type === 'users') params.set('type', 'users')
  if (conditions.q.trim() !== '') params.set('q', conditions.q.trim())
  if (conditions.type === 'posts') {
    if (conditions.categoryId !== null) params.set('categoryId', String(conditions.categoryId))
    if (conditions.ageGroup !== null) params.set('ageGroup', conditions.ageGroup)
  }
  return params
}

/** 検索結果画面の URL */
export function searchResultsUrl(conditions: SearchConditions): string {
  return `/search/results?${toParams(conditions)}`
}
