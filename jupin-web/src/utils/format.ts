export function formatDateTime(dt: string | null | undefined): string {
  if (!dt) return '-'
  return dt.replace('T', ' ').substring(0, 19)
}

export function formatDate(dt: string | null | undefined): string {
  if (!dt) return '-'
  return dt.substring(0, 10)
}

export function formatPrice(p: number | string | null | undefined): string {
  if (p === null || p === undefined) return '0.00'
  return Number(p).toFixed(2)
}
