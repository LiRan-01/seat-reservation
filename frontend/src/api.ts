export type Seat = {
  id: number
  code: string
  feature: string
  status: string
  busy: boolean
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  })
  const body = await res.json()
  if (body.code !== 'OK') {
    throw new Error(body.message || body.code)
  }
  return body.data as T
}

export const api = {
  rooms: () => request<{ id: number; name: string; floor: number }[]>('/api/rooms'),
  seats: (roomId: number, start: string, end: string) =>
    request<Seat[]>(`/api/rooms/${roomId}/seats?start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`),
  reserve: (userId: number, seatId: number, start: string, end: string) =>
    request<{ id: number; checkinCode: string; status: string }>('/api/reservations', {
      method: 'POST',
      body: JSON.stringify({ userId, seatId, start, end })
    }),
  checkin: (token: string, deviceId = 'web-demo') =>
    request<{ reservationId: number; seatId: number; status: string }>('/api/checkin', {
      method: 'POST',
      body: JSON.stringify({ token, deviceId })
    }),
  away: (id: number, minutes = 30) =>
    request<{ id: number; status: string }>(`/api/reservations/${id}/away?minutes=${minutes}`, { method: 'POST' }),
  back: (id: number) => request<{ id: number; status: string }>(`/api/reservations/${id}/back`, { method: 'POST' }),
  cancel: (id: number, userId: number) =>
    request<{ id: number; status: string }>(`/api/reservations/${id}/cancel?userId=${userId}`, { method: 'POST' }),
  finish: (id: number) =>
    request<{ id: number; status: string }>(`/api/reservations/${id}/finish`, { method: 'POST' }),
  notifications: (userId: number) =>
    request<NotificationItem[]>(`/api/notifications?userId=${userId}`)
}

export type NotificationItem = {
  id: number
  type: string
  title: string
  content: string
  seatId: number | null
  read: boolean
  createdAt: string
}
